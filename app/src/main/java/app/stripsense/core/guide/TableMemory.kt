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
enum class PlacedKind { CHECKED, NAME, REFUSED, UNKNOWN }

/** A strip the phone has read, and where it lay in the camera image when it was last seen clearly. */
data class PlacedStrip(val key: String, val med: Medicine?, val kind: PlacedKind, val box: CropBox, val readAt: Long, val heldAt: Long,
    val checkedAt: Long = -1)

/**
 * Where each strip lies, for the moments the camera cannot read it.
 *
 * The rule is narrow on purpose. A remembered strip stands in for a read one only when there is a reason its
 * print cannot be read: a hand is over it, or it has slid out of the picture. A strip that lies open in the
 * picture and is not being read is not there any more. It used to be kept for fifteen seconds whenever a hand
 * was in view, and a person swapping strips by hand always has a hand in view: the phone went on talking about
 * the strip that had been taken away.
 */
class TableMemory {
    private val strips = LinkedHashMap<String, PlacedStrip>()

    /** A strip read in this frame. Its place and what it is replace what was remembered. */
    fun see(key: String, med: Medicine?, kind: PlacedKind, box: CropBox, now: Long) {
        // The same place cannot hold two strips. Print being read there now is what is there now.
        // A named strip clears more readily than a patch nobody could name, which may be the old strip's own print.
        val limit = if (kind == PlacedKind.UNKNOWN) SAME_PLACE else NAMED_SAME_PLACE
        strips.values.removeIf { it.key != key && it.readAt != now && overlap(it.box, box) > limit }
        // A reaching hand hides the strength a moment before it is seen as a hand. The strip was checked seconds ago
        // and has not left its place, so it stays checked for a short while.
        val old = strips[key]
        val checkedAt = if (kind == PlacedKind.CHECKED) now else old?.checkedAt ?: -1
        val kept = if (kind == PlacedKind.NAME && checkedAt >= 0 && now - checkedAt <= KEEP_CHECKED_MS) PlacedKind.CHECKED else kind
        strips[key] = PlacedStrip(key, med, kept, box, now, now, checkedAt)
    }

    /**
     * The same, with a hand in view over a strip that has not moved: it is still being read, but a half-covered
     * strip reads as a smaller patch, so its remembered place is kept.
     */
    fun keep(key: String, med: Medicine?, kind: PlacedKind, box: CropBox, now: Long) {
        val old = strips[key]
        see(key, med, kind, old?.box ?: box, now)
    }

    /**
     * The phone moved. Strips that were not read in this frame keep their place on the table, which means their
     * place in the picture moves the other way with everything else. [live]: the strips read in this frame.
     */
    fun drift(dx: Int, dy: Int, live: Set<String>) {
        if (dx == 0 && dy == 0) return
        for ((key, strip) in strips) if (key !in live) strips[key] = strip.copy(box = strip.box.moved(dx, dy))
    }

    /**
     * What is on the table now. [covered]: the strips a hand is over, as the picture itself shows.
     * [imageW], [imageH]: the camera picture, to tell a strip that slid out of it.
     */
    fun current(now: Long, covered: Set<String> = emptySet(), imageW: Int = 0, imageH: Int = 0): List<PlacedStrip> {
        strips.values.removeIf { strip ->
            val unread = now - strip.readAt
            when {
                unread <= UNSEEN_MS -> false
                strip.key in covered -> unread > COVERED_MS
                atEdge(strip.box, imageW, imageH) -> unread > OUT_MS
                else -> true
            }
        }
        return strips.values.toList()
    }

    fun clear() = strips.clear()

    private fun overlap(a: CropBox, b: CropBox): Double {
        val w = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0)
        val h = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0)
        val smaller = minOf(a.width.toLong() * a.height, b.width.toLong() * b.height).coerceAtLeast(1)
        return w.toDouble() * h / smaller
    }

    companion object {
        /** A strip lying open in the picture and not read for this long is gone. Shorter gaps are a shaking hand. */
        const val UNSEEN_MS = 1000L

        /** Under a hand a strip is remembered this long after it was last really read. Then the phone must read it again. */
        const val COVERED_MS = 4000L

        /** A strip that slid out of the picture is remembered this long, so the phone can be led back to it. */
        const val OUT_MS = 4000L
        const val SAME_PLACE = 0.5
        const val NAMED_SAME_PLACE = 0.2
        const val KEEP_CHECKED_MS = 4000L

        /** The outer part of the picture, as a share of its width and height. */
        const val EDGE = 0.15

        /** The strip's middle is in the outer part of the picture or beyond it: it is leaving or has left. */
        fun atEdge(box: CropBox, imageW: Int, imageH: Int): Boolean {
            if (imageW <= 0 || imageH <= 0) return false
            val x = (box.left + box.right) / 2.0 / imageW
            val y = (box.top + box.bottom) / 2.0 / imageH
            return x < EDGE || x > 1 - EDGE || y < EDGE || y > 1 - EDGE
        }
    }
}
