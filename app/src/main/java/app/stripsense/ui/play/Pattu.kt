package app.stripsense.ui.play

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import app.stripsense.core.play.Mood
import kotlin.math.cos
import kotlin.math.sin

private val Body = Color(0xFF2FBF8A)
private val BodyDeep = Color(0xFF1BA070)
private val Shine = Color(0x968CE8C4)
private val Belly = Color(0xFFDEF8EC)
private val Leaf = Color(0xFF3FD39A)
private val LeafDeep = Color(0xFF22B07C)
private val Stem = Color(0xFF1E9E6E)
private val Ink = Color(0xFF16302A)
private val Cheek = Color(0xE0FF9DB0)
private val Petal = Color(0xFFFF7A96)
private val PetalDeep = Color(0xFFF05C7E)
private val Gold = Color(0xFFFFCC4D)
private val Tongue = Color(0xFFFF6F8B)
private val Tear = Color(0xE678C4FF)
private val SadBody = Color(0xFF74B096)
private val SadDeep = Color(0xFF5C967E)

/** Draws in a square of side [u] whose corner is at [ox], [oy]; every measure is a part of that side, as on the design sheet. */
private class Pad(val scope: DrawScope, val ox: Float, val oy: Float, val u: Float) {
    fun at(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)

    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, turn: Float = 0f) = with(scope) {
        rotate(turn, at(cx, cy)) { drawOval(color, at(cx - rx, cy - ry), Size(2 * rx * u, 2 * ry * u)) }
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Color) =
        scope.drawLine(color, at(x1, y1), at(x2, y2), strokeWidth = w * u, cap = StrokeCap.Round)

    fun arc(cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float, w: Float, color: Color) =
        scope.drawArc(color, start, sweep, false, at(cx - rx, cy - ry), Size(2 * rx * u, 2 * ry * u), style = Stroke(w * u, cap = StrokeCap.Round))

    fun pie(cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float, color: Color) =
        scope.drawArc(color, start, sweep, true, at(cx - rx, cy - ry), Size(2 * rx * u, 2 * ry * u))
}

/**
 * Pattu, the app's character: a round green sprout. "Patta" is both a leaf and a strip of tablets.
 *
 * [stage] is how grown it is: 0 a seed, 1 a sprout with two leaves, 2 a bud, 3 a flower, 4 a flower that shines.
 * [mood] is its face. It breathes, blinks and sways by itself; [hop] (0 to 1) lifts it off the ground, for a tap or
 * a dose just marked. [still]: no movement at all, for a small picture in a list.
 */
