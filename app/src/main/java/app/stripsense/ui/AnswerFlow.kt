package app.stripsense.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.stripsense.AppState
import app.stripsense.ScanOutcome
import app.stripsense.core.Answers
import app.stripsense.core.DoseEvent
import app.stripsense.core.Part
import app.stripsense.core.PromptAnswer
import app.stripsense.core.SecondReader
import app.stripsense.core.SecondReaderMode
import app.stripsense.core.StripAnswer
import app.stripsense.core.Verdict

enum class AnswerPhase { WAITING, ASKING, MARKED, CLOSED }

class AnswerFlow(val state: AppState, val outcome: ScanOutcome) {
    val med = Answers.medOf(outcome.verdict)
    val first = if (outcome.verdict is Verdict.Match) state.engine.stripAnswer(outcome.verdict.med, state.now(), state.store.data.value.events)
        else StripAnswer(Answers.forVerdict(outcome.verdict, outcome.unreadableCount), false, "")
    val strict = state.data.secondReader == SecondReaderMode.STRICT
    var parts by mutableStateOf(if (strict && first.askNow) first.parts.filter { it.id != "Q1" } else first.parts)
    var phase by mutableStateOf(if (first.askNow) AnswerPhase.WAITING else AnswerPhase.CLOSED)
    var marked by mutableStateOf(false)
    var taken by mutableStateOf<DoseEvent?>(null)
    var undoOpen by mutableStateOf(false)
    var reader by mutableStateOf<String?>(null)
    var readerStarted by mutableStateOf(false)
    private var ended = false

    fun start() {
        if (ended) return
        state.say(parts) { speechDone() }
        if (med != null && outcome.verdict is Verdict.Match && !state.trialMode && state.data.secondReader != SecondReaderMode.OFF) {
            readerStarted = true
            state.startSecondReader(outcome, med) { r ->
                reader = r
                if (strict && !ended && phase == AnswerPhase.WAITING) {
                    if (r == SecondReader.AGREED) {
                        parts = parts + Part("Q1")
                        phase = AnswerPhase.ASKING
                        state.say(listOf(Part("Q1")))
                    } else {
                        parts = parts + Part("C3")
                        phase = AnswerPhase.CLOSED
                        state.say(listOf(Part("C3")))
                    }
                }
            }
        }
    }

    fun speechDone() {
        if (!ended && phase == AnswerPhase.WAITING && !strict) phase = AnswerPhase.ASKING
    }

    fun answer(a: PromptAnswer) {
        if (ended || phase != AnswerPhase.ASKING) return
        val r = state.engine.answerPrompt(med!!, first.slotKey, a, state.now(), state.store.data.value.events, state.newId())
        r.event?.let { state.store.addEvent(it) }
        parts = r.parts
        if (a == PromptAnswer.YES_TAP && r.event != null) {
            taken = r.event
            undoOpen = true
            marked = true
            phase = AnswerPhase.MARKED
        } else phase = AnswerPhase.CLOSED
        state.say(parts)
    }

    fun undo() {
        if (ended || taken == null) return
        val r = state.engine.undo(taken!!, state.now(), state.store.data.value.events, state.newId())
        r.event?.let { state.store.addEvent(it) }
        parts = r.parts
        undoOpen = false
        phase = AnswerPhase.CLOSED
        state.say(parts)
    }

    fun timeout() { if (!ended && phase == AnswerPhase.ASKING) answer(PromptAnswer.TIMEOUT) }
    fun expireUndo() { undoOpen = false }
    fun close() { ended = true; if (phase == AnswerPhase.ASKING) phase = AnswerPhase.CLOSED }
}
