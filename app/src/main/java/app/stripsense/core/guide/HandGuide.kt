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

/** Which way the hand should go, as the person holding the phone over the table feels it. */
enum class Toward { LEFT, RIGHT, AWAY, CLOSER }

/** What the phone says about the hand in view. Only stable facts, so the same situation is the same cue. */
sealed interface HandCue {
    /** The finger is on the checked strip of the dose that is due. */
    data class Yes(val med: Medicine) : HandCue

    /** The finger is on another strip while a dose is due. [toward] leads to the due strip, when its place is known. */
    data class No(val med: Medicine?, val kind: PlacedKind, val timing: MedicationTiming?, val toward: Toward?) : HandCue

    /** Nothing is due. Touching a strip simply asks what it is. */
    data class Info(val med: Medicine?, val kind: PlacedKind, val timing: MedicationTiming?) : HandCue

    /**
     * The finger points at print the phone cannot read from here. It does not guess and it does not stay silent:
     * it asks for the phone to be brought closer, so it can say which medicine that is.
     */
    data object Closer : HandCue

    /** The finger is on no strip and the due strip's place is known. */
    data class Move(val toward: Toward) : HandCue
}

/**
 * Guiding the hand, not the camera. The fingertip is compared with the remembered places of the strips.
 * The due strip never borrows evidence from the hand: it must already have been read and checked.
 */
object HandGuide {
    /**
     * [fingerX], [fingerY]: the index fingertip in the camera image. [wanted]: medicines whose dose is due.
     * [timing] gives the saved schedule of a medicine for the words that are spoken.
     */
    fun cue(fingerX: Int, fingerY: Int, strips: List<PlacedStrip>, wanted: Set<String>, timing: (Medicine) -> MedicationTiming,
        ray: Pair<Float, Float>? = null, reach: Int = 0): HandCue? {
        val touched = pointed(fingerX, fingerY, strips, ray, reach)
        val targets = strips.filter { it.kind == PlacedKind.CHECKED && it.med?.id in wanted }
        val nearest = targets.minByOrNull { centreDistance(fingerX, fingerY, it.box) }
        if (touched != null) {
            if (touched.kind == PlacedKind.CHECKED && touched.med?.id in wanted) return HandCue.Yes(touched.med!!)
            val schedule = touched.med?.let(timing)
            // The wanted medicine by name, its strength not read: not "yes", and certainly not "not this one".
            if (touched.kind == PlacedKind.NAME && touched.med?.id in wanted) return HandCue.Info(touched.med, touched.kind, schedule)
            // Something is due but its strip has not been found yet: still "not this one", with no direction to give.
            return if (wanted.isNotEmpty()) HandCue.No(touched.med, touched.kind, schedule, nearest?.let { toward(fingerX, fingerY, it.box) })
            else HandCue.Info(touched.med, touched.kind, schedule)
        }
        return nearest?.let { HandCue.Move(toward(fingerX, fingerY, it.box)) }
    }

    /**
     * The strip the finger means. A fingertip on a strip means that strip. A finger held out straight means the
     * first strip that lies ahead of it within [reach] pixels: people point at a strip from beside it, they do
     * not press on it, and a fingertip two centimetres short of the box used to be answered with silence.
     * [ray]: the finger's direction in picture pixels, any length.
     */
    fun pointed(x: Int, y: Int, strips: List<PlacedStrip>, ray: Pair<Float, Float>? = null, reach: Int = 0): PlacedStrip? {
        // Where a strip being read now and a remembered one both lie under the finger, the one being read is the truth.
        val under = strips.filter { inside(x, y, it.box) }
        val newest = under.maxOfOrNull { it.readAt }
        under.filter { it.readAt == newest }.minByOrNull { centreDistance(x, y, it.box) }?.let { return it }
        val length = ray?.let { hypot(it.first.toDouble(), it.second.toDouble()) } ?: 0.0
        if (ray == null || length <= 0.0 || reach <= 0) return null
        val ux = ray.first / length
        val uy = ray.second / length
        return strips.mapNotNull { strip -> ahead(x, y, ux, uy, strip.box)?.takeIf { it <= reach }?.let { strip to it } }.minByOrNull { it.second }?.first
    }

    /** How far along the finger's line the strip's box begins; null when the line misses it or it lies behind. */
    private fun ahead(x: Int, y: Int, ux: Double, uy: Double, box: CropBox): Double? {
        val grow = maxOf(GROW_PX, (minOf(box.width, box.height) * GROW).toInt())
        var near = 0.0
        var far = Double.MAX_VALUE
        for ((origin, way, low, high) in listOf(listOf(x.toDouble(), ux, (box.left - grow).toDouble(), (box.right + grow).toDouble()),
            listOf(y.toDouble(), uy, (box.top - grow).toDouble(), (box.bottom + grow).toDouble()))) {
            if (abs(way) < 1e-6) { if (origin < low || origin > high) return null; continue }
            val a = (low - origin) / way
            val b = (high - origin) / way
            near = maxOf(near, minOf(a, b))
            far = minOf(far, maxOf(a, b))
        }
        return if (near <= far) near else null
    }

    /** A fingertip is rarely dead centre, and a finger covers print, so the strip's box is grown a little. */
    fun inside(x: Int, y: Int, box: CropBox): Boolean {
        val grow = maxOf(GROW_PX, (minOf(box.width, box.height) * GROW).toInt())
        return x >= box.left - grow && x <= box.right + grow && y >= box.top - grow && y <= box.bottom + grow
    }

    /** Left and right are the larger of the two gaps; the top of the image is the far side of the table. */
    fun toward(x: Int, y: Int, box: CropBox): Toward {
        val dx = (box.left + box.right) / 2 - x
        val dy = (box.top + box.bottom) / 2 - y
        return if (abs(dx) >= abs(dy)) (if (dx < 0) Toward.LEFT else Toward.RIGHT) else (if (dy < 0) Toward.AWAY else Toward.CLOSER)
    }

    /** 1 with the finger at the strip's centre, 0 when it is most of the picture away. Drives the ticking. */
    fun closeness(x: Int, y: Int, box: CropBox, imageW: Int, imageH: Int): Float {
        val reach = hypot(imageW.toDouble(), imageH.toDouble()) * REACH
        if (reach <= 0) return 0f
        return (1.0 - centreDistance(x, y, box) / reach).coerceIn(0.0, 1.0).toFloat()
    }

    private fun centreDistance(x: Int, y: Int, box: CropBox): Double =
        hypot((box.left + box.right) / 2.0 - x, (box.top + box.bottom) / 2.0 - y)

    const val GROW = 0.15
    const val GROW_PX = 24
    const val REACH = 0.6

    /** How far ahead of a straight finger a strip may lie and still be the one pointed at, as a part of the picture's diagonal. */
    const val POINT_REACH = 0.18
}
