package app.stripsense.ml

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.stripsense.core.hear.ClickFinder
import app.stripsense.core.hear.Pop
import app.stripsense.core.hear.PopWatch
import app.stripsense.data.Logs
import kotlin.math.sqrt

/**
 * Listens for a tablet pushed out of its strip, while the phone's question is open.
 *
 * The microphone's samples are measured as they arrive and thrown away: nothing is recorded, kept or sent.
 * The phone's own movement is watched at the same time, so a knock on the phone is not taken for a tablet.
 */
class TabletListener(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val sensors = context.getSystemService(SensorManager::class.java)
    @Volatile private var running = false
    @Volatile private var watch: PopWatch? = null
    @Volatile private var startedAt = 0L
    private var thread: Thread? = null

    /** How far the sound right now stands above the room, 0 to 1. Drawn on the screen so the person sees the phone listening. */
    @Volatile var loudness = 0f
        private set

    /** A tablet cannot be heard here: sharp sounds keep coming, or the room drowns it. The screen then asks for a press instead. */
    @Volatile var noisy = false
        private set

    private val last = FloatArray(3)
    private var lastSet = false
    private val knocks = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val dx = event.values[0] - last[0]
            val dy = event.values[1] - last[1]
            val dz = event.values[2] - last[2]
            // A sudden change from one reading to the next is a knock. Carrying or turning the phone changes slowly.
            if (lastSet && sqrt(dx * dx + dy * dy + dz * dz) >= KNOCK) watch?.jolt(SystemClock.elapsedRealtime() - startedAt)
            last[0] = event.values[0]; last[1] = event.values[1]; last[2] = event.values[2]
            lastSet = true
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun allowed(): Boolean = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Starts listening. [minLevel]: how loud a crack must be (PopWatch.levelFor). [speaking]: the phone is talking now,
     * so what it hears is itself. [onPop] is called once, on the main thread; listening has stopped by then.
     */
    @SuppressLint("MissingPermission")
    fun start(minLevel: Float, speaking: () -> Boolean, onPop: (Pop) -> Unit): Boolean {
        if (running || !allowed()) return false
        // The last listener gives the microphone back before a new one takes it.
        runCatching { thread?.join(400) }
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, RATE / 5 * 2))
        }.getOrNull() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        val watching = PopWatch(minLevel)
        watch = watching
        running = true
        lastSet = false
        startedAt = SystemClock.elapsedRealtime()
        sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(knocks, it, SensorManager.SENSOR_DELAY_GAME) }
        thread = Thread({ listen(rec, watching, minLevel, speaking, onPop) }, "StripSense-listen").apply { isDaemon = true; start() }
        return true
    }

    private fun listen(rec: AudioRecord, watching: PopWatch, minLevel: Float, speaking: () -> Boolean, onPop: (Pop) -> Unit) {
        val finder = ClickFinder(RATE)
        val buffer = ShortArray(RATE / 100)
        var samples = 0L
        var clicks = 0
        var pop: Pop? = null
        try {
            rec.startRecording()
            startedAt = SystemClock.elapsedRealtime()
            while (running && pop == null) {
                val n = rec.read(buffer, 0, buffer.size)
                if (n < 0) break
                if (n == 0) continue
                val at = samples * 1000 / RATE
                samples += n
                if (speaking()) watching.deaf(at - OWN_BEFORE_MS, at + OWN_AFTER_MS)
                for (click in finder.feed(buffer, n) { from, until -> watching.deaf(from, until) }) { watching.click(click); clicks++ }
                loudness = finder.loudness
                noisy = finder.floorDb > PopWatch.DROWNED_DB || watching.clicky(samples * 1000 / RATE + PopWatch.BUSY_SKIP_MS)
                pop = watching.poll(samples * 1000 / RATE)
            }
        } catch (e: Exception) {
            Logs.w("listening failed: ${e.javaClass.simpleName}")
        } finally {
            runCatching { rec.stop() }
            rec.release()
            loudness = 0f
            noisy = false
        }
        // Numbers only, never sound: what was heard, for setting how loud a tablet is on this phone.
        val loudest = watching.loudest
        Logs.i("tablet listener: ${samples / RATE} s, room ${finder.floorDb.toInt()}, clicks $clicks, needed ${minLevel.toInt()}, loudest ${loudest?.level?.toInt() ?: 0} above ${loudest?.above?.toInt() ?: 0} with ${loudest?.clicks ?: 0}, heard ${pop != null}")
        val heard = pop ?: return
        main.post { if (running && watch === watching) { stop(); onPop(heard) } }
    }

    /** Stops listening. Returns the loudest crack with company that was heard, for learning when the person pressed Yes. */
    fun stop(): Pop? {
        val heard = watch?.loudest
        running = false
        watch = null
        sensors?.unregisterListener(knocks)
        return heard
    }

    companion object {
        const val RATE = ClickFinder.RATE

        /** Metres per second squared, between two readings a fiftieth of a second apart. */
        private const val KNOCK = 2.5f
        private const val OWN_BEFORE_MS = 150L
        private const val OWN_AFTER_MS = 350L
    }
}
