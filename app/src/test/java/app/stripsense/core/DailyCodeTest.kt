package app.stripsense.core

import app.stripsense.core.play.*
import app.stripsense.core.say.PlayText
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** The daily code: one for each day, the same for everyone, felt in the hand and said back. */
class DailyCodeTest {
    private val sunday = LocalDate.of(2026, 10, 11)

    @Test fun thereIsOneCodeForEachDayAndItIsTheSameForEveryone() {
        assertEquals(DailyCodes.of(sunday, 1), DailyCodes.of(sunday, 1))
        assertEquals(3, DailyCodes.of(sunday, 1).number)
        assertEquals(1, DailyCodes.of(DailyCodes.FIRST, 1).number)
        // A higher level goes further along the same signals; it is not given a different code.
        val short = DailyCodes.of(sunday, 1).signals
        val long = DailyCodes.of(sunday, 7).signals
        assertEquals(short, long.take(short.size))
        // Tomorrow's is another one.
        val fortnight = (0L until 14).map { DailyCodes.of(sunday.plusDays(it), 1).signals }
        assertTrue(fortnight.toSet().size >= 12)
    }

    @Test fun itGrowsWithTheLevelFromFiveSignalsToEight() {
        assertEquals(listOf(5, 5, 6, 6, 7, 7, 8, 8, 8), listOf(1, 2, 3, 4, 5, 6, 7, 8, 30).map { DailyCodes.of(sunday, it).signals.size })
        assertEquals(5, DailyCodes.of(sunday, 0).signals.size)
    }

    @Test fun everyDayOfTheWeekHasItsTwistAndItsOwnPads() {
        assertEquals(CodeTwist.WAYS, DailyCodes.twist(sunday))
        assertEquals(listOf(CodeTwist.WAYS, CodeTwist.MEDICINES, CodeTwist.MIXED, CodeTwist.BACKWARDS, CodeTwist.WAYS, CodeTwist.MEDICINES, CodeTwist.MIXED),
            (0L until 7).map { DailyCodes.twist(sunday.plusDays(it)) })
        for (offset in 0L until 60) {
            val code = DailyCodes.of(sunday.plusDays(offset), 7)
            assertEquals(4, code.pads.toSet().size)
            assertTrue(code.signals.all { it in code.pads })
            // Never three of the same in a row.
            assertTrue(code.signals.windowed(3).none { it.toSet().size == 1 })
            // Every pad feels different from the others.
            assertEquals(4, code.pads.map { FeelQuiz.felt(it).merged() }.toSet().size)
        }
        assertEquals(listOf(FeelItem.LEFT, FeelItem.RIGHT, FeelItem.FORWARD, FeelItem.BACK), DailyCodes.of(sunday, 1).pads)
    }

    private fun sayBack(game: CodeGame): CodeGame {
        var now = CodeGames.watched(game)
        for (item in now.code.expected(now.shown)) now = CodeGames.press(now, item)
        return now
    }

    @Test fun saidBackRightItGrowsByOneUntilTheWholeCodeIsCracked() {
        val code = DailyCodes.of(sunday, 1)
        var game = CodeGames.start(code)
        assertEquals(CodeState.WATCH, game.state)
        assertEquals(3, game.shown)
        // A press before Pattu has finished is not an answer.
        assertEquals(game, CodeGames.press(game, code.signals.first()))
        game = sayBack(game)
        assertEquals(4, game.shown)
        assertEquals(CodeState.WATCH, game.state)
        game = sayBack(game)
        assertEquals(5, game.shown)
        game = sayBack(game)
        assertEquals(CodeState.WON, game.state)
        assertEquals(CodeGames.HEARTS, game.hearts)
        assertEquals(CodeGames.WIN + 3 * CodeGames.HEART, game.points)
        // With eyes closed it is worth double.
        var blind = CodeGames.start(code, blind = true)
        repeat(3) { blind = sayBack(blind) }
        assertEquals(2 * game.points, blind.points)
        assertTrue(PlayText.codeShare(blind, "en").contains("eyes closed") && PlayText.codeShare(blind, "en").contains("#3"))
        assertFalse(PlayText.codeShare(game, "en").contains("eyes closed"))
    }

