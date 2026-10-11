package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import app.stripsense.core.guide.Toward
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import org.junit.Assert.*
import org.junit.Test

/** Questions asked of the camera, answered from what it is reading. The model only names the request. */
class SceneQuestionTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private val bp = Medicine("t", "Telma 40", "", "TELMA", listOf("TELMISARTAN"), listOf(mg("40")), purpose = "BP")
    private val fever = Medicine("c", "Crocin", "", "CROCIN", listOf("PARACETAMOL"), listOf(mg("500")), purpose = "FEVER_PAIN")
    private val meds = listOf(bp, fever)
    private fun timing(due: Medicine? = null) = { med: Medicine -> MedicationTiming(if (med.id == due?.id) TimingKind.NOW else TimingKind.MARKED) }
    private fun ask(request: String, medicine: Int, scene: SceneNow, due: Medicine? = null, words: String = "a question") =
        SceneQuestion.answer(VoiceRequest(request, medicine, "none", words, false), meds, scene, timing(due))
    private fun text(answer: SceneAnswer) = Sentences.sceneAnswer(answer, "en")

    @Test fun isThisMyBpMedicineIsAnsweredYesOnlyWhenTheCameraReadsThatStrip() {
        val yes = ask("which_is_x", 1, SceneNow(bp, false, emptyList()), due = bp)
        assertEquals(SceneAnswer.Kind.YES, yes.kind)
        assertTrue(text(yes).startsWith("Yes. This is your BP medicine, Telma 40."))
        // The same question with the other strip in the frame, and the right one lying to the left.
        val no = ask("which_is_x", 1, SceneNow(fever, false, listOf(bp to Toward.LEFT)))
        assertEquals("No. This is your fever and pain medicine, Crocin. Your BP medicine, Telma 40 is on the left.", text(no))
        assertEquals(bp, no.lookFor)
        // The right one not in view at all.
        assertEquals("No. This is your fever and pain medicine, Crocin. I don't see your BP medicine, Telma 40 here.", text(ask("status_of", 1, SceneNow(fever, false, emptyList()))))
    }
    @Test fun whereIsItLeadsToItOrSaysItIsNotInView() {
        assertEquals("Your BP medicine, Telma 40 is on the right. Move the phone that way.", text(ask("which_is_x", 1, SceneNow(null, false, listOf(bp to Toward.RIGHT)))))
        val away = ask("which_is_x", 1, SceneNow(null, false, emptyList()))
        assertEquals(SceneAnswer.Kind.NOT_IN_VIEW, away.kind)
        assertEquals(bp, away.lookFor)
    }
    @Test fun whichOneIsThisNamesTheStripInTheFrameAndSaysWhenItsStrengthIsUnread() {
        val named = ask("which_one", 0, SceneNow(fever, true, emptyList()))
        assertTrue(text(named), text(named).startsWith("This is your fever and pain medicine, Crocin.") && text(named).endsWith("I have not read its strength yet."))
        assertEquals(SceneAnswer.Kind.NOTHING_IN_FRAME, ask("which_one", 0, SceneNow(null, false, listOf(bp to Toward.LEFT))).kind)
    }
    @Test fun whatDoITakeNowPointsAtTheDueStrip() {
        val due = ask("whats_left", 0, SceneNow(fever, false, listOf(bp to Toward.LEFT)), due = bp)
        assertEquals("Right now it is time for your BP medicine, Telma 40. It is on the left.", text(due))
        assertEquals("No medicine is due right now.", text(ask("whats_left", 0, SceneNow(fever, false, emptyList()))))
        // Asked in Roman Hindi, whatever request the model picked.
        assertEquals(SceneAnswer.Kind.DUE_NOW, ask("other", 0, SceneNow(null, false, emptyList()), due = bp, words = "abhi kaunsi dawai leni hai").kind)
    }
    @Test fun theModelCannotMakeThePhoneSayYes() {
        // The model names medicine 1; the camera reads nothing in the frame. The answer is never "yes".
        assertNotEquals(SceneAnswer.Kind.YES, ask("which_is_x", 1, SceneNow(null, false, emptyList())).kind)
        assertEquals(SceneAnswer.Kind.UNCLEAR, SceneQuestion.answer(null, meds, SceneNow(bp, false, emptyList()), timing()).kind)
        assertEquals(SceneAnswer.Kind.HELP, ask("which_one", 0, SceneNow(bp, false, emptyList()), words = "chest pain").kind)
        assertTrue(Sentences.sceneAnswer(ask("which_is_x", 1, SceneNow(bp, false, emptyList())), "hi").startsWith("हाँ। यह आपकी बीपी की दवा"))
    }
    @Test fun silenceIsNotAnsweredAsIfItWereAQuestion() {
        assertEquals(SceneAnswer.Kind.UNCLEAR, ask("which_is_x", 1, SceneNow(bp, false, emptyList()), words = " ").kind)
    }
}