@Composable
fun PattuFigure(stage: Int, mood: Mood, modifier: Modifier = Modifier, hop: Float = 0f, still: Boolean = false) {
    val life = rememberInfiniteTransition(label = "pattu")
    val breath by life.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val sway by life.animateFloat(-1f, 1f, infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val blink by life.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 3800
        0f at 0
        0f at 3450
        1f at 3560
        0f at 3700
    }), label = "blink")
    Canvas(modifier) {
        val u = minOf(size.width, size.height)
        val lift = if (still) 0f else -0.012f * breath - 0.13f * hop
        val c = Pad(this, (size.width - u) / 2, (size.height - u) / 2 + lift * u, u)
        val ground = Pad(this, (size.width - u) / 2, (size.height - u) / 2, u)
        val sad = mood == Mood.SAD
        val body = if (sad) SadBody else Body
        val deep = if (sad) SadDeep else BodyDeep
        val droop = if (sad) 26f else 0f
        val wave = if (still) 0f else sway * 4f
        ground.oval(0.5f, 0.945f, 0.27f * (1f - 0.35f * hop), 0.032f, Color(0x26000000))
        // What grows on its head.
        if (stage >= 1) {
            c.line(0.5f, 0.40f, 0.5f, 0.25f, 0.04f, Stem)
            val big = if (stage >= 2) 1f else 0.78f
            c.oval(0.5f - 0.125f * big, 0.205f + droop * 0.0022f, 0.15f * big, 0.078f * big, Leaf, -30f + droop + wave)
            c.oval(0.5f + 0.125f * big, 0.205f + droop * 0.0022f, 0.15f * big, 0.078f * big, LeafDeep, 30f - droop + wave)
        } else c.oval(0.5f, 0.345f, 0.045f, 0.05f, Leaf)
        if (stage == 2) {
            c.oval(0.5f, 0.165f, 0.05f, 0.06f, PetalDeep)
            c.oval(0.5f, 0.18f, 0.05f, 0.045f, LeafDeep)
        }
        if (stage >= 3) {
            for (i in 0 until 5) {
                val a = Math.toRadians(-90.0 + 72 * i + wave * 2)
                c.oval(0.5f + 0.072f * cos(a).toFloat(), 0.13f + 0.072f * sin(a).toFloat(), 0.058f, 0.058f, if (i % 2 == 0) Petal else PetalDeep)
            }
            c.oval(0.5f, 0.13f, 0.045f, 0.045f, Gold)
        }
        if (stage >= 4) for ((x, y, r) in listOf(Triple(0.14f, 0.20f, 0.03f), Triple(0.86f, 0.30f, 0.024f), Triple(0.84f, 0.10f, 0.02f))) {
            val glow = r * (0.7f + 0.3f * breath)
            c.line(x - glow, y, x + glow, y, 0.012f, Gold)
            c.line(x, y - glow, x, y + glow, 0.012f, Gold)
        }
        // Feet, body, the shine on it and its pale belly.
        c.oval(0.39f, 0.915f, 0.085f, 0.038f, deep)
        c.oval(0.61f, 0.915f, 0.085f, 0.038f, deep)
        val squash = if (still) 0f else 0.006f * breath
        c.oval(0.5f, 0.655f, 0.345f + squash, 0.285f - squash, deep)
        c.oval(0.5f, 0.64f, 0.335f + squash, 0.275f - squash, body)
        c.oval(0.385f, 0.50f, 0.15f, 0.075f, Shine, -18f)
        c.oval(0.5f, 0.775f, 0.19f, 0.12f, Belly)
        // Its little arms: up when it is proud.
        if (mood == Mood.PROUD) {
            c.line(0.185f, 0.60f, 0.10f, 0.47f, 0.065f, deep)
            c.line(0.815f, 0.60f, 0.90f, 0.47f, 0.065f, deep)
        } else {
            c.oval(0.175f, 0.70f, 0.05f, 0.075f, deep, 18f)
            c.oval(0.825f, 0.70f, 0.05f, 0.075f, deep, -18f)
        }
        // The face.
        val ey = 0.615f
        for (side in listOf(-1f, 1f)) {
            val ex = 0.5f + side * 0.125f
            when (mood) {
                Mood.PROUD -> c.arc(ex, ey + 0.02f, 0.05f, 0.05f, 200f, 140f, 0.022f, Ink)
                Mood.SLEEPY -> c.arc(ex, ey - 0.015f, 0.05f, 0.04f, 20f, 140f, 0.02f, Ink)
                else -> {
                    val wide = mood == Mood.WAITING
                    val open = if (still) 1f else 1f - 0.92f * blink
                    c.oval(ex, ey, if (wide) 0.06f else 0.053f, (if (wide) 0.072f else 0.064f) * open, Ink)
                    if (open > 0.5f) {
                        c.oval(ex + 0.018f, ey - 0.024f + (if (sad) 0.02f else 0f), 0.02f, 0.02f, Color.White)
                        c.oval(ex - 0.02f, ey + 0.026f, 0.009f, 0.009f, Color(0xDCFFFFFF))
                    }
                    // Sad brows rise in the middle; waiting brows are lifted.
                    if (sad) c.line(ex - side * 0.065f, ey - 0.108f, ex + side * 0.04f, ey - 0.086f, 0.016f, Ink)
                    if (wide) c.line(ex - 0.05f, ey - 0.118f, ex + 0.05f, ey - 0.118f + 0.012f * side, 0.014f, Ink)
                }
            }
            c.oval(0.5f + side * 0.235f, 0.70f, 0.058f, 0.034f, Cheek)
        }
        when (mood) {
            Mood.HAPPY -> c.arc(0.5f, 0.695f, 0.05f, 0.04f, 20f, 140f, 0.018f, Ink)
            Mood.PROUD -> {
                c.pie(0.5f, 0.69f, 0.07f, 0.075f, 0f, 180f, Ink)
                c.oval(0.5f, 0.735f, 0.038f, 0.022f, Tongue)
            }
            Mood.WAITING -> c.oval(0.5f, 0.715f, 0.024f, 0.028f, Ink)
            Mood.SAD -> {
                c.arc(0.5f, 0.745f, 0.045f, 0.035f, 200f, 140f, 0.018f, Ink)
                c.oval(0.36f, 0.72f, 0.02f, 0.03f, Tear)
            }
            Mood.SLEEPY -> {
                c.oval(0.5f, 0.71f, 0.018f, 0.014f, Ink)
                for ((x, y, s) in listOf(Triple(0.80f, 0.30f, 0.035f), Triple(0.88f, 0.21f, 0.026f))) {
                    c.line(x - s, y - s, x + s, y - s, 0.012f, Ink)
                    c.line(x + s, y - s, x - s, y + s, 0.012f, Ink)
                    c.line(x - s, y + s, x + s, y + s, 0.012f, Ink)
                }
            }
        }
    }
}

/** The flame of a row of days. Grey until there is a row. */
@Composable
fun Flame(lit: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        fun drop(left: Float, top: Float, w: Float, h: Float) = Path().apply {
            moveTo(left + w * 0.5f, top)
            cubicTo(left + w * 0.95f, top + h * 0.32f, left + w, top + h * 0.72f, left + w * 0.5f, top + h)
            cubicTo(left, top + h * 0.72f, left + w * 0.05f, top + h * 0.32f, left + w * 0.5f, top)
            close()
        }
        drawPath(drop(0f, 0f, size.width, size.height), if (lit) Color(0xFFFF8A1F) else Color(0xFFC9D2CE))
        drawPath(drop(size.width * 0.26f, size.height * 0.42f, size.width * 0.48f, size.height * 0.56f), if (lit) Color(0xFFFFD23F) else Color(0xFFE3E9E6))
    }
}

/** A ring that fills as today's doses are marked. */
@Composable
fun Ring(part: Float, modifier: Modifier = Modifier, track: Color = Color(0x33FFFFFF), fill: Color = Color.White) {
    Canvas(modifier) {
        val w = size.minDimension * 0.14f
        val inset = Offset(w / 2, w / 2)
        val box = Size(size.width - w, size.height - w)
        drawArc(track, 0f, 360f, false, inset, box, style = Stroke(w))
        if (part > 0f) drawArc(fill, -90f, 360f * part.coerceIn(0f, 1f), false, inset, box, style = Stroke(w, cap = StrokeCap.Round))
    }
}
