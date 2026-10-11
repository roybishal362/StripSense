package app.stripsense.core.play

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** What kind of day it is. WAYS: the four ways to move. MEDICINES: the rhythms. MIXED: ways, yes and no. BACKWARDS: said back the other way round. */
enum class CodeTwist { WAYS, MEDICINES, MIXED, BACKWARDS }

/**
 * Today's code: a row of signals Pattu plays into the hand, to be said back. There is one for each day and it is
 * the same for everyone on that day, so two people can ask each other "did you get today's?". It is made from the
 * date alone, so it needs no network. [number] counts the days since the first code.
 */
data class DailyCode(val day: LocalDate, val number: Int, val twist: CodeTwist, val pads: List<FeelItem>, val signals: List<FeelItem>) {
    /** What must be pressed after [shown] signals have been played: the same order, or the other way round on a backwards day. */
    fun expected(shown: Int): List<FeelItem> = signals.take(shown).let { if (twist == CodeTwist.BACKWARDS) it.reversed() else it }
}

object DailyCodes {
    /** The day of code number 1. */
    val FIRST: LocalDate = LocalDate.of(2026, 10, 9)
    const val START = 3

    fun twist(day: LocalDate): CodeTwist = when (day.dayOfWeek) {
        DayOfWeek.SUNDAY, DayOfWeek.THURSDAY -> CodeTwist.WAYS
        DayOfWeek.MONDAY, DayOfWeek.FRIDAY -> CodeTwist.MEDICINES
        DayOfWeek.TUESDAY, DayOfWeek.SATURDAY -> CodeTwist.MIXED
        else -> CodeTwist.BACKWARDS
    }

    /** [level]: the person's level from 1. The code is one signal longer for every two levels, from five to eight. */
    fun of(day: LocalDate, level: Int): DailyCode {
        val twist = twist(day)
        val pads = when (twist) {
            CodeTwist.WAYS, CodeTwist.BACKWARDS -> listOf(FeelItem.LEFT, FeelItem.RIGHT, FeelItem.FORWARD, FeelItem.BACK)
            CodeTwist.MEDICINES -> listOf(FeelItem.BP, FeelItem.SUGAR, FeelItem.FEVER, FeelItem.YES)
            CodeTwist.MIXED -> listOf(FeelItem.LEFT, FeelItem.RIGHT, FeelItem.YES, FeelItem.NO)
        }
        // The same signals for everyone that day, whatever their level: a higher level only goes further along them.
        val random = Random(day.toEpochDay() * 7919 + 17)
        val all = ArrayList<FeelItem>()
        while (all.size < MAX) {
            val next = pads[random.nextInt(pads.size)]
            // Never three of the same in a row: that is a held note, not a code.
            if (all.size >= 2 && all[all.size - 1] == next && all[all.size - 2] == next) continue
            all += next
        }
        val length = (MIN + (level.coerceAtLeast(1) - 1) / 2).coerceIn(MIN, MAX)
        return DailyCode(day, ChronoUnit.DAYS.between(FIRST, day).toInt() + 1, twist, pads, all.take(length))
    }

    const val MIN = 5
    const val MAX = 8
}

enum class CodeState { WATCH, ENTER, WON, LOST }

/**
 * One try at today's code. It is played like this: Pattu plays the first [shown] signals (WATCH), the person says
 * them back (ENTER), and each time they are right one more signal is added, until the whole code has been said back.
 * A wrong press costs a heart and the same signals are played again. With no heart left the try is over.
 */
data class CodeGame(val code: DailyCode, val shown: Int, val entered: List<FeelItem>, val hearts: Int, val state: CodeState, val blind: Boolean) {
    /** How many signals have been said back rightly so far, for the dots on the screen. */
    val solved: Int get() = if (state == CodeState.WON) code.signals.size else shown - 1

    /** Points for the journey. Cracked: more for each heart kept, and double with eyes closed. Not cracked: a little for each step reached. */
    val points: Int get() = when (state) {
        CodeState.WON -> (CodeGames.WIN + CodeGames.HEART * hearts) * (if (blind) 2 else 1)
        CodeState.LOST -> CodeGames.STEP * (shown - DailyCodes.START).coerceAtLeast(0)
        else -> 0
    }
}

object CodeGames {
    const val HEARTS = 3
    const val WIN = 30
    const val HEART = 10
    const val STEP = 5

    /** [blind]: the pads do not light up while Pattu plays, so the code is only felt. */
    fun start(code: DailyCode, blind: Boolean = false) = CodeGame(code, minOf(DailyCodes.START, code.signals.size), emptyList(), HEARTS, CodeState.WATCH, blind)

    /** Pattu has finished playing: it is the person's turn. */
    fun watched(game: CodeGame): CodeGame = if (game.state == CodeState.WATCH) game.copy(state = CodeState.ENTER, entered = emptyList()) else game

    fun press(game: CodeGame, item: FeelItem): CodeGame {
        if (game.state != CodeState.ENTER) return game
        val want = game.code.expected(game.shown)
        val now = game.entered + item
        return when {
            item != want[game.entered.size] -> if (game.hearts <= 1) game.copy(hearts = 0, entered = now, state = CodeState.LOST)
                else game.copy(hearts = game.hearts - 1, entered = emptyList(), state = CodeState.WATCH)
            now.size < want.size -> game.copy(entered = now)
            game.shown >= game.code.signals.size -> game.copy(entered = now, state = CodeState.WON)
            else -> game.copy(shown = game.shown + 1, entered = emptyList(), state = CodeState.WATCH)
        }
    }
}

/**
 * What the phone keeps about the daily code: the last day a try was finished, whether it was cracked, and the row of
 * days in a row it was cracked. A day counts once; playing again the same day is practice.
 */
fun app.stripsense.core.domain.StoreData.codeRecord() = CodeRecord(codeDay, codeWon, codeRow, codeBestRow)

data class CodeRecord(val day: String = "", val won: Boolean = false, val row: Int = 0, val bestRow: Int = 0) {
    fun doneOn(today: LocalDate): Boolean = day == today.toString()
    fun wonOn(today: LocalDate): Boolean = doneOn(today) && won

    /** The row as it stands today: it is over when yesterday's code was not cracked. */
    fun rowOn(today: LocalDate): Int = if (won && (day == today.toString() || day == today.minusDays(1).toString())) row else 0

    /** A finished try. Null when today was already counted. */
    fun finished(today: LocalDate, cracked: Boolean): CodeRecord? {
        if (doneOn(today)) return null
        val row = if (!cracked) 0 else if (won && day == today.minusDays(1).toString()) row + 1 else 1
        return CodeRecord(today.toString(), cracked, row, maxOf(bestRow, row))
    }
}
