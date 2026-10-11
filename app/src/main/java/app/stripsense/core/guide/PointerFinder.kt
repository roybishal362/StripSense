package app.stripsense.core.guide

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A finger reaching into the picture: where its tip is and which way it points, as fractions of the picture.
 * [pointing]: the end of it is narrow like a finger, so its direction means something.
 */
data class Pointer(val x: Float, val y: Float, val dx: Float, val dy: Float, val pointing: Boolean)

/**
 * One small look at the table: which of it is the table, which is a hand, and which is a thing lying on it.
 *
 * Two things the phone could not do from printed text alone are done from this.
 *
 * A pointing finger. The hand model needs to see a palm. A person pointing at a strip under a phone shows the
 * back of a fist and one finger, cut off by the edge of the picture, and on real pictures of exactly that the
 * model found no hand at any size or threshold. Here the hand is the patch of skin that is not the colour of
 * the table and comes in from the edge of the picture; its point deepest into the picture is the fingertip.
 *
 * A strip's real outline. A box drawn around the lines of print that run the same way as the name sat beside a
 * strip that prints its name sideways. The strip itself is the patch around its name that is neither table nor
 * hand, whichever way its print runs.
 *
 * Skin on a table of its own colour, and a strip on a table of its own colour, cannot be told apart this way.
 * Then nothing is returned and the hand model, the touch rule and the print's own extent are used as before.
 */
class TableLook private constructor(val w: Int, val h: Int, private val hand: BooleanArray, private val thing: BooleanArray) {

    /** The finger in this look, if a hand reaches in from the edge. */
    fun pointer(): Pointer? {
        // Thin things are taken away first: a cable or a shadow line joined to the hand was taken for its fingertip.
        val solid = BooleanArray(w * h) { i ->
            val x = i % w
            val y = i / w
            hand[i] && (x == 0 || hand[i - 1]) && (x == w - 1 || hand[i + 1]) && (y == 0 || hand[i - w]) && (y == h - 1 || hand[i + w])
        }
        val patch = largestFromEdge(solid) ?: return null
        val share = patch.size.toDouble() / (w * h)
        if (share < SMALLEST || share > LARGEST) return null
        val edge = patch.filter { i -> i % w == 0 || i / w == 0 || i % w == w - 1 || i / w == h - 1 }
        // The fingertip is the point deepest into the picture: furthest from everywhere the hand crosses the edge.
        // "Furthest from where it came in" failed on a hand that crossed two edges: the far end of the arm won.
        var tip = -1
        var deepest = -1.0
        for (i in patch) {
            var nearest = Double.MAX_VALUE
            for (e in edge) { val d = hypot((i % w - e % w).toDouble(), (i / w - e / w).toDouble()); if (d < nearest) nearest = d }
            if (nearest > deepest) { deepest = nearest; tip = i }
        }
        if (tip < 0 || deepest < REACH_IN * w) return null
        val tx = tip % w
        val ty = tip / w
        // The finger's own direction: from the middle of the patch's end third to its tip.
        val near = patch.filter { i -> hypot((i % w - tx).toDouble(), (i / w - ty).toDouble()) <= deepest / 3 }
        var dx = tx - near.sumOf { it % w } / near.size.toDouble()
        var dy = ty - near.sumOf { it / w } / near.size.toDouble()
        if (hypot(dx, dy) < 0.5) { dx = tx - edge.sumOf { it % w } / edge.size.toDouble(); dy = ty - edge.sumOf { it / w } / edge.size.toDouble() }
        val length = hypot(dx, dy).coerceAtLeast(1e-6)
        val narrow = near.size <= NARROW * (deepest / 3) * (deepest / 3)
        // One step further along the finger: taking thin things away also took the finger's last row.
        val px = (tx + (dx / length).roundToInt()).coerceIn(0, w - 1)
        val py = (ty + (dy / length).roundToInt()).coerceIn(0, h - 1)
        return Pointer((px + 0.5f) / w, (py + 0.5f) / h, (dx / w).toFloat(), (dy / h).toFloat(), narrow)
    }

