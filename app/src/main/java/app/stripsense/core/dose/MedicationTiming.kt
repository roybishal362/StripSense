package app.stripsense.core.dose

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
enum class TimingKind { NOW, NEXT, LATER, UNCONFIRMED, MARKED, NOT_SURE, NO_SCHEDULE, STOPPED }
data class MedicationTiming(val kind: TimingKind, val slot: DoseSlot? = null)

/** Camera recognition never decides a prescription. This only describes the saved schedule. */
object MedicationTimings {
    fun scene(med: Medicine, medicines: List<Medicine>, now: Long, events: List<DoseEvent>, engine: DoseEngine): MedicationTiming {
        val own = of(med, now, events, engine)
        if (own.kind != TimingKind.LATER) return own
        val timings = medicines.filter { it.status == MedStatus.ACTIVE }.map { of(it, now, events, engine) }
        if (timings.any { it.kind == TimingKind.NOW }) return own
        val nextAt = timings.filter { it.kind == TimingKind.LATER }.mapNotNull { it.slot }.minOfOrNull { engine.slotMillis(it) }
        return if (own.slot != null && engine.slotMillis(own.slot) == nextAt) own.copy(kind = TimingKind.NEXT) else own
    }
    fun stripAnswer(med: Medicine, now: Long, events: List<DoseEvent>, engine: DoseEngine): StripAnswer {
        val answer = engine.stripAnswer(med, now, events)
        val timing = of(med, now, events, engine)
        val slot = timing.slot ?: return answer
        if (timing.kind != TimingKind.LATER) return answer
        val values = Answers.medValues(med) + mapOf("slot" to Slot.of(slot.time), "time" to slot.time)
        return StripAnswer(listOf(Part("W1", values), engine.lastMarkPart(med, now, events), Part("CHK")), false, slot.key)
    }
    fun of(med: Medicine, now: Long, events: List<DoseEvent>, engine: DoseEngine): MedicationTiming {
        if (med.status == MedStatus.STOPPED) return MedicationTiming(TimingKind.STOPPED)
        val dose = engine.thisDose(med, now, events) ?: return MedicationTiming(TimingKind.NO_SCHEDULE)
        val at = engine.slotMillis(dose.slot)
        val kind = when {
            dose.state == SlotState.MARKED -> TimingKind.MARKED
            dose.state == SlotState.NOT_SURE -> TimingKind.NOT_SURE
            now < at -> TimingKind.LATER
            engine.recentMark(dose.slot, med, now, events) != null -> TimingKind.NOT_SURE
            dose.where == DoseWhere.IN_WINDOW && now - at <= 15 * DoseEngine.MIN -> TimingKind.NOW
            else -> TimingKind.UNCONFIRMED
        }
        return MedicationTiming(kind, dose.slot)
    }
}
