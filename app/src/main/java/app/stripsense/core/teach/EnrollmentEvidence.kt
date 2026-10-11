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
/** Visible contradictory print is never stored as a reference for another medicine. */
object EnrollmentEvidence {
    fun conflict(key: ReadKey, med: Medicine): Boolean {
        val brand = KnownWords.canon(med.brand)
        val generics = med.generics.map(KnownWords::canon).toSet()
        if (key.brands.any { it != brand }) return true
        if (key.generics.any { it !in generics }) return true
        if (key.strengths.any { printed -> med.strengths.none { Strengths.sameStrengths(setOf(printed), setOf(it)) } }) return true
        if (key.release.any { it != med.release?.trim()?.uppercase() }) return true
        val extras = med.extraWords.map { it.trim().uppercase() }.toSet()
        if (key.extraWords.any { it !in extras }) return true
        val expectedNumbers = Strengths.numbersOf(med)
        return key.brandNumbers.any { Strengths.number(it) !in expectedNumbers }
    }

    /**
     * A photo that must not join this medicine's saved views: it shows another saved name and not this one,
     * or a strength this medicine does not have. Saved views decide what the scan later accepts as this strip's
     * own print, so a wrong-strength photo saved here would teach the scan to accept that strength.
     * Label words the family never typed, such as "DT", do not block: the person confirms the photo is this medicine.
     */
    fun wrongStrip(key: ReadKey, med: Medicine): Boolean {
        val brand = KnownWords.canon(med.brand)
        return (key.brands.isNotEmpty() && brand !in key.brands) || Strengths.conflict(key, med)
    }

    fun verified(key: ReadKey, med: Medicine, tuning: Tuning): Boolean =
        !conflict(key, med) && Decider.decide(
            key.copy(strengthSeen = key.strengths.associateWith { 2 }), listOf(med), tuning,
        ) is Verdict.Match
}
