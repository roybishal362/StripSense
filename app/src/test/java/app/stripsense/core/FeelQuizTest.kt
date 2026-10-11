package app.stripsense.core

import app.stripsense.core.guide.FeelLanguage
import app.stripsense.core.guide.Toward
import app.stripsense.core.play.FeelItem
import app.stripsense.core.play.FeelQuiz
import app.stripsense.core.say.PlayText
import app.stripsense.core.teach.PurposeKind
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** "Feel and tell": the game that teaches the language of touch. */
class FeelQuizTest {
    @Test fun theQuestionsGetHarderAsTheRowGrows() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4), listOf(0, 1, 2, 4, 5, 8, 9).map(FeelQuiz::level))
        val random = Random(3)
        assertEquals(setOf(FeelItem.YES, FeelItem.NO), FeelQuiz.round(0, random).choices.toSet())
        assertEquals(setOf(FeelItem.BP, FeelItem.SUGAR, FeelItem.FEVER), FeelQuiz.round(3, random).choices.toSet())
        assertEquals(setOf(FeelItem.LEFT, FeelItem.RIGHT, FeelItem.FORWARD, FeelItem.BACK), FeelQuiz.round(6, random).choices.toSet())
    }

    @Test fun theAnswerIsAlwaysOneOfTheChoicesAndIsNotTheSameTwiceRunning() {
        val random = Random(7)
        var last: FeelItem? = null
        val asked = HashSet<FeelItem>()
        repeat(400) { i ->
            val round = FeelQuiz.round(i % 14, random, last)
            assertTrue(round.answer in round.choices)
            if (round.choices.size > 2) assertNotEquals(last, round.answer)
            asked += round.answer
            last = round.answer
        }
        // Over a long game every pattern is asked.
        assertEquals(FeelItem.entries.toSet(), asked)
    }

    @Test fun noTwoPatternsFeelTheSameEvenToAHandThatCannotTellTheEndsApart() {
        val felt = FeelItem.entries.map { FeelQuiz.felt(it).merged()!! }
        assertEquals(FeelItem.entries.size, felt.toSet().size)
    }

    @Test fun itIsTheSameLanguageTheScanSpeaks() {
        assertEquals(FeelLanguage.yes(), FeelQuiz.felt(FeelItem.YES))
        assertEquals(FeelLanguage.no(), FeelQuiz.felt(FeelItem.NO))
        assertEquals(FeelLanguage.signature(PurposeKind.BP), FeelQuiz.felt(FeelItem.BP))
        assertEquals(FeelLanguage.signature(PurposeKind.SUGAR), FeelQuiz.felt(FeelItem.SUGAR))
        assertEquals(FeelLanguage.signature(PurposeKind.FEVER_PAIN), FeelQuiz.felt(FeelItem.FEVER))
        assertEquals(FeelLanguage.pull(Toward.LEFT, 1f), FeelQuiz.felt(FeelItem.LEFT))
        assertEquals(FeelLanguage.pull(Toward.RIGHT, 1f), FeelQuiz.felt(FeelItem.RIGHT))
        assertEquals(FeelLanguage.pull(Toward.AWAY, 1f), FeelQuiz.felt(FeelItem.FORWARD))
        assertEquals(FeelLanguage.pull(Toward.CLOSER, 1f), FeelQuiz.felt(FeelItem.BACK))
    }

    @Test fun whatIsSaidAfterAnAnswer() {
        assertEquals("Right.", PlayText.right(1, "en"))
        assertEquals("Right. 4 in a row.", PlayText.right(4, "en"))
        assertEquals("That was: BP medicine. Feel it again.", PlayText.wrong(FeelItem.BP, "en"))
        assertTrue(PlayText.wrong(FeelItem.BP, "hi").contains(FeelItem.BP.hi))
        assertEquals(4, (1..4).map { PlayText.ask(it, "en") }.toSet().size)
        for (item in FeelItem.entries) assertTrue(item.en.isNotBlank() && item.hi.isNotBlank() && item.name("hi") == item.hi)
    }
}
