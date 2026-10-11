package app.stripsense.ui.family

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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import app.stripsense.data.ReferenceSaver
import app.stripsense.data.ReferenceInput
import app.stripsense.ui.theme.AppColors
import kotlinx.coroutines.*

/** Capture, review, confirm and persist are separate steps so moving print cannot discard a Save. */
@Composable
fun TeachingCamera(state: AppState, med: Medicine, onDone: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lang = state.language
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(hasCamera(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCamera(context) }
    var snapshot by remember(med.id) { mutableStateOf<TeachingSnapshot?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val count = state.data.looks.count { it.medId == med.id }
    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    DisposableEffect(snapshot) { val held = snapshot; onDispose { held?.photo?.recycle() } }
    BackHandler { if (!saving) { if (snapshot != null) snapshot = null else onDone() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onDone, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(Labels.get("back", lang)) }
        Text(nick(med, lang))
        Text(listOf(med.brand, med.generics.joinToString(" + "), Strengths.display(med.strengths), med.release.orEmpty()).filter(String::isNotBlank).joinToString(" · "))
        Text(Labels.fill("referenceCount", lang, "n" to count))
        if (!granted) Box(Modifier.fillMaxWidth().height(320.dp).background(AppColors.ink)) { CameraRefused(lang) }
        else if (snapshot == null && count < 8) TeachingCapture(state) { snapshot = it; confirmed = false; message = null }
        val captured = snapshot
        if (captured != null) {
            Image(captured.photo.asImageBitmap(), contentDescription = Labels.get("captureReference", lang), modifier = Modifier.fillMaxWidth().height(300.dp))
            val conflict = EnrollmentEvidence.wrongStrip(captured.key, med)
            val verified = EnrollmentEvidence.verified(captured.key, med, state.data.tuning)
            Text(Labels.get(if (conflict) "referenceConflict" else if (verified) "referenceVerified" else "referenceSupporting", lang),
                color = if (conflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(captured.lines.joinToString("\n") { it.text }.ifEmpty { Labels.get("sceneUnreadable", lang) })
            Row { Checkbox(confirmed, { confirmed = it }, enabled = !saving && !conflict); Text(Labels.get("confirmReference", lang), Modifier.padding(top = 12.dp).weight(1f)) }
            Button(onClick = {
                saving = true
                val owned = runCatching { captured.photo.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                if (owned == null) { saving = false; message = "teachFailed"; return@Button }
                scope.launch {
                    val ok = withContext(Dispatchers.IO + NonCancellable) {
                        ReferenceSaver(state.store, state.embedder).save(med, listOf(ReferenceInput(owned, captured.lines)), state.now(), false)
                    }
                    saving = false
                    message = if (ok) "referenceSaved" else "teachFailed"
                    if (ok) { state.noteTeachingSave(); snapshot = null; confirmed = false; onDone() }
                }
            }, enabled = confirmed && !conflict && !saving && count < 8, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(Labels.get(if (saving) "saving" else "saveStrip", lang)) }
            OutlinedButton(onClick = { snapshot = null; confirmed = false }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(Labels.get("retakeReference", lang)) }
        }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(Labels.get(it, lang)) }
        if (count >= 8) Text(Labels.get("referenceFull", lang))
    }
}
