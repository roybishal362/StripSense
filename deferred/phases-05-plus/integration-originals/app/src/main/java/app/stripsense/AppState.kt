package app.stripsense

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.stripsense.core.Answers
import app.stripsense.core.AppClock
import app.stripsense.core.DoseEngine
import app.stripsense.core.LookCheck
import app.stripsense.core.Medicine
import app.stripsense.core.OcrLine
import app.stripsense.core.Part
import app.stripsense.core.Rendered
import app.stripsense.core.SecondReader
import app.stripsense.core.SecondReaderMode
import app.stripsense.core.Sentences
import app.stripsense.core.StoreData
import app.stripsense.core.Trial
import app.stripsense.core.Trials
import app.stripsense.core.Verdict
import app.stripsense.data.Store
import app.stripsense.ml.CallKind
import app.stripsense.ml.Gemma
import app.stripsense.ml.GemmaInputs
import app.stripsense.ml.GemmaReply
import app.stripsense.ml.LookEmbedder
import app.stripsense.ml.Speaker
import app.stripsense.ml.crop
import app.stripsense.ml.fitWithin
import app.stripsense.ui.buzz
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { TODAY, ASK, SCAN, NUMBERS, FAMILY, ANSWER, HELP, DEMO, TRIAL_PICK, CHECK }

enum class FamilyTab { LIST, TEACH, FILES, SETTINGS }

/** The final verdict of one scan. [id] is new for every scan; [trial] is set in trial mode. */
data class ScanOutcome(val id: Long, val verdict: Verdict, val unreadableCount: Int, val trial: Trial? = null)

/** The second reader's state for one scan (spec 04 section 7). */
data class ReaderLine(val outcomeId: Long, val result: String?) // null while checking

/** One bubble of the Ask conversation. */
data class Bubble(val mine: Boolean, val text: List<Rendered>, val parts: List<Part> = emptyList(), val ms: Long? = null, val photo: String? = null)

