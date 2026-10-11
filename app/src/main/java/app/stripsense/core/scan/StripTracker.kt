package app.stripsense.core.scan

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

data class StripObservation(val box: CropBox, val lines: List<OcrLine>, val key: ReadKey)

data class TrackedStrip(
    val id: Long,
    val observation: StripObservation,
    val frames: Int,
    val nameFrames: Int,
    val named: Medicine?,
    val blocked: Boolean,
    val seenAt: Long,
    val appearedAt: Long,
)

/** One-to-one association. Missing, crossing or contradictory regions cannot inherit a result. */
class StripTracker {
    private var tracks = emptyList<TrackedStrip>()
    private var nextId = 0L

    fun reset() { tracks = emptyList() }

    fun update(observations: List<StripObservation>, medicines: List<Medicine>, now: Long): List<TrackedStrip> {
        val previous = tracks.filter { now >= it.seenAt && now - it.seenAt <= MAX_GAP_MS }
        val costs = observations.map { observation -> previous.map { old -> association(observation, old.observation) } }
        val proposed = costs.map { row -> uniqueBest(row) }
        val result = observations.mapIndexed { index, observation ->
            val oldIndex = proposed[index]
            val old = oldIndex?.takeIf { uniqueBest(costs.map { it[oldIndex] }) == index }?.let(previous::get)
            val rawName = NameRecognition.find(observation.key, medicines)
            val visibleConflict = hasConflict(observation.key, medicines)
            // A wrong strength that flickers out of OCR must not become a green name-only match.
            val blocked = visibleConflict || old?.blocked == true
            val named = rawName?.takeUnless { blocked }
            val frames = if (old != null && old.observation.key.sameAs(observation.key)) old.frames + 1 else 1
            val nameFrames = if (named == null) 0 else if (old?.named?.id == named.id) old.nameFrames + 1 else 1
            TrackedStrip(old?.id ?: ++nextId, observation, frames.coerceAtMost(100), nameFrames.coerceAtMost(100),
                named, blocked, now, old?.appearedAt ?: now)
        }
        tracks = result
        return result
    }

    private fun hasConflict(key: ReadKey, medicines: List<Medicine>): Boolean {
        if (key.brands.size != 1) return key.brands.size > 1
        val candidates = medicines.filter { KnownWords.canon(it.brand) in key.brands }
        return candidates.isNotEmpty() && candidates.all { EnrollmentEvidence.conflict(key, it) }
    }

    private fun uniqueBest(costs: List<Double>): Int? {
        val sorted = costs.withIndex().filter { it.value.isFinite() }.sortedBy { it.value }
        val best = sorted.firstOrNull() ?: return null
        if (sorted.size > 1 && sorted[1].value - best.value < 0.18) return null
        return best.index
    }

    private fun association(current: StripObservation, old: StripObservation): Double {
        val a = current.box
        val b = old.box
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return Double.POSITIVE_INFINITY
        if (current.key.brands.isNotEmpty() && old.key.brands.isNotEmpty() && current.key.brands != old.key.brands)
            return Double.POSITIVE_INFINITY
        val size = maxOf(64, a.width, a.height, b.width, b.height).toDouble()
        val distance = hypot((a.left + a.right - b.left - b.right) / 2.0, (a.top + a.bottom - b.top - b.bottom) / 2.0)
        if (distance > size * 0.65) return Double.POSITIVE_INFINITY
        val intersection = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0).toDouble() *
            (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0)
        val union = a.width.toDouble() * a.height + b.width.toDouble() * b.height - intersection
        return 1.0 - intersection / union + distance / size
    }

    companion object { const val MAX_GAP_MS = 650L }
}
