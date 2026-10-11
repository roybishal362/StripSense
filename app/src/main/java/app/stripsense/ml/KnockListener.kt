package app.stripsense.ml

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import app.stripsense.core.guide.DoubleKnock
import app.stripsense.data.Logs
import app.stripsense.ui.shell.Feel

/**
 * Waits for two knocks on the phone while a question is open. The readings of the movement sensor are looked at
 * as they arrive and nothing is kept. While the phone's own motors run, what the sensor feels is the phone itself.
 */
class KnockListener(context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private var knock = DoubleKnock()
    private var onKnock: (() -> Unit)? = null
    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val now = SystemClock.elapsedRealtime()
            if (knock.reading(event.values[0], event.values[1], event.values[2], now, own = Feel.moving(now))) {
                Logs.i("knock: two knocks felt")
                onKnock?.invoke()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /** [onKnock] is called on the main thread for each pair of knocks, until [stop]. */
    fun start(onKnock: () -> Unit): Boolean {
        stop()
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        knock = DoubleKnock()
        this.onKnock = onKnock
        // Two hundred readings a second: a knock is over in a few hundredths of a second. Faster than this an app
        // may not ask for without one more permission, and asking for "fastest" is refused outright.
        return runCatching { sensors.registerListener(listener, sensor, READING_MICROSECONDS) }
            .onFailure { Logs.w("knock listener refused: ${it.javaClass.simpleName}") }.getOrDefault(false)
    }

    fun stop() {
        onKnock = null
        sensors?.unregisterListener(listener)
    }

    companion object {
        const val READING_MICROSECONDS = 5000
    }
}
