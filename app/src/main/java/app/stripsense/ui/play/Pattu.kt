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
import androidx.compose.ui.graphics.lerp
import app.stripsense.core.play.Mood
import app.stripsense.core.play.Skin
import kotlin.math.cos
import kotlin.math.sin

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
private val Dull = Color(0xFF9AA8A2)

/** The colours of one Pattu: its body, the darker edge and limbs, the shine on it, and its pale belly. */
private class Look(val body: Color, val deep: Color, val shine: Color, val belly: Color)

private val Looks = mapOf(
    Skin.SPROUT to Look(Color(0xFF2FBF8A), Color(0xFF1BA070), Color(0x968CE8C4), Color(0xFFDEF8EC)),
    Skin.PUPPY to Look(Color(0xFFE6B37C), Color(0xFFC68A4C), Color(0x96FFE2BE), Color(0xFFFFF4E4)),
    Skin.KITTEN to Look(Color(0xFFB3BCCB), Color(0xFF8E99AB), Color(0x96E4E9F1), Color(0xFFF7F8FB)),
    Skin.FAIRY to Look(Color(0xFFF7AED6), Color(0xFFE184BB), Color(0x96FFDDF0), Color(0xFFFFF1F8)),
    Skin.IMP to Look(Color(0xFFEC6A64), Color(0xFFC84542), Color(0x96FFB3AD), Color(0xFFFFFFFF)),
)

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

    fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Color) {
        val a = at(x1, y1); val b = at(x2, y2); val c = at(x3, y3)
        scope.drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)
    }
}

/**
 * Pattu, the app's character: a round little sprout. "Patta" is both a leaf and a strip of tablets.
 *
 * [stage] is how grown it is: 0 a seed, 1 a sprout with two leaves, 2 a bud, 3 a flower, 4 a flower that shines.
 * [mood] is its face. [skin] is what it is dressed as; whatever that is, the sprout stays on its head.
 * It breathes, blinks and sways by itself; [hop] (0 to 1) lifts it off the ground, for a tap or a dose just marked.
 * [still]: no movement at all, for a small picture in a list.
 */
