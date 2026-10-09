package app.stripsense.ml

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import app.stripsense.core.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/** ML Kit text recognition (Latin model inside the APK, works offline). One frame at a time. */
class OcrReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val inFlight = AtomicBoolean(false)

    // Finish times of recent frames, for frames per second. Touched only on the main thread.
    private val finished = ArrayDeque<Long>()

    /**
     * Reads one camera frame. onLines runs on the main thread while the frame is still open;
     * the frame is closed right after it returns. A frame that arrives while one is in flight is dropped.
     */
    @OptIn(ExperimentalGetImage::class)
    fun read(proxy: ImageProxy, onLines: (List<OcrLine>) -> Unit) {
        if (!inFlight.compareAndSet(false, true)) {
            proxy.close()
            return
        }
        val media = proxy.image
        if (media == null) {
            inFlight.set(false)
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { text -> finish(proxy) { onLines(toLines(text)) } }
            .addOnFailureListener { finish(proxy) { onLines(emptyList()) } }
    }

    /** Frames ML Kit finished in the last second. Call on the main thread. */
    fun framesLastSecond(now: Long = System.currentTimeMillis()): Int {
        while (finished.isNotEmpty() && now - finished.first() > 1000) finished.removeFirst()
        return finished.size
    }

    fun close() = recognizer.close()

    private inline fun finish(proxy: ImageProxy, block: () -> Unit) {
        try {
            block()
        } finally {
            proxy.close()
            finished.addLast(System.currentTimeMillis())
            inFlight.set(false)
        }
    }

    private fun toLines(text: Text): List<OcrLine> = text.textBlocks.flatMap { block ->
        block.lines.map { line ->
            val box = line.boundingBox
            OcrLine(line.text, box?.left ?: 0, box?.top ?: 0, box?.right ?: 0, box?.bottom ?: 0, box?.height() ?: 0, line.confidence)
        }
    }
}
