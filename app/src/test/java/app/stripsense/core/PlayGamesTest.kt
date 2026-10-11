package app.stripsense.core

import app.stripsense.core.play.*
import app.stripsense.core.say.PlayText
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** Tic-tac-toe with Pattu, the day's gift, and today's chit chat. */
class PlayGamesTest {
    private val today = LocalDate.of(2026, 10, 11)

    @Test fun threeInARowWinsAndTurnsAlternate() {
        var board = Board()
        for (square in listOf(0, 3, 1, 4, 2)) board = board.play(square)
        assertEquals(Mark.YOU, board.winner)
        assertEquals(listOf(0, 1, 2), board.line)
        assertTrue(board.over)
        // A taken square, a square off the board and a move after the end change nothing.
        assertEquals(board, board.play(5))
        val one = Board().play(4)
        assertEquals(Mark.PATTU, one.turn)
        assertEquals(one, one.play(4))
        assertEquals(one, one.play(9))
        for (line in Board.LINES) assertEquals(Mark.PATTU, Board(List(9) { if (it in line) Mark.PATTU else Mark.NONE }).winner)
        // A full board with no line is a draw.
        val draw = Board(listOf(Mark.YOU, Mark.PATTU, Mark.YOU, Mark.YOU, Mark.PATTU, Mark.PATTU, Mark.PATTU, Mark.YOU, Mark.YOU))
        assertTrue(draw.over && draw.winner == Mark.NONE && draw.line == null)
    }

    /** One whole game: the person plays anywhere at random, Pattu plays at [level]. */
    private fun game(level: Int, random: Random, pattuFirst: Boolean): Mark {
        var board = Board(turn = if (pattuFirst) Mark.PATTU else Mark.YOU)
        while (!board.over) {
            val square = if (board.turn == Mark.PATTU) PattuPlays.move(board, level, random)!! else board.free.random(random)
            assertTrue(square in board.free)
            board = board.play(square)
        }
        return board.winner
    }

    @Test fun pattuCanBeBeatenAtFirstAndNeverFromLevelFive() {
        val random = Random(11)
        val beginner = (0 until 300).map { game(1, random, it % 2 == 0) }
        assertTrue(beginner.count { it == Mark.YOU } > 20)
        val grown = (0 until 300).map { game(5, random, it % 2 == 0) }
        assertEquals(0, grown.count { it == Mark.YOU })
        // In between it always takes a win and always blocks one.
        val about = Board(listOf(Mark.PATTU, Mark.PATTU, Mark.NONE, Mark.YOU, Mark.YOU, Mark.NONE, Mark.NONE, Mark.NONE, Mark.NONE), Mark.PATTU)
        assertEquals(2, PattuPlays.move(about, 3, random))
        val block = Board(listOf(Mark.YOU, Mark.YOU, Mark.NONE, Mark.PATTU, Mark.NONE, Mark.NONE, Mark.NONE, Mark.NONE, Mark.NONE), Mark.PATTU)
        assertEquals(2, PattuPlays.move(block, 3, random))
        // It does not move out of turn or after the end.
        assertNull(PattuPlays.move(Board(), 5, random))
        assertNull(PattuPlays.move(Board().play(0).play(3).play(1).play(4).play(2), 5, random))
    }

    @Test fun everyGiftIsItsOwnAndHasItsWordsInBothLanguages() {
        assertEquals(Gifts.ALL.size, Gifts.ALL.map { it.id }.toSet().size)
        assertEquals(Gifts.ALL.size, Gifts.ALL.map { it.title }.toSet().size)
        for (kind in GiftKind.entries) assertTrue(kind.name, Gifts.ALL.count { it.kind == kind } >= 6)
        for (gift in Gifts.ALL) {
            assertTrue(gift.title.isNotBlank() && gift.whyEn.isNotBlank() && gift.whyHi.isNotBlank())
            assertTrue(gift.id, gift.whyHi.none { it in 'a'..'z' || it in 'A'..'Z' })
            // A thought has nobody's name under it; everything else says who made it.
            assertEquals(gift.kind == GiftKind.THOUGHT, gift.by.isEmpty())
        }
    }

