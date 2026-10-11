package app.stripsense.ui.family

import kotlinx.coroutines.launch
import app.stripsense.ui.shell.Labels

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.stripsense.AppState
import app.stripsense.core.domain.Medicine
import app.stripsense.ui.theme.AppColors

@Composable
fun EnrollmentScreen(state: AppState, previous: Medicine? = null, onDone: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lang = state.language
    val scope = rememberCoroutineScope()
    val controller = remember(previous?.id) { EnrollmentController(state, previous, scope) }
    var editing by remember { mutableStateOf(false) }
    var customTime by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(hasCamera(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(controller) { controller.readSavedPhoto() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCamera(context) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    BackHandler { if (!controller.saving) { if (controller.capturing && controller.views.isNotEmpty()) controller.capturing = false else onDone() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(Labels.get("scanMedicine", lang), style = MaterialTheme.typography.headlineSmall)
        if (controller.restoring) {
            Text(Labels.get("readingSavedPhoto", lang))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (controller.capturing) {
            Text(Labels.get("readLabelGuide", lang))
            controller.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            if (granted) TeachingCapture(state, showGuide = false, onCapture = controller::capture)
            else Box(Modifier.fillMaxWidth().height(320.dp).background(AppColors.ink)) { CameraRefused(lang) }
        } else {
            Image(controller.views.last().photo.asImageBitmap(), contentDescription = Labels.get("capturedLabel", lang), modifier = Modifier.fillMaxWidth().height(200.dp))
            Text(Labels.get(controller.status, lang))
            if (controller.reading) LinearProgressIndicator(Modifier.fillMaxWidth())
            fun field(key: String, value: String, update: (String) -> Unit): @Composable () -> Unit = {
                OutlinedTextField(value, { update(it); controller.confirmed = false }, label = { Text(Labels.get(key, lang)) },
                    singleLine = true, enabled = !controller.reading && !controller.saving, modifier = Modifier.fillMaxWidth())
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("medicineBrand" to controller.brand, "medicineGenerics" to controller.generics,
                        "medicineStrengths" to controller.strengths).forEach { (key, value) ->
                        Text(Labels.get(key, lang), style = MaterialTheme.typography.labelMedium)
                        Text(value.ifBlank { Labels.get("labelFieldMissing", lang) }, style = MaterialTheme.typography.titleLarge)
                    }
                    if (controller.release.isNotBlank() || controller.variants.isNotBlank()) Text(listOf(controller.release, controller.variants).filter(String::isNotBlank).joinToString(" · "))
                }
            }
            OutlinedButton(onClick = { editing = !editing }, enabled = !controller.reading && !controller.saving) { Text(Labels.get("correctLabel", lang)) }
            if (editing) {
                field("medicineBrand", controller.brand) { controller.brand = it }()
                field("medicineGenerics", controller.generics) { controller.generics = it }()
                field("medicineStrengths", controller.strengths) { controller.strengths = it }()
                field("medicineRelease", controller.release) { controller.release = it }()
                field("medicineExtra", controller.variants) { controller.variants = it }()
                field("medicineName", controller.name) { controller.name = it }()
            }
            if (controller.views.size < 3) OutlinedButton(onClick = { controller.capturing = true }, enabled = !controller.reading && !controller.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("captureOtherSide", lang)) }
            Text(Labels.get("scheduleFromPrescription", lang))
            SayTheTimes(state, enabled = !controller.reading && !controller.saving) { times -> controller.times = times.joinToString(";"); controller.confirmed = false }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("08:00", "14:00", "20:00").forEach { time ->
                    val selected = controller.times.split(';').map(String::trim).filter(String::isNotEmpty)
                    FilterChip(selected = time in selected, enabled = !controller.saving && !controller.reading,
                        onClick = { controller.times = (if (time in selected) selected - time else selected + time).sorted().joinToString(";"); controller.confirmed = false },
                        label = { Text(time) })
                }
            }
            if (controller.times.isNotBlank()) Text(controller.times.replace(';', ','))
            TextButton(onClick = { customTime = !customTime }, enabled = !controller.reading && !controller.saving) { Text(Labels.get("customPrescribedTimes", lang)) }
            if (customTime) field("medicineTimes", controller.times) { controller.times = it }()
            if (controller.times.isBlank()) Text(Labels.get("scheduleLater", lang))
            Row { Checkbox(controller.confirmed, { controller.confirmed = it }, enabled = !controller.reading && !controller.saving)
                Text(Labels.get("confirmExtractedLabel", lang), Modifier.weight(1f).padding(top = 12.dp)) }
            controller.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { controller.save { Toast.makeText(context, Labels.get("medicineEnrolled", lang), Toast.LENGTH_LONG).show(); onDone() } },
                enabled = controller.confirmed && !controller.reading && !controller.saving && controller.draft().complete(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text(Labels.get(if (controller.saving) "saving" else "saveMedicineAndPhotos", lang)) }
            if (controller.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        OutlinedButton(onClick = onDone, enabled = !controller.saving, modifier = Modifier.fillMaxWidth()) { Text(Labels.get("cancel", lang)) }
    }
}

/**
 * "Say when it is taken": the family speaks the times instead of choosing them. The phone's model writes down the
 * words, the times are taken from the words by fixed rules, and both are shown so the family can see what was
 * understood before anything is saved.
 */
@Composable
private fun SayTheTimes(state: AppState, enabled: Boolean, onTimes: (List<String>) -> Unit) {
    val lang = state.language
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { app.stripsense.ml.Recorder() }
    var recording by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<String?>(null) }
    var understood by remember { mutableStateOf(true) }
    fun start() {
        recording = true
        heard = null
        state.speaker.stop()
        scope.launch {
            val wav = recorder.record()
            recording = false
            if (wav == null) { heard = ""; understood = false; return@launch }
            working = true
            try {
                val reply = state.gemma.ask(app.stripsense.ml.CallKind.ENROLLMENT, app.stripsense.core.teach.SpokenTeach.SYSTEM,
                    app.stripsense.ml.GemmaInputs.audioAsked(wav, app.stripsense.core.teach.SpokenTeach.QUESTION), null, 20_000)
                val said = (reply as? app.stripsense.ml.GemmaReply.Text)?.let { app.stripsense.core.teach.SpokenTeach.transcript(it.text) }.orEmpty()
                val times = app.stripsense.core.teach.SpokenTeach.times(said)
                heard = said
                understood = times.isNotEmpty()
                if (times.isNotEmpty()) onTimes(times)
            } finally { working = false }
        }
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { if (it) start() }
    DisposableEffect(Unit) { onDispose { recorder.stop() } }
    val ready = state.gemma.state.collectAsState().value is app.stripsense.ml.GemmaState.Ready
    if (ready) OutlinedButton(onClick = {
        if (recording) recorder.stop()
        else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(android.Manifest.permission.RECORD_AUDIO)
    }, enabled = enabled && !working, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(Labels.get(if (recording) "sayTimesStop" else "sayTimes", lang))
    }
    if (recording || working) LinearProgressIndicator(Modifier.fillMaxWidth())
    heard?.let { said ->
        Text(if (said.isBlank()) Labels.get("sayTimesNothing", lang) else Labels.fill(if (understood) "sayTimesHeard" else "sayTimesNoTime", lang, "t" to said),
            color = if (understood) AppColors.ink2 else AppColors.dueText)
    }
}

