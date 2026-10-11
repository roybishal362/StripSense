package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import app.stripsense.core.play.*
import app.stripsense.core.say.PlayText
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

/** The journey: points, levels, the row of full days, today's goals and badges, all worked out from the dose records. */
class JourneyTest {
    private val engine = DoseEngine()
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val sugar = Fixtures.M4 // 08:00 and 20:00
    private val since = mapOf(sugar.id to at(8, 0))
    private fun mark(day: Int, hour: Int, source: EventSource = EventSource.PROMPT_TAP) =
        DoseEvent("e$day-$hour", sugar.id, slotKey(LocalDate.of(2026, 10, day), LocalTime.of(hour, 0)), at(day, hour, 5), EventKind.TAKEN, source)
    private fun fullDay(day: Int, source: EventSource = EventSource.PROMPT_TAP) = listOf(mark(day, 8, source), mark(day, 20, source))
    private fun journey(events: List<DoseEvent>, now: Long, code: CodeRecord = CodeRecord(), bonus: Int = 0, beatPattu: Int = 0, gifts: Int = 0, giftToday: Boolean = false) =
        Journeys.of(listOf(sugar), events, now, engine, since, code, bonus, beatPattu, gifts, giftToday)

    @Test fun pointsComeFromHowThePhoneKnowsAboutEachDose() {
        // Day 10: a button press and a strip the phone checked, and the day is full. Today: a tablet the phone heard.
        val events = listOf(mark(10, 8, EventSource.MARK_BUTTON), mark(10, 20, EventSource.PROMPT_TAP), mark(11, 8, EventSource.HEARD))
        val j = Journeys.of(listOf(sugar), events, at(11, 12), engine, mapOf(sugar.id to at(10, 0)))
        assertEquals(Journeys.PRESSED + Journeys.SEEN + Journeys.FULL_DAY + Journeys.HEARD, j.points)
        assertEquals(2, j.level.number)
        assertEquals("Sprout", j.level.en)
        assertEquals(40, j.toNextLevel)
        assertEquals(1, j.stage)
        // A week the phone witnessed is worth three times a week of button presses, before the full days are added.
        val pressed = journey((8..11).flatMap { fullDay(it, EventSource.MARK_BUTTON) }, at(11, 22))
        val heard = journey((8..11).flatMap { fullDay(it, EventSource.HEARD) }, at(11, 22))
        assertEquals(3 * (pressed.points - 4 * Journeys.FULL_DAY), heard.points - 4 * Journeys.FULL_DAY)
        assertTrue(heard.level.number > pressed.level.number)
    }

    @Test fun anUndoneMarkAndAStoppedMedicineEarnNothing() {
        val undone = listOf(mark(11, 8), DoseEvent("u", sugar.id, slotKey(LocalDate.of(2026, 10, 11), LocalTime.of(8, 0)), at(11, 8, 6), EventKind.UNDONE, EventSource.PROMPT_TAP, undoes = "e11-8"))
        assertEquals(0, journey(undone, at(11, 9)).points)
        val stopped = Journeys.of(listOf(sugar.copy(status = MedStatus.STOPPED)), fullDay(10), at(11, 9), engine, since)
        assertEquals(0, stopped.points)
        assertEquals(DayState.NONE, stopped.today.state)
    }

    @Test fun theLevelsGoFromASeedToAForest() {
        assertEquals(listOf(1, 1, 2, 3, 4, 5, 6, 7, 7), listOf(0, 39, 40, 120, 260, 480, 800, 1300, 99999).map { Journeys.level(it).number })
        val top = Journeys.level(5000)
        assertNull(top.next)
        assertEquals("Forest", top.name("en"))
        for (points in listOf(0, 40, 120, 260, 480, 800, 1300)) assertTrue(Journeys.level(points).hi.isNotBlank())
        val half = journey(emptyList(), at(11, 7)).copy(points = 80, level = Journeys.level(80))
        assertEquals(0.5f, half.levelProgress, 0.001f)
    }

    @Test fun theRowOfFullDaysGrowsAndTodayJoinsItOnceItHasAMark() {
        val three = (8..10).flatMap { fullDay(it) }
        // Early this morning: the row stands at three and today is still open.
        val early = journey(three, at(11, 7))
        assertEquals(3, early.streak)
        assertTrue(early.atRisk)
        assertEquals(0, early.dueNow)
        assertEquals(DayState.PART, early.today.state)
        // The morning dose is due: Pattu is waiting.
        val due = journey(three, at(11, 8, 10))
        assertEquals(1, due.dueNow)
        assertEquals(Mood.WAITING, Journeys.mood(due, 8))
        // Marked: today has joined the row.
        val marked = journey(three + mark(11, 8), at(11, 12))
        assertEquals(4, marked.streak)
        assertTrue(marked.atRisk)
        // Both doses: the day is full and nothing is at risk.
        val done = journey(three + fullDay(11), at(11, 21))
        assertEquals(4, done.streak)
        assertFalse(done.atRisk)
        assertEquals(DayState.FULL, done.today.state)
        assertEquals(Mood.PROUD, Journeys.mood(done, 21))
        assertEquals(4, done.best)
    }

    @Test fun aMissedDoseEndsTheRowButTheBestIsKept() {
        val three = (8..10).flatMap { fullDay(it) }
        // The morning dose's time has passed with no mark.
        val missed = journey(three, at(11, 12))
        assertEquals(0, missed.streak)
        assertEquals(3, missed.best)
        assertEquals(DayState.MISSED, missed.today.state)
        assertTrue(missed.missed)
        assertEquals(Mood.SAD, Journeys.mood(missed, 12))
        // The next full day starts a new row of one.
        val again = journey(three + fullDay(12), at(12, 21))
        assertEquals(1, again.streak)
        assertEquals(3, again.best)
    }

