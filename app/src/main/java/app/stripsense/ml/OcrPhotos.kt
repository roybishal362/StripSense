package app.stripsense.ml

import android.graphics.Bitmap
import app.stripsense.core.domain.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** The OCR task owns private pixels until native processing finishes, even after timeout. */
object OcrPhotos {
    suspend fun read(photo: Bitmap): List<OcrLine> {
        val owned = photo.copy(Bitmap.Config.ARGB_8888, false) ?: return emptyList()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = CompletableDeferred<List<OcrLine>>()
        try {
            recognizer.process(InputImage.fromBitmap(owned, 0))
                .addOnSuccessListener { text ->
                    result.complete(text.textBlocks.flatMap { it.lines }.map { line ->
                        val box = line.boundingBox
                        OcrLine(line.text, box?.left ?: 0, box?.top ?: 0, box?.right ?: 0, box?.bottom ?: 0, box?.height() ?: 0, line.confidence)
                    })
                }
                .addOnFailureListener { result.complete(emptyList()) }
                .addOnCompleteListener { owned.recycle(); recognizer.close() }
        } catch (_: Exception) {
            owned.recycle()
            recognizer.close()
            return emptyList()
        }
        return withTimeoutOrNull(10_000) { result.await() } ?: emptyList()
    }
}
