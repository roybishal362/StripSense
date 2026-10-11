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
import kotlin.math.sqrt

/** A crop box in the upright image, in pixels. */
data class CropBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** The best saved look for a picture, over every taught strip (trial mode and the Numbers screen). */
data class LookScore(val medId: String?, val score: Float?)

/**
 * The look check of spec 01 section 6. The look never names a strip: it can only turn a Match
 * into NotShown or NotTaught. Pure functions; the vectors come from ml/LookEmbedder.
 */
object LookCheck {
    /** Same acceptance rule as apply; avoid native embedding when the local check already decides. */
    fun withFallback(verdict: Verdict, looks: List<Look>, on: Boolean, tLook: Float, fingerprint: List<Float>, vector: () -> List<Float>?): Verdict {
        val local = apply(verdict, null, looks, on, tLook, fingerprint)
        return if (local is Verdict.NotShown) apply(verdict, vector(), looks, on, tLook, fingerprint) else local
    }

    const val CROP_MARGIN = 0.12f
    const val MIN_BOX_PX = 8

    fun cosine(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        if (na == 0.0 || nb == 0.0) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }

    /**
     * The three cases, only after Match. [vector] is the current picture's vector (null if it could not be made).
     * Off, or no medicine taught at all: keep Match. This medicine not taught: NotTaught. Otherwise the best cosine decides.
     */
    fun apply(verdict: Verdict, vector: List<Float>?, looks: List<Look>, on: Boolean, tLook: Float, fingerprint: List<Float> = emptyList()): Verdict {
        if (verdict !is Verdict.Match || !on || looks.isEmpty()) return verdict
        val med = verdict.med
        val mine = looks.filter { it.medId == med.id }
        if (mine.isEmpty()) return Verdict.NotTaught(med)
        val best = mine.maxOf { look ->
            val embedding = if (vector != null) cosine(vector, look.vector) else 0f
            val local = if (fingerprint.isNotEmpty() && fingerprint.size == look.fingerprint.size)
                cosine(fingerprint, look.fingerprint) else 0f
            maxOf(embedding, local)
        }
        return if (best >= tLook) verdict else Verdict.NotShown(med)
    }

    /** The best score against every saved look, and the medicine a look-only app would have named (at or above tLook). */
    fun best(vector: List<Float>?, looks: List<Look>, tLook: Float): LookScore {
        if (vector == null || looks.isEmpty()) return LookScore(null, null)
        var bestLook: Look? = null
        var bestScore = -1f
        for (l in looks) {
            val s = cosine(vector, l.vector)
            if (s > bestScore) {
                bestScore = s
                bestLook = l
            }
        }
        return LookScore(if (bestScore >= tLook) bestLook?.medId else null, bestScore)
    }

    /** The box around all read lines plus 12% on each side, inside the image; the whole image if the box is tiny. */
    fun cropBox(lines: List<OcrLine>, imageW: Int, imageH: Int): CropBox {
        val whole = CropBox(0, 0, imageW, imageH)
        if (lines.isEmpty() || imageW <= 0 || imageH <= 0) return whole
        val l = lines.minOf { it.left }
        val t = lines.minOf { it.top }
        val r = lines.maxOf { it.right }
        val b = lines.maxOf { it.bottom }
        val w = r - l
        val h = b - t
        if (w < MIN_BOX_PX || h < MIN_BOX_PX) return whole
        val dx = (w * CROP_MARGIN).toInt()
        val dy = (h * CROP_MARGIN).toInt()
        val box = CropBox((l - dx).coerceAtLeast(0), (t - dy).coerceAtLeast(0), (r + dx).coerceAtMost(imageW), (b + dy).coerceAtMost(imageH))
        return if (box.width < MIN_BOX_PX || box.height < MIN_BOX_PX) whole else box
    }
}
