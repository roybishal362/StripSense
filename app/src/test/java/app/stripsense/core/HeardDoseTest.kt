package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import app.stripsense.core.report.*
import app.stripsense.core.say.*
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

/** A dose marked because the phone heard the tablet come out, and how that is told to the person and the family. */
class HeardDoseTest {
    private val engine = DoseEngine()
    private val telma = Medicine("t", "Telma 40", "", "TELMA", listOf("TELMISARTAN"), listOf(Strength("40", StrengthUnit.MG)), times = listOf("20:00"), purpose = "BP")
    private val now = ZonedDateTime.of(2026, 10, 10, 20, 2, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val key = engine.slotsFor(telma, engine.local(now).toLocalDate()).single().key
    private val maa = Person(callEn = "Maa", callHi = "माँ", isWoman = true)

    @Test fun theSoundAnswersTheOpenQuestionAndThePhoneSaysWhatItHeard() {
        val result = engine.answerPrompt(telma, key, PromptAnswer.HEARD, now, emptyList(), "e1")
        assertEquals(EventSource.HEARD, result.event!!.source)
        assertEquals(EventKind.TAKEN, result.event!!.kind)
        assertTrue(result.showUndo)
        assertEquals("H1", result.parts.single().id)
        val said = Sentences.render(result.parts, "en", maa).single().shown
        assertEquals("I heard the tablet come out. I've marked your BP medicine, Telma 40 at 8:02 pm.", said)
        // The word is "marked". Hearing a tablet come out is not knowing that it was swallowed.
        assertFalse(said.contains("took", ignoreCase = true) || said.contains("taken", ignoreCase = true))
        assertTrue(Sentences.render(result.parts, "hi", maa).single().shown.startsWith("गोली निकलने की आवाज़ सुनाई दी।"))
    }

    @Test fun itMarksNothingWhenTheQuestionIsNotOpen() {
        // Already marked for this time: the sound cannot mark it a second time.
        val marked = listOf(DoseEvent("e0", telma.id, key, now - 60_000, EventKind.TAKEN, EventSource.PROMPT_TAP))
        assertNull(engine.answerPrompt(telma, key, PromptAnswer.HEARD, now, marked, "e1").event)
        // Three hours early: no question is open, so nothing is marked.
        assertNull(engine.answerPrompt(telma, key, PromptAnswer.HEARD, now - 3 * 60 * 60_000, emptyList(), "e1").event)
    }

    @Test fun aHeardMarkCanBeTakenOffLikeAnyOther() {
        val heard = engine.answerPrompt(telma, key, PromptAnswer.HEARD, now, emptyList(), "e1").event!!
        val undone = engine.undo(heard, now + 20_000, listOf(heard), "e2")
        assertEquals(EventKind.UNDONE, undone.event!!.kind)
        assertEquals(SlotState.UNMARKED, engine.slotState(telma.id, key, listOf(heard, undone.event!!)))
    }

    @Test fun theFamilyIsToldHowThePhoneKnows() {
        assertEquals(Proof.HEARD, Proofs.of(EventSource.HEARD))
        assertEquals(Proof.SEEN, Proofs.of(EventSource.PROMPT_TAP))
        assertEquals(Proof.PRESSED, Proofs.of(EventSource.REMINDER_TAP))
        assertEquals(Proof.PRESSED, Proofs.of(EventSource.MARK_BUTTON))
        val events = listOf(DoseEvent("e1", telma.id, key, now, EventKind.TAKEN, EventSource.HEARD))
        val file = WeekFile.build(listOf(telma), events, now + 60_000, maa, 0, engine)
        assertTrue(file, file.lines().any { it.endsWith(",marked,20:02,heard") })
        val dose = WeekViews.build(listOf(telma), events, now + 60_000, engine).days.last().second.single()
        assertEquals(DoseMark.MARKED, dose.mark)
        assertEquals(Proof.HEARD, dose.proof)
    }
}
