package app.stripsense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.core.Decider
import app.stripsense.core.Look
import app.stripsense.core.LookCheck
import app.stripsense.core.Medicine
import app.stripsense.core.OcrLine
import app.stripsense.core.StripParser
import app.stripsense.core.Verdict
import app.stripsense.data.FilesIO
import app.stripsense.ml.OcrReader
import app.stripsense.ml.crop
import app.stripsense.ml.uprightBitmap
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TeachingCamera(state: AppState, med: Medicine, onDone: () -> Unit) {
    val lang = state.language
    val reader = remember { OcrReader() }
    DisposableEffect(reader) { onDispose { reader.close() } }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var lines by remember { mutableStateOf<List<OcrLine>>(emptyList()) }
    var readyToSave by remember { mutableStateOf(false) }
    var different by remember { mutableStateOf<Verdict.Different?>(null) }
    var saving by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(AppColors.ink)) {
        CameraWithReader(onFrame = { proxy ->
            if (saving) {
                proxy.close()
                return@CameraWithReader
            }
            reader.read(proxy) { read ->
                if (saving) return@read
                lines = read
                val key = StripParser.parse(read, listOf(med), state.data.tuning)
                val v = Decider.decide(key, listOf(med), state.data.tuning)
                readyToSave = v is Verdict.Match && v.med.id == med.id
                different = v as? Verdict.Different
                if (readyToSave) {
                    val frame = runCatching { proxy.uprightBitmap() }.getOrNull()
                    if (frame != null) {
                        saving = true
                        CoroutineScope(Dispatchers.Main).launch {
                            val cropBox = LookCheck.cropBox(read, frame.width, frame.height)
                            val cropped = withContext(Dispatchers.Default) { runCatching { frame.crop(cropBox) }.getOrNull() }
                            val vector = cropped?.let { withContext(Dispatchers.Default) { state.embedder.embed(it) } }
                            if (cropped != null && vector != null) {
                                val id = UUID.randomUUID().toString()
                                val file = withContext(Dispatchers.IO) { FilesIO.savePhoto(state.store.looksDir, "$id.jpg", cropped) }
                                if (file != null) {
                                    state.store.addLook(Look(id, med.id, vector, file, read.map { it.text }, state.now()))
                                    onDone()
                                    return@launch
                                }
                            }
                            saving = false
                        }
                    }
                }
            }
        })
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.weight(1f).fillMaxHeight().background(Color.Black.copy(alpha = 0.5f)).padding(16.dp)) {
                    Text(Labels.get("onTheList", lang), color = AppColors.brand100, style = AppType.caption)
                    Text(med.brand, color = Color.White, style = AppType.body)
                    Text(med.generics.joinToString(" + "), color = Color.White, style = AppType.caption)
                    Text(app.stripsense.core.Strengths.display(med.strengths), color = Color.White, style = AppType.body)
                }
                Column(Modifier.weight(1f).fillMaxHeight().background(Color.Black.copy(alpha = 0.7f)).padding(16.dp)) {
                    Text(Labels.get("readNow", lang), color = AppColors.brand100, style = AppType.caption)
                    lines.take(8).forEach { Text(it.text, color = Color.White, style = AppType.caption) }
                }
            }
            Column(Modifier.fillMaxWidth().background(AppColors.surface).padding(16.dp)) {
                if (different != null) {
                    Text(Labels.fill("cantTeach", lang, "printed" to different!!.printed, "listpart" to different!!.listPart), color = AppColors.stopText, style = AppType.forLanguage(AppType.body, lang))
                    Spacer(Modifier.height(8.dp))
                }
                WideButton(Labels.get("saveStrip", lang)) { /* saved automatically when readyToSave is true */ }
                Spacer(Modifier.height(8.dp))
                SideButton(Labels.get("cancel", lang), Modifier.fillMaxWidth()) { onDone() }
            }
        }
    }
}
