package app.stripsense.core.teach

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
data class ReferenceCandidate(val med: Medicine, val score: Float, val margin: Float)

/** Retrieval suggests a reference to compare. It never produces a Match or a dose action. */
object PartialReferences {
    fun find(key: ReadKey, vector: List<Float>?, fingerprint: List<Float>, meds: List<Medicine>, looks: List<Look>): ReferenceCandidate? {
        val ranked = meds.filter { it.type == MedType.CAMERA && !EnrollmentEvidence.conflict(key, it) }.mapNotNull { med ->
            val references = looks.filter { it.medId == med.id }
            val best = references.maxOfOrNull { look ->
                val embedding = vector?.takeIf { it.size == look.vector.size && it.isNotEmpty() }
                    ?.let { LookCheck.cosine(it, look.vector) } ?: 0f
                val local = if (fingerprint.isNotEmpty() && look.fingerprint.size == fingerprint.size)
                    LookCheck.cosine(fingerprint, look.fingerprint) else 0f
                maxOf(embedding, local)
            } ?: return@mapNotNull null
            med to best
        }.sortedByDescending { it.second }
        val first = ranked.firstOrNull() ?: return null
        val margin = first.second - (ranked.getOrNull(1)?.second ?: 0f)
        // Generic-only fragments and bare foil are often shared across brands and strengths.
        val supported = key.brands.isNotEmpty() || (key.generics.isNotEmpty() && key.strengths.isNotEmpty())
        return if (supported && first.second >= 0.80f && margin >= 0.08f)
            ReferenceCandidate(first.first, first.second, margin) else null
    }
}
