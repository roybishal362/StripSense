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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * "This dose": slots, windows, marks, and what to say about a strip. Spec 02.
 * Pure: the time is always passed in, the engine never saves anything.
 * Sections 7 to 9 are in DoseAnswers.kt and DoseDemo.kt.
 */
class DoseEngine(val zone: ZoneId = ZoneId.systemDefault()) {

    // ---- Section 1: time ----

    fun local(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)

    fun millis(t: LocalDateTime): Long = t.atZone(zone).toInstant().toEpochMilli()

    /** The dosing day starts at 04:00. */
    fun dosingDate(now: Long): LocalDate = local(now).minusHours(4).toLocalDate()

    // ---- Section 2: slots and windows ----

    fun times(med: Medicine): List<LocalTime> =
        med.times.mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }.distinct().sorted()

    fun slotsFor(med: Medicine, date: LocalDate): List<DoseSlot> = times(med).map { DoseSlot(med.id, date, it) }

    fun slotMillis(slot: DoseSlot): Long = millis(slot.dateTime)

    /** Opens 30 minutes before; closes at +120 minutes or half the gap to the next slot that day. */
    fun window(slot: DoseSlot, med: Medicine): LongRange {
        val at = slotMillis(slot)
        val next = times(med).firstOrNull { it > slot.time }
        var close = at + 120 * MIN
        if (next != null) close = minOf(close, at + (millis(LocalDateTime.of(slot.date, next)) - at) / 2)
        return (at - 30 * MIN)..close
    }

    /** Half the gap from the previous slot; the first slot's previous one is yesterday's last. */
    fun halfInterval(slot: DoseSlot, med: Medicine): Long {
        val ts = times(med)
        val prev = ts.lastOrNull { it < slot.time }
            ?.let { LocalDateTime.of(slot.date, it) }
            ?: LocalDateTime.of(slot.date.minusDays(1), ts.last())
        return (slotMillis(slot) - millis(prev)) / 2
    }

    // ---- Section 3: events ----

    private fun undoneIds(events: List<DoseEvent>): Set<String> =
        events.asSequence().filter { it.kind == EventKind.UNDONE }.mapNotNullTo(HashSet()) { it.undoes }

    /** The mark (TAKEN, not undone) of one slot, the latest if there are several. */
    fun markOf(medId: String, key: String, events: List<DoseEvent>): DoseEvent? {
        val undone = undoneIds(events)
        return events.filter { it.medId == medId && it.slotKey == key && it.kind == EventKind.TAKEN && it.id !in undone }
            .maxByOrNull { it.atMillis }
    }

    fun notSureOf(medId: String, key: String, events: List<DoseEvent>): DoseEvent? {
        val undone = undoneIds(events)
        return events.filter { it.medId == medId && it.slotKey == key && it.kind == EventKind.NOT_SURE && it.id !in undone }
            .maxByOrNull { it.atMillis }
    }

    fun slotState(medId: String, key: String, events: List<DoseEvent>): SlotState = when {
        markOf(medId, key, events) != null -> SlotState.MARKED
        notSureOf(medId, key, events) != null -> SlotState.NOT_SURE
        else -> SlotState.UNMARKED
    }

    fun isUndone(event: DoseEvent, events: List<DoseEvent>): Boolean =
        events.any { it.kind == EventKind.UNDONE && it.undoes == event.id }

    /** The latest TAKEN event of a medicine that is not undone (and not after now). */
    fun lastMark(med: Medicine, now: Long, events: List<DoseEvent>): DoseEvent? {
        val undone = undoneIds(events)
        return events.filter { it.medId == med.id && it.kind == EventKind.TAKEN && it.id !in undone && it.atMillis <= now }
            .maxByOrNull { it.atMillis }
    }

    /** A last mark less than the slot's half-interval ago, that belongs to a different slot. */
    fun recentMark(slot: DoseSlot, med: Medicine, now: Long, events: List<DoseEvent>): DoseEvent? {
        val last = lastMark(med, now, events) ?: return null
        if (last.slotKey == slot.key) return null
        return if (now - last.atMillis < halfInterval(slot, med)) last else null
    }

    // ---- Section 4: "this dose" ----

    fun thisDose(med: Medicine, now: Long, events: List<DoseEvent>): ThisDose? {
        val slots = slotsFor(med, dosingDate(now))
        if (slots.isEmpty()) return null
        fun of(s: DoseSlot, w: DoseWhere) = ThisDose(s, w, slotState(med.id, s.key, events))
        slots.firstOrNull { now in window(it, med) }?.let { return of(it, DoseWhere.IN_WINDOW) }
        slots.lastOrNull { slotMillis(it) < now }?.let {
            if (slotState(med.id, it.key, events) != SlotState.MARKED) return of(it, DoseWhere.PASSED)
        }
        slots.firstOrNull { slotMillis(it) > now }?.let { return of(it, DoseWhere.NEXT) }
        return of(slots.last(), DoseWhere.LAST_MARKED)
    }

    // ---- Section 5: the answer for a strip, only after verdict Match ----

    fun stripAnswer(med: Medicine, now: Long, events: List<DoseEvent>): StripAnswer {
        val td = thisDose(med, now, events)
            ?: return StripAnswer(listOf(Answers.name(med), Part("CHK")), askNow = false, slotKey = "")
        val slot = td.slot
        val base = Answers.medValues(med) + ("slot" to Slot.of(slot.time))
        val p2 = lastMarkPart(med, now, events)
        val chk = Part("CHK")
        val parts: List<Part>
        var ask = false
        when {
            // DS3
            td.state == SlotState.NOT_SURE && (td.where == DoseWhere.IN_WINDOW || td.where == DoseWhere.PASSED) -> {
                val ns = notSureOf(med.id, slot.key, events)!!
                parts = listOf(Answers.name(med), Part("NS1", base + ("time" to local(ns.atMillis).toLocalTime())))
            }
            td.where == DoseWhere.IN_WINDOW && td.state == SlotState.MARKED -> parts = listOf(a1(med, slot, now, events), chk) // DS2
            td.where == DoseWhere.IN_WINDOW && recentMark(slot, med, now, events) != null -> // DS5
                parts = listOf(Part("P3", base), p2, chk)
            td.where == DoseWhere.IN_WINDOW -> { // DS1
                ask = med.status == MedStatus.ACTIVE
                parts = listOfNotNull(Part("P1", base), p2, notePart(med), chk, Part("Q1"))
            }
            td.where == DoseWhere.NEXT -> // DS6
                parts = listOf(Part("W1", base + ("time" to slot.time)), p2, chk)
            td.where == DoseWhere.PASSED -> { // DS7
                val later = times(med).firstOrNull { it > slot.time }
                parts = listOfNotNull(Part("P3", base), p2, later?.let { Part("NX1", mapOf("time" to it)) }, chk)
            }
            else -> parts = listOf(a1(med, slot, now, events), chk) // DS8
        }
        return StripAnswer(parts, ask, slot.key)
    }

    /** P2 for the last mark, or P2n when the medicine was never marked. */
    fun lastMarkPart(med: Medicine, now: Long, events: List<DoseEvent>): Part {
        val last = lastMark(med, now, events) ?: return Part("P2n")
        val days = ChronoUnit.DAYS.between(dosingDate(last.atMillis), dosingDate(now)).toInt()
        return Part(
            "P2",
            mapOf(
                "when" to WhenValue(days, dosingDate(last.atMillis)),
                "time" to local(last.atMillis).toLocalTime(),
                "ago" to minutesAgo(last.atMillis, now),
            ),
        )
    }

    internal fun a1(med: Medicine, slot: DoseSlot, now: Long, events: List<DoseEvent>): Part {
        val mark = markOf(med.id, slot.key, events)
        val values = Answers.medValues(med) + ("slot" to Slot.of(slot.time))
        return Part("A1", values + markValues(mark, now))
    }

    internal fun markValues(mark: DoseEvent?, now: Long): Map<String, Any> =
        if (mark == null) emptyMap() else mapOf("time" to local(mark.atMillis).toLocalTime(), "ago" to minutesAgo(mark.atMillis, now))

    private fun notePart(med: Medicine): Part? =
        if (med.noteEn.isBlank() && med.noteHi.isBlank()) null
        else Part("NOTE", mapOf("note" to Bilingual(med.noteEn, med.noteHi)))

    /** Whole minutes between the mark and now, at least 1. Sentences drops it from 1,440 on. */
    fun minutesAgo(at: Long, now: Long): Int = ((now - at) / MIN).toInt().coerceAtLeast(1)

    // ---- Section 6: answering the question ----

    fun answerPrompt(
        med: Medicine, slotKey: String, answer: PromptAnswer, now: Long, events: List<DoseEvent>, newId: String,
    ): PromptResult = when (answer) {
        // HEARD: the tablet was heard coming out of the strip while the question was open. That answers it as a Yes does.
        PromptAnswer.YES_TAP, PromptAnswer.YES_VOICE, PromptAnswer.HEARD -> {
            val fresh = stripAnswer(med, now, events)
            if (fresh.askNow && fresh.slotKey == slotKey) {
                val source = when (answer) {
                    PromptAnswer.YES_TAP -> EventSource.PROMPT_TAP
                    PromptAnswer.HEARD -> EventSource.HEARD
                    else -> EventSource.PROMPT_VOICE
                }
                val event = DoseEvent(newId, med.id, slotKey, now, EventKind.TAKEN, source)
                // A mark made without a press is never a silent one: the phone says what it heard.
                PromptResult(event, listOf(if (answer == PromptAnswer.HEARD) Part("H1", r1(med, now).values) else r1(med, now)), showUndo = true)
            } else {
                PromptResult(null, fresh.parts)
            }
        }
        PromptAnswer.NO -> PromptResult(null, listOf(Part("NO1")))
        PromptAnswer.NOT_SURE, PromptAnswer.NOT_SURE_VOICE -> {
            val source = if (answer == PromptAnswer.NOT_SURE) EventSource.PROMPT_TAP else EventSource.PROMPT_VOICE
            PromptResult(DoseEvent(newId, med.id, slotKey, now, EventKind.NOT_SURE, source), listOf(Part("NS3")))
        }
        PromptAnswer.TIMEOUT -> PromptResult(null, listOf(Part("NM1")))
    }

    fun undo(taken: DoseEvent, now: Long, events: List<DoseEvent>, newId: String): PromptResult = when {
        isUndone(taken, events) -> PromptResult(null, listOf(Part("NM1")))
        now - taken.atMillis > UNDO_MS -> PromptResult(null, listOf(Part("AM1", markValues(taken, now))))
        else -> PromptResult(
            DoseEvent(newId, taken.medId, taken.slotKey, now, EventKind.UNDONE, taken.source, undoes = taken.id),
            listOf(Part("U2")),
        )
    }

    internal fun r1(med: Medicine, now: Long): Part =
        Part("R1", mapOf("nickname" to Answers.nickname(med), "time" to local(now).toLocalTime()))

    companion object {
        const val MIN = 60_000L
        const val UNDO_MS = 60_000L
    }
}
