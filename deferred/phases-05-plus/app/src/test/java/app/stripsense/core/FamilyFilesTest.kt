package app.stripsense.core

import app.stripsense.core.Fixtures.LIST
import app.stripsense.core.Fixtures.M1
import app.stripsense.core.Fixtures.M3
import app.stripsense.core.Fixtures.M4
import app.stripsense.core.Fixtures.M5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class WeekAndEveningTest {
    private val engine = DoseEngine(ZoneId.of("Asia/Kolkata"))
    private fun at(h: Int, m: Int, day: Int = 10) = engine.millis(LocalDateTime.of(2026, 10, day, h, m))
    private fun key(h: Int, m: Int) = slotKey(LocalDate.of(2026, 10, 10), LocalTime.of(h, m))
    private fun ev(id: String, med: Medicine, slot: String, at: Long, source: EventSource) =
        DoseEvent(id, med.id, slot, at, EventKind.TAKEN, source)

    @Test fun weekAfterT15() {
        val text = WeekFile.build(LIST, listOf(ev("a", M1, key(8, 0), at(8, 5), EventSource.PROMPT_TAP)), at(8, 6), Person(), 0, engine)
        val lines = text.trim().lines()
        assertEquals("exported_at,2026-10-10T08:06,person,Maa,list_saved_at,,woman,true", lines[0])
        assertEquals(WeekFile.HEADER, lines[1])
        assertTrue(lines.all { it.split(',').size == 8 })
        assertTrue("2026-10-10,08:00,fever tablet,FEVORA,500 mg,marked,08:05,prompt" in lines)
        assertTrue("2026-10-10,08:00,sugar tablet,GLYMET,500 mg,not marked,," in lines)
        assertTrue("2026-10-10,08:00,eye drops,TEARVA,0.5 %,not marked,," in lines)
        assertTrue("2026-10-10,14:00,fever tablet,FEVORA,500 mg,later,," in lines)
        assertTrue("2026-10-10,20:00,sugar tablet,GLYMET,500 mg,later,," in lines)
        assertTrue(lines.none { it.contains("BP tablet") })
        assertEquals("2026-10-04", lines[2].substringBefore(','))
    }

    private val exampleEvents = listOf(
        ev("a", M1, key(8, 0), at(8, 5), EventSource.PROMPT_TAP),
        ev("b", M5, key(8, 0), at(8, 12), EventSource.MARK_BUTTON),
        ev("c", M1, key(14, 0), at(14, 6), EventSource.PROMPT_VOICE),
    )

    @Test fun eveningExample() = assertEquals(
        "Maa, 10 October. Morning: fever tablet marked 8:05 am (on the prompt); sugar tablet not marked; eye drops marked 8:12 am (with its button). " +
            "Afternoon: fever tablet marked 2:06 pm (by voice); vitamin tablet not marked. " +
            "Night: fever tablet not marked; sugar tablet not marked; eye drops not marked. " +
            "\"Not marked\" doesn't always mean missed. Please check with Maa before asking her to take another.",
        EveningText.build(LIST, exampleEvents, at(21, 30), Person(), engine),
    )

    @Test fun eveningBeforeFirstSlotAndForAMan() = assertEquals(
        "Papa, 10 October. \"Not marked\" doesn't always mean missed. Please check with Papa before asking him to take another.",
        EveningText.build(LIST, emptyList(), at(6, 0), Person(callEn = "Papa", isWoman = false), engine),
    )

    /** laptop/sample-week.csv must be exactly what the code makes for the example day. */
    @Test fun sampleWeekFileMatchesTheCode() {
        val file = listOf(File("../laptop/sample-week.csv"), File("laptop/sample-week.csv")).firstOrNull { it.parentFile?.exists() == true }
            ?: error("laptop/ folder not found")
        // Set STRIPSENSE_WRITE_SAMPLE=1 to write the file again after a deliberate change to WeekFile.
        if (System.getenv("STRIPSENSE_WRITE_SAMPLE") == "1") file.writeText(sampleWeek(engine))
        assertEquals(sampleWeek(engine), file.readText().replace("\r\n", "\n"))
    }

    companion object {
        /** A made-up full week whose last day is the evening-text example. */
        fun sampleWeek(engine: DoseEngine): String {
            val events = mutableListOf<DoseEvent>()
            var n = 0
            for (day in 4..9) for (med in LIST.filter { it.status == MedStatus.ACTIVE }) {
                for (slot in engine.slotsFor(med, LocalDate.of(2026, 10, day))) {
                    // A few gaps, so the page shows every state.
                    if ((day == 6 && med.id == M4.id) || (day == 8 && slot.time.hour == 20 && med.id == M1.id)) continue
                    val kind = if (day == 7 && med.id == M5.id && slot.time.hour == 8) EventKind.NOT_SURE else EventKind.TAKEN
                    val src = if (med.type == MedType.BUTTON) EventSource.MARK_BUTTON else EventSource.PROMPT_TAP
                    events += DoseEvent("s${n++}", med.id, slot.key, engine.slotMillis(slot) + (5 + n % 9) * 60_000L, kind, src)
                }
            }
            val d10 = { h: Int, m: Int -> engine.millis(LocalDateTime.of(2026, 10, 10, h, m)) }
            events += DoseEvent("a", M1.id, slotKey(LocalDate.of(2026, 10, 10), LocalTime.of(8, 0)), d10(8, 5), EventKind.TAKEN, EventSource.PROMPT_TAP)
            events += DoseEvent("b", M5.id, slotKey(LocalDate.of(2026, 10, 10), LocalTime.of(8, 0)), d10(8, 12), EventKind.TAKEN, EventSource.MARK_BUTTON)
            events += DoseEvent("c", M1.id, slotKey(LocalDate.of(2026, 10, 10), LocalTime.of(14, 0)), d10(14, 6), EventKind.TAKEN, EventSource.PROMPT_VOICE)
            return WeekFile.build(LIST, events, d10(21, 30), Person(), d10(11, 20), engine)
        }
    }
}

