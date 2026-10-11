package app.stripsense.ui.scan

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.stripsense.core.guide.Homing
import kotlinx.coroutines.delay

/**
 * Ticks and pulses toward the due strip. [closeness] is 0 to 1: how near the hand is to the strip, or with
 * no hand in view how near the strip is to the centre of the picture. Null means silence.
 * [key] names what is being followed; [onHeld] runs once when [locked] has lasted.
 * [toward]: which way to move. That way's own pattern is then felt (see FeelLanguage.pull); with no
 * direction it is a plain tick at both ends. [beep]: the ticks are heard as well as felt.
 */
@Composable
fun SceneHoming(closeness: Float?, locked: Boolean, key: Any?, toward: app.stripsense.core.guide.Toward? = null, handLed: Boolean = false, beep: Boolean = true, onHeld: () -> Unit) {
    val context = LocalContext.current
    // Quiet enough to sit under the spoken guidance.
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 40) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { runCatching { tone?.release() } } }
    val near by rememberUpdatedState(closeness)
    val on by rememberUpdatedState(locked)
    val way by rememberUpdatedState(toward)
    val held by rememberUpdatedState(onHeld)
    // A different strip starts again from nothing: the hold never carries over.
    LaunchedEffect(key) {
        var lockedSince: Long? = null
        var opened = false
        while (true) {
            val value = near ?: break
            val now = SystemClock.elapsedRealtime()
            var wait = Homing.intervalMs(value)
            if (!on) {
                lockedSince = null
                if (beep && !Feel.touchOnly) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 30) }
                val felt = way?.let { app.stripsense.core.guide.FeelLanguage.pull(it, value) } ?: app.stripsense.core.guide.FeelLanguage.nearer(value)
                Feel.play(context, felt, tick = true)
                // A way to move has several parts: it is not begun again before it has ended and the hand has rested.
                wait = Homing.periodMs(value, felt.lasts)
            } else if (lockedSince == null) {
                // There: one firm pulse and a higher tone, felt and heard as different from a tick.
                lockedSince = now
                if (beep && !Feel.touchOnly) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 180) }
                // With a finger on the strip, its rhythm and the yes are already being felt: this one waits its turn.
                Feel.play(context, app.stripsense.core.guide.FeelLanguage.yes(), tick = handLed)
            } else if (!opened && Homing.held(lockedSince, now)) {
                opened = true
                held()
            }
            delay(wait)
        }
    }
}