/** The app-wide holder: store, clock, models, language, current screen. One per process (AppGraph). */
class AppState(
    private val app: Context,
    val store: Store,
    val speaker: Speaker,
    val gemma: Gemma,
    val embedder: LookEmbedder,
) {
    val engine = DoseEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** A Compose-readable copy of the store, so every screen redraws when it changes. */
    var data: StoreData by mutableStateOf(store.data.value)
        private set

    val clock = AppClock(store.data.value.clockOffsetMs)

    init {
        store.onChange = {
            data = it
            clock.offsetMs = it.clockOffsetMs
        }
    }

    val language: String get() = data.language

    fun setLanguage(lang: String) {
        if (lang != data.language) store.setLanguage(lang)
    }

    fun now(): Long = clock.now()

    var screen by mutableStateOf(if (store.data.value.checkSeen) Screen.TODAY else Screen.CHECK)
        private set

    private var beforeHelp = Screen.TODAY
    var helpSelfHarm by mutableStateOf(false)
        private set

    // ---- in memory only ----
    var speakAnswers by mutableStateOf(true)
    var showReadText by mutableStateOf(false)
    var trialMode by mutableStateOf(false)
    var trialTruth by mutableStateOf<String?>(null)
    var lastTrialPick by mutableStateOf<String?>(null)
    var familyUnlocked by mutableStateOf(false)
    var familyTab by mutableStateOf(FamilyTab.LIST)
    var lastLookScore by mutableStateOf<Float?>(null)
    var minuteTick by mutableIntStateOf(0)
    val bubbles = mutableStateListOf<Bubble>()
    var lastAnswer: List<Part> = emptyList()

    /** The Ask screen should start recording as soon as it opens (the microphone on the Today card). */
    var listenOnOpen by mutableStateOf(false)

    // ---- scan results ----
    var outcome by mutableStateOf<ScanOutcome?>(null)
        private set
    var readerLine by mutableStateOf<ReaderLine?>(null)
        private set

    /** The cropped strip for the second reader, taken by the Answer screen. */
    var pendingCrop: Bitmap? = null
        private set
    private var unreadableInRow = 0
    private var scanCounter = 0L
    @Volatile private var finishing = false

    fun go(target: Screen) {
        when (target) {
            Screen.HELP -> openHelp(selfHarm = false)
            Screen.ANSWER -> screen = if (outcome != null) Screen.ANSWER else Screen.TODAY
            Screen.SCAN -> {
                speaker.stop()
                // In trial mode every way into the camera asks first what is in the hand.
                screen = if (trialMode && trialTruth == null) Screen.TRIAL_PICK else Screen.SCAN
            }
            else -> {
                if (target == Screen.TODAY || target == Screen.ASK || target == Screen.NUMBERS || target == Screen.FAMILY) {
                    unreadableInRow = 0
                }
                if (screen == Screen.ANSWER || screen == Screen.SCAN || screen == Screen.ASK) speaker.stop()
                if (target != Screen.SCAN) trialTruth = null
                screen = target
            }
        }
    }

    fun openHelp(selfHarm: Boolean) {
        helpSelfHarm = selfHarm
        if (screen != Screen.HELP) beforeHelp = screen
        screen = Screen.HELP
    }

    fun closeHelp() {
        screen = beforeHelp
    }

    fun pickTrial(truth: String) {
        trialTruth = truth
        lastTrialPick = truth
        screen = Screen.SCAN
    }

    fun finishCheck() {
        store.setCheckSeen()
        screen = Screen.TODAY
    }

    /**
     * A scan reached its final verdict. The look check may turn a Match into NotShown or NotTaught;
     * nothing can turn a refusal into a name. The vector is made off the main thread, only when it is needed.
     */
    fun finishScan(verdict: Verdict, frame: Bitmap?, lines: List<OcrLine>, msToVerdict: Long) {
        if (finishing) return
        finishing = true
        val d = store.data.value
        val truth = if (trialMode) trialTruth else null
        val needLook = frame != null && d.looks.isNotEmpty() && (truth != null || (verdict is Verdict.Match && d.lookCheckOn))
        scope.launch {
            var cropped: Bitmap? = null
            var vector: List<Float>? = null
            if (frame != null) {
                withContext(Dispatchers.Default) {
                    cropped = runCatching { frame.crop(LookCheck.cropBox(lines, frame.width, frame.height)) }.getOrNull()
                    if (needLook) vector = cropped?.let { embedder.embed(it) }
                }
            }
            val final = LookCheck.apply(verdict, vector, d.looks, d.lookCheckOn, d.tuning.tLook)
            val best = LookCheck.best(vector, d.looks, d.tuning.tLook)
            if (best.score != null) lastLookScore = best.score
            pendingCrop = if (final is Verdict.Match) cropped?.let { c -> withContext(Dispatchers.Default) { c.fitWithin(896) } } else null
            val trial = truth?.let {
                Trial(
                    id = UUID.randomUUID().toString(), atMillis = now(), truth = it, verdict = Answers.nameOf(final),
                    verdictMedId = Answers.medOf(final)?.id, score = Trials.score(it, final, d.medicines),
                    msToVerdict = msToVerdict, lookScore = best.score, lookOnlyWouldName = best.medId,
                ).also { t -> store.addTrial(t) }
            }
            showVerdict(final, trial)
            finishing = false
        }
    }

    private fun showVerdict(verdict: Verdict, trial: Trial?) {
        unreadableInRow = if (verdict == Verdict.Unreadable) unreadableInRow + 1 else 0
        readerLine = null
        outcome = ScanOutcome(++scanCounter, verdict, unreadableInRow, trial)
        if (trial != null) trialTruth = null
        buzz(app)
        screen = Screen.ANSWER
    }

    /**
     * The second reader, after Match only, in the background. Skipped unless Gemma is free.
     * A new scan never cancels it; its result is only shown for the scan it belongs to.
     */
    fun startSecondReader(o: ScanOutcome, med: Medicine, onDone: (String) -> Unit) {
        val mode = data.secondReader
        val crop = pendingCrop
        pendingCrop = null
        if (mode == SecondReaderMode.OFF) return
        if (crop == null || !gemma.isFree()) {
            finishReader(o, SecondReader.SKIPPED, onDone)
            return
        }
        readerLine = ReaderLine(o.id, null)
        scope.launch {
            val contents = withContext(Dispatchers.Default) { GemmaInputs.picture(crop) }
            val reply = gemma.ask(CallKind.SECOND_READER, SecondReader.SYSTEM, contents, null, data.tuning.gemmaTimeoutMs)
            val result = when (reply) {
                is GemmaReply.Text -> SecondReader.outcome(reply.text, med).also { gemma.stats.readerOutcome(it, reply.text) }
                GemmaReply.TimedOut -> SecondReader.TIMEOUT
                GemmaReply.Busy -> SecondReader.SKIPPED
                is GemmaReply.Failed -> SecondReader.FAILED
            }
            finishReader(o, result, onDone)
        }
    }

    private fun finishReader(o: ScanOutcome, result: String, onDone: (String) -> Unit) {
        o.trial?.let { store.setTrialSecondReader(it.id, result) }
        if (outcome?.id == o.id) {
            readerLine = ReaderLine(o.id, result)
            onDone(result)
        }
    }

    // ---- speech ----

    /** Hindi only when the phone has an offline Hindi voice; otherwise English (spec 04 section 8). */
    fun speechLanguage(): String = if (language == "hi" && speaker.hasHindiVoice()) "hi" else "en"

    fun render(parts: List<Part>): List<Rendered> = Sentences.render(parts, language, data.person)

    /** Speaks the parts (if speaking is on) and calls [onDone] when the speech ends, or at once when silent. */
    fun say(parts: List<Part>, onDone: (() -> Unit)? = null) {
        if (parts.isNotEmpty()) lastAnswer = parts
        if (!speakAnswers || parts.isEmpty()) {
            speaker.stop()
            onDone?.invoke()
            return
        }
        val lang = speechLanguage()
        val text = Sentences.render(parts, lang, data.person).joinToString(" ") { it.spoken }
        speaker.speak(text, hindi = lang == "hi", onDone = onDone)
    }

    fun newId(): String = UUID.randomUUID().toString()
}
