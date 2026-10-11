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
/** Finds a saved schedule by printed name without claiming a full identity check. */
object NameRecognition {
    fun find(key: ReadKey, medicines: List<Medicine>): Medicine? {
        return candidates(key, medicines).singleOrNull()?.takeIf { it.type == MedType.CAMERA }
    }

    fun ambiguous(key: ReadKey, medicines: List<Medicine>): Boolean = candidates(key, medicines).size > 1

    private fun candidates(key: ReadKey, medicines: List<Medicine>): List<Medicine> {
        if (key.brands.size != 1 || key.brandNumbers.map(Strengths::number).distinct().size > 1) return emptyList()
        val brand = key.brands.single()
        return medicines.filter {
            KnownWords.canon(it.brand) == brand && !EnrollmentEvidence.conflict(key, it)
        }
    }
}

/** Other label fields may flicker; the same unambiguous name must remain visible. */
class NameScanSession(private val medicines: List<Medicine>) {
    private var lastId: String? = null
    private var frames = 0

    fun feed(key: ReadKey): Medicine? {
        val med = NameRecognition.find(key, medicines)
        frames = if (med != null && med.id == lastId) frames + 1 else if (med != null) 1 else 0
        lastId = med?.id
        return med?.takeIf { frames >= 2 }
    }
}
