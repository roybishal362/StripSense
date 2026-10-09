package app.stripsense.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/** One short vibration (30 ms) when an answer appears. */
fun buzz(context: Context) {
    runCatching {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            .vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
