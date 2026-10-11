package app.stripsense.ui.shell

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import app.stripsense.core.guide.Buzz
import app.stripsense.core.guide.Felt
import app.stripsense.data.Logs

/**
 * Drives the phone's vibration motors with the patterns of FeelLanguage.
 *
 * This phone has two motors, one at each end, and each can be driven by itself. So a tick can come from the end
 * the phone should move toward. On a phone with one motor every message is played on that one: the yes, the no
 * and the rhythms still work, and the way to move is then left to the voice.
 */
object Feel {
    /** The number, among the phone's motors, of the one at its top end. The family can swap it if it feels wrong. */
    @Volatile var topMotor = 1
    @Volatile private var busyUntil = 0L
    @Volatile private var movingUntil = 0L

    /** The phone says everything through the hand and stays silent (the family's "Touch only"). */
    @Volatile var touchOnly = false

    /** The phone's own motors are running, or have only just stopped: what the movement sensor feels now is the phone. */
    fun moving(now: Long = SystemClock.elapsedRealtime()): Boolean = now < movingUntil

    fun motors(context: Context): Int = runCatching { manager(context).vibratorIds.size }.getOrDefault(0)

    /**
     * Plays [felt]. A [tick] is one of the small ticks that lead the hand: it waits its turn, and is dropped while a
     * yes, a no or a medicine's rhythm is still being felt. Those are never cut off by a tick.
     */
    fun play(context: Context, felt: Felt, tick: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (tick && now < busyUntil) return
        val lasts = felt.lasts
        if (!tick) busyUntil = now + lasts + 120
        movingUntil = maxOf(movingUntil, now + lasts + 180)
        runCatching {
            val manager = manager(context)
            val ids = manager.vibratorIds
            if (ids.size < 2) {
                // One motor plays both ends as one, so a tick at one end followed by a buzz at the other is still felt as that.
                felt.merged()?.let { one(manager.defaultVibrator, effect(it)) }
                return
            }
            val top = if (topMotor in ids) topMotor else ids[1]
            val bottom = ids.first { it != top }
            val both = CombinedVibration.startParallel()
            felt.top?.let { both.addVibrator(top, effect(it)) }
            felt.bottom?.let { both.addVibrator(bottom, effect(it)) }
            if (felt.top == null && felt.bottom == null) return
            if (Build.VERSION.SDK_INT >= 33 && cameraBusy()) manager.vibrate(both.combine(), kind()) else manager.vibrate(both.combine())
        }.onFailure { Logs.w("feel failed: ${it.javaClass.simpleName}") }
    }

    fun stop(context: Context) { runCatching { manager(context).cancel() } }

    /**
     * How a message is sent while the camera is open. Measured on this phone: with the camera open it throws away
     * vibrations of no stated kind, and alarm, notification and ringtone ones (its own record calls them
     * "ignored"), and lets touch, accessibility, media and a few others through, at about two thirds of their
     * strength. Media came through strongest (0.71 against 0.65). Sent with no kind, as at first, nothing but the
     * shortest ticks was ever felt during a scan. With the camera closed a message of no stated kind is played at
     * full strength, so that is used then.
     */
    @androidx.annotation.RequiresApi(33)
    private fun kind(): VibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA)

    /** One motor, in the same way. Also used for the plain single pulses elsewhere in the app. */
    fun one(vibrator: android.os.Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33 && cameraBusy()) vibrator.vibrate(effect, kind()) else vibrator.vibrate(effect)
    }

    @Volatile private var cameraOpen = 0
    @Volatile private var cameraClosedAt = 0L

    /** Told by every screen that opens the camera, and again when it closes it. */
    fun camera(open: Boolean) {
        if (open) cameraOpen++ else { cameraOpen = maxOf(0, cameraOpen - 1); cameraClosedAt = SystemClock.elapsedRealtime() }
    }

    /** The camera is open, or was a moment ago: the phone may still be holding vibrations back. */
    private fun cameraBusy(): Boolean = cameraOpen > 0 || SystemClock.elapsedRealtime() - cameraClosedAt < CAMERA_LINGER_MS
    private const val CAMERA_LINGER_MS = 2500L

    private fun effect(buzz: Buzz): VibrationEffect =
        VibrationEffect.createWaveform(buzz.times.toLongArray(), buzz.strengths.toIntArray(), -1)

    private fun manager(context: Context) = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
}