    @Test fun theWeekIsTheLastSevenDaysOldestFirst() {
        val j = journey(fullDay(9) + mark(10, 8) + mark(11, 8), at(11, 12))
        assertEquals(7, j.week.size)
        assertEquals(LocalDate.of(2026, 10, 11), j.week.last().date)
        assertEquals(listOf(DayState.NONE, DayState.NONE, DayState.NONE, DayState.MISSED, DayState.FULL, DayState.MISSED, DayState.PART), j.week.map { it.state })
        assertEquals(1, j.today.marked)
        assertEquals(2, j.today.all)
    }

    @Test fun todaysGoalsAndTheBadges() {
        val fresh = journey(emptyList(), at(11, 7))
        assertTrue(fresh.badges.isEmpty())
        assertTrue(fresh.quests.none { it.complete })
        assertEquals(listOf(QuestKind.ALL_TODAY, QuestKind.SHOW_STRIP, QuestKind.GIFT), fresh.quests.map { it.kind })
        assertFalse(fresh.onTrack)
        // A button press is a first dose, and is not the phone seeing a strip.
        val pressed = journey(listOf(mark(11, 8, EventSource.MARK_BUTTON)), at(11, 9))
        assertEquals(setOf(Badge.FIRST_DOSE), pressed.badges)
        assertFalse(pressed.quests[1].complete)
        // Four days of heard tablets, today's code cracked, a game won against Pattu, five gifts and today's opened.
        val cracked = CodeRecord("2026-10-11", won = true, row = 1, bestRow = 1)
        val week = journey((8..11).flatMap { fullDay(it, EventSource.HEARD) }, at(11, 21), code = cracked, bonus = 60, beatPattu = 1, gifts = 5, giftToday = true)
        assertEquals(setOf(Badge.FIRST_DOSE, Badge.PHONE_SAW, Badge.PHONE_HEARD, Badge.THREE_DAYS, Badge.CODE_CRACKED, Badge.BEAT_PATTU, Badge.COLLECTOR), week.badges)
        assertTrue(week.quests.all { it.complete })
        assertTrue(week.onTrack)
        // The points of the games are added to the points of the doses.
        assertEquals(60 + 8 * Journeys.HEARD + 4 * Journeys.FULL_DAY, week.points)
        // The gift opens once the day is on track: the dose that came up is marked and nothing is waiting.
        assertTrue(journey(listOf(mark(11, 8)), at(11, 12)).onTrack)
        assertFalse(journey(emptyList(), at(11, 8, 10)).onTrack)   // the morning dose is due and not marked
        assertFalse(journey(emptyList(), at(11, 12)).onTrack)      // it was missed
        assertTrue(journey((5..11).flatMap { fullDay(it) }, at(11, 21)).badges.contains(Badge.SEVEN_DAYS).not()) // the phone knew the medicine from the 8th
        val known = Journeys.of(listOf(sugar), (5..11).flatMap { fullDay(it) }, at(11, 21), engine, mapOf(sugar.id to at(5, 0)))
        assertTrue(Badge.SEVEN_DAYS in known.badges)
        assertEquals(7, known.streak)
    }

    @Test fun pattuHasAMoodAndSomethingToSayInBothLanguages() {
        val going = journey((8..10).flatMap { fullDay(it) } + mark(11, 8), at(11, 12))
        assertEquals(Mood.HAPPY, Journeys.mood(going, 12))
        assertEquals(Mood.SLEEPY, Journeys.mood(going, 23))
        for (mood in Mood.entries) for (lang in listOf("en", "hi")) for (turn in listOf(-1, 0, 1, 2, 7)) {
            val line = PlayText.pattu(mood, going.streak, turn, lang)
            assertTrue(line.isNotBlank())
            // It never scolds and never says what to take.
            assertFalse(line, line.contains("must", true) || line.contains("should", true) || line.contains("take ", true))
        }
        assertNotEquals(PlayText.pattu(Mood.HAPPY, 4, 0, "en"), PlayText.pattu(Mood.HAPPY, 4, 1, "en"))
        assertTrue(PlayText.pattu(Mood.HAPPY, 4, 0, "en").contains("4 days in a row"))
    }

    @Test fun whatTheJourneySaysAboutItself() {
        val going = journey((8..10).flatMap { fullDay(it) } + mark(11, 8), at(11, 12))
        assertEquals("Finish today to keep it", PlayText.streak(going, "en"))
        assertEquals("days in a row", PlayText.streak(journey((8..10).flatMap { fullDay(it) } + fullDay(11), at(11, 21)), "en"))
        assertEquals("Start your row today", PlayText.streak(journey(emptyList(), at(11, 7)), "en"))
        assertEquals("Level ${going.level.number} · ${going.level.en}", PlayText.level(going, "en"))
        assertTrue(PlayText.toNext(going, "en").endsWith("points to " + Journeys.level(going.level.next!!).en))
        assertEquals("The highest level", PlayText.toNext(going.copy(points = 5000, level = Journeys.level(5000)), "en"))
        assertEquals("Today: 1 of 2 doses", PlayText.today(going, "en"))
        assertEquals("+20 points", PlayText.gained(20, "en"))
        assertEquals("Today is complete. 4 days in a row.", PlayText.dayDone(4, "en"))
        for (lang in listOf("en", "hi")) {
            assertEquals(QuestKind.entries.size, QuestKind.entries.map { PlayText.quest(it, lang) }.toSet().size)
            assertEquals(Badge.entries.size, Badge.entries.map { PlayText.badge(it, lang) }.toSet().size)
            assertTrue(PlayText.rule(lang).contains("30") && PlayText.level(going, lang).isNotBlank() && PlayText.toNext(going, lang).isNotBlank())
        }
    }
}