    @Test fun theSameGiftIsNotGivenTwiceUntilAllOfItsKindHaveBeenGiven() {
        val movies = Gifts.ALL.count { it.kind == GiftKind.MOVIE }
        val kept = ArrayList<String>()
        for (day in 0L until movies) kept += Gifts.entry(today.plusDays(day), Gifts.pick(GiftKind.MOVIE, kept, today.plusDays(day)))
        assertEquals(movies, Gifts.given(kept).map { it.gift.id }.toSet().size)
        assertTrue(Gifts.given(kept).all { it.gift.kind == GiftKind.MOVIE })
        // All given: the one given longest ago comes round again.
        assertEquals(Gifts.given(kept).first().gift, Gifts.pick(GiftKind.MOVIE, kept, today.plusDays(30)))
        // Another kind is not touched by that.
        assertEquals(GiftKind.BOOK, Gifts.pick(GiftKind.BOOK, kept, today).kind)
        assertEquals(Gifts.given(kept).first(), Gifts.givenOn(kept, today))
        assertNull(Gifts.givenOn(kept, today.minusDays(1)))
        // An entry the app no longer knows is left out and breaks nothing.
        assertEquals(movies, Gifts.given(kept + "2026-10-01|gone").size)
    }

    @Test fun pattuAsksAboutAGiftOnALaterDayOnceAndRemembersTheAnswer() {
        val movie = Gifts.ALL.first { it.kind == GiftKind.MOVIE }
        val thought = Gifts.ALL.first { it.kind == GiftKind.THOUGHT }
        val kept = listOf(Gifts.entry(today, movie))
        // The same day it only asks how the day is going. Once that is answered there is nothing more today.
        assertEquals(ChatTopic.AboutDay, Chats.topic(kept, emptyList(), today, dayAnswered = false))
        assertNull(Chats.topic(kept, emptyList(), today, dayAnswered = true))
        // The next day it asks about the film.
        val tomorrow = today.plusDays(1)
        assertEquals(ChatTopic.AboutGift(Given(today.toString(), movie)), Chats.topic(kept, emptyList(), tomorrow, dayAnswered = false))
        assertTrue(PlayText.chatAsk(Chats.topic(kept, emptyList(), tomorrow, false)!!, "en").contains(movie.title))
        // Loved: it does not ask again, and that kind is the favourite.
        val loved = listOf(Chats.entry(movie, Liked.LOVED))
        assertEquals(ChatTopic.AboutDay, Chats.topic(kept, loved, tomorrow, dayAnswered = false))
        assertEquals(GiftKind.MOVIE, Chats.favourite(kept, loved))
        // Not yet: not again the same day, but again another day.
        val notYet = listOf(Chats.entry(movie, Liked.NOT_YET))
        assertEquals(ChatTopic.AboutDay, Chats.topic(kept, notYet, tomorrow, dayAnswered = false, askedToday = true))
        assertEquals(ChatTopic.AboutGift(Given(today.toString(), movie)), Chats.topic(kept, notYet, tomorrow.plusDays(1), dayAnswered = false))
        assertNull(Chats.favourite(kept, notYet))
        // A thought is not asked about.
        assertEquals(ChatTopic.AboutDay, Chats.topic(listOf(Gifts.entry(today, thought)), emptyList(), tomorrow, dayAnswered = false))
        // An answer that cannot be read is ignored.
        assertTrue(Chats.answers(listOf("m1|WHAT", "nonsense")).isEmpty())
    }

    @Test fun whatPattuSaysBackInBothLanguages() {
        for (lang in listOf("en", "hi")) {
            assertEquals(3, Liked.entries.map { PlayText.chatReply(it, lang) }.toSet().size)
            assertEquals(3, DayFeel.entries.map { PlayText.dayReply(it, lang) }.toSet().size)
            assertEquals(3, Liked.entries.map { PlayText.liked(it, lang) }.toSet().size)
            assertEquals(3, DayFeel.entries.map { PlayText.dayFeel(it, lang) }.toSet().size)
            assertEquals(3, Mark.entries.map { PlayText.tttEnd(it, 0, lang) }.toSet().size)
            assertTrue(PlayText.tttEnd(Mark.YOU, 15, lang).contains("15"))
            assertTrue(PlayText.chatNotMine(lang).isNotBlank() && PlayText.giftChoose(lang).isNotBlank() && PlayText.collection(4, lang).contains("4"))
        }
        val book = Gifts.ALL.first { it.kind == GiftKind.BOOK }
        assertTrue(PlayText.chatAsk(ChatTopic.AboutGift(Given("2026-10-10", book)), "en").contains("reading"))
    }
}
