package app.stripsense.ui.ask

import app.stripsense.core.domain.*

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.stripsense.AppState
import app.stripsense.ml.Recorder
import app.stripsense.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AskScreen(state: AppState) {
    val context = LocalContext.current
    val lang = state.language
    val scope = rememberCoroutineScope()
    val controller = remember { AskController(state, scope) }
    val recorder = remember { Recorder() }
    var recording by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var denied by remember { mutableStateOf(false) }
    var autoPending by remember { mutableStateOf(false) }
    fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun start() {
        if (recording || controller.working) return
        recording = true
        scope.launch {
            try {
                val wav = recorder.record()
                recording = false
                wav?.let(controller::audio)
            } finally { recording = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted && autoPending) start()
        autoPending = false
    }
    LaunchedEffect(Unit) {
        if (state.listenOnOpen) {
            state.listenOnOpen = false
            if (allowed()) start() else { autoPending = true; permission.launch(Manifest.permission.RECORD_AUDIO) }
        }
    }
    DisposableEffect(Unit) {
        onDispose { controller.close(); recorder.stop(); if (state.screen == app.stripsense.Screen.TODAY) state.speaker.stop() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.pause(); recorder.stop(); state.speaker.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.resume() }
    Column(Modifier.fillMaxSize().imePadding()) {
        // Said only while the phone cannot listen yet. When it can, there is nothing to tell.
        if (state.gemma.state.collectAsState().value !is app.stripsense.ml.GemmaState.Ready) Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { GemmaChip(state) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), reverseLayout = true) {
            items(state.bubbles.asReversed()) { bubble -> AskBubble(state, bubble) }
            if (state.bubbles.isEmpty()) item { Text(Labels.get("askEmpty", lang), color = Color.White, style = AppType.body) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(onClick = { controller.quick() }, label = { Text(Labels.get("askLeft", lang)) })
            People.medicines(state.data).forEach { med -> SuggestionChip(onClick = { controller.quick(med) }, label = { Text(Labels.fill("askDidTake", lang, "n" to nick(med, lang))) }) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (denied) {
                Text(Labels.get("micNeeded", lang), color = Color.White)
                TextButton(onClick = { openAppSettings(context) }) { Text(Labels.get("openSettings", lang), color = Color.White) }
            }
            if (controller.working) Text(Labels.get("thinking", lang), color = Color.White)
            val pulse = rememberInfiniteTransition(label = "record")
            val alpha by pulse.animateFloat(0.6f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse")
            Box(Modifier.size(80.dp).clip(CircleShape).background(AppColors.brand500.copy(alpha = if (recording) alpha else 1f))
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        if (recording) recorder.stop()
                        else if (!controller.working) {
                            if (!allowed()) permission.launch(Manifest.permission.RECORD_AUDIO)
                            else { start(); try { tryAwaitRelease() } finally { recorder.stop() } }
                        }
                    })
                }, contentAlignment = Alignment.Center) {
                Icon(AppIcons.Microphone, contentDescription = Labels.get("holdToTalk", lang), tint = Color.White, modifier = Modifier.size(38.dp))
            }
            Text(Labels.get(if (recording) "listening" else "holdToTalk", lang), color = Color.White, modifier = Modifier.padding(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(typed, { typed = it.take(1000) }, placeholder = { Text(Labels.get("askTyped", lang)) }, modifier = Modifier.weight(1f), maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = AppColors.surface, unfocusedContainerColor = AppColors.surface))
                TextButton(onClick = { val text = typed.trim(); typed = ""; controller.typed(text) }, enabled = typed.isNotBlank() && !controller.working && !recording) {
                    Text(Labels.get("send", lang), color = Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
