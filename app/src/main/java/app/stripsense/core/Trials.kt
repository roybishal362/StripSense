package app.stripsense.core

/** Scoring a try of trial mode, and the totals for the Numbers screen (spec 05 section 6). */
object Trials {

    const val NOT_HERS = "NOT_HERS"
    const val COVERED = "COVERED"

    /** The scoring table, row by row. [truth] is a medicine id, NOT_HERS or COVERED. */
    fun score(truth: String, verdict: Verdict, meds: List<Medicine>): TrialScore {
        val named = Answers.medOf(verdict)?.id
        return when (truth) {
            COVERED -> if (verdict is Verdict.Match) TrialScore.WRONG else TrialScore.RIGHT
            NOT_HERS -> when (verdict) {
                is Verdict.Match, is Verdict.ButtonOnly -> TrialScore.WRONG
                is Verdict.Different, is Verdict.WrongMedicine, Verdict.Unknown,
                is Verdict.NotShown, is Verdict.NotTaught, is Verdict.Stopped -> TrialScore.RIGHT
                else -> TrialScore.NOT_SURE
            }
            else -> {
                val x = meds.firstOrNull { it.id == truth }
                when (verdict) {
                    is Verdict.Match -> if (named == truth) TrialScore.RIGHT else TrialScore.WRONG
                    is Verdict.Stopped, is Verdict.ButtonOnly ->
                        if (named != truth) TrialScore.WRONG
                        else if (verdict is Verdict.Stopped && x?.status == MedStatus.STOPPED) TrialScore.RIGHT
                        else if (verdict is Verdict.ButtonOnly && x?.type == MedType.BUTTON) TrialScore.RIGHT
                        else TrialScore.WRONG
                    is Verdict.NotShown, is Verdict.NotTaught -> if (named == truth) TrialScore.NOT_SURE else TrialScore.WRONG
                    Verdict.Unreadable, Verdict.NotSure, Verdict.OneAtATime, Verdict.Searching -> TrialScore.NOT_SURE
                    is Verdict.Different, is Verdict.WrongMedicine, Verdict.Unknown -> TrialScore.WRONG_REFUSAL
                }
            }
        }
    }

    data class Counts(val tries: Int, val right: Int, val wrong: Int, val notSure: Int, val wrongRefusals: Int)

    data class Summary(
        val all: Counts,
        val hers: Counts,
        val notHers: Counts,
        val covered: Counts,
        val medianMs: Double?,
        val p90Ms: Long?,
        val lookAloneNamed: Int,
        val printNamed: Int,
    )

    fun summary(trials: List<Trial>): Summary {
        fun counts(t: List<Trial>) = Counts(
            t.size, t.count { it.score == TrialScore.RIGHT }, t.count { it.score == TrialScore.WRONG },
            t.count { it.score == TrialScore.NOT_SURE }, t.count { it.score == TrialScore.WRONG_REFUSAL },
        )
        val notHers = trials.filter { it.truth == NOT_HERS }
        val times = trials.map { it.msToVerdict }.filter { it >= 0 }
        return Summary(
            all = counts(trials),
            hers = counts(trials.filter { it.truth != NOT_HERS && it.truth != COVERED }),
            notHers = counts(notHers),
            covered = counts(trials.filter { it.truth == COVERED }),
            medianMs = Stats.median(times),
            p90Ms = Stats.p90(times),
            lookAloneNamed = notHers.count { it.lookOnlyWouldName != null },
            printNamed = notHers.count { it.score == TrialScore.WRONG },
        )
    }

    /** "1 in N" with N rounded to a whole number; null for a count of 0. */
    fun oneIn(count: Int, total: Int): Long? = if (count == 0 || total == 0) null else Math.round(total.toDouble() / count)

    fun csv(trials: List<Trial>, engine: DoseEngine): String {
        val sb = StringBuilder("id,at,truth,verdict,verdict_medicine,score,ms_to_verdict,look_score,look_only_would_name,second_reader\n")
        for (t in trials) {
            sb.append(
                listOf(
                    t.id, engine.local(t.atMillis).toString(), t.truth, t.verdict, t.verdictMedId.orEmpty(), t.score.name,
                    t.msToVerdict.toString(), t.lookScore?.let { String.format(java.util.Locale.ROOT, "%.3f", it) }.orEmpty(),
                    t.lookOnlyWouldName.orEmpty(), t.secondReader.orEmpty(),
                ).joinToString(","),
            ).append('\n')
        }
        return sb.toString()
    }
}
