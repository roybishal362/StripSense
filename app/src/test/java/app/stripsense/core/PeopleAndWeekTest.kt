package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import app.stripsense.core.guide.*
import app.stripsense.core.report.*
import app.stripsense.core.say.*
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class PeopleAndWeekTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private val papa = Profile("papa", "Papa", "पापा")
    private val telma = Medicine("t", "Telma 40", "", "TELMA", listOf("TELMISARTAN"), listOf(mg("40")), times = listOf("20:00"), purpose = "BP")
    private val glyco = Medicine("g", "Glycomet", "", "GLYCOMET", listOf("METFORMIN"), listOf(mg("500")), times = listOf("08:00"), purpose = "SUGAR", personId = "papa")
    private val data = StoreData(person = Person(callEn = "Maa", callHi = "माँ"), medicines = listOf(telma, glyco), people = listOf(papa))
    private val engine = DoseEngine()
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun everyMedicineBelongsToOnePersonAndTodayShowsOnePersonAtATime() {
        assertEquals(listOf("Maa", "Papa"), People.all(data).map { it.callEn })
        assertEquals(listOf(telma), People.medicines(data))
        assertEquals(listOf(glyco), People.medicines(data.copy(activePerson = "papa")))
        assertTrue(People.someoneElses(data, glyco))
        assertFalse(People.someoneElses(data.copy(activePerson = "papa"), glyco))
    }
    @Test fun aPersonWhoWasRemovedLeavesTheirMedicinesWithTheFirstPerson() {
        val gone = data.copy(people = emptyList(), activePerson = "papa")
        assertEquals(People.FIRST, People.active(gone))
        assertEquals(listOf(telma, glyco), People.medicines(gone))
        assertEquals("Maa", People.of(gone, glyco).callEn)
    }
    @Test fun theCameraSaysWhoseStripItIsAndNothingMore() {
        val found = ScanMessage(ScanHint.FOUND, ScanTarget(1, glyco, MedicationTiming(TimingKind.NOW), ScanSide.SINGLE, false), owner = papa)
        assertEquals("This is Papa's sugar medicine, Glycomet. It is not yours.", Sentences.scanGuidance(found, "en"))
        assertEquals("यह पापा की शुगर की दवा, Glycomet है। यह आपकी नहीं है।", Sentences.scanGuidance(found, "hi"))
        // It is different news from the same strip seen by its owner, so it is said when the phone changes hands.
        val queue = ScanAnnouncements()
        queue.next(found.copy(owner = null), 0)
        assertNotNull(queue.next(found.copy(owner = null), ScanAnnouncements.FOUND_MS))
        queue.next(found, 5000)
        assertNotNull(queue.next(found, 5000 + ScanAnnouncements.FOUND_MS))
    }
    @Test fun theWeekCountsWhatWasRecordedAndListsWhatNeedsALook() {
        val key = { day: Int -> engine.slotsFor(telma, engine.local(at(day, 12)).toLocalDate()).single().key }
        val events = listOf(
            DoseEvent("a", telma.id, key(8), at(8, 20, 5), EventKind.TAKEN, EventSource.PROMPT_TAP),
            DoseEvent("b", telma.id, key(9), at(9, 22, 30), EventKind.TAKEN, EventSource.PROMPT_TAP),
            DoseEvent("c", telma.id, key(7), at(7, 20, 10), EventKind.NOT_SURE, EventSource.REMINDER_TAP))
        val week = WeekViews.build(listOf(telma), events, at(10, 18), engine)
        assertEquals(7, week.days.size)
        assertEquals(2, week.marked)
        assertEquals(1, week.notSure)
        assertEquals(1, week.later)
        assertEquals(3, week.notMarked)
        assertEquals(33, week.markedPercent)
        // Newest first: the late mark of the 9th, then the not-sure of the 7th, then the unmarked days before.
        val needs = week.attention
        assertTrue(needs.first().late && needs.first().markedAt == LocalTime.of(22, 30))
        assertEquals(DoseMark.NOT_SURE, needs[1].mark)
        assertEquals(5, needs.size)
        assertNull(WeekViews.build(emptyList(), events, at(10, 18), engine).markedPercent)
    }
    @Test fun dosesFromBeforeTheMedicineWasSavedAreNotCountedAgainstThePerson() {
        // Saved on the 10th at noon: the week has one dose that has come up... none yet, and one still to come.
        val week = WeekViews.build(listOf(telma), emptyList(), at(10, 18), engine, mapOf(telma.id to at(10, 12)))
        assertEquals(0, week.notMarked)
        assertEquals(1, week.later)
        assertNull(week.markedPercent)
        val look = Look("l", telma.id, emptyList(), "", emptyList(), taughtAtMillis = at(9, 9))
        val since = WeekViews.knownSince(StoreData(medicines = listOf(telma), looks = listOf(look)))
        assertEquals(1, WeekViews.build(listOf(telma), emptyList(), at(10, 18), engine, since).notMarked)
    }
}