    @Test fun aWrongPressCostsAHeartAndTheSameSignalsArePlayedAgain() {
        val code = DailyCodes.of(sunday, 1)
        val wrong = code.pads.first { it != code.signals.first() }
        var game = CodeGames.press(CodeGames.watched(CodeGames.start(code)), wrong)
        assertEquals(2, game.hearts)
        assertEquals(3, game.shown)
        assertEquals(CodeState.WATCH, game.state)
        assertTrue(game.entered.isEmpty())
        // Right this time: on to four, and a heart is not given back.
        game = sayBack(game)
        assertEquals(4, game.shown)
        assertEquals(2, game.hearts)
        // Two more wrong presses: no heart left, and the try is over with a little for the step reached.
        game = CodeGames.press(CodeGames.watched(game), code.pads.first { it != code.expected(4).first() })
        game = CodeGames.press(CodeGames.watched(game), code.pads.first { it != code.expected(4).first() })
        assertEquals(CodeState.LOST, game.state)
        assertEquals(0, game.hearts)
        assertEquals(CodeGames.STEP, game.points)
        assertEquals(game, CodeGames.press(game, code.signals.first()))
    }

    @Test fun onABackwardsDayItIsSaidBackTheOtherWayRound() {
        val wednesday = sunday.plusDays(3)
        val code = DailyCodes.of(wednesday, 1)
        assertEquals(CodeTwist.BACKWARDS, code.twist)
        assertEquals(code.signals.take(3).reversed(), code.expected(3))
        var game = CodeGames.start(code)
        repeat(3) { game = sayBack(game) }
        assertEquals(CodeState.WON, game.state)
        assertNotEquals(PlayText.codeHow(CodeTwist.WAYS, "en"), PlayText.codeHow(CodeTwist.BACKWARDS, "en"))
    }

    @Test fun theRowOfDaysTheCodeWasCrackedIsKeptAndADayCountsOnce() {
        val first = CodeRecord().finished(sunday, cracked = true)!!
        assertEquals(CodeRecord("2026-10-11", true, 1, 1), first)
        assertTrue(first.wonOn(sunday))
        // The same day again is practice.
        assertNull(first.finished(sunday, cracked = true))
        val second = first.finished(sunday.plusDays(1), cracked = true)!!
        assertEquals(2, second.row)
        assertEquals(2, second.rowOn(sunday.plusDays(1)))
        // Still standing the next morning, gone the morning after.
        assertEquals(2, second.rowOn(sunday.plusDays(2)))
        assertEquals(0, second.rowOn(sunday.plusDays(3)))
        // A day skipped starts again at one. A day not cracked is nothing, and the best is kept.
        assertEquals(1, second.finished(sunday.plusDays(3), cracked = true)!!.row)
        val lost = second.finished(sunday.plusDays(2), cracked = false)!!
        assertEquals(0, lost.row)
        assertEquals(2, lost.bestRow)
        assertFalse(lost.wonOn(sunday.plusDays(2)))
        assertTrue(lost.doneOn(sunday.plusDays(2)))
    }

    @Test fun whatIsSaidAboutTheCodeInBothLanguages() {
        val code = DailyCodes.of(sunday, 1)
        for (lang in listOf("en", "hi")) {
            assertTrue(PlayText.codeTitle(code, lang).contains("#3"))
            assertEquals(4, CodeTwist.entries.map { PlayText.twistName(it, lang) }.toSet().size)
            assertTrue(PlayText.codeTomorrow(CodeTwist.MEDICINES, lang).contains(PlayText.twistName(CodeTwist.MEDICINES, lang)))
            assertEquals(3, (0..2).map { PlayText.codeRow(it, lang) }.toSet().size)
            assertTrue(PlayText.codeStep(3, 5, lang).contains("3") && PlayText.codeStep(3, 5, lang).contains("5"))
        }
    }
}
