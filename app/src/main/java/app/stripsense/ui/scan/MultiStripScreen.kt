package app.stripsense.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip

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

import android.Manifest
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.ml.*
import app.stripsense.ui.theme.AppColors
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The hand is looked for this often; between looks the last answer stands. */
private const val HAND_EVERY_MS = 200L
private const val HAND_KEEP_MS = 350L
private const val HAND_IDLE_MS = 300L

/** A strip's patch must show a hand in this many looks in a row before it counts as touched. */
private const val TOUCH_LOOKS = 2

/** The scene as one picture of the table: strips followed by name, a hand in view, and Gemma reading what is unsettled. */
@Composable
fun MultiStripScreen(state: AppState) {
    val context = LocalContext.current
    val view = LocalView.current
    var granted by remember { mutableStateOf(hasCamera(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCamera(context) }
    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    if (state.data.medicines.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Text(Labels.get("emptyRealList", state.language), color = Color.White)
            Button(onClick = { state.familyTab = app.stripsense.FamilyTab.LIST; state.go(Screen.FAMILY) }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(Labels.get("addMedicine", state.language)) }
        }
    } else if (!granted) CameraRefused(state.language)
    else SceneReader(state)
}

@Composable
private fun SceneReader(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reader = remember { OcrReader() }
    val active = remember { AtomicBoolean(true) }
    val scanId = state.scanId
    val scene = remember { Scene() }
    val index = remember(state.data.medicines, state.data.looks) { PrintIndex.build(state.data.medicines, state.data.looks) }
    var regions by remember { mutableStateOf<List<SceneRegion>>(emptyList()) }
    // What is drawn: one box for each medicine, nothing for print that has not been placed.
    var boxes by remember { mutableStateOf<List<SceneRegion>>(emptyList()) }
    var selected by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var piece by remember { mutableStateOf<Medicine?>(null) }
    var named by remember { mutableStateOf<Medicine?>(null) }
    var selectedAt by remember { mutableLongStateOf(0L) }
    var seenAt by remember { mutableLongStateOf(0L) }
    var imageW by remember { mutableIntStateOf(1) }
    var imageH by remember { mutableIntStateOf(1) }
    var viewW by remember { mutableIntStateOf(1) }
    var viewH by remember { mutableIntStateOf(1) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var smallPrint by remember { mutableStateOf(false) }
    // The hand, looked for a few times a second on a small copy of the frame, on the camera thread.
    val hands = remember { HandFinder(context) }
    val handAt = remember { AtomicLong(0) }
    val fingerAt = remember { AtomicLong(0) }
    var finger by remember { mutableStateOf<Fingertip?>(null) }
    // Where the strips were read, kept while a hand covers them, and each strip's own usual colour.
    val table = remember { TableMemory() }
    var placed by remember { mutableStateOf<List<PlacedStrip>>(emptyList()) }
    val usual = remember { HashMap<String, Float>() }
    val touchRun = remember { arrayOf<Any?>(null, 0) }
    var touched by remember { mutableStateOf<String?>(null) }
    // Gemma as a second reader of print the fast reader has not settled. One call at a time, with rests.
    val reading = remember { AtomicBoolean(false) }
    val asked = remember { HashMap<Long, Long>() }
    val tries = remember { HashMap<Long, Int>() }
    val firstSeen = remember { HashMap<Long, Long>() }
    val askedAt = remember { AtomicLong(0) }
    var readerMs by remember { mutableLongStateOf(0L) }
    var zoomed by remember { mutableStateOf(false) }
    val pace = remember { LongArray(3) }
    val handMs = remember { AtomicLong(0) }
    val pointerRun = remember { IntArray(2) }
    // The last small look at the table: what is table, hand and thing. Strip outlines are taken from it.
    val tableLook = remember { java.util.concurrent.atomic.AtomicReference<Pair<Long, TableLook?>>(0L to null) }
    // The strips a hand is over right now. Only these may be remembered while their print cannot be read.
    val coveredNow = remember { java.util.concurrent.atomic.AtomicReference<Set<String>>(emptySet()) }
    // Which way the voice is steering, drawn as an arrow, and whether the medicine is inside the frame.
    var aim by remember { mutableStateOf<Toward?>(null) }
    var inFrame by remember { mutableStateOf(false) }
    val smallSince = remember { AtomicLong(0) }
    // Talking to the camera: a question asked out loud while scanning, answered from what the camera reads now.
    val recorder = remember { Recorder() }
    var listening by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    val micAllowed = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    DisposableEffect(Unit) { onDispose { recorder.stop() } }
    fun sceneNow(): SceneNow {
        val named = regions.mapNotNull { region -> sceneIdentity(region)?.let { region to it } }
        val middle = named.maxByOrNull { Homing.closeness(it.first.box, imageW, imageH) }?.takeIf { Homing.closeness(it.first.box, imageW, imageH) >= ScanGuidance.CENTRED_STAY }
        return SceneNow(middle?.second?.med, middle?.second?.nameOnly == true,
            named.filter { it !== middle }.map { it.second.med to HandGuide.toward(imageW / 2, imageH / 2, it.first.box) })
    }
    fun askTheCamera() {
        if (listening) { recorder.stop(); return }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) { micAllowed.launch(Manifest.permission.RECORD_AUDIO); return }
        listening = true
        said = null
        state.speaker.stop()
        scope.launch {
            val wav = recorder.record()
            listening = false
            if (wav == null) return@launch
            thinking = true
            try {
                val data = state.data
                val meds = People.medicines(data)
                val reply = state.gemma.askJson(CallKind.VOICE, VoicePrompt.system(meds), GemmaInputs.audio(wav), VoicePrompt.SCHEMA, 15_000)
                val request = (reply as? GemmaReply.Text)?.let { VoiceRequest.parse(it.text, meds.size) }
                // The scene is read after the model has answered: the strip may have been moved while it was thinking.
                val answer = SceneQuestion.answer(request, meds, sceneNow()) { med -> MedicationTimings.scene(med, data.medicines, state.now(), data.events, state.engine) }
                if (answer.kind == SceneAnswer.Kind.HELP) { state.openHelp(false); return@launch }
                answer.lookFor?.let { state.scanFind = it.id }
                said = Sentences.sceneAnswer(answer, state.language)
                state.speaker.speak(Sentences.sceneAnswer(answer, state.speechLanguage()), state.speechLanguage() == "hi")
                app.stripsense.data.Logs.i("camera question: request ${request?.request ?: "none"}, answer ${answer.kind}")
            } finally { thinking = false }
        }
    }
    // What was answered stays on the screen long enough to read, and the scene's own voice waits meanwhile.
    LaunchedEffect(said) { if (said != null) { kotlinx.coroutines.delay(7000); said = null } }
    val paused =named != null || piece != null || state.reminderRequest != null
    val screen = state.screen
    fun forget() { scene.reset(); regions = emptyList(); boxes = emptyList(); table.clear(); placed = emptyList(); finger = null; touched = null }
    DisposableEffect(reader) { onDispose { active.set(false); reader.close(); hands.close() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { selected = null; named = null; piece = null; forget(); state.speaker.stop() }
    LaunchedEffect(index, state.data.tuning) { forget(); selected = null }
    LaunchedEffect(paused) { if (paused) forget() }
    LaunchedEffect(reader) {
        while (true) {
            kotlinx.coroutines.delay(200)
            val now = SystemClock.elapsedRealtime()
            if (now - seenAt > 2000 && regions.isNotEmpty()) forget()
            if (selected != null && now - selectedAt > 2000) selected = null
        }
    }
    // The picture above, the controls below it, never over it. The controls used to lie on top of the lower quarter
    // of the camera picture: the phone read and named a strip lying there that the person could not see, and it
    // looked as if the strip in the middle had been called by another's name.
    Column(Modifier.fillMaxSize().background(AppColors.ink)) {
    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { viewW = it.width; viewH = it.height }) {
        CameraWithReader(onFrame = { proxy ->
            if (!active.get() || paused || state.screen != screen || state.scanId != scanId) { proxy.close(); return@CameraWithReader }
            val frameAt = SystemClock.elapsedRealtime()
            // A hand is only looked for once there is a strip it could be on. Until then every frame goes to reading print,
            // which is when reading speed matters most.
            // With no strip named yet a finger is still looked for, less often, wherever there is print in view:
            // a finger pointing at print too far to read is told to bring the phone closer.
            val nothingNamed = placed.isEmpty()
            if (nothingNamed && regions.isEmpty() && !smallPrint) { if (finger != null) finger = null; if (touched != null) touched = null }
            else if (frameAt - handAt.get() >= (if (nothingNamed) HAND_IDLE_MS else HAND_EVERY_MS)) {
                handAt.set(frameAt)
                runCatching {
                    val small = proxy.smallUpright(HandFinder.PICTURE_PX)
                    // First each remembered strip's own patch: has skin appeared over it? This needs no palm in view.
                    val skin = skinOver(small, placed, imageW)
                    coveredNow.set(Touch.covered(skin, usual))
                    val covered = Touch.pick(skin, usual)
                    touchRun[1] = if (covered != null && covered == touchRun[0]) (touchRun[1] as Int) + 1 else if (covered != null) 1 else 0
                    touchRun[0] = covered
                    touched = covered?.takeIf { (touchRun[1] as Int) >= TOUCH_LOOKS }
                    // A finger reaching in from the edge, found from the picture itself. This is what pointing looks like
                    // under a phone, and the hand model does not see it. Two looks in a row, so one odd frame is not a finger.
                    val look = runCatching { tableLookOf(small) }.getOrNull()
                    tableLook.set(frameAt to look)
                    val pointer = look?.pointer()
                    // Only a finger held out counts. An arm or a flat hand at the edge of the picture is not a question.
                    val steadyPointer = pointer?.takeIf { it.pointing && pointerRun[0] >= 1 }
                    pointerRun[0] = if (pointer != null) pointerRun[0] + 1 else 0
                    // Then the whole hand, when the palm can be seen: its fingertip is exact. The finder recycles the picture.
                    // The model is asked on every second look and never when a pointing finger is already found:
                    // together the two looks took up to 80 ms of the camera's time, and reading slowed down with them.
                    val fromPointer = steadyPointer?.let { Fingertip(it.x, it.y, it.dx, it.dy, it.pointing) }
                    val found = fromPointer ?: if (pointerRun[1]++ % 2 == 0) hands.find(small) else null
                    if (!small.isRecycled) small.recycle()
                    // One missed look is not a hand taken away.
                    if (found != null) { fingerAt.set(frameAt); finger = found }
                    else if (frameAt - fingerAt.get() > HAND_KEEP_MS) finger = null
                }
                handMs.set(SystemClock.elapsedRealtime() - frameAt)
            }
            reader.read(proxy) { lines ->
                if (!active.get() || paused || state.screen != screen || state.scanId != scanId) return@read
                val turned = proxy.imageInfo.rotationDegrees % 180 != 0
                imageW = if (turned) proxy.height else proxy.width
                imageH = if (turned) proxy.width else proxy.height
                val now = SystemClock.elapsedRealtime()
                seenAt = now
                val data = state.data
                smallPrint = StripParser.tooSmallLines(lines, data.tuning) > 0
                val usable = lines.filter { it.confidence >= 0.2f && it.heightPx >= 8 }
                val look = tableLook.get().takeIf { now - it.first <= LOOK_FRESH_MS }?.second
                val seen = scene.read(usable, index, data.tuning, now, look?.let { fresh -> { names -> fresh.outlines(names, imageW, imageH) } })
                val next = seen.map { SceneRegion(it.box, it.lines, it.id, it.result, it.expiry) }
                regions = next
                boxes = Scene.oneBoxEach(seen).map { SceneRegion(it.box, it.lines, it.id, it.result, it.expiry) }
                // Numbers only, every few seconds: how many frames are read a second and how long the scene takes with them.
                pace[0] += SystemClock.elapsedRealtime() - now
                pace[1]++
                if (now - pace[2] > 5000) {
                    app.stripsense.data.Logs.i("scan pace: ${reader.framesLastSecond()} frames/s, text reader ${reader.lastReadMs} ms for ${lines.size} lines, scene ${pace[0] / pace[1].coerceAtLeast(1)} ms, hand look ${handMs.get()} ms, strips ${next.count { sceneIdentity(it) != null }}, hand ${finger != null || touched != null}, gemma busy ${!state.gemma.isFree()}")
                    pace[0] = 0; pace[1] = 0; pace[2] = now
                    if (next.count { sceneIdentity(it) != null } >= 1) scene.layout().lines().forEach { app.stripsense.data.Logs.i("layout ${imageW}x$imageH $it".take(3500)) }
                }

                // With no hand in view every reading updates the table. With a hand in view the table is held and
                // only follows the camera's own movement: a half-covered strip must not lose its place.
                val hand = finger != null || touched != null
                val hidden = coveredNow.get() + (finger?.let { f ->
                    placed.filter { HandGuide.inside((f.x * imageW).toInt(), (f.y * imageH).toInt(), it.box) }.map { it.key }
                } ?: emptyList())
                val held = table.current(now, hidden, imageW, imageH).associateBy { it.key }
                val liveKeys = HashSet<String>()
                for (strip in seen) {
                    val (key, med, kind) = when (val result = strip.result) {
                        is PrintResult.Checked -> Triple(result.med.id, result.med, PlacedKind.CHECKED)
                        is PrintResult.NameOnly -> Triple(result.med.id, result.med, PlacedKind.NAME)
                        is PrintResult.Differs -> Triple("track-${strip.id}", result.med, PlacedKind.REFUSED)
                        PrintResult.Unknown -> Triple("track-${strip.id}", null, PlacedKind.UNKNOWN)
                        else -> continue
                    }
                    // A strip read clearly somewhere else has been moved: it is followed even with a hand in view,
                    // or one false touch would keep the table frozen for good.
                    if (!strip.live) continue
                    // Every strip read in this frame is fresh. Under a hand its place is kept, because a half-covered
                    // strip reads as a smaller patch; a strip read clearly somewhere else has been moved and is followed.
                    val old = held[key]
                    if (hand && old != null && !movedAway(old.box, strip.box)) table.keep(key, med, kind, strip.box, now)
                    else table.see(key, med, kind, strip.box, now)
                    liveKeys += key
                }
                // Strips not read in this frame stay where they are on the table, so in the picture they move with it.
                // They are kept only under a hand or beyond the edge of the picture: an open strip that is not read is gone.
                table.drift(scene.shift.first, scene.shift.second, liveKeys)
                placed = table.current(now, hidden, imageW, imageH)

                // Print too small to read and nothing named for a while: zoom in once, by itself.
                if (smallPrint && next.none { sceneIdentity(it) != null }) {
                    if (smallSince.get() == 0L) smallSince.set(now)
                    else if (!zoomed && now - smallSince.get() > 1500) { zoomed = true; camera?.cameraControl?.setZoomRatio(zoomFor(camera, true)) }
                } else smallSince.set(0)

                // Print the fast reader has not settled, and that could be a saved medicine, is shown alone and small
                // to Gemma. Its copy of the print comes back as one more reading for the same rules.
                val inView = seen.mapTo(HashSet()) { it.id }
                asked.keys.retainAll(inView)
                firstSeen.keys.retainAll(inView)
                for (id in inView) firstSeen.getOrPut(id) { now }
                // The count of tries is kept when a strip leaves: swapping strips used to start Gemma again on every
                // swap, and reading fell to one frame a second just when a new strip had to be named quickly.
                if (tries.size > 40) tries.clear()
                if (!hand && now - askedAt.get() >= LiveReader.REST_MS && state.gemma.isFree() && reading.compareAndSet(false, true)) {
                    val pick = seen.filter { it.live && (tries[it.id] ?: 0) < LiveReader.MAX_TRIES && now - (asked[it.id] ?: 0L) >= LiveReader.AGAIN_MS &&
                        now - (firstSeen[it.id] ?: now) >= LiveReader.WAIT_MS &&
                        LiveReader.worthAsking(it.result, it.promising) }
                        .maxByOrNull { if (it.result == PrintResult.Reading) 0 else 1 }
                    val frame = pick?.let { runCatching { proxy.uprightBitmap() }.getOrNull() }
                    if (pick == null || frame == null) reading.set(false) else {
                        asked[pick.id] = now
                        tries[pick.id] = (tries[pick.id] ?: 0) + 1
                        askedAt.set(now)
                        val crop = frame.crop(LookCheck.cropBox(pick.lines, frame.width, frame.height))
                        val small = crop.fitWithin(LiveReader.PICTURE_PX)
                        if (crop !== small && crop !== frame) crop.recycle()
                        if (frame !== small) frame.recycle()
                        scope.launch {
                            try {
                                val started = SystemClock.elapsedRealtime()
                                val contents = withContext(Dispatchers.Default) { GemmaInputs.reading(small) }
                                val reply = state.gemma.ask(CallKind.LIVE_READER, LiveReader.SYSTEM, contents, null, LiveReader.TIMEOUT_MS)
                                if (reply is GemmaReply.Text) {
                                    readerMs = SystemClock.elapsedRealtime() - started
                                    scene.addReading(pick.id, LiveReader.lines(reply.text), index, state.data.tuning, SystemClock.elapsedRealtime())
                                }
                            } finally { small.recycle(); reading.set(false) }
                        }
                    }
                }

                // A chosen strip opens its answer from a fresh frame in which it is still the same checked medicine.
                val chosen = selected?.let { (id, medId) -> next.firstOrNull { it.trackId == id && (it.result as? PrintResult.Checked)?.med?.id == medId } }
                if (chosen != null) {
                    val frame = runCatching { proxy.uprightBitmap() }.getOrNull()
                    selected = null
                    if (frame != null) {
                        val box = LookCheck.cropBox(chosen.lines, frame.width, frame.height)
                        val crop = frame.crop(box)
                        val localLines = chosen.lines.map { it.copy(left = it.left - box.left, top = it.top - box.top,
                            right = it.right - box.left, bottom = it.bottom - box.top) }
                        if (crop !== frame) frame.recycle()
                        active.set(false)
                        state.finishScan(Verdict.Match((chosen.result as PrintResult.Checked).med), crop, localLines, -1L, scanId, printChecked = true)
                    }
                }
            }
        }, onCamera = { camera = it }, onFailure = { failed = true })
        SceneBoxes(state, boxes, viewW, viewH, imageW, imageH, { piece = it }, { named = it }) { region ->
            (region.result as? PrintResult.Checked)?.let { selected = region.trackId to it.med.id; selectedAt = SystemClock.elapsedRealtime() }
        }
        SceneFrame(aim, inFrame, viewW, viewH, imageW, imageH, state.language, showHint = !paused && boxes.isEmpty())
        // The strip the finger is on or points at is outlined, so a watcher sees which one the phone means.
        val pointedAt = touched ?: finger?.let { f ->
            HandGuide.pointed((f.x * imageW).toInt(), (f.y * imageH).toInt(), placed, f.takeIf { it.pointing }?.let { it.dx * imageW to it.dy * imageH },
                (kotlin.math.hypot(imageW.toDouble(), imageH.toDouble()) * HandGuide.POINT_REACH).toInt())?.key
        }
        SceneHandMarks(state, placed, regions, finger, pointedAt, viewW, viewH, imageW, imageH)
        if (failed) Text(Labels.get("cameraFailed", state.language), color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        // The round microphone on the picture: press, ask about the strip in front of the camera, press again.
        if (!paused && state.gemma.state.collectAsState().value is GemmaState.Ready) Column(Modifier.align(Alignment.BottomEnd).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).shadow(8.dp, androidx.compose.foundation.shape.CircleShape).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (listening) Color(0xFFD72C20) else AppColors.brand700).clickable(enabled = !thinking, role = androidx.compose.ui.semantics.Role.Button,
                    onClickLabel = Labels.get("askCamera", state.language)) { askTheCamera() }, contentAlignment = Alignment.Center) {
                if (thinking) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                else Icon(app.stripsense.ui.theme.AppIcons.Microphone, Labels.get("askCamera", state.language), tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Text(Labels.get(if (listening) "askCameraStop" else if (thinking) "askCameraThinking" else "askCamera", state.language), color = Color.White, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // In a round: which medicine this is and of how many, and a way on when this strip is not at hand.
        state.round?.let { round ->
            val med = state.data.medicines.firstOrNull { it.id == round.current.first }
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Labels.fill("roundAt", state.language, "i" to round.at + 1, "n" to round.items.size) + (med?.let { ": " + app.stripsense.ui.today.nick(it, state.language) }.orEmpty()),
                    color = Color(0xFF7FD3B7), fontSize = 15.sp, maxLines = 2, modifier = Modifier.weight(1f))
                TextButton(onClick = { state.skipInRound() }) { Text(Labels.get("roundSkip", state.language), color = Color.White) }
            }
        }
        said?.let { Text(it, color = Color.White, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp)) }
        SceneVoiceGuide(state, regions, imageW, imageH, smallPrint, paused || selected != null || listening || thinking || said != null, placed,
            finger?.let { (it.x * imageW).toInt() to (it.y * imageH).toInt() },
            finger?.takeIf { it.pointing }?.let { it.dx * imageW to it.dy * imageH }, touched,
            onOpenRemembered = { med ->
                // The hand has rested on the checked strip of the due dose. Its print is covered now, so the
                // answer opens from what was read a moment ago. It still asks before anything is marked.
                active.set(false)
                state.finishScan(Verdict.Match(med), null, emptyList(), -1L, scanId, printChecked = true)
            }, onAim = { toward, centredNow -> aim = toward; inFrame = centredNow },
            extraButtons = {
                PanelButton(Labels.get("torchShort", state.language), enabled = camera?.cameraInfo?.hasFlashUnit() == true) {
                    torch = !torch; camera?.cameraControl?.enableTorch(torch)
                }
                PanelButton(Labels.get(if (zoomed) "zoomOut" else "zoomShort", state.language)) {
                    zoomed = !zoomed; camera?.cameraControl?.setZoomRatio(zoomFor(camera, zoomed))
                }
            }) { region, identity ->
            if (identity.nameOnly) named = identity.med
            else { selected = region.trackId to identity.med.id; selectedAt = SystemClock.elapsedRealtime() }
        }
        if (selected != null) Text(Labels.get("sceneWait", state.language), color = Color.White)
    }
    }
    named?.let { NameScheduleDialog(state, it) { named = null } }
    piece?.let { med ->
        val look = state.data.looks.firstOrNull { it.medId == med.id && it.verifiedPrint }
            ?: state.data.looks.firstOrNull { it.medId == med.id }
        val photo = look?.let { rememberThumb(File(state.store.looksDir, it.photoFile)) }
        AlertDialog(onDismissRequest = { piece = null }, title = { Text(nick(med, state.language)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                photo?.let { Image(it.asImageBitmap(), contentDescription = nick(med, state.language), modifier = Modifier.fillMaxWidth().height(220.dp)) }
                Text(med.brand + " · " + Strengths.display(med.strengths) + " " + med.release.orEmpty())
                Text(Sentences.referenceCandidate(med, state.language))
                if (med.status == MedStatus.STOPPED) Text(Labels.get("stopped", state.language))
            } },
            confirmButton = { TextButton(onClick = { piece = null }) { Text(Labels.get("close", state.language)) } })
    }
}

/** Twice as close for small print, or as far as this camera goes; back to the camera's widest otherwise. */
private fun zoomFor(camera: Camera?, close: Boolean): Float {
    val zoom = camera?.cameraInfo?.zoomState?.value
    return if (close) minOf(2f, zoom?.maxZoomRatio ?: 1f) else maxOf(1f, zoom?.minZoomRatio ?: 1f)
}

/** The centre has gone further than half the strip's own size: the strip is somewhere else now. */
private fun movedAway(was: CropBox, now: CropBox): Boolean {
    val dx = kotlin.math.abs((was.left + was.right) - (now.left + now.right)) / 2
    val dy = kotlin.math.abs((was.top + was.bottom) - (now.top + now.bottom)) / 2
    return dx > was.width / 2 || dy > was.height / 2
}

/**
 * How much of each remembered strip's patch is skin-coloured, sampled on the small picture.
 * The middle of the patch only, so the table at its edges does not count.
 */
private fun skinOver(small: Bitmap, strips: List<PlacedStrip>, imageW: Int): Map<String, Float> {
    if (imageW <= 1 || strips.isEmpty()) return emptyMap()
    val scale = small.width.toFloat() / imageW
    return strips.associate { strip ->
        val insetX = strip.box.width * 0.15f
        val insetY = strip.box.height * 0.15f
        val pixels = IntArray(GRID * GRID)
        var n = 0
        for (row in 0 until GRID) for (col in 0 until GRID) {
            val x = ((strip.box.left + insetX + (strip.box.width - 2 * insetX) * (col + 0.5f) / GRID) * scale).toInt()
            val y = ((strip.box.top + insetY + (strip.box.height - 2 * insetY) * (row + 0.5f) / GRID) * scale).toInt()
            if (x in 0 until small.width && y in 0 until small.height) pixels[n++] = small.getPixel(x, y)
        }
        strip.key to Touch.fraction(pixels.copyOf(n))
    }
}

private const val GRID = 10

/** The small picture made smaller still, and sorted into table, hand and things lying on the table. */
private fun tableLookOf(small: Bitmap): TableLook? {
    val step = maxOf(1, small.width / POINTER_ACROSS)
    val w = small.width / step
    val h = small.height / step
    if (w < 8 || h < 8) return null
    val all = IntArray(small.width * small.height)
    small.getPixels(all, 0, small.width, 0, 0, small.width, small.height)
    val grid = IntArray(w * h) { i -> all[(i / w) * step * small.width + (i % w) * step] }
    return TableLook.of(grid, w, h)
}

private const val POINTER_ACROSS = 96
private const val LOOK_FRESH_MS = 600L
