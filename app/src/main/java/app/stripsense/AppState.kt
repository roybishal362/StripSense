package app.stripsense

import app.stripsense.core.dose.DoseRound
import app.stripsense.core.dose.PromptAnswer
import app.stripsense.core.guide.Felt
import app.stripsense.core.guide.FeelLanguage
import app.stripsense.core.dose.ExampleWeek
import app.stripsense.core.domain.People

import app.stripsense.data.FamilyLink

import app.stripsense.data.VoiceNotes
import app.stripsense.ml.NotePlayer

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.stripsense.core.dose.AnswerHistory
import app.stripsense.core.say.Answers
import app.stripsense.core.domain.AppClock
import app.stripsense.core.dose.DoseEngine
import app.stripsense.core.scan.LookCheck
import app.stripsense.core.domain.Medicine
import app.stripsense.core.domain.OcrLine
import app.stripsense.core.say.Part
import app.stripsense.core.say.Rendered
import app.stripsense.core.scan.SecondReader
import app.stripsense.core.domain.SecondReaderMode
import app.stripsense.core.say.Sentences
import app.stripsense.core.domain.StoreData
import app.stripsense.core.domain.Trial
import app.stripsense.core.report.Trials
import app.stripsense.core.domain.Verdict
import app.stripsense.data.Logs
import app.stripsense.data.Store
import app.stripsense.reminders.ReminderAlarms
import app.stripsense.ml.CallKind
import app.stripsense.ml.Gemma
import app.stripsense.ml.GemmaInputs
import app.stripsense.ml.GemmaReply
import app.stripsense.ml.LookEmbedder
import app.stripsense.ml.ReferenceFingerprint
import app.stripsense.ml.Speaker
import app.stripsense.ml.crop
import app.stripsense.ml.fitWithin
import app.stripsense.ui.shell.buzz
import app.stripsense.ui.shell.Feel
import app.stripsense.ui.scan.AnswerFlow
import app.stripsense.ui.scan.AnswerPhase
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { TODAY, ASK, SCAN, MULTI_STRIP, NUMBERS, HEALTH, FAMILY, ANSWER, HELP, DEMO, TRIAL_PICK, CHECK, EVALUATION }

enum class FamilyTab { LIST, ALARMS, TEACH, PEOPLE, FILES, SETTINGS }

/** The final verdict of one scan. [id] is new for every scan; [trial] is set in trial mode. */
data class ScanOutcome(val id: Long, val verdict: Verdict, val unreadableCount: Int, val trial: Trial? = null)

/** The second reader's state for one scan (spec 04 section 7). */
data class ReaderLine(val outcomeId: Long, val result: String?) // null while checking

/** One bubble of the Ask conversation. */
data class Bubble(val mine: Boolean, val text: List<Rendered>, val parts: List<Part> = emptyList(), val ms: Long? = null, val photo: String? = null, val callButton: Boolean = false,
    /** Several taught photos, each with the medicine's name, and the medicine the camera can lead to from here. */
    val photos: List<Pair<String, String>> = emptyList(), val findMed: String? = null,
    /** The doses the answer was about, so "Find it with the camera" can go through all of them in a round. */
    val round: List<Pair<String, String>> = emptyList())

