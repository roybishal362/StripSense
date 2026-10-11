package app.stripsense.core.scan

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
/**
 * Gemma as a second pair of eyes during scanning. When the fast text reader cannot make a strip out
 * (worn foil, a torn piece, a curve), the model is shown that strip alone and asked to copy its print.
 * It is never told which medicines are saved, so it cannot be led to say one of them, and its copy is
 * only one more reading for the same rules that judge every camera frame.
 */
object LiveReader {
    const val SYSTEM = "You copy printed text from medicine strips. You write only what is printed and nothing else."
    const val QUESTION = "Copy the medicine name and each strength printed on this strip, one per line, exactly as printed. " +
        "Do not add anything that is not printed. If you cannot read it, reply UNREADABLE."

    /** How long a strip must stay unresolved before the model is asked, and how long before it is asked again. */
    const val WAIT_MS = 1500L
    const val AGAIN_MS = 3000L
    const val TIMEOUT_MS = 5000L

    /** The picture's longest side. Small, because reading time grows with the picture. */
    const val PICTURE_PX = 448

    /** The reply as printed lines. A refusal, or a reply too long to be a copy of a strip, gives nothing. */
    fun lines(reply: String): List<String> {
        if (reply.contains("UNREADABLE", ignoreCase = true) || reply.length > 600) return emptyList()
        return reply.lines().map { it.trim().trim('-', '*', '•').trim() }.filter { it.length in 2..80 }.take(12)
    }

    /** The model is never asked more often than this, whatever is in view. Constant calls slow the camera and heat the phone. */
    const val REST_MS = 3000L

    /**
     * How many times the model is asked about one strip while it stays in view. A strip whose strength is too
     * small to read was being read again every three seconds for as long as it lay there, 54 readings in one
     * session, and the text reader ran at half speed beside it. Three tries, then the strip is left alone.
     */
    const val MAX_TRIES = 2

    /** Only print that is still not settled, and that could be a saved medicine, is worth the model's time. */
    fun worthAsking(result: PrintResult, promising: Boolean): Boolean = when (result) {
        is PrintResult.NameOnly, is PrintResult.Piece -> true
        PrintResult.Reading -> promising
        else -> false
    }
}
