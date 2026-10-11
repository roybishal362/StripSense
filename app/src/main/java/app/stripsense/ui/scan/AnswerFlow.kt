package app.stripsense.ui.scan

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*

import android.os.SystemClock
import androidx.compose.runtime.*
import app.stripsense.AppState
import app.stripsense.ScanOutcome

enum class AnswerPhase { NONE, WAITING, ASKING, MARKED, CLOSED }

/** Keeps confirmation, strict-reader gating, and Undo tied to one scan result. */
class AnswerFlow(private val state: AppState, val outcome: ScanOutcome) {
    val med = (outcome.verdict as? Verdict.Match)?.med
    val trial = outcome.trial != null
    val first = if (med != null && !trial) MedicationTimings.stripAnswer(med, state.now(), state.data.events, state.engine)
        else StripAnswer(Answers.forVerdict(outcome.verdict, outcome.unreadableCount).take(if (trial) 1 else Int.MAX_VALUE), false, "")
    val strict = !trial && first.askNow && state.data.secondReader == SecondReaderMode.STRICT

    /** While the question is open the phone listens for the tablet coming out of the strip (see TabletListener). */
    val hearing = !trial && first.askNow && state.canHearTablet()
    private val question = if (hearing) listOf(Part("Q1"), Part("LS1")) else listOf(Part("Q1"))
    var parts by mutableStateOf(if (strict) first.parts.filter { it.id != "Q1" } else if (hearing) first.parts + Part("LS1") else first.parts)
        private set
    var phase by mutableStateOf(if (first.askNow) AnswerPhase.WAITING else AnswerPhase.NONE)
        private set
    var marked by mutableStateOf(false)
        private set
    var taken by mutableStateOf<DoseEvent?>(null)
        private set
    var undoOpen by mutableStateOf(false)
        private set
    var reader by mutableStateOf<String?>(null)
        private set
    var readerStarted by mutableStateOf(false)
        private set
    private var spoken = false
    private var open = true
    private var started = false
    private var undoUntil = 0L
    fun undoRemainingMs(): Long = (undoUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    fun start() {
        open = true
        if (started) return
        started = true
        state.say(parts) { speechDone() }
        if (med != null) {
            readerStarted = state.data.secondReader != SecondReaderMode.OFF
            state.startSecondReader(outcome, med) { result ->
                reader = result
                evaluate()
            }
        }
    }

    fun speechDone() { if (open) { spoken = true; evaluate() } }

    private fun evaluate() {
        if (!open || phase != AnswerPhase.WAITING || !spoken) return
        if (!strict) { phase = AnswerPhase.ASKING; return }
        val result = reader ?: return
        if (result == SecondReader.AGREED) {
            phase = AnswerPhase.ASKING
            state.say(question)
        } else {
            parts = parts.filter { it.id != "Q1" } + Part("C3")
            phase = AnswerPhase.CLOSED
            state.say(listOf(Part("C3")))
        }
    }

    fun timeout() {
        if (open && phase == AnswerPhase.ASKING) {
            parts = listOf(Part("NM1"))
            phase = AnswerPhase.CLOSED
        }
    }

    fun answer(answer: PromptAnswer) {
        val medicine = med ?: return
        if (!open || trial || phase != AnswerPhase.ASKING) return
        val current = state.data.medicines.firstOrNull { it.id == medicine.id } ?: return
        val result = state.engine.answerPrompt(current, first.slotKey, answer, state.now(), state.data.events, state.newId())
        result.event?.let(state.store::addEvent)
        parts = result.parts
        if (result.showUndo && result.event != null) {
            taken = result.event
            marked = true
            undoOpen = true
            undoUntil = SystemClock.elapsedRealtime() + 60_000
            phase = AnswerPhase.MARKED
        } else phase = AnswerPhase.CLOSED
        state.say(parts)
    }

    /** The tablet was heard coming out of the strip while the question was open: that answers it. */
    fun heard(pop: app.stripsense.core.hear.Pop) {
        if (!open || phase != AnswerPhase.ASKING) return
        state.store.learnTabletSound(pop.level)
        answer(PromptAnswer.HEARD)
    }

    /** This mark was made by hearing the tablet, not by a press. */
    val heardIt: Boolean get() = taken?.source == EventSource.HEARD

    fun undo() {
        val event = taken ?: return
        if (!open || !undoOpen) return
        if (undoRemainingMs() == 0L) { expireUndo(); return }
        val result = state.engine.undo(event, state.now(), state.data.events, state.newId())
        result.event?.let(state.store::addEvent)
        parts = result.parts
        marked = false
        undoOpen = false
        phase = AnswerPhase.CLOSED
        state.say(parts)
    }

    fun expireUndo() { undoOpen = false }
    fun pause() {
        if (phase == AnswerPhase.WAITING || phase == AnswerPhase.ASKING) {
            parts = listOf(Part("NM1"))
            phase = AnswerPhase.CLOSED
        }
    }
    fun close() { pause(); open = false }
}