class TrialsTest {
    private val list = LIST
    private fun s(truth: String, v: Verdict) = Trials.score(truth, v, list)

    @Test fun herMatch() = assertEquals(TrialScore.RIGHT, s(M1.id, Verdict.Match(M1)))
    @Test fun herMatchedAsAnother() = assertEquals(TrialScore.WRONG, s(M1.id, Verdict.Match(M4)))
    @Test fun herNamedAnotherByStoppedOrNotShown() {
        assertEquals(TrialScore.WRONG, s(M1.id, Verdict.Stopped(M3)))
        assertEquals(TrialScore.WRONG, s(M1.id, Verdict.NotShown(M4)))
        assertEquals(TrialScore.WRONG, s(M1.id, Verdict.ButtonOnly(M5)))
    }
    @Test fun herStoppedAndButtonKinds() {
        assertEquals(TrialScore.RIGHT, s(M3.id, Verdict.Stopped(M3)))
        assertEquals(TrialScore.RIGHT, s(M5.id, Verdict.ButtonOnly(M5)))
    }
    @Test fun herNotSure() {
        for (v in listOf(Verdict.Unreadable, Verdict.NotSure, Verdict.OneAtATime, Verdict.NotShown(M1), Verdict.NotTaught(M1))) {
            assertEquals(TrialScore.NOT_SURE, s(M1.id, v))
        }
    }
    @Test fun herWrongRefusal() {
        assertEquals(TrialScore.WRONG_REFUSAL, s(M1.id, Verdict.Different(M1, "650 mg", "500 mg")))
        assertEquals(TrialScore.WRONG_REFUSAL, s(M1.id, Verdict.WrongMedicine(M1)))
        assertEquals(TrialScore.WRONG_REFUSAL, s(M1.id, Verdict.Unknown))
    }
    @Test fun notHersNamed() {
        assertEquals(TrialScore.WRONG, s(Trials.NOT_HERS, Verdict.Match(M1)))
        assertEquals(TrialScore.WRONG, s(Trials.NOT_HERS, Verdict.ButtonOnly(M5)))
    }
    @Test fun notHersRefused() {
        for (v in listOf(Verdict.Different(M1, "", ""), Verdict.WrongMedicine(M1), Verdict.Unknown, Verdict.NotShown(M1), Verdict.NotTaught(M1), Verdict.Stopped(M3))) {
            assertEquals(TrialScore.RIGHT, s(Trials.NOT_HERS, v))
        }
    }
    @Test fun notHersNotSure() = assertEquals(TrialScore.NOT_SURE, s(Trials.NOT_HERS, Verdict.Unreadable))
    @Test fun coveredMatchIsWrong() = assertEquals(TrialScore.WRONG, s(Trials.COVERED, Verdict.Match(M1)))
    @Test fun coveredElseRight() = assertEquals(TrialScore.RIGHT, s(Trials.COVERED, Verdict.Unreadable))

    @Test fun summaryOfASmallList() {
        fun t(truth: String, score: TrialScore, ms: Long, look: String? = null) = Trial("x", 0, truth, "v", null, score, ms, null, look)
        val sum = Trials.summary(
            listOf(
                t(M1.id, TrialScore.RIGHT, 1000), t(M1.id, TrialScore.NOT_SURE, 3000),
                t(Trials.NOT_HERS, TrialScore.RIGHT, 2000, look = M1.id), t(Trials.NOT_HERS, TrialScore.WRONG, -1),
                t(Trials.COVERED, TrialScore.RIGHT, 4000),
            ),
        )
        assertEquals(Trials.Counts(5, 3, 1, 1, 0), sum.all)
        assertEquals(2, sum.hers.tries); assertEquals(2, sum.notHers.tries); assertEquals(1, sum.covered.tries)
        assertEquals(2500.0, sum.medianMs!!, 0.0)
        assertEquals(4000L, sum.p90Ms)
        assertEquals(1, sum.lookAloneNamed); assertEquals(1, sum.printNamed)
        assertEquals(5L, Trials.oneIn(1, 5)); assertEquals(null, Trials.oneIn(0, 5))
    }
}
