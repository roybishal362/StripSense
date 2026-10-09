package app.stripsense.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/** Live preview plus one analysis stream (spec 07 section 3). The receiver of onFrame must close each frame. */
@Composable
fun CameraWithReader(onFrame: (ImageProxy) -> Unit, onCamera: (Camera) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }

    DisposableEffect(owner) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val selector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy -> onFrame(proxy) }
            p.unbindAll()
            onCamera(p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}
