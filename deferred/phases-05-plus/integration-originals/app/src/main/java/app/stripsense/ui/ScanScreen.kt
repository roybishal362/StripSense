package app.stripsense.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.stripsense.AppState
import app.stripsense.core.Hint
import app.stripsense.core.OcrLine
import app.stripsense.core.ScanResult
import app.stripsense.core.ScanSession
import app.stripsense.core.StripParser
import app.stripsense.core.Verdict
import app.stripsense.ml.OcrReader
import app.stripsense.ml.uprightBitmap
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppIcons
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

/** The live camera, read on every frame, until the scan session gives a final verdict (spec 06, Scan). */
@Composable
fun ScanScreen(state: AppState) {
    val context = LocalContext.current
    val language = state.language
    var granted by remember { mutableStateOf(hasCamera(context)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    // Coming back from the settings page: check again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCamera(context) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(AppColors.ink)) {
        when {
            granted -> LiveReader(state)
            asked -> CameraRefused(language)
        }
    }
}

@Composable
private fun LiveReader(state: AppState) {
    val language = state.language
    val reader = remember { OcrReader() }
    DisposableEffect(reader) { onDispose { reader.close() } }

    // One session per visit to this screen; the list and tuning are fixed for the scan.
    val meds = remember { state.data.medicines }
    val tuning = remember { state.data.tuning }
    val session = remember { ScanSession(meds, tuning) }
    val done = remember { AtomicBoolean(false) }
    val firstText = remember { longArrayOf(-1L) }

    var lines by remember { mutableStateOf<List<OcrLine>>(emptyList()) }
    var hint by remember { mutableStateOf(Hint.HOLD_INSIDE) }
    var imageW by remember { mutableIntStateOf(0) }
    var imageH by remember { mutableIntStateOf(0) }
    var fps by remember { mutableIntStateOf(0) }
    var lineCount by remember { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val showText = state.showReadText

    LaunchedEffect(reader) {
        while (true) {
            delay(1000)
            fps = reader.framesLastSecond()
            Log.i("StripSense", "scan fps=$fps lines=$lineCount")
        }
    }

    Box(Modifier.fillMaxSize()) {
        CameraWithReader(
            onFrame = { proxy ->
                if (done.get()) {
                    proxy.close()
                } else {
                    reader.read(proxy) { read ->
                        if (done.get()) return@read
                        val turned = proxy.imageInfo.rotationDegrees % 180 != 0
                        imageW = if (turned) proxy.height else proxy.width
                        imageH = if (turned) proxy.width else proxy.height
                        lineCount = read.size
                        if (state.showReadText) lines = read
                        val now = SystemClock.elapsedRealtime()
                        val frame = StripParser.read(read, meds, tuning)
                        if (firstText[0] < 0 && frame.key.letters >= tuning.textLetters) firstText[0] = now
                        when (val r = session.feed(frame.key, now, frame.smallestKeyLinePx, frame.tooSmallLines)) {
                            is ScanResult.Looking -> if (hint != r.hint) hint = r.hint
                            is ScanResult.Final -> {
                                done.set(true)
                                // The picture is kept only for a Match: the look check and the second reader use it.
                                val bitmap = if (r.verdict is Verdict.Match || state.trialMode) {
                                    runCatching { proxy.uprightBitmap() }.getOrNull()
                                } else null
                                val ms = if (firstText[0] < 0) -1L else now - firstText[0]
                                state.finishScan(r.verdict, bitmap, read, ms)
                            }
                        }
                    }
                }
            },
            onCamera = { camera = it },
        )
        val hintText = when (hint) {
            Hint.HOLD_INSIDE -> Labels.get("guideHint", language)
            Hint.CLOSER -> Labels.get("bringCloser", language)
            Hint.READING -> Labels.get("reading", language)
        }
        GuideFrame(hintText, language)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (showText) {
                ReadTextPanel(lines, fps, imageW, imageH, language, Modifier.fillMaxHeight(0.5f))
            }
            Row(
                Modifier.fillMaxWidth().background(AppColors.ink).navigationBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    },
                    shape = AppShapes.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (torchOn) AppColors.brand100 else Color.White.copy(alpha = 0.15f),
                        contentColor = if (torchOn) AppColors.brand700 else Color.White,
                    ),
                    modifier = Modifier.height(56.dp).weight(1f),
                ) {
                    Icon(AppIcons.Torch, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(Labels.get("torch", language), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { state.showReadText = !state.showReadText },
                    shape = AppShapes.button,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.height(56.dp).weight(1f),
                ) {
                    Text(Labels.get(if (showText) "hideText" else "showText", language), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** The white rounded frame (85% wide, 45% high) with the hint line under it. */
@Composable
private fun GuideFrame(hint: String, language: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.45f)
                .border(3.dp, Color.White, AppShapes.card),
        )
        Text(
            hint,
            color = Color.White,
            textAlign = TextAlign.Center,
            style = AppType.forLanguage(AppType.body, language).copy(color = Color.White, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(16.dp).background(Color.Black.copy(alpha = 0.45f), AppShapes.chip).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun CameraRefused(language: String) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(Labels.get("cameraNeeded", language), style = AppType.forLanguage(AppType.body, language).copy(color = Color.White))
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { openAppSettings(context) },
            shape = AppShapes.button,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand500),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text(Labels.get("openSettings", language), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}

fun hasCamera(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
