package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.SupplyText
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

/** The phone counts the tablets down by itself, follows the doctor's number of days, and says so before it is a problem. */
class SupplyTest {
    private val engine = DoseEngine()
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val now = at(11, 9)
    private val sugar = Fixtures.M4 // two doses a day
    private fun key(day: Int, hour: Int) = slotKey(LocalDate.of(2026, 10, day), LocalTime.of(hour, 0))
    private fun taken(id: String, day: Int, hour: Int, example: Boolean = false, med: Medicine = sugar) =
        DoseEvent(id, med.id, key(day, hour), at(day, hour, 5), EventKind.TAKEN, EventSource.PROMPT_TAP, example = example)

    @Test fun nothingIsSaidUntilTheFamilyCounts() {
        val supply = Supply.of(sugar, listOf(taken("a", 10, 8)), now, engine)
        assertFalse(supply.known)
        assertFalse(supply.needsALook)
        assertNull(SupplyText.line(supply, "en"))
        assertTrue(Supply.needingALook(listOf(sugar), emptyList(), now, engine).isEmpty())
    }

    @Test fun thePhoneCountsDownByItself() {
        val counted = sugar.copy(stock = 10, stockAtMillis = at(10, 7))
        val events = listOf(
            taken("before", 9, 20),             // before the count: already not in the ten
            taken("a", 10, 8),                  // one tablet
            taken("b", 10, 20),                 // marked, then undone: the tablet is still there
            DoseEvent("u", sugar.id, key(10, 20), at(10, 20, 6), EventKind.UNDONE, EventSource.PROMPT_TAP, undoes = "b"),
            taken("example", 11, 8, example = true), // an example week is nobody's tablets
        )
        val supply = Supply.of(counted, events, now, engine)
        assertEquals(9, supply.left)
        assertEquals(4, supply.daysLeft)
        assertEquals(LocalDate.of(2026, 10, 15), supply.runsOut)
        assertFalse(supply.low)
        assertEquals("9 tablets left, enough for 4 days.", SupplyText.line(supply, "en"))
        assertNull(SupplyText.chip(supply, "en"))
    }

    @Test fun itSaysDaysAheadThatAMedicineIsRunningOut() {
        val five = Supply.of(sugar.copy(stock = 5, stockAtMillis = at(11, 7)), emptyList(), now, engine)
        assertTrue(five.low && five.needsALook)
        assertEquals("Only 2 days left: 5 tablets. Tell your family to buy more.", SupplyText.line(five, "en"))
        assertEquals("2 days left", SupplyText.chip(five, "en"))
        val one = Supply.of(sugar.copy(stock = 1, stockAtMillis = at(11, 7)), emptyList(), now, engine)
        assertEquals("Tablets for today only: 1 left. Tell your family to buy more.", SupplyText.line(one, "en"))
        val none = Supply.of(sugar.copy(stock = 1, stockAtMillis = at(11, 7)), listOf(taken("a", 11, 8)), now, engine)
        assertTrue(none.empty)
        assertEquals("By the count, this medicine is finished. Tell your family.", SupplyText.line(none, "en"))
        // It never goes below nothing, whatever was marked.
        assertEquals(0, Supply.of(sugar.copy(stock = 1, stockAtMillis = at(10, 7)), listOf(taken("a", 10, 8), taken("b", 10, 20), taken("c", 11, 8)), now, engine).left)
    }

    @Test fun theDoctorsNumberOfDaysIsFollowed() {
        val third = Supply.of(sugar.copy(courseDays = 5, courseStart = "2026-10-09"), emptyList(), now, engine)
        assertEquals(3, third.courseDay)
        assertEquals(3, third.courseLeft)
        assertFalse(third.needsALook)
        assertEquals("Day 3 of 5.", SupplyText.line(third, "en"))
        val over = Supply.of(sugar.copy(courseDays = 5, courseStart = "2026-10-01"), emptyList(), now, engine)
        assertTrue(over.courseOver && over.needsALook)
        assertEquals("The 5 days the doctor said are over. Ask the doctor before you go on.", SupplyText.line(over, "en"))
        assertEquals("Course over", SupplyText.chip(over, "en"))
        // With a count as well, both are said.
        val both = Supply.of(sugar.copy(stock = 20, stockAtMillis = at(11, 7), courseDays = 5, courseStart = "2026-10-09"), emptyList(), now, engine)
        assertEquals("20 tablets left, enough for 10 days. Day 3 of 5.", SupplyText.line(both, "en"))
        // A course with no day it began on is no course.
        assertNull(Supply.of(sugar.copy(courseDays = 5), emptyList(), now, engine).courseDay)
    }

    @Test fun tabletsThatWillNotLastTheCourseAreSaid() {
        val short = Supply.of(sugar.copy(stock = 10, stockAtMillis = at(11, 7), courseDays = 15, courseStart = "2026-10-11"), emptyList(), now, engine)
        assertTrue(short.shortForCourse && short.needsALook && !short.low)
        assertEquals("Tablets for 5 days, but 15 days of the course are left. Buy more.", SupplyText.line(short, "en"))
    }

    @Test fun hindiSaysTheSameThings() {
        val low = Supply.of(sugar.copy(stock = 5, stockAtMillis = at(11, 7)), emptyList(), now, engine)
        val plenty = Supply.of(sugar.copy(stock = 40, stockAtMillis = at(11, 7), courseDays = 10, courseStart = "2026-10-10"), emptyList(), now, engine)
        val over = Supply.of(sugar.copy(courseDays = 5, courseStart = "2026-10-01"), emptyList(), now, engine)
        for ((supply, has) in listOf(low to "5", plenty to "40", over to "5")) {
            val line = SupplyText.line(supply, "hi")!!
            assertTrue(line, line.contains(has) && line.none { it in 'a'..'z' || it in 'A'..'Z' })
        }
        assertTrue(SupplyText.line(low, "hi")!!.contains("परिवार"))
        assertTrue(SupplyText.line(over, "hi")!!.contains("डॉक्टर"))
    }

    @Test fun theOneThatRunsOutSoonestComesFirstAndAStoppedOneIsLeftOut() {
        val fever = Fixtures.M1.copy(stock = 3, stockAtMillis = at(11, 7))   // three a day: one day
        val sugarLow = sugar.copy(stock = 5, stockAtMillis = at(11, 7))      // two days
        val vitamin = Fixtures.M2.copy(stock = 60, stockAtMillis = at(11, 7)) // plenty
        val stopped = Fixtures.M3.copy(stock = 1, stockAtMillis = at(11, 7))
        val look = Supply.needingALook(listOf(vitamin, sugarLow, stopped, fever), emptyList(), now, engine)
        assertEquals(listOf("m1", "m4"), look.map { it.first.id })
    }
}
