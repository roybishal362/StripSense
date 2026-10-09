package app.stripsense.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.stripsense.ml.GemmaState
import app.stripsense.ml.ModelCopy
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import java.io.File

@Composable
fun ModelPicker(lang: String, state: GemmaState, onDone: (android.net.Uri?) -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(-1f) }
    var errorKey by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        errorKey = null
        progress = 0f
    }

    LaunchedEffect(progress) {
        if (progress == 0f) {
            // Re-get the uri if we can (hard because of OpenDocument, so we actually just do the work inside the launcher usually,
            // but we need the coroutine scope here. Let's pass the uri through a state.)
        }
    }

    // Workaround: do the copy in a LaunchedEffect triggered by the uri.
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val realLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            errorKey = null
            progress = 0f
            pickedUri = uri
        }
    }

    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        val target = File(File(context.filesDir, "models"), app.stripsense.ml.Gemma.MODEL_NAME)
        val r = ModelCopy.copy(context, uri, target) { p -> progress = p }
        progress = -1f
        pickedUri = null
        when (r) {
            is ModelCopy.Result.Done -> {
                if (r.wrongSize) errorKey = "wrongModelSize"
                onDone(uri)
            }
            ModelCopy.Result.NoSpace -> errorKey = "copyNoSpace"
            ModelCopy.Result.Failed -> errorKey = "copyFail"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val text = when (state) {
            GemmaState.NoFile -> Labels.get("voiceNoFile", lang)
            GemmaState.Loading -> Labels.get("voiceLoading", lang)
            is GemmaState.Ready -> Labels.fill("chkGemmaOk", lang, "b" to state.backend)
            is GemmaState.Failed -> Labels.get("voiceFailed", lang)
        }
        Text(text, style = AppType.body, fontWeight = FontWeight.Bold)

        if (progress >= 0f) {
            Text(Labels.get("copyingModel", lang), style = AppType.body)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(AppShapes.chip), color = AppColors.brand700)
        } else {
            SideButton(Labels.get("chooseModel", lang), Modifier.fillMaxWidth()) {
                realLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            }
        }
        if (errorKey != null) {
            Text(Labels.get(errorKey!!, lang), style = AppType.label.copy(color = AppColors.stopText))
        }
    }
}
