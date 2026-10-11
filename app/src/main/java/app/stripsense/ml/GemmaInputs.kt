package app.stripsense.ml

import android.graphics.Bitmap
import app.stripsense.core.scan.SecondReader
import app.stripsense.core.voice.VoicePrompt
import app.stripsense.core.teach.LabelPrompt
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import java.io.ByteArrayOutputStream

/** The three message shapes, so that no screen imports LiteRT-LM (spec 04 section 2). */
object GemmaInputs {
    fun audio(wav: ByteArray): Contents = Contents.of(Content.AudioBytes(wav), Content.Text(VoicePrompt.AUDIO_TEXT))

    /** A recording with a request of our own, for writing down what was said while teaching a medicine. */
    fun audioAsked(wav: ByteArray, question: String): Contents = Contents.of(Content.AudioBytes(wav), Content.Text(question))

    fun typed(text: String): Contents = Contents.of(Content.Text(text))

    fun labels(photos: List<Bitmap>, print: String): Contents {
        val images = photos.map { photo ->
            val bytes = ByteArrayOutputStream().use { out -> photo.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
            Content.ImageBytes(bytes)
        }
        return Contents.of(*(images + Content.Text(LabelPrompt.QUESTION + "\nOCR transcription:\n" + print.take(10000))).toTypedArray())
    }

    /** One strip, small, with the request to copy its print. Never the saved list and never what ML Kit read. */
    fun reading(crop: Bitmap): Contents {
        val png = ByteArrayOutputStream().use { out ->
            crop.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        return Contents.of(Content.ImageBytes(png), Content.Text(app.stripsense.core.scan.LiveReader.QUESTION))
    }

    /** A page of a prescription with the request to copy its medicine lines. Never the saved list: the model must not be led to a name. */
    fun prescription(page: Bitmap): Contents {
        val png = ByteArrayOutputStream().use { out ->
            page.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        return Contents.of(Content.ImageBytes(png), Content.Text(app.stripsense.core.rx.RxPrompt.QUESTION))
    }

    /** The cropped strip as PNG, then the question. Never what ML Kit read. */
    fun picture(crop: Bitmap): Contents {
        val png = ByteArrayOutputStream().use { out ->
            crop.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        return Contents.of(Content.ImageBytes(png), Content.Text(SecondReader.QUESTION))
    }
}
