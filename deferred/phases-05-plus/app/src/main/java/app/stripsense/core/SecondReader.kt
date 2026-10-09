package app.stripsense.core

/** Gemma's second reading of the strength (spec 04 section 7). SecondReaderMode lives in StoreModels.kt. */
object SecondReader {

    const val SYSTEM = "You read printed text on medicine strips. You answer with what is printed and nothing else."
    const val QUESTION = "Read the strength printed on this strip: the number and its unit, for example 250 mg. " +
        "If there are several, give them all separated by commas. If any digit is unclear, reply UNREADABLE."

    /** Agreed only when the reply's strengths equal the medicine's. UNREADABLE, nothing, or another set: not agreed. */
    fun agrees(reply: String, med: Medicine): Boolean {
        if (reply.contains("UNREADABLE", ignoreCase = true)) return false
        val read = StripParser.strengthsIn(reply)
        return read.isNotEmpty() && Strengths.sameStrengths(read, med.strengths)
    }

    /** The outcome word saved in a Trial and counted on the Numbers screen. */
    fun outcome(reply: String, med: Medicine): String = when {
        reply.contains("UNREADABLE", ignoreCase = true) || StripParser.strengthsIn(reply).isEmpty() -> UNREADABLE
        agrees(reply, med) -> AGREED
        else -> NOT_AGREED
    }

    const val AGREED = "agreed"
    const val NOT_AGREED = "not_agreed"
    const val UNREADABLE = "unreadable"
    const val TIMEOUT = "timeout"
    const val FAILED = "failed"
    const val SKIPPED = "skipped"
}
