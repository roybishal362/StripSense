package app.stripsense.reminders

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import app.stripsense.data.Logs

/**
 * Rings like an alarm clock while the reminder is on the screen: the chime and a long vibration, again every few
 * seconds, for one minute or until the person presses anything. One notification chime was missed in another
 * room; an alarm is not.
 */
class AlarmRinger(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var startedAt = 0L
    @Volatile var ringing = false
        private set

    /** What the alarm feels like in the hand: a long buzz and then the rhythm of the medicine it is for. */
    var felt: app.stripsense.core.guide.Felt? = null

    /** [quiet]: true while the phone is speaking, so the chime waits for the sentence. [onStop] runs when the minute is over. */
    fun start(quiet: () -> Boolean, onStop: () -> Unit) {
        stop()
        ringing = true
        startedAt = System.currentTimeMillis()
        val cycle = object : Runnable {
            override fun run() {
                if (!ringing) return
                if (System.currentTimeMillis() - startedAt > RING_MS) { stop(); onStop(); return }
                if (!quiet()) { if (!app.stripsense.ui.shell.Feel.touchOnly) chime(); buzz() }
                main.postDelayed(this, EVERY_MS)
            }
        }
        main.post(cycle)
    }

    fun stop() {
        ringing = false
        main.removeCallbacksAndMessages(null)
        runCatching { player?.release() }
        player = null
        runCatching { vibrator().cancel() }
        app.stripsense.ui.shell.Feel.stop(context)
    }

    private fun chime() {
        runCatching {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(context, Uri.parse("android.resource://${context.packageName}/raw/medicine_chime"))
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.onFailure { Logs.w("alarm chime failed: ${it.javaClass.simpleName}") }
    }

    private fun buzz() {
        felt?.let { app.stripsense.ui.shell.Feel.play(context, it); return }
        runCatching { vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 300, 700), -1)) }
    }

    private fun vibrator() = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    companion object {
        const val RING_MS = 60_000L
        const val EVERY_MS = 4_500L
    }
}
