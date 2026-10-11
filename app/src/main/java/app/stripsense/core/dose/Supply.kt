package app.stripsense.core.dose

import app.stripsense.core.domain.DoseEvent
import app.stripsense.core.domain.EventKind
import app.stripsense.core.domain.Medicine
import app.stripsense.core.domain.MedStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How much of one medicine is left, and how far along the doctor's course it is.
 * [left]: tablets left by the count, null when the family has not counted. [daysLeft]: whole days they last.
 * [courseDay]: which day of the course today is, 1 on the first day; null when it goes on with no end.
 */
data class SupplyState(val left: Int?, val daysLeft: Int?, val runsOut: LocalDate?, val courseDay: Int?, val courseDays: Int?) {
    val empty: Boolean get() = left != null && left <= 0
    val low: Boolean get() = daysLeft != null && daysLeft <= Supply.LOW_DAYS
    val courseOver: Boolean get() = courseDay != null && courseDays != null && courseDay > courseDays

    /** Days of the course still to come, today included. */
    val courseLeft: Int? get() = if (courseDay != null && courseDays != null) (courseDays - courseDay + 1).coerceAtLeast(0) else null

    /** The tablets will run out before the course does. */
    val shortForCourse: Boolean get() {
        val course = courseLeft
        return daysLeft != null && course != null && !courseOver && daysLeft < course
    }

    /** Something the family should hear about before it is a problem. */
    val needsALook: Boolean get() = courseOver || low || shortForCourse

    val known: Boolean get() = left != null || courseDay != null
}

/**
 * The phone counts the tablets down by itself. The family counts once ("30 tablets"); every dose that is marked
 * after that takes one away, and an undone mark gives it back. So the phone can say days ahead that a medicine is
 * running out, and which day of the doctor's course this is. One tablet for each dose is assumed.
 */
object Supply {
    /** At this many days or fewer the family is told to buy more. */
    const val LOW_DAYS = 3

    fun of(med: Medicine, events: List<DoseEvent>, now: Long, engine: DoseEngine): SupplyState {
        val today = engine.dosingDate(now)
        val aDay = engine.times(med).size.coerceAtLeast(1)
        val left = if (med.stock < 0) null else {
            // An example week is nobody's tablets.
            val used = events.count { it.medId == med.id && it.kind == EventKind.TAKEN && !it.example && it.atMillis >= med.stockAtMillis && !engine.isUndone(it, events) }
            (med.stock - used).coerceAtLeast(0)
        }
        val days = left?.let { it / aDay }
        val start = if (med.courseDays > 0) runCatching { LocalDate.parse(med.courseStart) }.getOrNull() else null
        val day = start?.let { (ChronoUnit.DAYS.between(it, today) + 1).toInt().coerceAtLeast(1) }
        return SupplyState(left, days, days?.let { today.plusDays(it.toLong()) }, day, if (day != null) med.courseDays else null)
    }

    /** The active medicines the family should hear about, the one that runs out soonest first. */
    fun needingALook(meds: List<Medicine>, events: List<DoseEvent>, now: Long, engine: DoseEngine): List<Pair<Medicine, SupplyState>> =
        meds.filter { it.status == MedStatus.ACTIVE }.map { it to of(it, events, now, engine) }.filter { it.second.needsALook }
            .sortedBy { it.second.daysLeft ?: Int.MAX_VALUE }
}
