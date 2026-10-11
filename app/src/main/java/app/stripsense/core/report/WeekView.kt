package app.stripsense.core.report

import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import java.time.LocalDate
import java.time.LocalTime

enum class DoseMark { MARKED, NOT_SURE, NOT_MARKED, LATER }

/** One dose of one medicine in the week, as the phone recorded it. */
data class WeekDose(val date: LocalDate, val time: LocalTime, val med: Medicine, val mark: DoseMark, val markedAt: LocalTime?,
    /** For a marked dose: how the phone knows (see Proof). */
    val proof: Proof? = null) {
    /** Marked more than an hour after its time. */
    val late: Boolean get() = mark == DoseMark.MARKED && markedAt != null && markedAt.toSecondOfDay() - time.toSecondOfDay() > WeekViews.LATE_MINUTES * 60
}

/**
 * The last seven days of one person, for the family to look at on the phone itself.
 * It shows what was recorded. "Marked" is what the person confirmed; it never says a medicine was taken.
 */
data class WeekView(val days: List<Pair<LocalDate, List<WeekDose>>>) {
    private val all: List<WeekDose> get() = days.flatMap { it.second }
    val marked: Int get() = all.count { it.mark == DoseMark.MARKED }
    val notMarked: Int get() = all.count { it.mark == DoseMark.NOT_MARKED }
    val notSure: Int get() = all.count { it.mark == DoseMark.NOT_SURE }
    val later: Int get() = all.count { it.mark == DoseMark.LATER }

    /** The share of doses that have come up and were marked, 0 to 100. Null when none has come up yet. */
    val markedPercent: Int? get() = (marked + notMarked + notSure).takeIf { it > 0 }?.let { marked * 100 / it }

    /** What the family should look at, newest first: not marked, not sure, marked late. */
    val attention: List<WeekDose> get() = all.filter { it.mark == DoseMark.NOT_MARKED || it.mark == DoseMark.NOT_SURE || it.late }
        .sortedWith(compareByDescending<WeekDose> { it.date }.thenByDescending { it.time })
}

object WeekViews {
    const val LATE_MINUTES = 60

    /** When the phone first knew each medicine: its first taught photo or its first recorded dose, whichever is earlier. */
    fun knownSince(data: StoreData): Map<String, Long> = data.medicines.associate { med ->
        val taught = data.looks.filter { it.medId == med.id && it.taughtAtMillis > 0 }.minOfOrNull { it.taughtAtMillis }
        // A recorded dose counts from its own time, not from the minutes later when it was marked: the 8:00 dose
        // marked at 8:03 on a medicine's first day was left out of the week, though the phone had recorded it.
        val recorded = data.events.filter { it.medId == med.id }.minOfOrNull { event -> minOf(event.atMillis, doseTime(event.slotKey) ?: event.atMillis) }
        med.id to (listOfNotNull(taught, recorded).minOrNull() ?: Long.MIN_VALUE)
    }

    private fun doseTime(slotKey: String): Long? =
        runCatching { java.time.LocalDateTime.parse(slotKey).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()

    /** The same seven days and the same states as the week file (WeekFile.build), as values instead of text. */
    /**
     * [since]: for each medicine, when the phone first knew it. Doses from before that are left out: a medicine saved
     * today has no "not marked" doses last Monday. Without it a new list showed a week of red and 9% marked.
     */
    fun build(meds: List<Medicine>, events: List<DoseEvent>, now: Long, engine: DoseEngine, since: Map<String, Long> = emptyMap()): WeekView {
        val today = engine.dosingDate(now)
        val days = (6 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            date to meds.filter { it.status == MedStatus.ACTIVE }.flatMap { med ->
                engine.slotsFor(med, date).filter { slot -> engine.slotMillis(slot) >= (since[med.id] ?: Long.MIN_VALUE) }.map { slot ->
                    val mark = engine.markOf(med.id, slot.key, events)
                    val state = when {
                        mark != null -> DoseMark.MARKED
                        engine.notSureOf(med.id, slot.key, events) != null -> DoseMark.NOT_SURE
                        engine.slotMillis(slot) > now -> DoseMark.LATER
                        else -> DoseMark.NOT_MARKED
                    }
                    WeekDose(date, slot.time, med, state, mark?.let { engine.local(it.atMillis).toLocalTime() }, mark?.let { Proofs.of(it.source) })
                }
            }.sortedBy { it.time }
        }
        return WeekView(days)
    }
}
