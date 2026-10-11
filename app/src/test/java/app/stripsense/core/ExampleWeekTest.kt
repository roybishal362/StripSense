package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import app.stripsense.core.report.*
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

/** The example week a presenter can fill in, and take out again. */
class ExampleWeekTest {
    private val engine = DoseEngine()
    private fun med(id: String, vararg times: String) = Medicine(id, id, "", id.uppercase(), listOf("X"), listOf(Strength("5", StrengthUnit.MG)), times = times.toList())
    private val meds = listOf(med("telma", "08:00", "20:00"), med("glyco", "08:00"), med("crocin", "14:30"))
    private val now = ZonedDateTime.of(2026, 10, 10, 21, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
    private var next = 0
    private fun id() = "x${next++}"

    @Test fun itFillsTheSixDaysBeforeTodayAndLeavesTodayAlone() {
        val made = ExampleWeek.build(meds, emptyList(), now, engine, ::id)
        assertTrue(made.all { it.example })
        val today = engine.dosingDate(now).toString()
        assertTrue(made.none { it.slotKey.startsWith(today) })
        assertTrue(made.all { it.slotKey.substringBefore('T') >= engine.dosingDate(now).minusDays(6).toString() })
        val week = WeekViews.build(meds, made, now, engine, mapOf())
        // Four doses a day for six days: 24. Most are marked, two are not, one is "not sure".
        assertEquals(24, made.size + 2)
        assertEquals(1, made.count { it.kind == EventKind.NOT_SURE })
        val past = week.days.dropLast(1).flatMap { it.second }
        assertEquals(21, past.count { it.mark == DoseMark.MARKED })
        assertEquals(2, past.count { it.mark == DoseMark.NOT_MARKED })
        assertEquals(1, past.count { it.late })
    }

    @Test fun theWeekShowsAllThreeWaysThePhoneKnows() {
        val made = ExampleWeek.build(meds, emptyList(), now, engine, ::id)
        val proofs = WeekViews.build(meds, made, now, engine).days.flatMap { it.second }.mapNotNull { it.proof }
        for (proof in Proof.entries) assertTrue(proof.name, proofs.count { it == proof } >= 2)
        assertTrue(proofs.count { it == Proof.HEARD } > proofs.count { it == Proof.PRESSED })
    }

    @Test fun theSameMedicinesGiveTheSameWeek() {
        fun picture() = ExampleWeek.build(meds, emptyList(), now, engine, ::id).map { Triple(it.medId + it.slotKey, it.kind, it.source to it.atMillis) }
        assertEquals(picture(), picture())
    }

    @Test fun aDoseWithARealRecordIsNeverTouchedAndTakingTheExampleOutLeavesOnlyWhatWasReal() {
        val yesterday = engine.slotsFor(meds[0], engine.dosingDate(now).minusDays(1)).first()
        val real = listOf(DoseEvent("r1", meds[0].id, yesterday.key, engine.slotMillis(yesterday) + 60_000, EventKind.TAKEN, EventSource.PROMPT_TAP))
        val made = ExampleWeek.build(meds, real, now, engine, ::id)
        assertTrue(made.none { it.medId == meds[0].id && it.slotKey == yesterday.key })
        val all = real + made
        assertTrue(ExampleWeek.shown(all))
        assertEquals(real, all.filterNot { it.example })
    }

    @Test fun whenANewDayBeginsTheExampleWeekIsMovedAlong() {
        val made = ExampleWeek.build(meds, emptyList(), now, engine, ::id)
        assertFalse(ExampleWeek.stale(made, now, engine))
        assertFalse(ExampleWeek.stale(emptyList(), now, engine))
        val nextDay = now + 24 * 60 * 60_000
        assertTrue(ExampleWeek.stale(made, nextDay, engine))
        val again = ExampleWeek.build(meds, emptyList(), nextDay, engine, ::id)
        assertFalse(ExampleWeek.stale(again, nextDay, engine))
    }

    @Test fun theFirstDayOfTheWeekIsShownOnTheFamilysWeek() {
        // The week leaves out doses from before the phone knew a medicine. A dose's own time counts, not the minutes after it.
        val made = ExampleWeek.build(meds, emptyList(), now, engine, ::id)
        val data = StoreData(medicines = meds, events = made)
        val week = WeekViews.build(meds, made, now, engine, WeekViews.knownSince(data))
        assertEquals(4, week.days.first().second.size)
        assertTrue(week.days.first().second.all { it.mark == DoseMark.MARKED })
    }

    @Test fun everyMedicineIsShownFromTheFirstDayOfTheWeek() {
        val made = ExampleWeek.build(meds, emptyList(), now, engine, ::id)
        val firstDay = engine.dosingDate(now).minusDays(6).toString()
        for (m in meds) assertTrue(m.id, made.any { it.medId == m.id && it.slotKey.startsWith(firstDay) && it.kind == EventKind.TAKEN })
    }

    @Test fun oneMedicineOnceADayStillGivesAWeekWorthShowing() {
        val one = listOf(med("telma", "20:00"))
        // In the morning, before today's own dose has come up.
        val morning = now - 11 * 60 * 60_000
        val made = ExampleWeek.build(one, emptyList(), morning, engine, ::id)
        assertEquals(5, made.size)
        assertEquals(83, WeekViews.build(one, made, morning, engine).markedPercent)
    }
}