@Composable
fun PattuFigure(stage: Int, mood: Mood, modifier: Modifier = Modifier, hop: Float = 0f, still: Boolean = false, skin: Skin = Skin.SPROUT) {
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
        val look = Looks.getValue(skin)
        val body = if (sad) lerp(look.body, Dull, 0.5f) else look.body
        val deep = if (sad) lerp(look.deep, Dull, 0.5f) else look.deep
        val droop = if (sad) 26f else 0f
        val wave = if (still) 0f else sway * 4f
        ground.oval(0.5f, 0.945f, 0.27f * (1f - 0.35f * hop), 0.032f, Color(0x26000000))
        // What is behind it: a tail, or wings.
        when (skin) {
            Skin.PUPPY -> c.oval(0.86f, 0.80f, 0.085f, 0.034f, deep, -38f + wave * 3f)
            Skin.KITTEN -> c.arc(0.84f, 0.74f, 0.09f, 0.13f, 255f, 190f, 0.045f, deep)
            Skin.FAIRY -> for (side in listOf(-1f, 1f)) {
                c.oval(0.5f + side * 0.34f, 0.50f, 0.15f, 0.24f, Color(0xC8CFE9FF), side * (22f + wave))
                c.oval(0.5f + side * 0.33f, 0.74f, 0.10f, 0.14f, Color(0xB4E3F3FF), side * (-18f + wave))
            }
            Skin.IMP -> {
                c.arc(0.86f, 0.76f, 0.10f, 0.11f, 250f, 170f, 0.022f, Color(0xFFA32B2B))
                c.tri(0.925f, 0.60f, 0.985f, 0.66f, 0.905f, 0.68f, Color(0xFFA32B2B))
            }
            Skin.SPROUT -> Unit
        }
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
        // Ears and horns that stand up from behind the head.
        when (skin) {
            Skin.KITTEN -> for (side in listOf(-1f, 1f)) {
                c.tri(0.5f + side * 0.29f, 0.52f, 0.5f + side * 0.25f, 0.305f, 0.5f + side * 0.08f, 0.40f, deep)
                c.tri(0.5f + side * 0.255f, 0.47f, 0.5f + side * 0.235f, 0.35f, 0.5f + side * 0.14f, 0.41f, Color(0xFFFFB3C1))
            }
            Skin.IMP -> for (side in listOf(-1f, 1f))
                c.tri(0.5f + side * 0.24f, 0.44f, 0.5f + side * 0.20f, 0.29f, 0.5f + side * 0.09f, 0.385f, Color(0xFFA32B2B))
            else -> Unit
        }
        // Feet, body, the shine on it and its pale belly.
        c.oval(0.39f, 0.915f, 0.085f, 0.038f, deep)
        c.oval(0.61f, 0.915f, 0.085f, 0.038f, deep)
        val squash = if (still) 0f else 0.006f * breath
        c.oval(0.5f, 0.655f, 0.345f + squash, 0.285f - squash, deep)
        c.oval(0.5f, 0.64f, 0.335f + squash, 0.275f - squash, body)
        c.oval(0.385f, 0.50f, 0.15f, 0.075f, look.shine, -18f)
        c.oval(0.5f, 0.775f, 0.19f, 0.12f, look.belly)
        // A puppy's ears hang down the sides of its head.
        if (skin == Skin.PUPPY) for (side in listOf(-1f, 1f))
            c.oval(0.5f + side * 0.30f, 0.52f, 0.075f, 0.15f, lerp(deep, Ink, 0.25f), side * -14f)
        // The little devil doctor has something to listen with round its neck.
        if (skin == Skin.IMP) {
            c.arc(0.5f, 0.775f, 0.11f, 0.085f, 15f, 150f, 0.016f, Color(0xFF4A5560))
            c.oval(0.5f, 0.865f, 0.026f, 0.026f, Color(0xFF8A96A3))
        }
        // Its little arms: up when it is proud.
        if (mood == Mood.PROUD) {
            c.line(0.185f, 0.60f, 0.10f, 0.47f, 0.065f, deep)
            c.line(0.815f, 0.60f, 0.90f, 0.47f, 0.065f, deep)
        } else {
            c.oval(0.175f, 0.70f, 0.05f, 0.075f, deep, 18f)
            c.oval(0.825f, 0.70f, 0.05f, 0.075f, deep, -18f)
        }
        // A fairy carries a wand with a star on it.
        if (skin == Skin.FAIRY) {
            val tipX = if (mood == Mood.PROUD) 0.95f else 0.93f
            val tipY = if (mood == Mood.PROUD) 0.34f else 0.52f
            c.line(if (mood == Mood.PROUD) 0.90f else 0.84f, if (mood == Mood.PROUD) 0.47f else 0.68f, tipX, tipY, 0.016f, Color(0xFFB98A2E))
            for (i in 0 until 4) {
                val a = Math.toRadians(45.0 * i)
                c.line(tipX - 0.045f * cos(a).toFloat(), tipY - 0.045f * sin(a).toFloat(), tipX + 0.045f * cos(a).toFloat(), tipY + 0.045f * sin(a).toFloat(), 0.014f, Gold)
            }
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
            // A kitten's whiskers.
            if (skin == Skin.KITTEN) {
                c.line(0.5f + side * 0.20f, 0.675f, 0.5f + side * 0.345f, 0.655f, 0.008f, Ink)
                c.line(0.5f + side * 0.20f, 0.695f, 0.5f + side * 0.345f, 0.71f, 0.008f, Ink)
            }
        }
        // A puppy and a kitten have a nose, and a mouth that hangs from it.
        val snout = skin == Skin.PUPPY || skin == Skin.KITTEN
        if (snout) {
            c.oval(0.5f, 0.668f, if (skin == Skin.PUPPY) 0.034f else 0.022f, if (skin == Skin.PUPPY) 0.024f else 0.016f, if (skin == Skin.PUPPY) Ink else Color(0xFFFF8FA6))
            if (mood != Mood.SAD && mood != Mood.WAITING && mood != Mood.SLEEPY) {
                c.arc(0.47f, 0.692f, 0.03f, 0.028f, 10f, 160f, 0.014f, Ink)
                c.arc(0.53f, 0.692f, 0.03f, 0.028f, 10f, 160f, 0.014f, Ink)
                // The puppy's tongue is out.
                if (skin == Skin.PUPPY) c.oval(0.5f, 0.748f, 0.026f, 0.034f, Tongue)
            }
        }
        when (mood) {
            Mood.HAPPY -> if (!snout) c.arc(0.5f, 0.695f, 0.05f, 0.04f, 20f, 140f, 0.018f, Ink)
            Mood.PROUD -> if (!snout) {
                c.pie(0.5f, 0.69f, 0.07f, 0.075f, 0f, 180f, Ink)
                c.oval(0.5f, 0.735f, 0.038f, 0.022f, Tongue)
            }
            Mood.WAITING -> c.oval(0.5f, 0.725f, 0.024f, 0.028f, Ink)
            Mood.SAD -> {
                c.arc(0.5f, 0.755f, 0.045f, 0.035f, 200f, 140f, 0.018f, Ink)
                c.oval(0.36f, 0.72f, 0.02f, 0.03f, Tear)
            }
            Mood.SLEEPY -> {
                c.oval(0.5f, 0.72f, 0.018f, 0.014f, Ink)
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
