package app.stripsense.core.domain

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
 * Whether a picked file may become the on-phone model. On 10 October the family's 414-byte medicine list was
 * picked by mistake and replaced the 2.5 GB model without a question. A file is now taken only if it could be
 * a model at all, and never in place of a good model unless it is exactly the size the model should be.
 */
object ModelRule {
    /** No language model that can read a picture is smaller than this. */
    const val SMALLEST = 200L * 1024 * 1024

    /** [picked], [installed] in bytes; [installed] is 0 when there is no model yet. */
    fun accept(picked: Long, installed: Long, expected: Long): Boolean =
        picked >= SMALLEST && (picked == expected || installed != expected)
}
