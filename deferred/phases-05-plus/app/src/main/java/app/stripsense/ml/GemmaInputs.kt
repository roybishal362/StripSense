package app.stripsense.ml

import android.graphics.Bitmap
import app.stripsense.core.SecondReader
import app.stripsense.core.VoicePrompt
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import java.io.ByteArrayOutputStream

/** The three message shapes, so that no screen imports LiteRT-LM (spec 04 section 2). */
object GemmaInputs {
    fun audio(wav: ByteArray): Contents = Contents.of(Content.AudioBytes(wav), Content.Text(VoicePrompt.AUDIO_TEXT))

    fun typed(text: String): Contents = Contents.of(Content.Text(text))

    /** The cropped strip as PNG, then the question. Never what ML Kit read. */
    fun picture(crop: Bitmap): Contents {
        val png = ByteArrayOutputStream().use { out ->
            crop.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        return Contents.of(Content.ImageBytes(png), Content.Text(SecondReader.QUESTION))
    }
}
