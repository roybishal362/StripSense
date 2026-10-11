package app.stripsense.ui.shell

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/** One short vibration (30 ms) when an answer appears. */
fun buzz(context: Context) = pulse(context, 30)

/** One vibration of [ms]. Short for a homing tick, long when the due strip is centred. */
fun pulse(context: Context, ms: Long) {
    runCatching {
        // Through Feel, so the phone does not throw it away while the camera is open (see Feel.kt).
        Feel.one((context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator,
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
