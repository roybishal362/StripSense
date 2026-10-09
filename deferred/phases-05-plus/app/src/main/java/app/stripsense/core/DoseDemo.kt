package app.stripsense.core

import java.time.LocalDateTime

// Spec 02 section 9 (demo clock helpers) and the rows of the Today screen.

/** Undo every TAKEN and NOT_SURE event of today's dosing date that is not undone yet. Nothing is deleted. */
fun DoseEngine.freshDay(now: Long, events: List<DoseEvent>, newId: () -> String): List<DoseEvent> {
    val prefix = dosingDate(now).toString() + "T"
    val undone = events.filter { it.kind == EventKind.UNDONE }.mapNotNullTo(HashSet()) { it.undoes }
    return events
        .filter { (it.kind == EventKind.TAKEN || it.kind == EventKind.NOT_SURE) && it.id !in undone && it.slotKey.startsWith(prefix) }
        .map { DoseEvent(newId(), it.medId, it.slotKey, now, EventKind.UNDONE, EventSource.DEMO, undoes = it.id) }
}

/** TAKEN events for yesterday's slots of every ACTIVE medicine, 10 minutes after each slot. Marked slots are skipped. */
fun DoseEngine.seedYesterday(meds: List<Medicine>, now: Long, events: List<DoseEvent>, newId: () -> String): List<DoseEvent> {
    val yesterday = dosingDate(now).minusDays(1)
    val out = mutableListOf<DoseEvent>()
    for (med in meds) {
        if (med.status != MedStatus.ACTIVE) continue
        for (slot in slotsFor(med, yesterday)) {
            if (markOf(med.id, slot.key, events) != null) continue
            out += DoseEvent(newId(), med.id, slot.key, slotMillis(slot) + 10 * DoseEngine.MIN, EventKind.TAKEN, EventSource.DEMO)
        }
    }
    return out
}

/** Millis of a wall-clock time today (real calendar date), for the demo clock buttons. */
fun DoseEngine.todayAt(realNow: Long, hour: Int, minute: Int): Long =
    millis(LocalDateTime.of(local(realNow).toLocalDate(), java.time.LocalTime.of(hour, minute)))

/** Every slot of today: ACTIVE medicines first in list order, stopped ones last. Sorted by time inside each group. */
fun DoseEngine.todayRows(meds: List<Medicine>, now: Long, events: List<DoseEvent>): List<TodayRow> {
    val today = dosingDate(now)
    val rows = mutableListOf<TodayRow>()
    for (med in meds) for (slot in slotsFor(med, today)) {
        val state = slotState(med.id, slot.key, events)
        val w = window(slot, med)
        rows += TodayRow(
            med = med,
            slot = slot,
            state = state,
            markAt = markOf(med.id, slot.key, events)?.atMillis,
            due = state == SlotState.UNMARKED && now in w,
            passed = state == SlotState.UNMARKED && now > w.last,
        )
    }
    return rows.sortedWith(compareBy<TodayRow> { it.med.status == MedStatus.STOPPED }.thenBy { it.slot.time })
}

/**
 * The "Next up" slot: the earliest slot due now (inside its window, not marked); else the earliest
 * still to come; else the earliest passed slot that is not marked. Active medicines only.
 */
fun DoseEngine.nextUp(rows: List<TodayRow>, now: Long): TodayRow? {
    val active = rows.filter { it.med.status == MedStatus.ACTIVE }
    return active.firstOrNull { it.due }
        ?: active.firstOrNull { it.state == SlotState.UNMARKED && slotMillis(it.slot) > now }
        ?: active.firstOrNull { it.state == SlotState.UNMARKED && it.passed }
}
