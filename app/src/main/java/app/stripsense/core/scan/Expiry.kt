package app.stripsense.core.scan

import app.stripsense.core.domain.*

import java.time.LocalDate
import java.time.YearMonth

/** The expiry date as strips print it: a month and a year. The strip is good to the end of that month. */
data class ExpiryDate(val year: Int, val month: Int) {
    fun passed(today: LocalDate): Boolean = today.isAfter(YearMonth.of(year, month).atEndOfMonth())
}

/**
 * Reads the expiry date printed on a strip. Only a date that stands straight after the word for expiry is
 * taken: the same line often carries the date it was made, and "MFG. DEC.2025" must never be read as an expiry.
 */
object ExpiryText {
    private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private val KEY = Regex("(?:EXPIRY|EXPIRES|EXP|USE BEFORE|BEST BEFORE)[A-Z]{0,2}[\\s.:]*(?:DATE)?[\\s.:]*")
    private val NAMED = Regex("^(" + MONTHS.joinToString("|") + ")[A-Z]{0,6}[\\s./-]*(20\\d{2}|\\d{2})(?!\\d)")
    private val NUMBERED = Regex("^(0?[1-9]|1[0-2])\\s*[/.-]\\s*(20\\d{2}|\\d{2})(?!\\d)")

    fun read(line: String): ExpiryDate? {
        val text = line.uppercase()
        for (key in KEY.findAll(text)) {
            val after = text.substring(key.range.last + 1)
            val named = NAMED.find(after)
            val numbered = NUMBERED.find(after)
            val month = named?.let { MONTHS.indexOf(it.groupValues[1]) + 1 } ?: numbered?.groupValues?.get(1)?.toInt() ?: continue
            val year = (named ?: numbered)!!.groupValues[2].toInt().let { if (it < 100) 2000 + it else it }
            // A year far from now is a misread digit, not a date.
            if (year in 2015..2045) return ExpiryDate(year, month)
        }
        return null
    }
}

/**
 * The expiry date of one strip, gathered over frames like its other print. Small stamped digits are misread
 * often, and a wrong "this has expired" would frighten somebody for nothing: a date counts only after it was
 * read the same way in several frames and no other date was read nearly as often.
 */
class ExpiryVotes {
    private val seen = HashMap<ExpiryDate, ArrayDeque<Long>>()

    fun add(lines: List<OcrLine>, now: Long) {
        for (date in lines.mapNotNullTo(HashSet()) { ExpiryText.read(it.text) }) seen.getOrPut(date) { ArrayDeque() }.addLast(now)
        for (queue in seen.values) while (queue.isNotEmpty() && now - queue.first() > WINDOW_MS) queue.removeFirst()
        seen.values.removeIf { it.isEmpty() }
    }

    fun decided(): ExpiryDate? {
        val ranked = seen.entries.sortedByDescending { it.value.size }
        val best = ranked.firstOrNull() ?: return null
        if (best.value.size < FRAMES) return null
        if (ranked.size > 1 && ranked[1].value.size * 2 > best.value.size) return null
        return best.key
    }

    companion object {
        const val WINDOW_MS = 8000L
        const val FRAMES = 3
    }
}
