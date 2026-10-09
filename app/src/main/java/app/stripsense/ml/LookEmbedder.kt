package app.stripsense.ml

import android.content.Context
import android.graphics.Bitmap
import app.stripsense.core.CropBox
import app.stripsense.data.Logs
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder

/**
 * MediaPipe Image Embedder with MobileNet V3 small (spec 07 section 5): a picture -> a list of numbers.
 * Made on first use; one call at a time. Call off the main thread.
 */
class LookEmbedder(private val context: Context) {

    private var embedder: ImageEmbedder? = null
    private var failed = false

    @Synchronized
    fun embed(bitmap: Bitmap): List<Float>? {
        val e = embedder ?: open() ?: return null
        return runCatching {
            val image = BitmapImageBuilder(bitmap).build()
            try { e.embed(image).embeddingResult().embeddings()[0].floatEmbedding().toList() }
            finally { image.close() }
        }.onFailure { Logs.w("embed failed: ${it.javaClass.simpleName}") }.getOrNull()
    }

    @Synchronized
    fun close() {
        embedder?.close()
        embedder = null
    }

    private fun open(): ImageEmbedder? {
        if (failed) return null
        return runCatching {
            val base = BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(Delegate.CPU).build()
            val options = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(base).setRunningMode(RunningMode.IMAGE).setL2Normalize(true).setQuantize(false).build()
            ImageEmbedder.createFromOptions(context.applicationContext, options)
        }.onFailure {
            failed = true
            Logs.w("embedder could not start: ${it.javaClass.simpleName}")
        }.getOrNull()?.also { embedder = it }
    }

    companion object {
        const val MODEL = "mobilenet_v3_small.tflite"
    }
}

/** The crop of an upright frame (spec 05 section 4, spec 04 section 7). */
fun Bitmap.crop(box: CropBox): Bitmap {
    val l = box.left.coerceIn(0, width - 1)
    val t = box.top.coerceIn(0, height - 1)
    val w = box.width.coerceIn(1, width - l)
    val h = box.height.coerceIn(1, height - t)
    return if (l == 0 && t == 0 && w == width && h == height) this else Bitmap.createBitmap(this, l, t, w, h)
}

/** Scaled down so the longest side is at most [maxSide]; never scaled up. */
fun Bitmap.fitWithin(maxSide: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return this
    val f = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * f).toInt().coerceAtLeast(1), (height * f).toInt().coerceAtLeast(1), true)
}
