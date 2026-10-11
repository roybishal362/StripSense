package app.stripsense.ui.family

import androidx.compose.ui.unit.sp

import app.stripsense.core.domain.*
import app.stripsense.core.dose.nextUp
import app.stripsense.core.dose.todayRows
import app.stripsense.core.text.*
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppColors
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.Screen
import app.stripsense.ui.theme.AppType

@Composable
fun SettingsTab(state: AppState) {
    val lang = state.language
    var person by remember(state.data.person) { mutableStateOf(state.data.person) }
    var saved by remember { mutableStateOf(false) }
    var voices by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(10) { kotlinx.coroutines.delay(500); voices++ }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // A tool for whoever is showing the app, not for the family: it appears only after a long press on the heading above.
        if (state.presenter) { SectionTitle(Labels.get("secDemo", lang)); DemoTime(state); ExampleWeekCard(state) }
        SectionTitle(Labels.get("secPerson", lang))
        fun edit() { saved = false }
        SettingField("callNameEn", person.callEn, lang) { person = person.copy(callEn = it); edit() }
        SettingField("callNameHi", person.callHi, lang) { person = person.copy(callHi = it); edit() }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(Labels.get("womanForms", lang), Modifier.weight(1f), style = AppType.body)
            Switch(person.isWoman, { person = person.copy(isWoman = it); edit() })
        }
        SettingField("contactEn", person.contactEn, lang) { person = person.copy(contactEn = it); edit() }
        SettingField("contactHi", person.contactHi, lang) { person = person.copy(contactHi = it); edit() }
        SettingField("contactPhone", person.contactPhone, lang) { person = person.copy(contactPhone = it.filter { c -> c.isDigit() || c == '+' }.take(20)); edit() }
        OutlinedTextField(
            value = person.pin, onValueChange = { person = person.copy(pin = it.filter { c -> c in '0'..'9' }.take(4)); edit() },
            label = { Text(Labels.get("pinField", lang)) }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
            isError = person.pin.length != 4, singleLine = true,
        )
        Button(onClick = { state.store.setPerson(person); saved = true }, enabled = person.pin.length == 4, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(Labels.get(if (saved) "done" else "save", lang))
        }
        // The phone hears the tablet come out and marks the dose by itself. On unless the family turns it off.
        SectionTitle(Labels.get("hearTitle", lang))
        Column(Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(Labels.get("hearTitle", lang), Modifier.weight(1f), style = AppType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                Switch(state.data.hearTablet, { state.store.setHearTablet(it) })
            }
            Text(Labels.get("hearBody", lang), style = AppType.label)
            if (state.data.hearTablet && state.canHearTablet()) HearTry(state)
            if (state.data.hearTablet && !state.canHearTablet()) {
                val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
                OutlinedButton(onClick = { ask.launch(android.Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("hearNeedsMic", lang)) }
            }
        }
        FeelCard(state)
        SectionTitle(Labels.get("secVoice", lang))
        key(voices) {
            if (!state.speaker.hasHindiVoice()) Text(Labels.get("noHindiVoice", lang), style = AppType.caption)
        }
        // Voices belong to the phone, not to this app: this opens the phone's own page for adding one.
        val voiceContext = androidx.compose.ui.platform.LocalContext.current
        OutlinedButton(onClick = {
            runCatching { voiceContext.startActivity(android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { runCatching { voiceContext.startActivity(android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("installVoice", lang)) }
        // Asked for only on a phone that was set up without the helper it needs. A family never sees it otherwise.
        if (state.gemma.state.collectAsState().value !is app.stripsense.ml.GemmaState.Ready) { SetupHeading("setupAssistant", "setupAssistantNote", lang); ModelPicker(state) }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun SettingField(key: String, value: String, lang: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.take(120)) }, label = { Text(Labels.get(key, lang)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

/**
 * What the phone says through the hand, to be felt once so it is known: the two ends, yes, no, and a medicine's
 * own rhythm. If "top" is felt at the bottom, one press swaps the two motors.
 */
@Composable
private fun FeelCard(state: AppState) {
    val lang = state.language
    val context = androidx.compose.ui.platform.LocalContext.current
    if (Feel.motors(context) == 0) return
    val two = Feel.motors(context) >= 2
    SectionTitle(Labels.get("feelTitle", lang))
    Column(Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Labels.get(if (two) "feelBody" else "feelBodyOne", lang), style = AppType.label)
        @Composable fun Try(key: String, felt: app.stripsense.core.guide.Felt, modifier: Modifier) =
            OutlinedButton(onClick = { Feel.play(context, felt) }, modifier = modifier.heightIn(min = 52.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text(Labels.get(key, lang), maxLines = 1) }
        val language = app.stripsense.core.guide.FeelLanguage
        // The four ways to move the phone, exactly as they are felt during a scan.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Try("feelLeft", language.pull(app.stripsense.core.guide.Toward.LEFT, 1f), Modifier.weight(1f))
            Try("feelRight", language.pull(app.stripsense.core.guide.Toward.RIGHT, 1f), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Try("feelForward", language.pull(app.stripsense.core.guide.Toward.AWAY, 1f), Modifier.weight(1f))
            Try("feelBack", language.pull(app.stripsense.core.guide.Toward.CLOSER, 1f), Modifier.weight(1f))
        }
        if (two) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // One motor alone, for finding out which end is which.
            Try("feelTop", app.stripsense.core.guide.Felt(app.stripsense.core.guide.Buzz.beats(255, 120, 60, 60, 60), null), Modifier.weight(1f))
            Try("feelBottom", app.stripsense.core.guide.Felt(null, app.stripsense.core.guide.Buzz.beats(255, 120, 60, 60, 60)), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Try("feelYes", language.yes(), Modifier.weight(1f))
            Try("feelNo", language.no(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Try("feelBp", language.signature(app.stripsense.core.teach.PurposeKind.BP), Modifier.weight(1f))
            Try("feelSugar", language.signature(app.stripsense.core.teach.PurposeKind.SUGAR), Modifier.weight(1f))
            Try("feelFever", language.signature(app.stripsense.core.teach.PurposeKind.FEVER_PAIN), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Try("feelQuestion", language.question(), Modifier.weight(1f))
            Try("feelDone", language.done(), Modifier.weight(1f))
        }
        if (two) TextButton(onClick = { state.store.setTopMotor(if (state.data.topMotor == 1) 0 else 1) }) { Text(Labels.get("feelSwap", lang)) }
        // For someone who can neither see the screen nor hear the phone.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(Labels.get("touchOnlyTitle", lang), Modifier.weight(1f), style = AppType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            Switch(state.data.touchOnly, { state.store.setTouchOnly(it) })
        }
        Text(Labels.get("touchOnlyBody", lang), style = AppType.label)
    }
}

/**
 * Trying the listening once, away from any dose: the family pushes one tablet out near the phone, sees that the
 * phone heard it, and the phone keeps how loud that was for this person's strips. Nothing is marked by it.
 */
@Composable
private fun HearTry(state: AppState) {
    val lang = state.language
    val context = androidx.compose.ui.platform.LocalContext.current
    val listener = remember { app.stripsense.ml.TabletListener(context.applicationContext) }
    var trying by remember { mutableStateOf(false) }
    // null: not tried yet. true: heard. false: the time ran out with nothing heard.
    var heard by remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(trying) {
        if (trying && !listener.start(app.stripsense.core.hear.PopWatch.LOWEST_LEVEL, { Feel.moving() }) { pop ->
                state.store.learnTabletSound(pop.level)
                buzz(context)
                heard = true
                trying = false
            }) trying = false
        onDispose { listener.stop() }
    }
    LaunchedEffect(trying) { if (trying) { kotlinx.coroutines.delay(TRY_MS); if (trying) { heard = false; trying = false } } }
    if (trying) ListeningRow(Labels.get(if (listener.noisy) "hearTryNoisy" else "hearTryNow", lang)) { listener.loudness }
    else heard?.let { ListeningRow(Labels.get(if (it) "hearTryHeard" else "hearTryNothing", lang), null) }
    OutlinedButton(onClick = { heard = null; trying = !trying }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(Labels.get(if (trying) "hearTryStop" else if (heard == null) "hearTry" else "hearTryAgain", lang))
    }
}

private const val TRY_MS = 30_000L

/**
 * For showing the app on the day it was set up: a week cannot be waited for, so the six days before today are
 * filled with an example of one. It is named as an example wherever it shows, and one press takes it out again.
 */
@Composable
private fun ExampleWeekCard(state: AppState) {
    val lang = state.language
    val shown = state.data.events.any { it.example }
    Column(Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Labels.get("exampleWeekTitle", lang), style = AppType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        Text(Labels.get(if (shown) "exampleWeekOn" else "exampleWeekOff", lang), style = AppType.label)
        androidx.compose.material3.Button(onClick = { state.fillExampleWeek() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get(if (shown) "exampleWeekAgain" else "exampleWeekFill", lang)) }
        if (shown) OutlinedButton(onClick = { state.store.removeExampleWeek() }, modifier = Modifier.fillMaxWidth()) { Text(Labels.get("exampleWeekRemove", lang)) }
    }
}

/**
 * For showing the app to someone: one tap makes the next saved dose due now, so "it is time for this one" and the
 * guidance to the due strip can be shown without waiting for the hour. It only moves the app's own clock.
 */
@Composable
private fun DemoTime(state: AppState) {
    val lang = state.language
    @Suppress("UNUSED_VARIABLE") val tick = state.minuteTick
    val offset = state.data.clockOffsetMs
    val now = state.engine.local(state.now())
    Column(Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Labels.get("demoTimeTitle", lang), style = AppType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        Text(if (offset == 0L) Labels.get("demoTimeOff", lang) else Labels.fill("demoTimeOn", lang, "t" to Words.timeShown(now.toLocalTime(), lang)), style = AppType.label)
        androidx.compose.material3.Button(onClick = {
            // One minute past the next saved dose time, today or tomorrow, counted from the real time.
            val real = java.time.LocalDateTime.now()
            val times = state.data.medicines.filter { it.status == MedStatus.ACTIVE }.flatMap { it.times }
                .mapNotNull { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }.distinct().sorted()
            val shown = state.engine.local(state.now())
            val next = times.map { shown.toLocalDate().atTime(it) }.firstOrNull { it.isAfter(shown) }
                ?: times.firstOrNull()?.let { shown.toLocalDate().plusDays(1).atTime(it) }
            if (next != null) state.store.setClockOffset(java.time.Duration.between(real, next.plusMinutes(1)).toMillis())
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("demoTimeNext", lang)) }
        // The alarm for the dose that is due by the app's clock, at once: a real alarm rings by the real time and
        // cannot be waited for in front of an audience. It opens the same screen the alarm opens.
        val rows = state.engine.todayRows(People.medicines(state.data), state.now(), state.data.events)
        state.engine.nextUp(rows, state.now())?.takeIf { it.due }?.let { due ->
            OutlinedButton(onClick = { state.go(Screen.TODAY); state.reminderRequest = due.med.id to due.slot.key }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(Labels.get("demoAlarmNow", lang))
            }
        }
        if (offset != 0L) OutlinedButton(onClick = { state.store.setClockOffset(0) }, modifier = Modifier.fillMaxWidth()) { Text(Labels.get("demoTimeReal", lang)) }
    }
}
