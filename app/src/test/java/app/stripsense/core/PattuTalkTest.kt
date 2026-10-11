package app.stripsense.core

import app.stripsense.core.play.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Pattu's free chit chat: what the model is told, what is never sent to it, and what may be shown of its reply. */
class PattuTalkTest {
    private val today = LocalDate.of(2026, 10, 11)

    @Test fun aQuestionAboutMedicineOrHealthIsNeverSentToTheModel() {
        for (asked in listOf("Should I take two tablets?", "what is the dose of my BP medicine", "I have a fever", "my head is in pain", "क्या मैं दो गोली ले लूँ?", "मुझे बुख़ार है", "दवा कब लेनी है"))
            assertTrue(asked, PattuTalk.aboutHealth(asked))
        for (asked in listOf("What should I watch tonight?", "I loved Piku", "Tell me a book for a long train ride", "आज कौन सी फ़िल्म देखूँ?", "How are you, Pattu?"))
            assertFalse(asked, PattuTalk.aboutHealth(asked))
    }

    @Test fun theBookOfKnowledgeSaysWhatWasGivenAndWhatThePersonSaid() {
        val piku = Gifts.ALL.first { it.title == "Piku" }
        val ikigai = Gifts.ALL.first { it.title == "Ikigai" }
        val kept = listOf(Gifts.entry(today.minusDays(2), piku), Gifts.entry(today.minusDays(1), ikigai))
        val book = PattuTalk.knowledge(kept, listOf(Chats.entry(piku, Liked.LOVED)), streak = 4, level = "Bloom", lang = "en")
        assertTrue(book.contains("\"Piku\"") && book.contains("They loved it."))
        assertTrue(book.contains("\"Ikigai\"") && book.contains("You have not asked them about it yet."))
        assertTrue(book.contains("4 full days in a row") && book.contains("Bloom"))
        // What was given is not offered again as something new, and a thought is not a thing to suggest.
        val others = book.substringAfter("Other things you know")
        assertFalse(others.contains("\"Piku\"") || others.contains("\"Ikigai\""))
        assertTrue(others.contains("\"Navillera\""))
        assertFalse(others.contains(Gifts.ALL.first { it.kind == GiftKind.THOUGHT }.title))
        // The rules it is given.
        assertTrue(book.contains("Never talk about medicines") && book.contains("at most two short sentences") && book.contains("Do not invent titles"))
        assertTrue(PattuTalk.knowledge(emptyList(), emptyList(), 0, "Seed", "hi").contains("Devanagari"))
        assertTrue(PattuTalk.knowledge(emptyList(), emptyList(), 0, "Seed", "en").contains("You have not given this person anything yet."))
    }

    @Test fun aReplyIsShownOnlyWhenItIsShortAndHasNothingAboutMedicineInIt() {
        assertEquals("Piku is a lovely one. Watch it with your father.", PattuTalk.clean("  **Piku** is a lovely one. Watch it with your father. It came out in 2015. And another thing."))
        assertEquals("Try Navillera!", PattuTalk.clean("\"Try Navillera!\""))
        assertNull(PattuTalk.clean("You should take your tablet after food."))
        assertNull(PattuTalk.clean("The dosage is 500 mg twice a day."))
        assertNull(PattuTalk.clean("आपको दवा समय पर लेनी चाहिए।"))
        assertNull(PattuTalk.clean("   "))
        // A title with a hospital or a doctor in it is small talk, not advice.
        assertEquals("Hospital Playlist is about five doctors who are friends.", PattuTalk.clean("Hospital Playlist is about five doctors who are friends."))
        assertTrue(PattuTalk.clean("a".repeat(500))!!.length <= 220)
        assertEquals("what should I watch", PattuTalk.message("  what   should I\nwatch  "))
        assertEquals(240, PattuTalk.message("x".repeat(900)).length)
    }

    @Test fun everyPattuOpensAtALevelAndTheChosenOneIsKeptOnlyWhenItIsOpen() {
        assertEquals(listOf(1, 2, 3, 4, 5), Skin.entries.map { it.level })
        assertEquals(Skin.SPROUT, Skins.of("", 7))
        assertEquals(Skin.KITTEN, Skins.of("KITTEN", 3))
        assertEquals(Skin.SPROUT, Skins.of("KITTEN", 2))
        assertEquals(Skin.SPROUT, Skins.of("LUFFY", 7))
        assertEquals(Skin.PUPPY, Skins.next(1))
        assertNull(Skins.next(5))
        for (skin in Skin.entries) assertTrue(skin.en.isNotBlank() && skin.hi.isNotBlank())
    }
}
