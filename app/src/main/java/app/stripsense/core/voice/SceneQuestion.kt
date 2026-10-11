package app.stripsense.core.voice

import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import app.stripsense.core.guide.Toward

/**
 * What the camera has in front of it at the moment a question is asked.
 * [inFrame]: the strip held in the middle, if one is read. [nameOnly]: its strength has not been read.
 * [around]: the other saved strips in view and which way each lies.
 */
data class SceneNow(val inFrame: Medicine?, val nameOnly: Boolean, val around: List<Pair<Medicine, Toward>>)

/** The answer to a question asked of the camera. [lookFor]: a medicine the camera should now lead to. */
data class SceneAnswer(val kind: Kind, val asked: Medicine? = null, val shown: Medicine? = null, val toward: Toward? = null,
    val timing: MedicationTiming? = null, val nameOnly: Boolean = false, val lookFor: Medicine? = null) {
    enum class Kind { YES, NO_OTHER_SIDE, NO_NOT_HERE, WHERE, NOT_IN_VIEW, THIS_IS, NOTHING_IN_FRAME, DUE_NOW, NOTHING_DUE, HELP, UNCLEAR }
}

/**
 * Talking to the camera. The person holds up a strip and asks out loud, in Hindi or English: "Is this my BP
 * medicine?", "Which one is this?", "Where is my sugar medicine?", "What do I take now?". The on-phone model
 * turns the speech into one of the fixed requests, exactly as on the Ask screen. The answer is then worked out
 * here, from what the camera is reading at that moment: the model never sees the picture and never writes
 * the answer, so it cannot say "yes" to a strip the camera did not read.
 */
object SceneQuestion {
    fun answer(request: VoiceRequest?, meds: List<Medicine>, scene: SceneNow, timing: (Medicine) -> MedicationTiming): SceneAnswer {
        // Nothing was said, or nothing was heard: the phone says so. It does not answer a question nobody asked.
        if (request == null || request.transcript.isBlank()) return SceneAnswer(SceneAnswer.Kind.UNCLEAR)
        if (request.emergency || request.request in setOf("emergency", "self_harm") || Keywords.check(request.transcript) != Keywords.Hit.NONE)
            return SceneAnswer(SceneAnswer.Kind.HELP)
        val asked = meds.getOrNull(request.medicine - 1)
        val inFrame = scene.inFrame
        // A question about one named medicine: is it this one, and if not, where is it?
        if (asked != null) {
            if (inFrame?.id == asked.id) return SceneAnswer(SceneAnswer.Kind.YES, asked = asked, shown = asked, timing = timing(asked), nameOnly = scene.nameOnly)
            val beside = scene.around.firstOrNull { it.first.id == asked.id }
            return when {
                inFrame != null && beside != null -> SceneAnswer(SceneAnswer.Kind.NO_OTHER_SIDE, asked, inFrame, beside.second, lookFor = asked)
                inFrame != null -> SceneAnswer(SceneAnswer.Kind.NO_NOT_HERE, asked, inFrame, lookFor = asked)
                beside != null -> SceneAnswer(SceneAnswer.Kind.WHERE, asked, toward = beside.second, lookFor = asked)
                else -> SceneAnswer(SceneAnswer.Kind.NOT_IN_VIEW, asked, lookFor = asked)
            }
        }
        // "What do I take now?", "which is next?": the dose that is due, and where its strip is if it is in view.
        if (request.request in setOf("due_at", "whats_left") || DueQuestion.parse(request.transcript) != null) {
            val due = meds.filter { it.status == MedStatus.ACTIVE && timing(it).kind == TimingKind.NOW }
            val first = due.firstOrNull() ?: return SceneAnswer(SceneAnswer.Kind.NOTHING_DUE)
            if (inFrame?.id == first.id) return SceneAnswer(SceneAnswer.Kind.YES, asked = first, shown = first, timing = timing(first), nameOnly = scene.nameOnly)
            return SceneAnswer(SceneAnswer.Kind.DUE_NOW, asked = first, toward = scene.around.firstOrNull { it.first.id == first.id }?.second, lookFor = first)
        }
        // "Which one is this?", or anything about "this" with no medicine named.
        if (inFrame != null) return SceneAnswer(SceneAnswer.Kind.THIS_IS, shown = inFrame, timing = timing(inFrame), nameOnly = scene.nameOnly)
        return if (request.request in setOf("which_one", "which_is_x", "status_of", "taken_now", "yes_no")) SceneAnswer(SceneAnswer.Kind.NOTHING_IN_FRAME)
        else SceneAnswer(SceneAnswer.Kind.UNCLEAR)
    }
}
