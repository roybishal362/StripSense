package app.stripsense.core.dose

import app.stripsense.core.domain.DoseEvent
import app.stripsense.core.domain.EventKind
import app.stripsense.core.domain.EventSource
import app.stripsense.core.domain.MedStatus
import app.stripsense.core.domain.Medicine
import java.util.Random

/**
 * An example week, for showing the app to someone on the day it was set up.
 *
 * A phone that learned its medicines today has no week to show. This fills the six days before today as a week
 * might really go: most doses marked a few minutes after their time, some by the tablet being heard, some after
 * the strip was seen, some by a button; one marked late, one or two not marked, one "not sure".
 *
 * Every event it makes carries [DoseEvent.example], so it is shown as an example and can be taken out again
 * without touching anything the person really did. Today is left alone, and so is any dose with a real record.
 */
object ExampleWeek {
    const val DAYS = 6

    /** The example events for one person's medicines. The same medicines give the same week every time. */
    fun build(meds: List<Medicine>, events: List<DoseEvent>, now: Long, engine: DoseEngine, newId: () -> String): List<DoseEvent> {
        val active = meds.filter { it.status == MedStatus.ACTIVE }
        val today = engine.dosingDate(now)
        val real = events.filterNot { it.example }.mapTo(HashSet()) { it.medId + "|" + it.slotKey }
        val slots = (DAYS downTo 1).flatMap { back -> active.flatMap { engine.slotsFor(it, today.minusDays(back.toLong())) } }
            .filter { it.medId + "|" + it.key !in real }
        if (slots.isEmpty()) return emptyList()
        val random = Random(active.joinToString("|") { it.id }.hashCode().toLong())
        // A medicine's first dose of the week is always marked: the week is shown from a medicine's first record on.
        val first = slots.groupBy { it.medId }.values.mapTo(HashSet()) { it.first() }
        val odd = slots.filter { it !in first }.shuffled(random)
        val n = slots.size
        val notMarked = odd.take(maxOf(1, n / 10)).toSet()
        val notSure = odd.drop(notMarked.size).take(if (n >= 10) 1 else 0).toSet()
        val late = odd.drop(notMarked.size + notSure.size).take(if (n >= 5) 1 else 0).toSet()
        return slots.mapNotNull { slot ->
            val at = engine.slotMillis(slot)
            when (slot) {
                in notMarked -> null
                in notSure -> DoseEvent(newId(), slot.medId, slot.key, at + 6 * DoseEngine.MIN, EventKind.NOT_SURE, EventSource.PROMPT_TAP, example = true)
                in late -> DoseEvent(newId(), slot.medId, slot.key, at + (70 + random.nextInt(25)) * DoseEngine.MIN, EventKind.TAKEN, EventSource.REMINDER_TAP, example = true)
                else -> {
                    val roll = random.nextInt(100)
                    val source = if (roll < 50) EventSource.HEARD else if (roll < 80) EventSource.PROMPT_TAP else EventSource.REMINDER_TAP
                    DoseEvent(newId(), slot.medId, slot.key, at + (1 + random.nextInt(14)) * DoseEngine.MIN, EventKind.TAKEN, source, example = true)
                }
            }
        }
    }

    fun shown(events: List<DoseEvent>): Boolean = events.any { it.example }

    /**
     * The example week is showing but a new day has begun since it was filled: yesterday has none of it.
     * It is then filled again, so the week on the screen still ends yesterday.
     */
    fun stale(events: List<DoseEvent>, now: Long, engine: DoseEngine): Boolean {
        val last = events.filter { it.example }.maxOfOrNull { it.slotKey.substringBefore('T') } ?: return false
        return last < engine.dosingDate(now).minusDays(1).toString()
    }
}
