package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class DueQuestionTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private val telma = Medicine("t", "Telma 40", "", "TELMA", listOf("TELMISARTAN"), listOf(mg("40")), times = listOf("20:00"), purpose = "BP")
    private val crocin = Medicine("c", "Crocin", "", "CROCIN", listOf("PARACETAMOL"), listOf(mg("500")), times = listOf("08:00", "20:00"))
    private val p500 = Medicine("p", "P-500", "", "P 500", listOf("PARACETAMOL"), listOf(mg("500")), times = listOf("14:35"))
    private val meds = listOf(telma, crocin, p500)
    private val engine = DoseEngine()
    private fun at(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, 10, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun time(text: String) = DueQuestion.parse(text)?.time

    @Test fun theQuestionIsUnderstoodInEnglishHindiAndRomanHindi() {
        assertEquals(LocalTime.of(20, 0), time("Which medicine should I take at 8 in the evening?"))
        assertEquals(LocalTime.of(20, 0), time("which medicine at 8 pm"))
        assertEquals(LocalTime.of(8, 0), time("What do I take at 8 am?"))
        assertEquals(LocalTime.of(20, 0), time("raat ko kaunsi dawai leni hai"))
        assertEquals(LocalTime.of(8, 0), time("subah kaun si goli hai"))
        assertEquals(LocalTime.of(20, 0), time("रात 8 बजे कौन सी दवा लेनी है"))
        assertEquals(LocalTime.of(14, 30), time("which tablet at 2:30 in the afternoon"))
        assertEquals(DueQuestion.Asked(null, true), DueQuestion.parse("which one is next?"))
    }
    @Test fun otherQuestionsAreLeftAlone() {
        assertNull(DueQuestion.parse("Did I take my 8 pm tablet?"))
        assertNull(DueQuestion.parse("kya maine raat ki goli le li"))
        assertNull(DueQuestion.parse("What's left today?"))
        assertNull(DueQuestion.parse("Which one is my BP tablet?"))
        assertNull(DueQuestion.parse("hello"))
    }
    @Test fun theMedicinesSavedForThatTimeAreListedWithWhatIsMarked() {
        val marked = DoseEvent("e", crocin.id, engine.slotsFor(crocin, engine.local(at(17)).toLocalDate()).first { it.time == LocalTime.of(20, 0) }.key,
            at(17), EventKind.TAKEN, EventSource.PROMPT_TAP)
        val due = DueQuestion.answer(DueQuestion.parse("which medicine at 8 in the evening")!!, meds, at(18), listOf(marked), engine)
        assertEquals(LocalTime.of(20, 0), due.time)
        assertEquals(listOf(telma to false, crocin to true), due.items.map { it.med to it.marked })
        assertEquals("For 8:00 pm you have 2 medicines saved: your BP medicine, Telma 40; and your Crocin (already marked). Their pictures are below.",
            Sentences.dueAt(due, "en"))
        assertTrue(Sentences.dueAt(due, "hi").contains("आपकी बीपी की दवा, Telma 40"))
        assertFalse(Sentences.dueAt(due, "en").contains("take", ignoreCase = true))
    }
    @Test fun aTimeNearASavedOneFindsItAndAnEmptyTimeSaysSo() {
        assertEquals(LocalTime.of(14, 35), DueQuestion.answer(DueQuestion.Asked(LocalTime.of(14, 0), false), meds, at(9), emptyList(), engine).time)
        val none = DueQuestion.answer(DueQuestion.Asked(LocalTime.of(11, 0), false), meds, at(9), emptyList(), engine)
        assertTrue(none.items.isEmpty())
        assertEquals("No medicine is saved for that time.", Sentences.dueAt(none, "en"))
    }
    @Test fun theNextOneIsTheFirstSavedTimeStillAhead() {
        assertEquals(LocalTime.of(14, 35), DueQuestion.answer(DueQuestion.Asked(null, true), meds, at(9), emptyList(), engine).time)
        assertEquals(LocalTime.of(8, 0), DueQuestion.answer(DueQuestion.Asked(null, true), meds, at(22), emptyList(), engine).time)
    }
    @Test fun theRouterAnswersItWhateverRequestTheModelPicked() {
        for (picked in listOf("whats_left", "other", "which_one", "due_at")) {
            val request = VoiceRequest(picked, 0, "none", "Which medicine should I take at 8 in the evening?", false, "20:00")
            val action = VoiceRouter.route(request, meds, at(18), emptyList(), engine, false)
            assertTrue(picked, action is VoiceAction.ShowDue && action.due.items.size == 2)
        }
        // An emergency in the same sentence still wins.
        val urgent = VoiceRequest("due_at", 0, "none", "chest pain, which medicine at 8 pm", false, "20:00")
        assertTrue(VoiceRouter.route(urgent, meds, at(18), emptyList(), engine, false) is VoiceAction.Help)
    }
}