/** The app-wide holder: store, clock, models, language, current screen. One per process (AppGraph). */
class AppState(
    private val app: Context,
    val store: Store,
    val speaker: Speaker,
    val gemma: Gemma,
    val embedder: LookEmbedder,
) {
    val engine = DoseEngine()

    /** Messages the family recorded in their own voice, one for each medicine, and what plays them. */
    val notes = VoiceNotes(java.io.File(store.looksDir.parentFile, "voices"), store.cipher)
    val notePlayer = NotePlayer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** A Compose-readable copy of the store, so every screen redraws when it changes. */
    var data: StoreData by mutableStateOf(store.data.value)
        private set

    val clock = AppClock(store.data.value.clockOffsetMs)
    private var sentMarks = store.data.value.events.size

    init {
        Feel.topMotor = store.data.value.topMotor
        Feel.touchOnly = store.data.value.touchOnly
        speaker.silent = store.data.value.touchOnly
        store.onChange = { _ ->
            scope.launch {
                data = store.data.value
                Feel.topMotor = data.topMotor
                Feel.touchOnly = data.touchOnly
                if (speaker.silent != data.touchOnly) { speaker.silent = data.touchOnly; if (data.touchOnly) speaker.stop() }
                clock.offsetMs = data.clockOffsetMs
                refreshReminders()
                // The family's page follows a mark at once, when the family has turned the link on.
                // Anything else that changed waits its minute.
                val marks = data.events.size
                FamilyLink.maybeSend(app, data, force = marks != sentMarks)
                sentMarks = marks
                checkRound()
            }
        }
    }

    val language: String get() = data.language

    /** The phone may listen for the tablet coming out: the family has left that on and the microphone is allowed. */
    fun canHearTablet(): Boolean = data.hearTablet &&
        app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun setLanguage(lang: String) {
        if (lang != data.language) store.setLanguage(lang)
    }

    fun now(): Long = clock.now()

    /** Says something through the hand (see FeelLanguage). */
    fun feel(felt: Felt, tick: Boolean = false) = Feel.play(app, felt, tick)

    /**
     * Yes, given by hand while the question is open: two knocks on the phone, or a volume button in "Touch only".
     * False when no question is open, so the press or the knock is left to mean what it usually means.
     */
    fun answerYesByHand(): Boolean {
        val result = outcome ?: return false
        if (screen != Screen.ANSWER) return false
        val flow = answerFlowFor(result)
        if (flow.phase != AnswerPhase.ASKING) return false
        flow.answer(PromptAnswer.YES_TAP)
        return true
    }

    /** A new day has begun since the example week was filled: it is moved along with the days. */
    fun keepExampleWeekFresh() {
        // Looked at once for each day: a yesterday that is all real has no example in it and never will.
        // By the real time: with the presenter's clock moved to tomorrow, the example would be filled into today.
        val today = engine.dosingDate(System.currentTimeMillis()).toString()
        if (exampleLookedAt == today) return
        exampleLookedAt = today
        if (ExampleWeek.stale(data.events, System.currentTimeMillis(), engine)) fillExampleWeek()
    }
    private var exampleLookedAt: String? = null

    /** Fills the six days before today with an example week for every person on this phone. For showing the app. */
    fun fillExampleWeek() {
        val real = data.events.filterNot { it.example }
        store.fillExampleWeek(People.all(data).flatMap { person ->
            ExampleWeek.build(People.medicines(data, person.id), real, System.currentTimeMillis(), engine) { newId() }
        })
    }
    var reminderRequest by mutableStateOf<Pair<String, String>?>(null)
    var scanDoseRequest by mutableStateOf<Pair<String, String>?>(null)

    /** The medicine the person asked the phone to find on the table ("Where is my BP tablet?"). */
    var scanFind by mutableStateOf<String?>(null)

    /** Set by a long press in Family: shows the tool for moving the app's clock while the app is being shown to someone. */
    var presenter by mutableStateOf(false)
    fun refreshReminders() {
        scope.launch(Dispatchers.IO) { ReminderAlarms.reschedule(app) }
    }

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
    private var answerFlow: AnswerFlow? = null

    fun answerFlowFor(result: ScanOutcome): AnswerFlow =
        answerFlow?.takeIf { it.outcome.id == result.id }
            ?: AnswerFlow(this, result).also { answerFlow = it }

    /** The cropped strip for the second reader, taken by the Answer screen. */
    var pendingCrop: Bitmap? = null
        private set
    private var unreadableInRow = 0
    private val answerHistory = AnswerHistory()
    var scanId by mutableStateOf(0L)
        private set
    var teachingElapsedMs by mutableStateOf(0L)
        private set
    private var teachingStarted: Long? = null
    fun noteTeachingSave() {
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val started = teachingStarted ?: elapsed.also { teachingStarted = it }
        teachingElapsedMs = elapsed - started
    }
    fun resetTeachingTime() { teachingStarted = null; teachingElapsedMs = 0 }
    private var scanCounter = 0L
    @Volatile private var finishingScan: Long? = null

    fun go(target: Screen) {
        if (target in setOf(Screen.NUMBERS, Screen.DEMO, Screen.TRIAL_PICK, Screen.CHECK, Screen.EVALUATION)) return
        if (target == Screen.TODAY || target == Screen.FAMILY) { scanDoseRequest = null; scanFind = null }
        if (target == Screen.HEALTH || target == Screen.TODAY) keepExampleWeekFresh()
        // Leaving for Today or Family by hand ends a round. The round's own moves do not.
        if ((target == Screen.TODAY || target == Screen.FAMILY) && !roundMoving) round = null
        when (target) {
            Screen.HELP -> openHelp(selfHarm = false)
            Screen.ANSWER -> screen = if (outcome != null) Screen.ANSWER else Screen.TODAY
            Screen.SCAN, Screen.MULTI_STRIP -> {
                scanId++
                speaker.stop()
                // In trial mode every way into the camera asks first what is in the hand.
                screen = if (trialMode && trialTruth == null) Screen.TRIAL_PICK else target
            }
            else -> {
                if (target == Screen.TODAY || target == Screen.ASK || target == Screen.NUMBERS || target == Screen.FAMILY) {
                    unreadableInRow = 0
                    answerHistory.reset()
                }
                if (screen == Screen.ANSWER || screen == Screen.SCAN || screen == Screen.ASK) speaker.stop()
                if (target != Screen.SCAN) trialTruth = null
                pendingCrop?.recycle()
                pendingCrop = null
                screen = target
            }
        }
    }

    /** Opens the scan to find one medicine on the table. Nothing is marked by finding it. */
    fun findOnTable(medId: String) {
        go(Screen.SCAN)
        scanDoseRequest = null
        scanFind = medId
    }

    /** The medicines of one dose time being gone through one after another (see DoseRound). */
    var round by mutableStateOf<DoseRound?>(null)
        private set
    private var roundMoving = false

    /** Starts a round over these doses and opens the camera on the first of them. Nothing is marked by starting. */
    fun startRound(items: List<Pair<String, String>>) {
        val started = DoseRound.of(items, data.events, engine) ?: return
        round = started
        roundMoving = true
        findReminderMedicine(started.current.first, started.current.second)
        roundMoving = false
    }

    /** The person does not have this strip at hand: on to the next one. The skipped dose stays unmarked. */
    fun skipInRound() { round?.let { moveRound(it, it.next(data.events, engine), wait = 0) } }

    /** Called whenever the saved data changes: the dose being looked for has been answered, so the round goes on. */
    private fun checkRound() {
        val now = round ?: return
        if (roundMoving || !now.answered(data.events, engine)) return
        moveRound(now, now.next(data.events, engine), wait = ROUND_PAUSE_MS)
    }

    private fun moveRound(from: DoseRound, next: DoseRound?, wait: Long) {
        roundMoving = true
        scope.launch {
            try {
                // The answer screen says "marked" first. Then the phone goes on by itself.
                if (wait > 0) {
                    kotlinx.coroutines.delay(wait)
                    // "I heard the tablet come out..." is longer than the pause. It is not cut off by the next strip.
                    var extra = 0
                    while (speaker.isSpeaking() && extra++ < 30) kotlinx.coroutines.delay(200)
                }
                if (round != from) return@launch
                if (next == null) {
                    val lang = speechLanguage()
                    round = null
                    go(Screen.TODAY)
                    speaker.speak(Sentences.roundDone(from.marked(data.events, engine), from.items.size, lang), hindi = lang == "hi")
                } else {
                    round = next
                    findReminderMedicine(next.current.first, next.current.second)
                }
            } finally { roundMoving = false }
        }
    }

    fun findReminderMedicine(medId: String, slotKey: String) {
        reminderRequest = null
        go(Screen.SCAN)
        scanDoseRequest = medId to slotKey
    }

    fun openHelp(selfHarm: Boolean) {
        helpSelfHarm = selfHarm
        if (screen != Screen.HELP) beforeHelp = screen
        speaker.stop()
        screen = Screen.HELP
    }

    fun closeHelp() {
        speaker.stop()
        if (beforeHelp == Screen.SCAN) scanId++
        screen = beforeHelp
    }

    fun pickTrial(truth: String) {
        scanId++
        trialTruth = truth
        lastTrialPick = truth
        screen = Screen.SCAN
    }

    fun finishCheck() {
        store.setCheckSeen()
        screen = Screen.TODAY
    }

    /** First-run setup was saved to disk by the setup screen. */
    fun setupDone() {
        screen = Screen.TODAY
        refreshReminders()
    }

    /** The saved file was read again, or the family chose to start empty: open the right first screen. */
    fun storeRecovered() {
        store.prepareRealUse()
        screen = if (store.data.value.checkSeen) Screen.TODAY else Screen.CHECK
    }

    /**
     * A scan reached its final verdict. The look check may turn a Match into NotShown or NotTaught;
     * nothing can turn a refusal into a name. The vector is made off the main thread, only when it is needed.
     */
    fun finishScan(verdict: Verdict, frame: Bitmap?, lines: List<OcrLine>, msToVerdict: Long, fromScan: Long = scanId, printChecked: Boolean = false) {
        if (finishingScan == fromScan || screen !in listOf(Screen.SCAN, Screen.MULTI_STRIP) || fromScan != scanId) { frame?.recycle(); return }
        finishingScan = fromScan
        val d = store.data.value
        val truth = if (trialMode) trialTruth else null
        val needLook = frame != null && d.looks.isNotEmpty() && (truth != null || (verdict is Verdict.Match && d.lookCheckOn))
        scope.launch {
            var cropped: Bitmap? = null
            var readerCrop: Bitmap? = null
            try {
                var vector: List<Float>? = null
                if (frame != null) withContext(Dispatchers.Default) {
                    cropped = runCatching { frame.crop(LookCheck.cropBox(lines, frame.width, frame.height)) }.getOrNull()
                    // Trial scoring still needs a vector. Normal checking starts with its cheap fingerprint.
                    if (needLook && truth != null) vector = cropped?.let { embedder.embed(it) }
                }
                if (screen !in listOf(Screen.SCAN, Screen.MULTI_STRIP) || fromScan != scanId) return@launch
                val fingerprint = cropped?.let { ReferenceFingerprint.extract(it) }.orEmpty()
                // The print engine already compared this strip with its own saved photos, word by word.
                // A picture-similarity score on top could only turn that checked answer into a refusal.
                val final = if (printChecked) verdict else if (truth != null) LookCheck.apply(verdict, vector, d.looks, d.lookCheckOn, d.tuning.tLook, fingerprint)
                    else withContext(Dispatchers.Default) {
                        LookCheck.withFallback(verdict, d.looks, d.lookCheckOn, d.tuning.tLook, fingerprint) { cropped?.let { embedder.embed(it) } }
                    }
                val best = LookCheck.best(vector, d.looks, d.tuning.tLook)
                if (best.score != null) lastLookScore = best.score
                if (final is Verdict.Match && d.secondReader != SecondReaderMode.OFF) {
                    readerCrop = cropped?.let { c -> withContext(Dispatchers.Default) { c.fitWithin(896) } }
                }
                if (screen !in listOf(Screen.SCAN, Screen.MULTI_STRIP) || fromScan != scanId) return@launch
                pendingCrop?.recycle()
                pendingCrop = readerCrop
                readerCrop = null
                val trial = truth?.let {
                    Trial(
                        id = newId(), atMillis = now(), truth = it, verdict = Answers.nameOf(final),
                        verdictMedId = Answers.medOf(final)?.id, score = Trials.score(it, final, d.medicines),
                        msToVerdict = msToVerdict, lookScore = best.score, lookOnlyWouldName = best.medId,
                    ).also(store::addTrial)
                }
                showVerdict(final, trial)
            } catch (e: Exception) {
                Logs.w("scan preparation failed: ${e.javaClass.simpleName}")
                if (screen in listOf(Screen.SCAN, Screen.MULTI_STRIP) && fromScan == scanId) showVerdict(Verdict.NotSure, null)
            } finally {
                readerCrop?.takeUnless { it === pendingCrop }?.recycle()
                cropped?.takeUnless { it === pendingCrop || it.isRecycled }?.recycle()
                frame?.takeUnless { it === pendingCrop || it.isRecycled }?.recycle()
                if (finishingScan == fromScan) finishingScan = null
            }
        }
    }

    private fun showVerdict(verdict: Verdict, trial: Trial?) {
        unreadableInRow = answerHistory.accept(verdict)
        readerLine = null
        outcome = ScanOutcome(++scanCounter, verdict, unreadableInRow, trial)
        if (trial != null) trialTruth = null
        // A tick, so it never cuts off the rhythm and the yes that led here.
        Feel.play(app, FeelLanguage.nearer(1f), tick = true)
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
        if (mode == SecondReaderMode.OFF) { crop?.recycle(); return }
        if (crop == null || !gemma.isFree()) {
            crop?.recycle()
            finishReader(o, SecondReader.SKIPPED, onDone)
            return
        }
        readerLine = ReaderLine(o.id, null)
        scope.launch {
            val contents = try {
                withContext(Dispatchers.Default) { GemmaInputs.picture(crop) }
            } catch (e: Exception) {
                Logs.w("reader image failed: ${e.javaClass.simpleName}")
                finishReader(o, SecondReader.FAILED, onDone)
                return@launch
            } finally { crop.recycle() }
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

    /**
     * Hindi mode speaks Hindi sentences, always. With no Hindi voice on the phone the speaker reads them in
     * Roman letters with the English voice; it used to switch to English sentences, which is not what Hindi mode means.
     */
    fun speechLanguage(): String = if (language == "hi") "hi" else "en"

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

    fun flushStore() { scope.launch(Dispatchers.IO) { store.flush() } }

    fun newId(): String = UUID.randomUUID().toString()
}

/** How long the answer to one medicine stays on the screen before a round goes on to the next. */
private const val ROUND_PAUSE_MS = 3000L
