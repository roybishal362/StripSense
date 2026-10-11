package app.stripsense.ui.family

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.text.*
import app.stripsense.reminders.AlarmRinger
import app.stripsense.reminders.ReminderAlarms
import app.stripsense.reminders.ReminderNotifications
import app.stripsense.ui.scan.medColour
import app.stripsense.ui.shell.Labels
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppIcons
import app.stripsense.ui.today.nick
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Brand600 = Color(0xFF0C7C5D)
private val Brand900 = Color(0xFF05372A)
private val Hairline = Color(0x140D1714)

/**
 * The reminder, drawn as the prototype drew it (finale/_not-for-finale/prototype, js/ui-remind.js and the
 * ".alarm" styles): a green top with a ringing bell, the time large, "Night medicines" and one line to the
 * person; a white card with each medicine of this time and "Which one now?"; then "Remind me in 10 minutes".
 * It rings like an alarm clock for a minute. Nothing is marked here without the person's own press, and
 * "not sure" stays its own answer.
 */
@Composable
fun ReminderPanel(state: AppState) {
    val request = state.reminderRequest ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = state.language
    val med = state.data.medicines.firstOrNull { it.id == request.first }
    val slot = med?.let { ReminderPlan.slot(it, request.second) }
    var busy by remember(request) { mutableStateOf(false) }
    var failed by remember(request) { mutableStateOf(false) }
    val ringer = remember { AlarmRinger(context.applicationContext) }
    var ringing by remember(request) { mutableStateOf(true) }

    // Every medicine of this same time, the one the alarm came for first. Marked ones stay in the list with their mark.
    val rows = if (med == null || slot == null) emptyList() else (listOf(med to slot) + state.data.medicines.filter { it.id != med.id && it.status == MedStatus.ACTIVE && it.personId == med.personId }
        .mapNotNull { other -> state.engine.slotsFor(other, slot.date).firstOrNull { it.time == slot.time }?.let { other to it } })
    fun stateOf(m: Medicine, s: DoseSlot) = state.engine.slotState(m.id, s.key, state.data.events)
    val open = rows.filter { (m, s) -> stateOf(m, s) == SlotState.UNMARKED }
    val allDone = rows.isNotEmpty() && open.isEmpty()
    // A dose can be answered only inside its own time. Opened before it (from the list, or by a test), the screen
    // shows the dose and leads to the strip, and offers no answer that could only be refused.
    val answerable = med != null && slot != null && System.currentTimeMillis() in state.engine.window(slot, med)
    val spoken = if (med == null || slot == null) "" else Sentences.reminderRound(open.map { it.first }.ifEmpty { listOf(med) }, slot.time, People.of(state.data, med).call(state.speechLanguage()), state.speechLanguage())

    fun quiet() { ringing = false; ringer.stop(); state.speaker.stop() }
    DisposableEffect(request) {
        // The notification's own sound stops here; this screen rings from now on.
        ReminderNotifications.cancel(context, request.first, request.second)
        ringer.felt = app.stripsense.core.guide.FeelLanguage.alarm(med?.let { app.stripsense.core.teach.Purposes.of(it) })
        ringer.start(quiet = { state.speaker.isSpeaking() }, onStop = { ringing = false })
        onDispose { ringer.stop() }
    }
    LaunchedEffect(request) {
        delay(1500)
        if (ringing && spoken.isNotEmpty()) state.speaker.speak(spoken, state.speechLanguage() == "hi")
    }
    // Picking the phone up from the table answers the alarm: the ringing stops and the camera opens on the first strip,
    // with no button to find. Nothing is marked by it.
    val toShow by rememberUpdatedState(open.filter { it.first.type == MedType.CAMERA }.map { it.first.id to it.second.key })
    val mayLead by rememberUpdatedState(!busy)
    DisposableEffect(request) {
        val sensors = context.getSystemService(android.hardware.SensorManager::class.java)
        val pick = app.stripsense.core.guide.PickUp()
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                if (pick.reading(event.values[0], event.values[1], event.values[2], android.os.SystemClock.elapsedRealtime()) && mayLead && toShow.isNotEmpty()) {
                    quiet()
                    app.stripsense.data.Logs.i("alarm: picked up, the camera opens on ${toShow.size}")
                    state.startRound(toShow)
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sensors?.unregisterListener(listener) }
    }
    fun save(targets: List<Pair<Medicine, DoseSlot>>, kind: EventKind?, thenClose: Boolean) {
        quiet()
        busy = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                targets.map { (m, s) ->
                    (if (kind == null) state.store.saveReminderConfirmation(m.id, s.key, System.currentTimeMillis(), state.newId())
                    else state.store.saveReminderConfirmation(m.id, s.key, System.currentTimeMillis(), state.newId(), kind)).also {
                        if (it) ReminderNotifications.cancel(context, m.id, s.key)
                    }
                }.all { it }.also { ReminderAlarms.reschedule(context) }
            }
            failed = !saved
            busy = false
            if (saved && thenClose) state.reminderRequest = null
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.canvas).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { quiet() }
        .verticalScroll(rememberScrollState())) {
        // ---- the green top
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)).drawBehind {
            drawRect(Brush.linearGradient(listOf(Brand600, Brand900), start = Offset(size.width * 0.44f, 0f), end = Offset(size.width * 0.56f, size.height)))
            drawRect(Brush.radialGradient(listOf(Color(0x577FD3B7), Color.Transparent), center = Offset(size.width * 0.88f, 0f), radius = size.width * 0.9f))
            drawCircle(Color.White.copy(alpha = 0.05f), radius = 100.dp.toPx(), center = Offset(50.dp.toPx(), size.height + 10.dp.toPx()), style = Stroke(40.dp.toPx()))
        }.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Bell(ringing)
            if (slot == null) {
                Text(Labels.get("reminderExpired", lang), color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
            } else {
                Text(Words.timeShown(slot.time, lang), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp, modifier = Modifier.padding(top = 16.dp))
                Text(Sentences.reminderTitle(slot.time, lang), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                Text(Sentences.reminderLine(med?.let { People.of(state.data, it).call(lang) }.orEmpty(), slot.time, open.any { it.first.type == MedType.CAMERA }, lang), color = Color.White.copy(alpha = 0.93f),
                    fontSize = 16.5.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 340.dp))
            }
            if (ringing) Row(Modifier.padding(top = 16.dp).heightIn(min = 44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.26f), CircleShape).clickable(role = Role.Button) { quiet() }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(Labels.get("alarmStopSound", lang), color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        // ---- the body
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rows.isNotEmpty()) Column(Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .clip(RoundedCornerShape(24.dp)).background(AppColors.surface).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)) {
                rows.forEachIndexed { i, (m, s) ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(lerp(Color.White, medColour(m), 0.15f)), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(medColour(m)))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(nick(m, lang), color = AppColors.ink, fontSize = 17.5.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                            // The strength is not said twice when the name the family gave already ends with it.
                            val sub = listOf(Strengths.display(m.strengths).takeIf { !nick(m, lang).contains(it) }.orEmpty(), if (lang == "hi") m.noteHi.ifEmpty { m.noteEn } else m.noteEn).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, color = AppColors.ink3, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        val mark = state.engine.markOf(m.id, s.key, state.data.events)
                        when {
                            mark != null -> Row(Modifier.height(30.dp).clip(CircleShape).background(AppColors.okBg).padding(start = 8.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Check, null, tint = AppColors.okText, modifier = Modifier.size(16.dp))
                                Text(Labels.fill("alarmMarkedAt", lang, "t" to Words.timeShown(state.engine.local(mark.atMillis).toLocalTime(), lang)), color = AppColors.okText,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 5.dp))
                            }
                            stateOf(m, s) == SlotState.NOT_SURE -> Text(Labels.get("reminderNotSure", lang), color = AppColors.unsureText, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(CircleShape).background(AppColors.unsureBg).padding(horizontal = 10.dp, vertical = 6.dp))
                            // A medicine with no strip to show (drops, syrup) is marked with its own button, as on Today.
                            m.type == MedType.BUTTON -> Text(Labels.get("alarmMark", lang), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(AppColors.brand700).clickable(enabled = !busy, role = Role.Button) { save(listOf(m to s), null, false) }
                                    .padding(horizontal = 18.dp, vertical = 11.dp))
                            // A strip is checked by the camera: this leads to it.
                            else -> Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFEEF1F0)).clickable(enabled = !busy, role = Role.Button, onClickLabel = Labels.get("alarmWhichOne", lang)) {
                                quiet(); state.findReminderMedicine(m.id, s.key)
                            }, contentAlignment = Alignment.Center) { Icon(AppIcons.Camera, Labels.get("alarmWhichOne", lang), tint = AppColors.ink2, modifier = Modifier.size(22.dp)) }
                        }
                    }
                }
                open.firstOrNull { it.first.type == MedType.CAMERA }?.let { (m, s) ->
                    Row(Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(min = 64.dp).shadow(6.dp, RoundedCornerShape(20.dp), spotColor = AppColors.brand700).clip(RoundedCornerShape(20.dp))
                        .background(AppColors.brand700).clickable(enabled = !busy, role = Role.Button) {
                            // One press takes the person through every strip of this time, one after another.
                            quiet()
                            state.startRound(open.filter { it.first.type == MedType.CAMERA }.map { it.first.id to it.second.key })
                        },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(AppIcons.Camera, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Text(Labels.get("alarmWhichOne", lang), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            if (failed && answerable) Text(Labels.get(if (state.store.writeFailed.value) "reminderSaveFailed" else "reminderExpired", lang), color = AppColors.stopText, fontSize = 15.sp)
            if (allDone) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.okBg).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(AppColors.okText), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, tint = Color.White) }
                    Text(Labels.get("alarmAllMarked", lang), color = Color(0xFF075A3E), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                }
                SoftButton(Labels.get("close", lang), dark = true, enabled = !busy) { quiet(); state.reminderRequest = null }
            } else {
                if (slot != null && med != null && ReminderPlan.canSnooze(slot, med, System.currentTimeMillis(), state.engine)) SoftButton(Labels.get("alarmSnooze", lang), clock = true, enabled = !busy) {
                    quiet()
                    busy = true
                    scope.launch {
                        // Every medicine of this time is asked for again together.
                        val saved = withContext(Dispatchers.IO) { open.ifEmpty { listOf(med to slot) }.map { (m, s) -> ReminderAlarms.snooze(context, m.id, s.key) }.any { it } }
                        if (saved) state.reminderRequest = null else failed = true
                        busy = false
                    }
                }
                // The two answers that need no camera, kept small: said already, or not sure. "Not sure" ends the follow-ups
                // and shows on Today for the family, without a taken mark.
                if (open.isNotEmpty() && answerable) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LinkText(Labels.get("reportedTaken", lang), !busy) { save(open, null, true) }
                    LinkText(Labels.get("reminderNotSure", lang), !busy) { save(open, EventKind.NOT_SURE, true) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LinkText(Labels.get("help", lang), true) { quiet(); state.reminderRequest = null; state.openHelp(false) }
                    LinkText(Labels.get("close", lang), !busy) { quiet(); state.reminderRequest = null }
                }
            }
        }
    }
}

