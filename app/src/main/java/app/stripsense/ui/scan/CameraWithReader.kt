package app.stripsense.ui.scan

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.stripsense.data.Logs
import java.util.concurrent.Executors

/** Preview and one analysis stream. The receiver closes each frame. */
// Every camera screen tells Feel that the camera is open: the phone holds vibrations back while it is (see Feel.kt).
@Composable
private fun CameraOpenForFeel() {
    androidx.compose.runtime.DisposableEffect(Unit) {
        app.stripsense.ui.shell.Feel.camera(true)
        onDispose { app.stripsense.ui.shell.Feel.camera(false) }
    }
}

@Composable
fun CameraWithReader(onFrame: (ImageProxy) -> Unit, onCamera: (Camera) -> Unit = {}, onFailure: () -> Unit = {}) {
    CameraOpenForFeel()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val frameCallback by rememberUpdatedState(onFrame)
    val cameraCallback by rememberUpdatedState(onCamera)
    val failureCallback by rememberUpdatedState(onFailure)
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                val p = future.get()
                provider = p
                val selector = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
                val preview = Preview.Builder().setResolutionSelector(selector).build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    try { frameCallback(proxy) }
                    catch (e: Exception) { proxy.close(); Logs.w("camera frame failed: ${e.javaClass.simpleName}") }
                }
                p.unbindAll()
                cameraCallback(p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
            }.onFailure {
                Logs.w("camera start failed: ${it.javaClass.simpleName}")
                failureCallback()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; provider?.unbindAll(); executor.shutdown() }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}
