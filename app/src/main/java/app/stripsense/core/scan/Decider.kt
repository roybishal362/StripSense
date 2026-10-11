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
/** A ReadKey and the list -> a Verdict. Spec 01 section 4: the first rule that fits wins. */
object Decider {

    /** A medicine with its words in the parser's form, so typed case and dashes never matter. */
    private class Cand(val med: Medicine) {
        val brand = KnownWords.canon(med.brand)
        val generics: Set<String> = med.generics.mapTo(LinkedHashSet()) { KnownWords.canon(it) }
        val release: Set<String> = med.release?.trim()?.uppercase()?.ifEmpty { null }?.let { setOf(it) } ?: emptySet()
        val extraWords: Set<String> = med.extraWords.mapTo(HashSet()) { it.trim().uppercase() }
        val numbers = Strengths.numbersOf(med)

        fun brandSeen(key: ReadKey) = brand in key.brands
        fun genericsSeen(key: ReadKey) = generics.count { it in key.generics }
    }

    fun decide(key: ReadKey, meds: List<Medicine>, tuning: Tuning = Tuning()): Verdict {
        // D0: two brands of the list, or two different brand numbers
        val numbers = key.brandNumbers.mapTo(HashSet()) { Strengths.number(it) }
        if (key.brands.size >= 2 || numbers.size >= 2) return Verdict.OneAtATime

        val candidates = meds.map { Cand(it) }.filter { c ->
            c.brandSeen(key) || c.generics.any { it in key.generics }
        }
        if (candidates.isEmpty()) {
            // D1: plenty of text, none of it on the list
            val enough = key.letters >= tuning.unknownLetters &&
                (key.strengths.isNotEmpty() || key.letters >= tuning.unknownLettersNoStrength)
            return if (enough) Verdict.Unknown else Verdict.Searching // D2
        }

        // D3: exactly one full match; D4: more than one
        val full = candidates.filter { isFullMatch(key, it, numbers) }
        if (full.size == 1) {
            val med = full[0].med
            return when {
                med.status == MedStatus.STOPPED -> Verdict.Stopped(med)
                med.type == MedType.BUTTON -> Verdict.ButtonOnly(med)
                else -> Verdict.Match(med)
            }
        }
        if (full.size > 1) return Verdict.NotSure

        // No full match: the best candidate is a brand match first, then the most generics matched.
        val c = candidates.maxWith(compareBy<Cand>({ it.brandSeen(key) }, { it.genericsSeen(key) }))
        val med = c.med
        val listStrengths = Strengths.display(med.strengths)

        // D5: no strength with a unit was read
        if (key.strengths.isEmpty()) {
            val wrong = key.brandNumbers.firstOrNull { Strengths.number(it) !in c.numbers }
                ?: return Verdict.Unreadable
            val unit = med.strengths.firstOrNull()?.let { " " + Strengths.unitLabel(it.unit) } ?: ""
            return Verdict.Different(med, wrong + unit, listStrengths)
        }

        // D6: her brand name, another medicine's generic inside
        if (c.brandSeen(key) && key.generics.isNotEmpty() && key.generics.none { it in c.generics }) {
            return Verdict.WrongMedicine(med)
        }

        // D7: a strength or brand number that is not hers
        if (Strengths.conflict(key, med)) {
            return Verdict.Different(med, Strengths.display(key.strengths), listStrengths)
        }

        // D8: no conflict, but only some of her strengths were read
        val keyNorms = Strengths.normSet(key.strengths)
        if (keyNorms.size < Strengths.normSet(med.strengths).size) return Verdict.Unreadable

        val sameStrengths = Strengths.sameStrengths(key.strengths, med.strengths)

        // D9: an extra word on the brand line that her medicine does not have
        if (sameStrengths) {
            val extra = key.extraWords.firstOrNull { it !in c.extraWords }
            if (extra != null) return Verdict.Different(med, "${med.brand} $extra", med.brand)
        }

        // D10: the release word differs
        if (sameStrengths && key.release != c.release) {
            val printed = if (key.release.isEmpty()) {
                "plain ${Strengths.display(key.strengths)}"
            } else {
                "${Strengths.display(key.strengths)} ${key.release.joinToString(" ")}"
            }
            val listPart = if (c.release.isEmpty()) "plain $listStrengths" else "$listStrengths ${c.release.first()}"
            return Verdict.Different(med, printed, listPart)
        }

        // D11: same medicine and strength, but not her brand
        if (sameStrengths && key.generics == c.generics && !c.brandSeen(key)) return Verdict.NotShown(med)

        // D12
        return Verdict.NotSure
    }

    // D3: brand, every generic, every strength, brand numbers, release word and extra words agree.
    private fun isFullMatch(key: ReadKey, c: Cand, keyNumbers: Set<String>): Boolean =
        c.brandSeen(key) &&
            key.generics == c.generics &&
            Strengths.sameStrengths(key.strengths, c.med.strengths) &&
            keyNumbers.all { it in c.numbers } &&
            key.release == c.release &&
            c.extraWords.containsAll(key.extraWords)
}