    /**
     * The outline of the thing each name is printed on. [names]: where each strip's name was read, in camera
     * picture pixels. Two strips that touch are one patch; it is divided between them by which name is nearer.
     * A name with no outline here is left out: the table is its colour, or the thing fills the picture.
     */
    fun outlines(names: Map<String, CropBox>, imageW: Int, imageH: Int): Map<String, CropBox> {
        if (imageW <= 0 || imageH <= 0 || names.isEmpty()) return emptyMap()
        val patches = HashMap<String, BooleanArray>()
        val centres = HashMap<String, Pair<Double, Double>>()
        for ((name, box) in names) {
            val left = (box.left * w / imageW - 1).coerceIn(0, w - 1)
            val right = (box.right * w / imageW + 1).coerceIn(0, w - 1)
            val top = (box.top * h / imageH - 1).coerceIn(0, h - 1)
            val bottom = (box.bottom * h / imageH + 1).coerceIn(0, h - 1)
            val starts = ArrayList<Int>()
            for (y in top..bottom) for (x in left..right) if (thing[y * w + x]) starts += y * w + x
            if (starts.isEmpty()) continue
            patches[name] = flood(starts)
            centres[name] = (left + right) / 2.0 to (top + bottom) / 2.0
        }
        val found = HashMap<String, CropBox>()
        for ((name, patch) in patches) {
            // Other names printed on the same patch: their strips touch this one.
            val rivals = patches.keys.filter { other -> other != name && centres.getValue(other).let { (x, y) -> patch[y.toInt() * w + x.toInt()] } }
            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1
            var cells = 0
            val own = centres.getValue(name)
            for (i in patch.indices) {
                if (!patch[i]) continue
                val x = i % w
                val y = i / w
                val mine = hypot(x - own.first, y - own.second)
                if (rivals.any { centres.getValue(it).let { (rx, ry) -> hypot(x - rx, y - ry) < mine } }) continue
                cells++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            if (cells == 0 || cells > LARGEST_THING * w * h) continue
            found[name] = CropBox(minX * imageW / w, minY * imageH / h, (maxX + 1) * imageW / w, (maxY + 1) * imageH / h)
        }
        return found
    }

    private fun flood(starts: List<Int>): BooleanArray {
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        for (s in starts) if (!seen[s]) { seen[s] = true; queue[tail++] = s }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0 && thing[i - 1] && !seen[i - 1]) { seen[i - 1] = true; queue[tail++] = i - 1 }
            if (x < w - 1 && thing[i + 1] && !seen[i + 1]) { seen[i + 1] = true; queue[tail++] = i + 1 }
            if (y > 0 && thing[i - w] && !seen[i - w]) { seen[i - w] = true; queue[tail++] = i - w }
            if (y < h - 1 && thing[i + w] && !seen[i + w]) { seen[i + w] = true; queue[tail++] = i + w }
        }
        return seen
    }

    /** The largest joined patch of the mask that reaches the edge of the picture. */
    private fun largestFromEdge(mask: BooleanArray): List<Int>? {
        val seen = BooleanArray(w * h)
        var best: List<Int>? = null
        val queue = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!mask[start] || seen[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            var onEdge = false
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) onEdge = true
                if (x > 0 && mask[i - 1] && !seen[i - 1]) { seen[i - 1] = true; queue[tail++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && !seen[i + 1]) { seen[i + 1] = true; queue[tail++] = i + 1 }
                if (y > 0 && mask[i - w] && !seen[i - w]) { seen[i - w] = true; queue[tail++] = i - w }
                if (y < h - 1 && mask[i + w] && !seen[i + w]) { seen[i + w] = true; queue[tail++] = i + w }
            }
            if (onEdge && tail > (best?.size ?: 0)) best = queue.copyOf(tail).toList()
        }
        return best
    }

    companion object {
        /** A colour must differ from the table by this much, as the sum of the three colour differences. */
        const val NOT_TABLE = 42

        /** The hand as a share of the picture: smaller is a speck, larger is the table or an arm across everything. */
        const val SMALLEST = 0.02
        const val LARGEST = 0.5

        /** The tip must be this deep in the picture, as a share of the picture's width. */
        const val REACH_IN = 0.15

        /** The end third of the hand covers at most this share of the square on its own length: a finger, not a palm. */
        const val NARROW = 0.6

        /** One colour must cover this much of the picture before it is taken for the table. */
        const val TABLE_SHARE = 0.2

        /** A thing larger than this share of the picture is not one strip. */
        const val LARGEST_THING = 0.35

        /** [pixels]: a small copy of the camera picture, row by row, [w] wide and [h] high. About 96 across is enough. */
        fun of(pixels: IntArray, w: Int, h: Int): TableLook? {
            if (w < 8 || h < 8 || pixels.size < w * h) return null
            val table = tableColour(pixels, w, h) ?: return null
            val hand = BooleanArray(w * h)
            val thing = BooleanArray(w * h)
            for (i in 0 until w * h) {
                val p = pixels[i]
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                if (abs(r - table[0]) + abs(g - table[1]) + abs(b - table[2]) <= NOT_TABLE) continue
                if (skin(r, g, b)) hand[i] = true else thing[i] = true
            }
            return TableLook(w, h, hand, thing)
        }

        /** The colour most of the picture has: the table. Null when no one colour covers enough of it to be a surface. */
        private fun tableColour(pixels: IntArray, w: Int, h: Int): IntArray? {
            val counts = IntArray(4096)
            for (i in 0 until w * h) counts[bin(pixels[i])]++
            val top = counts.indices.maxByOrNull { counts[it] } ?: return null
            val tr = top shr 8
            val tg = (top shr 4) and 15
            val tb = top and 15
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0
            for (i in 0 until w * h) {
                val p = pixels[i]
                val pr = (p shr 16) and 0xff
                val pg = (p shr 8) and 0xff
                val pb = p and 0xff
                if (abs((pr shr 4) - tr) <= 1 && abs((pg shr 4) - tg) <= 1 && abs((pb shr 4) - tb) <= 1) { r += pr; g += pg; b += pb; n++ }
            }
            if (n < w * h * TABLE_SHARE) return null
            return intArrayOf((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        }

        private fun bin(p: Int): Int = (((p shr 16) and 0xff) shr 4 shl 8) or (((p shr 8) and 0xff) shr 4 shl 4) or ((p and 0xff) shr 4)

        /**
         * Skin of any tone, down to very dark: the back of a dark hand in its own shadow under a phone is nearly
         * black, and with the usual lower limit on brightness only its palm side was found.
         */
        private fun skin(r: Int, g: Int, b: Int): Boolean {
            val y = 0.299 * r + 0.587 * g + 0.114 * b
            val cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
            val cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
            return y > 18 && cb in 77.0..127.0 && cr in 133.0..173.0
        }
    }
}

/** A pointing finger found without a hand model. See [TableLook]. */
object PointerFinder {
    fun find(pixels: IntArray, w: Int, h: Int): Pointer? = TableLook.of(pixels, w, h)?.pointer()
}
