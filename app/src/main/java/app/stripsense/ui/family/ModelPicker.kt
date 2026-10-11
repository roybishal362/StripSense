package app.stripsense.ui.family

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.ml.GemmaState
import app.stripsense.ml.ModelCopy
import app.stripsense.ui.theme.AppType
import kotlinx.coroutines.launch

@Composable
fun ModelPicker(state: AppState) {
    val context = LocalContext.current.applicationContext
    val lang = state.language
    val scope = rememberCoroutineScope()
    val engine by state.gemma.state.collectAsState()
    var copying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !copying) scope.launch {
            copying = true
            progress = 0f
            message = null
            try {
                when (val result = ModelCopy.copy(context, uri, state.gemma.modelFile) { p -> scope.launch { progress = p.coerceIn(0f, 1f) } }) {
                    is ModelCopy.Result.Done -> {
                        message = if (result.wrongSize) "wrongModelSize" else if (engine is GemmaState.Ready) "modelNextStart" else "done"
                        state.gemma.start()
                    }
                    ModelCopy.Result.NotModel -> message = "notAModel"
                    ModelCopy.Result.NoSpace -> message = "copyNoSpace"
                    ModelCopy.Result.Failed -> message = "copyFail"
                }
            } finally { copying = false }
        }
    }
    GemmaChip(state)
    Button(onClick = { runCatching { picker.launch(arrayOf("*/*")) }.onFailure { message = "copyFail" } },
        enabled = !copying && engine != GemmaState.Loading, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text(Labels.get(if (copying) "copyingModel" else "chooseModel", lang))
    }
    if (copying) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    message?.let { Text(Labels.get(it, lang), style = AppType.body) }
}

@Composable
fun GemmaChip(state: AppState) {
    val engine by state.gemma.state.collectAsState()
    val busy by state.gemma.busy.collectAsState()
    val key = if (busy) "voiceLoading" else when (engine) {
        GemmaState.NoFile -> "voiceNoFile"
        GemmaState.Loading -> "voiceLoading"
        is GemmaState.Ready -> "voiceReady"
        is GemmaState.Failed -> "voiceFailed"
    }
    val backend = (engine as? GemmaState.Ready)?.backend
    Surface(shape = app.stripsense.ui.theme.AppShapes.chip, color = app.stripsense.ui.theme.AppColors.brand100) {
        Text(Labels.get(key, state.language), Modifier.padding(12.dp), style = AppType.label)
    }
}
