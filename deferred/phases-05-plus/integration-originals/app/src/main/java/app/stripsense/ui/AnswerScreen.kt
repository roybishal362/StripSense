package app.stripsense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.core.Answers
import app.stripsense.core.DoseEvent
import app.stripsense.core.PromptAnswer
import app.stripsense.core.StripAnswer
import app.stripsense.core.Verdict
import app.stripsense.core.Words
import java.time.LocalTime
import kotlinx.coroutines.delay

private enum class Phase { NONE, WAITING, ASKING, MARKED, CLOSED }
private enum class Look { VERDICT, DUE, MARKED, MARKED_NOW, NAMED }

private const val QUESTION_SECONDS = 60
private const val QUESTION_FALLBACK_MS = 4_000L

/** The verdict banner, the medicine card, what StripSense says, and the question (spec 06, Answer). */
@Composable
fun AnswerScreen(state: AppState) {
    val outcome = state.outcome ?: return
    val lang = state.language
    val verdict = outcome.verdict
    val med = Answers.medOf(verdict)
    val match = verdict as? Verdict.Match

    val first = remember(outcome.id) {
        if (match != null) state.engine.stripAnswer(match.med, state.now(), state.store.data.value.events)
        else StripAnswer(Answers.forVerdict(verdict, outcome.unreadableCount), askNow = false, slotKey = "")
    }
    var parts by remember(outcome.id) { mutableStateOf(first.parts) }
    var phase by remember(outcome.id) { mutableStateOf(if (first.askNow) Phase.WAITING else Phase.NONE) }
    var look by remember(outcome.id) { mutableStateOf(if (match == null) Look.VERDICT else lookFor(first)) }
    var taken by remember(outcome.id) { mutableStateOf<DoseEvent?>(null) }
    var undoOpen by remember(outcome.id) { mutableStateOf(false) }

    // Speak the answer once when it opens; the question appears when speech ends, or after 4 seconds.
    LaunchedEffect(outcome.id) {
        state.say(first.parts) { if (phase == Phase.WAITING) phase = Phase.ASKING }
        if (phase == Phase.WAITING) {
            delay(QUESTION_FALLBACK_MS)
            if (phase == Phase.WAITING) phase = Phase.ASKING
        }
    }
    // No tap in 60 seconds: close the question, show NM1 without speaking it.
    LaunchedEffect(outcome.id, phase) {
        if (phase == Phase.ASKING) {
            delay(QUESTION_SECONDS * 1000L)
            if (phase == Phase.ASKING) {
                parts = state.engine.answerPrompt(match!!.med, first.slotKey, PromptAnswer.TIMEOUT, state.now(), emptyList(), "").parts
                phase = Phase.CLOSED
            }
        }
    }
    LaunchedEffect(taken) {
        if (taken != null) {
            delay(60_000)
            undoOpen = false
        }
    }
    val view = LocalView.current
    DisposableEffect(phase) {
        val keep = phase == Phase.ASKING || phase == Phase.WAITING
        if (keep) view.keepScreenOn = true
        onDispose { if (keep) view.keepScreenOn = false }
    }

    fun answer(a: PromptAnswer) {
        val m = match?.med ?: return
        val r = state.engine.answerPrompt(m, first.slotKey, a, state.now(), state.store.data.value.events, state.newId())
        r.event?.let { state.store.addEvent(it) }
        parts = r.parts
        when {
            a == PromptAnswer.YES_TAP && r.event != null -> {
                taken = r.event
                undoOpen = true
                look = Look.MARKED_NOW
                phase = Phase.MARKED
            }
            a == PromptAnswer.YES_TAP -> {
                look = lookFor(StripAnswer(r.parts, false, first.slotKey))
                phase = Phase.CLOSED
            }
            else -> phase = Phase.CLOSED
        }
        state.say(parts)
    }

    fun undo() {
        val t = taken ?: return
        val r = state.engine.undo(t, state.now(), state.store.data.value.events, state.newId())
        r.event?.let { state.store.addEvent(it) }
        parts = r.parts
        if (r.event != null) look = Look.DUE
        undoOpen = false
        phase = Phase.CLOSED
        state.say(parts)
    }

    val nickname = med?.let { if (lang == "hi") it.nickHi.ifEmpty { it.nickEn } else it.nickEn }
    val caption = slotTime(first.slotKey)?.let { Labels.doseLine(it, lang) }
    val banner = when (look) {
        Look.VERDICT -> Banners.forVerdict(verdict, lang, nickname)
        Look.DUE -> Banners.due(lang, caption)
        Look.MARKED -> Banners.marked(lang, caption)
        Look.MARKED_NOW -> Banners.marked(lang, caption, justNow = true)
        Look.NAMED -> Banners.named(nickname.orEmpty(), caption)
    }
    val showQuestion = phase == Phase.ASKING
    val shownParts = if (showQuestion || phase == Phase.WAITING) parts.filter { it.id != "Q1" } else parts
    val rendered = state.render(shownParts)
    val showCard = verdict is Verdict.Match || verdict is Verdict.NotShown || verdict is Verdict.Stopped ||
        verdict is Verdict.ButtonOnly || verdict is Verdict.NotTaught

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            AnswerBanner(banner, lang)
            if (showCard && med != null) MedicineCard(med, lang)
            if (rendered.isNotEmpty()) SaysCard(rendered, shownParts.map { it.id }, lang) { state.say(parts) }
            Spacer(Modifier.height(if (phase == Phase.NONE || phase == Phase.CLOSED) 96.dp else 300.dp))
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            when (phase) {
                Phase.ASKING -> QuestionPanel(
                    question = state.render(listOf(app.stripsense.core.Part("Q1"))).first().shown,
                    lang = lang,
                    seconds = QUESTION_SECONDS,
                    onYes = { answer(PromptAnswer.YES_TAP) },
                    onNo = { answer(PromptAnswer.NO) },
                    onNotSure = { answer(PromptAnswer.NOT_SURE) },
                )
                Phase.MARKED -> MarkedRow(
                    markedAt = Words.timeShown(state.engine.local(taken!!.atMillis).toLocalTime(), lang),
                    lang = lang,
                    undoOpen = undoOpen,
                    onUndo = { undo() },
                    onDone = { state.go(Screen.TODAY) },
                )
                Phase.WAITING -> Unit
                else -> WideButton(
                    Labels.get("scanAnother", lang),
                    Modifier.navigationBarsPadding().padding(16.dp),
                ) { state.go(Screen.SCAN) }
            }
        }
    }
}

private fun lookFor(a: StripAnswer): Look = when {
    a.askNow -> Look.DUE
    a.parts.firstOrNull()?.id == "A1" -> Look.MARKED
    else -> Look.NAMED
}

private fun slotTime(slotKey: String): LocalTime? =
    slotKey.substringAfter('T', "").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
