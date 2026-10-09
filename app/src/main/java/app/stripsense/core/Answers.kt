package app.stripsense.core

import java.time.LocalDate

/** Which sentences go with which verdict. Spec 03 section 4. */
object Answers {

    /**
     * The parts for a verdict. [unreadableCount] is how many Unreadable verdicts came in a row,
     * this one included (1 gives C1, 2 gives C2, 3 or more C3).
     * A Match gets NAME here; the dose answer for a Match comes from DoseEngine.stripAnswer.
     */
    fun forVerdict(verdict: Verdict, unreadableCount: Int = 1): List<Part> = when (verdict) {
        is Verdict.Match -> listOf(name(verdict.med))
        is Verdict.Different -> listOf(
            Part("V1", mapOf("printed" to verdict.printed, "listpart" to verdict.listPart)),
        )
        is Verdict.WrongMedicine -> listOf(Part("D1"))
        is Verdict.NotShown -> listOf(Part("N1", medValues(verdict.med)))
        is Verdict.Stopped -> stopped(verdict.med)
        is Verdict.ButtonOnly -> listOf(Part("L1"))
        is Verdict.NotTaught -> listOf(Part("L2"))
        Verdict.Unknown -> listOf(Part("K1"))
        Verdict.OneAtATime -> listOf(Part("RZ1"))
        Verdict.Unreadable -> listOf(Part(unreadableId(unreadableCount)))
        Verdict.NotSure -> listOf(Part("U1"))
        Verdict.Searching -> emptyList()
    }

    fun unreadableId(count: Int): String = when {
        count <= 1 -> "C1"
        count == 2 -> "C2"
        else -> "C3"
    }

    fun name(med: Medicine): Part = Part("NAME", medValues(med))

    fun medValues(med: Medicine): Map<String, Any> =
        mapOf("nickname" to nickname(med), "strength" to med.strengths)

    fun nickname(med: Medicine) = Bilingual(med.nickEn, med.nickHi)

    /** S1 needs the date; a stopped medicine with no readable date gets S0 alone. */
    fun stopped(med: Medicine): List<Part> {
        val date = med.stoppedOn?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
        return if (date != null) listOf(Part("S1", mapOf("date" to date)), Part("S0")) else listOf(Part("S0"))
    }

    /** The medicine a verdict names, if any. */
    fun medOf(verdict: Verdict): Medicine? = when (verdict) {
        is Verdict.Match -> verdict.med
        is Verdict.Different -> verdict.med
        is Verdict.WrongMedicine -> verdict.med
        is Verdict.NotShown -> verdict.med
        is Verdict.Stopped -> verdict.med
        is Verdict.ButtonOnly -> verdict.med
        is Verdict.NotTaught -> verdict.med
        else -> null
    }

    /** The verdict's name, as written in the trials file. */
    fun nameOf(verdict: Verdict): String = when (verdict) {
        is Verdict.Match -> "Match"
        is Verdict.Different -> "Different"
        is Verdict.WrongMedicine -> "WrongMedicine"
        is Verdict.NotShown -> "NotShown"
        is Verdict.Stopped -> "Stopped"
        is Verdict.ButtonOnly -> "ButtonOnly"
        is Verdict.NotTaught -> "NotTaught"
        Verdict.Unknown -> "Unknown"
        Verdict.OneAtATime -> "OneAtATime"
        Verdict.Unreadable -> "Unreadable"
        Verdict.NotSure -> "NotSure"
        Verdict.Searching -> "Searching"
    }
}
