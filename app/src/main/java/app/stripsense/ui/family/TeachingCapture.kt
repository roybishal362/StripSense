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

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.ml.*
import app.stripsense.ui.theme.AppColors
import java.util.concurrent.atomic.AtomicBoolean

data class TeachingSnapshot(val photo: Bitmap, val lines: List<OcrLine>, val key: ReadKey)

@Composable
fun TeachingCapture(state: AppState, showGuide: Boolean = true, onCapture: (TeachingSnapshot) -> Unit) {
    val reader = remember { OcrReader() }
    val active = remember { AtomicBoolean(true) }
    val requested = remember { AtomicBoolean(false) }
    var live by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(reader) { onDispose { active.set(false); reader.close() } }
    if (showGuide) Text(Labels.get("referenceGuide", state.language))
    Box(Modifier.fillMaxWidth().height(320.dp).background(AppColors.ink)) {
        CameraWithReader(onFrame = { proxy ->
            if (!active.get()) { proxy.close(); return@CameraWithReader }
            reader.read(proxy) { lines ->
                if (!active.get()) return@read
                live = true
                if (requested.getAndSet(false)) {
                    val frame = runCatching { proxy.uprightBitmap() }.getOrNull()
                    if (frame == null) { failed = true; return@read }
                    val useful = lines.filter { it.heightPx >= 8 && it.confidence >= 0.2f }
                    val box = LookCheck.cropBox(useful, frame.width, frame.height)
                    val raw = frame.crop(box)
                    val photo = raw.fitWithin(1280)
                    if (raw !== photo && raw !== frame) raw.recycle()
                    if (frame !== photo) frame.recycle()
                    active.set(false)
                    onCapture(TeachingSnapshot(photo, useful, StripParser.parse(useful, state.data.medicines, state.data.tuning)))
                }
            }
        }, onFailure = { failed = true })
    }
    if (failed) Text(Labels.get("cameraFailed", state.language), color = MaterialTheme.colorScheme.error)
    Button(onClick = { requested.set(true) }, enabled = live && !requested.get(), modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        Text(Labels.get("captureReference", state.language))
    }
}
