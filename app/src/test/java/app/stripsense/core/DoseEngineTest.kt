package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*

import app.stripsense.core.Fixtures.LIST
import app.stripsense.core.Fixtures.M1
import app.stripsense.core.Fixtures.M3
import app.stripsense.core.Fixtures.M5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Every vector T1 to T18 of spec 02 section 10, and the window table of section 2. */
class DoseEngineTest {

    private val engine = DoseEngine(ZoneId.of("Asia/Kolkata"))
    private val m1 = M1.copy(noteEn = "after food", noteHi = "खाने के बाद")
    private val list = listOf(m1) + LIST.drop(1)

    private fun at(h: Int, m: Int, s: Int = 0, day: Int = 10) =
        engine.millis(LocalDateTime.of(2026, 10, day, h, m, s))

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)
    private fun key(h: Int, m: Int, day: Int = 10) = slotKey(LocalDate.of(2026, 10, day), t(h, m))
    private fun ev(id: String, med: Medicine, slot: String, at: Long, kind: EventKind = EventKind.TAKEN, undoes: String? = null) =
        DoseEvent(id, med.id, slot, at, kind, EventSource.PROMPT_TAP, undoes)

    private val seeded = ev("seed", m1, key(20, 0, day = 9), at(20, 10, day = 9))
    private fun ids(parts: List<Part>) = parts.map { it.id }
    private fun part(parts: List<Part>, id: String) = parts.first { it.id == id }

    @Test fun windowTable() {
        fun med(vararg times: String) = M1.copy(times = times.toList())
        fun w(med: Medicine, h: Int) = engine.window(DoseSlot(med.id, LocalDate.of(2026, 10, 10), t(h, 0)), med)
        fun half(med: Medicine, h: Int) = engine.halfInterval(DoseSlot(med.id, LocalDate.of(2026, 10, 10), t(h, 0)), med) / 3_600_000.0
        val once = med("08:00")
        assertEquals(at(7, 30)..at(10, 0), w(once, 8)); assertEquals(12.0, half(once, 8), 0.0)
        val twice = med("08:00", "20:00")
        assertEquals(at(7, 30)..at(10, 0), w(twice, 8)); assertEquals(at(19, 30)..at(22, 0), w(twice, 20))
        assertEquals(6.0, half(twice, 8), 0.0); assertEquals(6.0, half(twice, 20), 0.0)
        val three = med("08:00", "14:00", "20:00")
        assertEquals(at(13, 30)..at(16, 0), w(three, 14))
        assertEquals(6.0, half(three, 8), 0.0); assertEquals(3.0, half(three, 14), 0.0); assertEquals(3.0, half(three, 20), 0.0)
        val close = med("08:00", "12:00")
        assertEquals(at(7, 30)..at(10, 0), w(close, 8)); assertEquals(at(11, 30)..at(14, 0), w(close, 12))
        assertEquals(10.0, half(close, 8), 0.0); assertEquals(2.0, half(close, 12), 0.0)
    }

    @Test fun t01_dueNowAsks() {
        val a = engine.stripAnswer(m1, at(8, 5), listOf(seeded))
        assertEquals(listOf("P1", "P2", "NOTE", "CHK", "Q1"), ids(a.parts))
        assertTrue(a.askNow)
        assertEquals("2026-10-10T08:00", a.slotKey)
        val p2 = part(a.parts, "P2").values
        assertEquals(1, (p2["when"] as WhenValue).daysAgo)
        assertEquals(t(20, 10), p2["time"])
        assertEquals(715, p2["ago"])
        assertEquals(Slot.MORNING, part(a.parts, "P1").values["slot"])
        assertEquals(
            "Last one was marked yesterday at 8:10 pm, 11 hours ago.",
            Sentences.render(part(a.parts, "P2"), "en", Person()).shown,
        )
    }

    @Test fun t02_alreadyMarked() {
        val events = listOf(seeded, ev("a", m1, key(8, 0), at(8, 5)))
        val a = engine.stripAnswer(m1, at(8, 6), events)
        assertEquals(listOf("A1", "CHK"), ids(a.parts))
        assertFalse(a.askNow)
        assertEquals(t(8, 5), part(a.parts, "A1").values["time"])
        assertEquals(1, part(a.parts, "A1").values["ago"])
    }

    @Test fun t03_beforeWindow() {
        val a = engine.stripAnswer(m1, at(7, 10), listOf(seeded))
        assertEquals(listOf("W1", "P2", "CHK"), ids(a.parts))
        assertEquals(Slot.MORNING, part(a.parts, "W1").values["slot"])
        assertEquals(t(8, 0), part(a.parts, "W1").values["time"])
        assertFalse(a.askNow)
    }

    @Test fun t04_passedAfterCutoff() {
        val a = engine.stripAnswer(m1, at(11, 0), listOf(seeded))
        assertEquals(listOf("P3", "P2", "NX1", "CHK"), ids(a.parts))
        assertEquals(t(14, 0), part(a.parts, "NX1").values["time"])
        assertFalse(a.askNow)
    }

    @Test fun t05_afternoonAfterMorningMark() {
        val events = listOf(seeded, ev("a", m1, key(8, 0), at(8, 5)))
        val a = engine.stripAnswer(m1, at(13, 40), events)
        assertEquals(listOf("P1", "P2", "NOTE", "CHK", "Q1"), ids(a.parts))
        assertEquals(Slot.AFTERNOON, part(a.parts, "P1").values["slot"])
        val p2 = part(a.parts, "P2").values
        assertEquals(0, (p2["when"] as WhenValue).daysAgo)
        assertEquals(t(8, 5), p2["time"])
        assertEquals(335, p2["ago"])
        assertTrue(a.askNow)
        assertEquals("2026-10-10T14:00", a.slotKey)
        assertEquals(
            "Last one was marked today at 8:05 am, 5 hours ago.",
            Sentences.render(part(a.parts, "P2"), "en", Person()).shown,
        )
    }

    @Test fun t06_notSure() {
        val events = listOf(seeded, ev("n", m1, key(8, 0), at(8, 2), EventKind.NOT_SURE))
        val a = engine.stripAnswer(m1, at(8, 5), events)
        assertEquals(listOf("NAME", "NS1"), ids(a.parts))
        assertEquals(t(8, 2), part(a.parts, "NS1").values["time"])
        assertFalse(a.askNow)
    }

    @Test fun t07_undoneMarkIsLikeNothing() {
        val events = listOf(seeded, ev("a", m1, key(8, 0), at(8, 5)), ev("u", m1, key(8, 0), at(8, 6), EventKind.UNDONE, "a"))
        val a = engine.stripAnswer(m1, at(8, 7), events)
        assertEquals(listOf("P1", "P2", "NOTE", "CHK", "Q1"), ids(a.parts))
        assertTrue(a.askNow)
        assertEquals(1, (part(a.parts, "P2").values["when"] as WhenValue).daysAgo)
    }

    private val twoSlots = M1.copy(id = "m7", times = listOf("08:00", "12:00"))

    @Test fun t08_recentMark() {
        val events = listOf(ev("a", twoSlots, key(8, 0), at(9, 55)))
        val a = engine.stripAnswer(twoSlots, at(11, 35), events)
        assertEquals(listOf("P3", "P2", "CHK"), ids(a.parts))
        assertEquals(Slot.AFTERNOON, part(a.parts, "P3").values["slot"])
        assertFalse(a.askNow)
    }

    @Test fun t09_recentMarkOver() {
        val events = listOf(ev("a", twoSlots, key(8, 0), at(9, 55)))
        val a = engine.stripAnswer(twoSlots, at(12, 0), events)
        assertEquals("P1", a.parts.first().id)
        assertEquals("Q1", a.parts.last().id)
        assertTrue(a.askNow)
    }

    @Test fun t10_markButton() {
        val m = engine.markButton(M5, at(8, 10), emptyList())
        assertEquals(listOf("MB1"), ids(m.parts))
        assertTrue(m.ask)
        val r = engine.confirmMark(M5, m.slotKey, at(8, 10), emptyList(), "x")
        assertEquals(EventKind.TAKEN, r.event!!.kind)
        assertEquals(EventSource.MARK_BUTTON, r.event!!.source)
        assertEquals(listOf("R1"), ids(r.parts))
        assertTrue(r.showUndo)
    }

    @Test fun t10b_yesTap() {
        val a = engine.stripAnswer(m1, at(8, 5), listOf(seeded))
        val r = engine.answerPrompt(m1, a.slotKey, PromptAnswer.YES_TAP, at(8, 5), listOf(seeded), "x")
        assertEquals(DoseEvent("x", m1.id, "2026-10-10T08:00", at(8, 5), EventKind.TAKEN, EventSource.PROMPT_TAP), r.event)
        assertEquals(listOf("R1"), ids(r.parts))
        assertEquals(t(8, 5), r.parts[0].values["time"])
        assertTrue(r.showUndo)
    }

    @Test fun t10c_notSureNoTimeout() {
        val slot = key(8, 0)
        val ns = engine.answerPrompt(m1, slot, PromptAnswer.NOT_SURE, at(8, 5), listOf(seeded), "x")
        assertEquals(EventKind.NOT_SURE, ns.event!!.kind)
        assertEquals(listOf("NS3"), ids(ns.parts))
        val no = engine.answerPrompt(m1, slot, PromptAnswer.NO, at(8, 5), listOf(seeded), "x")
        assertNull(no.event); assertEquals(listOf("NO1"), ids(no.parts))
        val to = engine.answerPrompt(m1, slot, PromptAnswer.TIMEOUT, at(8, 5), listOf(seeded), "x")
        assertNull(to.event); assertEquals(listOf("NM1"), ids(to.parts))
    }

    @Test fun t10d_yesWhenAlreadyMarked() {
        val events = listOf(seeded, ev("a", m1, key(8, 0), at(8, 5)))
        val r = engine.answerPrompt(m1, key(8, 0), PromptAnswer.YES_TAP, at(8, 6), events, "x")
        assertNull(r.event)
        assertEquals(listOf("A1", "CHK"), ids(r.parts))
    }

    @Test fun t10e_undo() {
        val taken = engine.answerPrompt(m1, key(8, 0), PromptAnswer.YES_TAP, at(8, 5), listOf(seeded), "x").event!!
        val events = listOf(seeded, taken)
        val u = engine.undo(taken, at(8, 5, 30), events, "u")
        assertEquals(EventKind.UNDONE, u.event!!.kind)
        assertEquals("x", u.event!!.undoes)
        assertEquals(listOf("U2"), ids(u.parts))
        val again = engine.undo(taken, at(8, 5, 40), events + u.event!!, "u2")
        assertNull(again.event); assertEquals(listOf("NM1"), ids(again.parts))
        val late = engine.undo(taken, at(8, 7), events, "u3")
        assertNull(late.event); assertEquals(listOf("AM1"), ids(late.parts))
        assertEquals(t(8, 5), late.parts[0].values["time"])
    }

    @Test fun t11_markButtonAlreadyMarked() {
        val r = engine.confirmMark(M5, key(8, 0), at(8, 10), emptyList(), "x")
        val m = engine.markButton(M5, at(8, 13), listOf(r.event!!))
        assertEquals(listOf("AM1"), ids(m.parts))
        assertEquals(t(8, 10), m.parts[0].values["time"])
        assertEquals(3, m.parts[0].values["ago"])
        assertFalse(m.ask)
    }

    @Test fun t12_markButtonOutsideWindow() =
        assertEquals(listOf("MB0"), ids(engine.markButton(M5, at(11, 0), emptyList()).parts))

    @Test fun t13_statusMarked() {
        val p = engine.statusAnswer(m1, at(8, 6), listOf(ev("a", m1, key(8, 0), at(8, 5))))
        assertEquals(listOf("VS2", "CHK", "V2"), ids(p))
        assertEquals(t(8, 5), p[0].values["time"])
        assertEquals(1, p[0].values["ago"])
    }

    @Test fun t14_statusStopped() = assertEquals(listOf("VS8", "S0"), ids(engine.statusAnswer(M3, at(8, 5), emptyList())))

    @Test fun t15_whatsLeft() {
        val p = engine.whatsLeft(list, at(8, 6), listOf(ev("a", m1, key(8, 0), at(8, 5))))
        assertEquals(listOf("WL2", "WL1", "CHK"), ids(p))
        @Suppress("UNCHECKED_CAST")
        fun items(id: String) = (part(p, id).values["list"] as List<ListItem>).map { it.nickname.en to it.time }
        assertEquals(listOf("sugar tablet" to t(8, 0), "eye drops" to t(8, 0)), items("WL2"))
        assertEquals(
            listOf(
                "fever tablet" to t(14, 0), "vitamin tablet" to t(14, 0),
                "fever tablet" to t(20, 0), "sugar tablet" to t(20, 0), "eye drops" to t(20, 0),
            ),
            items("WL1"),
        )
    }

    @Test fun t16_everythingMarked() {
        var n = 0
        val events = list.filter { it.status == MedStatus.ACTIVE }.flatMap { med ->
            engine.slotsFor(med, LocalDate.of(2026, 10, 10)).map { ev("e${n++}", med, it.key, at(8, 0)) }
        }
        assertEquals(listOf("WL0"), ids(engine.whatsLeft(list, at(21, 0), events)))
    }

    @Test fun t17_afterMidnightIsStillYesterdaysDosingDay() {
        val a = engine.stripAnswer(m1, at(2, 30, day = 11), listOf(seeded))
        assertEquals(listOf("P3", "P2", "CHK"), ids(a.parts))
        assertEquals(Slot.NIGHT, part(a.parts, "P3").values["slot"])
        assertEquals("2026-10-10T20:00", a.slotKey)
        assertEquals(1, (part(a.parts, "P2").values["when"] as WhenValue).daysAgo)
        assertEquals(
            "Last one was marked yesterday at 8:10 pm.",
            Sentences.render(part(a.parts, "P2"), "en", Person()).shown,
        )
        assertFalse(a.askNow)
    }

    @Test fun t18_freshDay() {
        val events = listOf(seeded, ev("a", m1, key(8, 0), at(8, 5)))
        var n = 0
        val undos = engine.freshDay(at(8, 6), events, { "f${n++}" })
        assertEquals(1, undos.size)
        assertEquals("a", undos[0].undoes)
        val all = events + undos
        assertTrue(all.any { it.id == "a" })
        val a = engine.stripAnswer(m1, at(8, 5), all)
        assertEquals(listOf("P1", "P2", "NOTE", "CHK", "Q1"), ids(a.parts))
        assertTrue(a.askNow)
    }

    @Test fun seedYesterdayIsIdempotent() {
        var n = 0
        val first = engine.seedYesterday(list, at(8, 5), emptyList(), { "s${n++}" })
        assertEquals(8, first.size) // fever 3, vitamin 1, sugar 2, eye drops 2; the stopped BP tablet is skipped
        val second = engine.seedYesterday(list, at(8, 5), first, { "s${n++}" })
        assertTrue(second.isEmpty())
        assertTrue(first.all { it.source == EventSource.DEMO })
    }
}
