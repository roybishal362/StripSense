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
// Spec 02 sections 7 and 8: Mark buttons, and answers with no strip. Never NAME, P1, P3, A1 or W1.

/** Section 7: what a Mark button says. ask is true only with MB1. */
fun DoseEngine.markButton(med: Medicine, now: Long, events: List<DoseEvent>): MarkAnswer {
    if (med.status == MedStatus.STOPPED) return MarkAnswer(stoppedParts(med), ask = false, slotKey = "")
    val td = thisDose(med, now, events) ?: return MarkAnswer(listOf(Part("MB0")), ask = false, slotKey = "")
    val slot = td.slot
    val parts = when {
        td.state == SlotState.MARKED -> listOf(Part("AM1", markValues(markOf(med.id, slot.key, events), now)))
        td.state == SlotState.NOT_SURE -> listOf(vs3(med, slot, events), Part("CHK"))
        td.where != DoseWhere.IN_WINDOW -> listOf(Part("MB0"))
        recentMark(slot, med, now, events) != null -> listOf(lastMarkPart(med, now, events), Part("CHK"))
        else -> return MarkAnswer(
            listOf(Part("MB1", mapOf("nickname" to Answers.nickname(med), "slot" to Slot.of(slot.time)))),
            ask = true,
            slotKey = slot.key,
        )
    }
    return MarkAnswer(parts, ask = false, slotKey = slot.key)
}

/** Section 7: Yes on MB1. The slot must still be askable. */
fun DoseEngine.confirmMark(med: Medicine, slotKey: String, now: Long, events: List<DoseEvent>, newId: String): PromptResult {
    val fresh = markButton(med, now, events)
    if (!fresh.ask || fresh.slotKey != slotKey) return PromptResult(null, fresh.parts)
    val event = DoseEvent(newId, med.id, slotKey, now, EventKind.TAKEN, EventSource.MARK_BUTTON)
    return PromptResult(event, listOf(r1(med, now)), showUndo = true)
}

/** Section 8: the state of a medicine's dose, for voice and taps on the Today list. */
fun DoseEngine.statusAnswer(med: Medicine, now: Long, events: List<DoseEvent>): List<Part> {
    if (med.status == MedStatus.STOPPED) return stoppedParts(med)
    val nick = mapOf("nickname" to Answers.nickname(med))
    val td = thisDose(med, now, events)
    val parts = mutableListOf<Part>()
    if (td != null) {
        val slot = td.slot
        val withSlot = nick + ("slot" to Slot.of(slot.time))
        parts += when {
            td.state == SlotState.MARKED -> Part("VS2", withSlot + markValues(markOf(med.id, slot.key, events), now))
            td.state == SlotState.NOT_SURE -> vs3(med, slot, events)
            td.where == DoseWhere.IN_WINDOW -> Part("VS1", withSlot)
            td.where == DoseWhere.PASSED -> Part("VS4", withSlot)
            else -> Part("VS6", withSlot + ("time" to slot.time))
        }
    }
    parts += Part("CHK")
    if (med.type == MedType.CAMERA) parts += Part("V2")
    return parts
}

/** Section 8: what is left today, in the order WL2, WL2x, WL3, WL1, then CHK. */
fun DoseEngine.whatsLeft(meds: List<Medicine>, now: Long, events: List<DoseEvent>): List<Part> {
    val today = dosingDate(now)
    val wl1 = mutableListOf<ListItem>()
    val wl2 = mutableListOf<ListItem>()
    val wl2x = mutableListOf<ListItem>()
    val wl3 = mutableListOf<ListItem>()
    var any = false
    for (med in meds) {
        if (med.status != MedStatus.ACTIVE) continue
        for (slot in slotsFor(med, today)) {
            any = true
            val item = ListItem(Answers.nickname(med), slot.time)
            when (slotState(med.id, slot.key, events)) {
                SlotState.MARKED -> Unit
                SlotState.NOT_SURE -> wl3 += item
                SlotState.UNMARKED -> when {
                    slotMillis(slot) > now -> wl1 += item
                    now <= window(slot, med).last -> wl2 += item
                    else -> wl2x += item
                }
            }
        }
    }
    if (!any) return listOf(Part("WL0b"))
    val lists = listOf("WL2" to wl2, "WL2x" to wl2x, "WL3" to wl3, "WL1" to wl1).filter { it.second.isNotEmpty() }
    if (lists.isEmpty()) return listOf(Part("WL0"))
    // sortedBy is stable: slots at the same time keep the order of the medicine list.
    return lists.map { (id, items) -> Part(id, mapOf("list" to items.sortedBy { it.time })) } + Part("CHK")
}

/** VS8 and S0 for a stopped medicine; S0 alone when the stop date cannot be read. */
internal fun stoppedParts(med: Medicine): List<Part> {
    val stopped = Answers.stopped(med)
    if (stopped.firstOrNull()?.id != "S1") return stopped
    val date = stopped.first().values.getValue("date")
    return listOf(Part("VS8", mapOf("nickname" to Answers.nickname(med), "date" to date)), Part("S0"))
}

private fun DoseEngine.vs3(med: Medicine, slot: DoseSlot, events: List<DoseEvent>): Part {
    val ns = notSureOf(med.id, slot.key, events)
    val values = mutableMapOf<String, Any>("nickname" to Answers.nickname(med), "slot" to Slot.of(slot.time))
    if (ns != null) values["time"] = local(ns.atMillis).toLocalTime()
    return Part("VS3", values)
}
