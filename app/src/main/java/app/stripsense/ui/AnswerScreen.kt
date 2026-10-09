package app.stripsense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.core.*
import app.stripsense.ui.theme.*
import java.time.LocalTime
import kotlinx.coroutines.delay

@Composable
fun AnswerScreen(state: AppState) {
    val outcome = state.outcome ?: return
    val lang = state.language
    val flow = remember(outcome.id) { AnswerFlow(state, outcome) }
    val med = Answers.medOf(outcome.verdict)
    val caption = flow.first.slotKey.substringAfter('T', "").takeIf(String::isNotEmpty)?.let {
        runCatching { Labels.doseLine(LocalTime.parse(it), lang) }.getOrNull()
    }
    val nickname = med?.let { nick(it, lang) }
    val banner = when {
        flow.marked -> Banners.marked(lang, caption, justNow = true)
        outcome.verdict !is Verdict.Match -> Banners.forVerdict(outcome.verdict, lang, nickname)
        flow.first.parts.firstOrNull()?.id == "A1" -> Banners.marked(lang, caption)
        flow.first.askNow -> Banners.due(lang, caption)
        else -> Banners.named(nickname.orEmpty(), caption)
    }
    LaunchedEffect(outcome.id) {
        flow.start()
        if (flow.first.askNow) { delay(if (flow.strict) 6000 else 4000); flow.speechDone() }
    }
    LaunchedEffect(flow.phase) { if (flow.phase == AnswerPhase.ASKING) { delay(60_000); flow.timeout() } }
    LaunchedEffect(flow.taken) { if (flow.taken != null) { delay(60_000); flow.expireUndo() } }
    val view = LocalView.current
    DisposableEffect(flow) { onDispose { flow.close(); state.speaker.stop(); view.keepScreenOn = false } }
    SideEffect { view.keepScreenOn = flow.phase == AnswerPhase.ASKING || flow.phase == AnswerPhase.WAITING }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { flow.close(); state.speaker.stop() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val panelLimit = maxHeight * 0.6f
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(2.dp))
        AnswerBanner(banner, lang)
        val showMed = outcome.verdict is Verdict.Match || outcome.verdict is Verdict.NotShown || outcome.verdict is Verdict.Stopped ||
            outcome.verdict is Verdict.ButtonOnly || outcome.verdict is Verdict.NotTaught
        if (showMed && med != null) MedicineCard(med, lang)
        val shown = flow.parts.filter { it.id != "Q1" }
        SaysCard(state.render(shown), shown.map { it.id }, lang) { state.say(flow.parts) }
        if (flow.readerStarted) SecondReaderLine(flow.reader, lang, med, flow.strict)
        Spacer(Modifier.height(12.dp))
    }
    // Keep confirmation visible. Its controls can scroll within a bounded panel at large font sizes.
    Column(Modifier.fillMaxWidth().heightIn(max = panelLimit).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val trial = outcome.trial
        if (trial != null) {
            val key = when (trial.score) {
                TrialScore.RIGHT -> "rightLabel"
                TrialScore.WRONG -> "wrongLabel"
                TrialScore.NOT_SURE -> "notsure"
                TrialScore.WRONG_REFUSAL -> "wrongRefusals"
            }
            Text(Labels.fill("trialLogged", lang, "s" to Labels.get(key, lang)), style = AppType.body,
                color = if (trial.score == TrialScore.WRONG || trial.score == TrialScore.WRONG_REFUSAL) AppColors.stopText else AppColors.okText)
            WideButton(Labels.get("nextTry", lang)) { state.go(Screen.SCAN) }
        } else when (flow.phase) {
            AnswerPhase.ASKING -> QuestionPanel(state.render(listOf(Part("Q1"))).single().shown, lang, 60,
                { flow.answer(PromptAnswer.YES_TAP) }, { flow.answer(PromptAnswer.NO) }, { flow.answer(PromptAnswer.NOT_SURE) })
            AnswerPhase.MARKED -> MarkedRow(Words.timeShown(state.engine.local(flow.taken!!.atMillis).toLocalTime(), lang), lang, flow.undoOpen,
                flow::undo, { state.go(Screen.TODAY) })
            AnswerPhase.WAITING -> LinearProgressIndicator(Modifier.fillMaxWidth())
            else -> WideButton(Labels.get("scanAnother", lang)) { state.go(Screen.SCAN) }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
    }
    }
}

@Composable
private fun SecondReaderLine(result: String?, lang: String, med: Medicine?, strict: Boolean) {
    if (result == SecondReader.SKIPPED && !strict) return
    val agreed = result == SecondReader.AGREED
    val text = when {
        result == null -> Labels.get("secondChecking", lang)
        agreed -> Labels.fill("secondAgrees", lang, "x" to med?.let { Strengths.display(it.strengths) }.orEmpty())
        else -> Labels.get("secondUnsure", lang)
    }
    Surface(color = if (agreed) AppColors.okBg else AppColors.dueBg, shape = AppShapes.card) {
        Text(text, Modifier.fillMaxWidth().padding(14.dp), color = if (agreed) AppColors.okText else AppColors.dueText, style = AppType.body)
    }
}