/** The bell in its round badge. While the alarm rings it swings and two rings spread out from it. */
@Composable
private fun Bell(ringing: Boolean) {
    val motion = rememberInfiniteTransition(label = "bell")
    val swing by motion.animateFloat(-14f, 14f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "swing")
    val spread by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "spread")
    Box(contentAlignment = Alignment.Center) {
        if (ringing) for (late in listOf(0f, 0.5f)) {
            val at = (spread + late) % 1f
            Box(Modifier.size(86.dp).scale(1f + at * 0.5f).border(2.dp, Color.White.copy(alpha = 0.45f * (1f - at)), CircleShape))
        }
        Box(Modifier.size(78.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).border(1.dp, Color.White.copy(alpha = 0.26f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Notifications, null, tint = Color.White, modifier = Modifier.size(38.dp).rotate(if (ringing) swing else 0f))
        }
    }
}

/** The wide white button under the card, or the dark one that closes a finished reminder. */
@Composable
private fun SoftButton(label: String, dark: Boolean = false, clock: Boolean = false, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).shadow(3.dp, RoundedCornerShape(20.dp), spotColor = Color(0x22000000)).clip(RoundedCornerShape(20.dp))
        .background(if (dark) AppColors.ink else AppColors.surface).border(1.dp, Hairline, RoundedCornerShape(20.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (clock) Icon(AppIcons.Clock, null, tint = AppColors.ink, modifier = Modifier.size(24.dp))
        Text(label, color = if (dark) Color.White else AppColors.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = if (clock) 10.dp else 0.dp))
    }
}

@Composable
private fun LinkText(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(label, color = AppColors.ink2, fontSize = 15.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline,
        modifier = Modifier.heightIn(min = 44.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp))
}
