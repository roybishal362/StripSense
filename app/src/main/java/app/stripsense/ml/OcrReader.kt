package app.stripsense.ml

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import app.stripsense.core.OcrLine
import app.stripsense.data.Logs
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/** One offline OCR frame at a time. Frames always close, including after leaving the screen. */
class OcrReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var closed = false
    private var inFlight = false
    private var released = false
    private val finished = ArrayDeque<Long>()

    @OptIn(ExperimentalGetImage::class)
    @Synchronized
    fun read(proxy: ImageProxy, onLines: (List<OcrLine>) -> Unit) {
        if (closed || inFlight) { proxy.close(); return }
        inFlight = true
        val completed = AtomicBoolean(false)
        fun finish(lines: List<OcrLine>) {
            if (!completed.compareAndSet(false, true)) return
            try {
                synchronized(this) { if (!closed) onLines(lines) }
            } finally {
                proxy.close()
                synchronized(this) {
                    finished.addLast(System.currentTimeMillis())
                    inFlight = false
                    releaseIfIdle()
                }
            }
        }
        try {
            val media = proxy.image
            if (media == null) { finish(emptyList()); return }
            recognizer.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                .addOnSuccessListener { finish(toLines(it)) }
                .addOnFailureListener { finish(emptyList()) }
        } catch (e: Exception) {
            Logs.w("read failed: ${e.javaClass.simpleName}")
            finish(emptyList())
        }
    }

    @Synchronized
    fun framesLastSecond(now: Long = System.currentTimeMillis()): Int {
        while (finished.isNotEmpty() && now - finished.first() > 1000) finished.removeFirst()
        return finished.size
    }

    @Synchronized
    fun close() { closed = true; releaseIfIdle() }

    private fun releaseIfIdle() {
        if (closed && !inFlight && !released) { released = true; recognizer.close() }
    }

    private fun toLines(text: Text): List<OcrLine> = text.textBlocks.flatMap { block ->
        block.lines.map { line ->
            val box = line.boundingBox
            OcrLine(line.text, box?.left ?: 0, box?.top ?: 0, box?.right ?: 0, box?.bottom ?: 0, box?.height() ?: 0, line.confidence)
        }
    }
}
