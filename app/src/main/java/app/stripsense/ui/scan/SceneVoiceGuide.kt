package app.stripsense.ui.scan

import kotlinx.coroutines.launch

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

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import kotlinx.coroutines.delay

/**
 * One voice for the scan. It says what is true now, finishes its sentence unless something urgent happens,
 * and answers a hand in view before anything else.
 * [finger]: the fingertip in the camera image, when the hand model found one. [ray]: where a straight finger points.
 * [touched]: the remembered strip a hand is covering, noticed from its own patch of the picture.
 * [extraButtons]: the camera's own buttons, put in the same row so the panel stays low and the picture stays large.
 */
@Composable
fun SceneVoiceGuide(state: AppState, regions: List<SceneRegion>, imageWidth: Int, imageHeight: Int,
    smallPrint: Boolean, paused: Boolean, placed: List<PlacedStrip>, finger: Pair<Int, Int>?, ray: Pair<Float, Float>?, touched: String?,
    onOpenRemembered: (Medicine) -> Unit, onAim: (Toward?, Boolean) -> Unit, extraButtons: @Composable RowScope.() -> Unit,
    onOpen: (SceneRegion, SceneIdentity) -> Unit) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var talkBack by remember { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { talkBack = it }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    val announcements = remember { ScanAnnouncements() }
    var enabled by remember { mutableStateOf(true) }
    var centred by remember { mutableStateOf(false) }
    var lastWanted by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf<String?>(null) }
    var displayed by remember { mutableStateOf<ScanMessage?>(null) }
    // The medicines the person has already been told are on the table. A new one appearing is told; one flickering is not.
    var toldTable by remember { mutableStateOf<Set<String>>(emptySet()) }
    var repeat by remember { mutableIntStateOf(0) }
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val started = remember { SystemClock.elapsedRealtime() }
    LaunchedEffect(Unit) { while (true) { delay(150); clock = SystemClock.elapsedRealtime() } }

    val identities = regions.mapNotNull { region -> sceneIdentity(region)?.let { region to it } }
    // One strip often reads as two patches of print: the same medicine counts once.
    val strips = regions.filter { it.result != PrintResult.Reading }.map { region ->
        when (val result = region.result) {
            is PrintResult.Checked -> result.med.id
            is PrintResult.NameOnly -> result.med.id
            is PrintResult.Piece -> result.med.id
            else -> "track-${region.trackId}"
        }
    }.distinct().size
    fun timingOf(med: Medicine) = MedicationTimings.scene(med, state.data.medicines, state.now(), state.data.events, state.engine)
    // The medicine a reminder asked for, or the one the person asked the phone to find.
    val asked = state.scanFind
    // The medicine the phone says it is looking for: one asked for by name, or the one a reminder or a round is on.
    val askedMed = (asked ?: state.scanDoseRequest?.first)?.let { id -> state.data.medicines.firstOrNull { it.id == id } }
    val requested = state.scanDoseRequest?.first ?: asked
    // The medicines of the person using the phone now. Another person's strip is named as theirs and never led to.
    val ownIds = People.medicines(state.data).mapTo(HashSet()) { it.id }
    val targets = identities.map { (region, identity) -> ScanTarget(region.trackId, identity.med, timingOf(identity.med),
        ScanGuidance.side(region.box, imageWidth, strips), identity.nameOnly) }
    // Which strip the phone is talking about: the one held in the frame. With no strip in the frame, the medicine
    // that is wanted, and after that the nearest (see ScanGuidance.subject).
    val nearness = identities.associate { (region, _) -> region.trackId to Homing.closeness(region.box, imageWidth, imageHeight) }
    val target = ScanGuidance.subject(targets, nearness, requested, current)

    // A hand in view: where it is, against where the strips were read a moment ago.
    val covered = placed.firstOrNull { it.key == touched }
    val point = finger ?: covered?.let { (it.box.left + it.box.right) / 2 to (it.box.top + it.box.bottom) / 2 }
    val handInView = point != null && !paused && (placed.isNotEmpty() || finger != null)
    val wanted = if (!handInView) emptySet() else state.data.medicines.filter { med ->
        med.status == MedStatus.ACTIVE && med.id in ownIds && (med.id == asked || timingOf(med).kind.let { it == TimingKind.NOW || (med.id == requested && it == TimingKind.UNCONFIRMED) })
    }.mapTo(HashSet()) { it.id }
    val pointReach = (kotlin.math.hypot(imageWidth.toDouble(), imageHeight.toDouble()) * HandGuide.POINT_REACH).toInt()
    val known = if (handInView) HandGuide.cue(point!!.first, point.second, placed, wanted, ::timingOf, ray.takeIf { finger != null }, pointReach) else null
    // The finger points at print that is in view but not read: too far or too small. The phone asks to come closer
    // instead of saying nothing. Only for a finger that really points, at print that is really there.
    val unread = if (known != null || finger == null || paused) emptyList() else regions.filter { sceneIdentity(it) == null && it.result !is PrintResult.Differs }
        .map { PlacedStrip("unread-${it.trackId}", null, PlacedKind.UNKNOWN, it.box, 0, 0) }
    val cue = known ?: HandCue.Closer.takeIf { unread.isNotEmpty() && HandGuide.pointed(finger!!.first, finger.second, unread, ray, pointReach) != null }

    // Steering: the medicine is known, so the phone can say which way to move to bring it into the frame.
    // A strip that slid out of view a moment ago is steered back to from its remembered place.
    val targetBox = target?.let { t -> regions.firstOrNull { it.trackId == t.trackId }?.box }
    val liveSteer = targetBox?.let { ScanGuidance.steer(it, imageWidth, imageHeight, centred) }
    // "Move the phone left" is silenced only while a hand is being answered. A hand that is merely in the picture,
    // holding the phone's edge or resting on the table, used to silence every direction: they seemed to be gone.
    val answering = cue != null
    val ghost = if (target != null || answering) null
        else placed.firstOrNull { it.med?.id == lastWanted && (it.kind == PlacedKind.CHECKED || it.kind == PlacedKind.NAME) &&
            TableMemory.atEdge(it.box, imageWidth, imageHeight) }
    val steer = when {
        answering || paused -> null
        target != null -> liveSteer?.let { target.med to it }
        else -> ghost?.let { strip -> ScanGuidance.steer(strip.box, imageWidth, imageHeight, false)?.let { strip.med!! to it } }
    }
    // The strip in the frame is not the one that is due, and the due one is in view too: say so, and where it is.
    val elsewhere = if (target == null || liveSteer != null || answering) null else targets.firstOrNull { other ->
        other.med.id != target.med.id && (other.med.id == requested || (asked == null && other.timing.kind == TimingKind.NOW)) &&
            target.med.id != requested && (asked != null || target.timing.kind != TimingKind.NOW)
    }?.let { other -> regions.firstOrNull { it.trackId == other.trackId }?.let { other.med to HandGuide.toward(imageWidth / 2, imageHeight / 2, it.box) } }
    SideEffect {
        centred = target != null && liveSteer == null
        current = target?.med?.id
        if (target != null) lastWanted = target.med.id
        // The same direction is drawn as an arrow, and the frame turns green when the medicine is in it.
        onAim(steer?.second, target != null && liveSteer == null && !answering)
    }
    // The strip in the frame has the same ingredient as another saved medicine that was marked today. Said once, with it.
    val shared = target?.takeIf { it.med.id in ownIds && liveSteer == null && it.timing.kind != TimingKind.MARKED }
        ?.let { SameIngredient.of(it.med, state.data.medicines, state.data.events, state.now()) }?.takeIf { it.markedAt != null }
    val message = ScanGuidance.message(target, regions.any { it.result is PrintResult.Differs }, false,
        regions.any { it.result == PrintResult.Ambiguous }, clock - started < 5000, smallPrint,
        piece = regions.firstNotNullOfOrNull { (it.result as? PrintResult.Piece)?.med },
        tooClose = regions.any { it.result == PrintResult.TooClose }, hand = cue, steer = steer, elsewhere = elsewhere).let { base ->
        // Several medicines in view, none held in the frame, none of them wanted: say what lies where, once,
        // by what each is for. After that the phone leads to the nearest as before.
        val inView = targets.map { it.med.id }.toSet()
        val wantedInView = targets.any { it.med.id == requested || it.timing.kind == TimingKind.NOW }
        val tellTable = cue == null && targets.size >= 2 && liveSteer != null && !wantedInView && !toldTable.containsAll(inView)
        when {
            tellTable -> {
                val places = targets.distinctBy { it.med.id }.mapNotNull { t -> regions.firstOrNull { it.trackId == t.trackId }?.let { t.med to it.box } }
                val across = places.maxOf { it.second.left + it.second.right } - places.minOf { it.second.left + it.second.right }
                val along = places.maxOf { it.second.top + it.second.bottom } - places.minOf { it.second.top + it.second.bottom }
                val farToNear = along > across
                ScanMessage(ScanHint.OVERVIEW, farToNear = farToNear,
                    overview = places.sortedBy { if (farToNear) it.second.top + it.second.bottom else it.second.left + it.second.right }.map { it.first })
            }
            base.hint == ScanHint.FOUND -> base.copy(asked = asked != null && elsewhere != null, shared = shared,
                owner = target?.med?.takeIf { it.id !in ownIds }?.let { People.of(state.data, it) },
                expired = regions.firstOrNull { it.trackId == target?.trackId }?.expiry?.takeIf { it.passed(state.engine.local(state.now()).toLocalDate()) })
            // Nothing found yet while looking for one medicine: say which one is being looked for.
            askedMed != null && base.hint in setOf(ScanHint.START, ScanHint.SEARCHING, ScanHint.CLOSER) -> base.copy(hint = if (base.hint == ScanHint.START) ScanHint.START else ScanHint.SEARCHING, steerMed = askedMed,
                roundAt = state.round?.let { it.at + 1 to it.items.size })
            else -> base
        }
    }
    DisposableEffect(Unit) { onDispose { state.speaker.stop(); state.notePlayer.stop() } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(paused, enabled) { if (paused || !enabled) { state.speaker.stop(); state.notePlayer.stop() } }
    // One place decides what is said. It looks again several times a second, so a message that had to wait
    // for the sentence before it is said as soon as that sentence ends, if it is still true.
    LaunchedEffect(clock, repeat) {
        announcements.observe(message, clock)
        // The words on the screen follow what is true, once it has held for a moment: no flicker, no old sentence.
        if (displayed == null || announcements.steadyFor(clock) >= SHOW_MS) displayed = message
        if (paused) return@LaunchedEffect
        // "Voice off" silences the voice and nothing else. What is felt in the hand goes on: with the voice off
        // the phone used to stop ticking and buzzing as well, which is exactly when touch is all there is.
        val voiced = enabled && !talkBack
        // A sentence about a strip that is no longer in front of the camera is not finished.
        // The family's recorded message counts as speaking: nothing talks over it, and it is cut off like a sentence.
        val talking = state.speaker.isSpeaking() || state.notePlayer.isPlaying()
        if (voiced && talking && announcements.stale(clock)) {
            state.speaker.stop()
            state.notePlayer.stop()
            app.stripsense.data.Logs.i("voice: cut off, the camera is on something else")
        }
        val urgent = announcements.urgent(message)
        announcements.next(message, clock, busy = voiced && (state.speaker.isSpeaking() || state.notePlayer.isPlaying()))?.let {
            if (voiced) {
                if (urgent) state.speaker.stop()
                state.notePlayer.stop()
                // When a strip has just been named, the family's own recorded message for it follows, if there is one.
                // Not in "Touch only": the phone makes no sound there.
                val named = when {
                    it.hint == ScanHint.FOUND -> it.target?.med?.id
                    it.hand is HandCue.Yes -> (it.hand as HandCue.Yes).med.id
                    it.hand is HandCue.Info -> (it.hand as HandCue.Info).med?.id
                    else -> null
                }?.takeIf { id -> !Feel.touchOnly && state.notes.has(id) }
                state.speaker.speak(Sentences.scanGuidance(it, state.speechLanguage()), state.speechLanguage() == "hi", onDone = if (named == null) null else ({
                    scope.launch {
                        val wav = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { state.notes.load(named) }
                        if (wav != null && !state.speaker.isSpeaking()) state.notePlayer.play(wav)
                    }
                }))
            }
            if (it.hint == ScanHint.OVERVIEW) toldTable = toldTable + it.overview.map { med -> med.id }
            // A strip that has just been named is also felt: each kind of medicine has its own rhythm in the hand.
            if (it.hint == ScanHint.FOUND && cue == null) it.target?.med?.let { med -> Feel.play(context, FeelLanguage.signature(Purposes.of(med))) }
            // What was said or felt and about which medicine, so a trial can be checked afterwards. Never the print that was read.
            app.stripsense.data.Logs.i("${if (voiced) "voice" else "felt"}: ${it.hint} ${it.hand?.let { cue -> cue::class.simpleName }.orEmpty()} about ${announcements.subject(it) ?: "nothing"}, strips in view ${identities.size}")
        }
    }

    // Ticking and pulses lead to the due strip: the hand when one is in view, otherwise the phone itself.
    // They do not wait on the voice: with "Voice off" pressed the hand is still led, and only the beeps stop.
    val homed = target?.takeIf { it.med.id in ownIds && !paused && !handInView && (Homing.wanted(it, requested) || (it.med.id == asked && !it.nameOnly)) }
        ?.let { wantedTarget -> identities.firstOrNull { it.first.trackId == wantedTarget.trackId } }
    val reaching = point?.takeIf { handInView }?.let { (x, y) ->
        placed.filter { it.kind == PlacedKind.CHECKED && it.med?.id in wanted }.maxByOrNull { HandGuide.closeness(x, y, it.box, imageWidth, imageHeight) }
            ?.let { it to HandGuide.closeness(x, y, it.box, imageWidth, imageHeight) }
    }
    val homing = homed?.first?.box
    // The table as a map for the hand. The strip under the finger is felt as its own rhythm, so the hand knows which
    // medicine it is on, and then as yes or no when a dose is due. It is felt at once, before the voice has said it.
    val under: Triple<String, Medicine?, Boolean?>? = when (val c = cue) {
        is HandCue.Yes -> Triple("yes-" + c.med.id, c.med, true)
        is HandCue.No -> Triple("no-" + (c.med?.id ?: touched ?: "print"), c.med, false)
        is HandCue.Info -> c.med?.let { Triple("info-" + it.id, it, null) }
        else -> null
    }
    LaunchedEffect(under?.first) {
        val (_, med, due) = under ?: return@LaunchedEffect
        Feel.play(context, FeelLanguage.touched(med?.let { Purposes.of(it) }, due, known = med != null))
    }
    when {
        // The hand is led by feel: each way to move has its own pattern (FeelLanguage.pull).
        reaching != null -> SceneHoming(reaching.second, cue is HandCue.Yes, "hand-${reaching.first.key}",
            toward = (cue as? HandCue.Move)?.toward ?: (cue as? HandCue.No)?.toward, handLed = true, beep = enabled) { reaching.first.med?.let(onOpenRemembered) }
        homing != null -> SceneHoming(Homing.closeness(homing, imageWidth, imageHeight), Homing.locked(Homing.closeness(homing, imageWidth, imageHeight)),
            homed?.second?.med?.id, toward = liveSteer, beep = enabled) { homed?.let { onOpen(it.first, it.second) } }
        // Any medicine the voice is steering to is steered to by feel as well: the pattern of the way to move,
        // sooner as the strip nears the middle, and it stops once it is there. It was felt only
        // for the dose that is due; with nothing due the hand was told nothing.
        steer != null && targetBox != null && !handInView -> SceneHoming(Homing.closeness(targetBox, imageWidth, imageHeight), false,
            "lead-" + steer.first.id, toward = steer.second, beep = false) { }
        else -> SceneHoming(null, false, null) { }
    }
    if (paused) return
    // The words on the screen are what is true in this frame. The voice may still be finishing its last sentence;
    // an old sentence left standing here read as the phone calling one strip by another's name.
    val showing = displayed ?: message
    val shown = Sentences.scanGuidance(if (showing.hint == ScanHint.AIM) showing.copy(intro = true) else showing, state.language)
    Text(shown, color = Color.White, fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().semantics { if (talkBack && enabled) liveRegion = LiveRegionMode.Polite })
    if (enabled && !talkBack && state.speaker.isReady() && !state.speaker.hasEnglishVoice() && !state.speaker.hasHindiVoice())
        Text(Labels.get("scanVoiceUnavailable", state.language), color = Color.White, fontSize = 13.sp)
    val selected = target?.let { t -> identities.firstOrNull { it.first.trackId == t.trackId } }
    if (selected != null && message.hint == ScanHint.FOUND) Button(onClick = { onOpen(selected.first, selected.second) },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 52.dp)) { Text(Labels.get("openScanResult", state.language)) }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelButton(Labels.get("repeatShort", state.language)) {
            enabled = true
            toldTable = emptySet()
            announcements.reset()
            repeat++
        }
        PanelButton(Labels.get(if (enabled) "voiceOff" else "voiceOn", state.language)) {
            enabled = !enabled
            state.speaker.stop()
            announcements.reset()
        }
        extraButtons()
    }
}

/** What is on the screen changes only after the new state has held this long. */
private const val SHOW_MS = 300L

/** One of the four low buttons under the camera picture. Still a full finger high. */
@Composable
fun RowScope.PanelButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    // Its own white outline: on the dark panel the theme's dark outline could not be seen and the buttons looked like bare words.
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.55f else 0.2f)),
        modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
        Text(label, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
