package app.stripsense.core.voice

import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import java.time.LocalDate
import java.time.LocalTime

/** One medicine of the time that was asked about, and whether that dose is already marked. */
data class DueItem(val med: Medicine, val marked: Boolean)

/** The answer to "which medicine at eight in the evening?": the saved time it matched and the medicines of that time. */
data class DueAnswer(val time: LocalTime?, val items: List<DueItem>)

/**
 * "Which medicine do I take at 8 in the evening?", "raat ko kaunsi dawai?", "what comes next?"
 *
 * The question is recognised from the words themselves, in English, Hindi and Hindi written in Roman letters,
 * so it is answered even when the model picks another request or is not loaded. It asks which medicines belong
 * to a time; it is not a question about whether one was taken, which has no "which" or "what" in it.
 */
object DueQuestion {
    /** [time]: the hour asked about. Null with [next] true means "the next one". */
    data class Asked(val time: LocalTime?, val next: Boolean)

    private val WHICH = listOf("which", "what", "kaun", "kon ", "konsi", "kaunsi", "kounsi", "कौन")
    private val NEXT = listOf("next", "now", "abhi", "agli", "agla", "अभी", "अगली", "अगला")
    private val MORNING = listOf("morning", "subah", "savere", "सुबह", "सवेरे")
    private val AFTERNOON = listOf("afternoon", "dopahar", "dupahar", "दोपहर")
    private val EVENING = listOf("evening", "night", "tonight", "shaam", "sham ", "raat", "rat ", "शाम", "रात", " pm", "p.m")
    private val CLOCK = Regex("(?<![\\d:])(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|a\\.m|pm|p\\.m|baje|बजे|o'clock|o clock)?")

    fun parse(text: String): Asked? {
        val t = " " + Keywords.norm(text).replace(Regex("[?,!]"), " ") + " "
        if (WHICH.none { t.contains(it) }) return null
        val morning = MORNING.any { t.contains(it) } || t.contains(" am ") || t.contains("a.m")
        val afternoon = AFTERNOON.any { t.contains(it) }
        val evening = EVENING.any { t.contains(it) }
        val clock = CLOCK.findAll(t).firstOrNull { it.groupValues[1].toInt() in 1..23 && (it.groupValues[3].isNotEmpty() || morning || afternoon || evening) }
        if (clock != null) {
            var hour = clock.groupValues[1].toInt()
            val minute = clock.groupValues[2].toIntOrNull() ?: 0
            val mark = clock.groupValues[3]
            if (minute > 59) return null
            val pm = mark.startsWith("p") || (!mark.startsWith("a") && !morning && (evening || afternoon))
            if (pm && hour < 12) hour += 12
            if (mark.startsWith("a") && hour == 12) hour = 0
            return Asked(LocalTime.of(hour % 24, minute), next = false)
        }
        return when {
            morning -> Asked(LocalTime.of(8, 0), false)
            afternoon -> Asked(LocalTime.of(14, 0), false)
            evening -> Asked(LocalTime.of(20, 0), false)
            NEXT.any { t.contains(" $it") } -> Asked(null, next = true)
            else -> null
        }
    }

    /**
     * The medicines saved for the time asked about: the saved dose time nearest to it, within [NEAR_MINUTES].
     * For "the next one": the first saved time still ahead today, or tomorrow's first when today's are over.
     */
    fun answer(asked: Asked, meds: List<Medicine>, now: Long, events: List<DoseEvent>, engine: DoseEngine): DueAnswer {
        val active = meds.filter { it.status == MedStatus.ACTIVE }
        val today: LocalDate = engine.local(now).toLocalDate()
        val clock = engine.local(now).toLocalTime()
        val times = active.flatMap { med -> engine.slotsFor(med, today).map { it.time } }.distinct().sorted()
        if (times.isEmpty()) return DueAnswer(asked.time, emptyList())
        var date = today
        val chosen = if (asked.time == null) {
            times.firstOrNull { it.isAfter(clock) } ?: times.first().also { date = today.plusDays(1) }
        } else times.minByOrNull { kotlin.math.abs(it.toSecondOfDay() - asked.time.toSecondOfDay()) }
            ?.takeIf { kotlin.math.abs(it.toSecondOfDay() - asked.time.toSecondOfDay()) <= NEAR_MINUTES * 60 } ?: return DueAnswer(asked.time, emptyList())
        val items = active.mapNotNull { med ->
            engine.slotsFor(med, date).firstOrNull { it.time == chosen }?.let { DueItem(med, engine.slotState(med.id, it.key, events) == SlotState.MARKED) }
        }
        return DueAnswer(chosen, items)
    }

    const val NEAR_MINUTES = 90
}
