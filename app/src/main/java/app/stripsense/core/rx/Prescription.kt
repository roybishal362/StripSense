package app.stripsense.core.rx

import app.stripsense.core.domain.*
import app.stripsense.core.scan.*
import app.stripsense.core.text.*

/** When a prescription says to take a medicine. [asNeeded]: "SOS", only when required, so no fixed times. */
data class RxDose(val morning: Boolean, val afternoon: Boolean, val night: Boolean, val asNeeded: Boolean = false, val food: RxFood = RxFood.UNSAID) {
    /** The clock times this stands for, with the family's usual hours. Empty for "as needed". */
    fun times(): List<String> = if (asNeeded) emptyList() else listOfNotNull(
        RxDoses.MORNING.takeIf { morning }, RxDoses.AFTERNOON.takeIf { afternoon }, RxDoses.NIGHT.takeIf { night })
}

enum class RxFood { BEFORE, AFTER, UNSAID }

/**
 * One line of a prescription and what the phone makes of it.
 * [match]: the one saved medicine this line is about. [candidates]: the saved medicines it could be, when the
 * line cannot tell them apart or nothing is close enough to be sure. [conflict]: the strength written on the
 * prescription and the strength of the saved strip, when they differ.
 */
data class RxLine(val text: String, val match: Medicine?, val candidates: List<Medicine>, val dose: RxDose?, val conflict: Pair<String, String>? = null)

/** How often and when, as doctors in India write it: "1-0-1", "BD", "TDS", "HS", "SOS", "after food". */
object RxDoses {
    const val MORNING = "08:00"
    const val AFTERNOON = "14:00"
    const val NIGHT = "20:00"

    private val PATTERN = Regex("(?<![\\d/])([0-3]|1/2|½)\\s*[-–—_]\\s*([0-3]|1/2|½)\\s*[-–—_]\\s*([0-3]|1/2|½)(?![\\d/])")

    fun read(line: String): RxDose? {
        val text = " " + line.uppercase().replace(Regex("[.,;:()]"), " ").replace(Regex("\\s+"), " ") + " "
        fun has(vararg words: String) = words.any { text.contains(" $it ") }
        val food = when {
            has("AC", "BEFORE FOOD", "BEFORE MEALS", "BEFORE MEAL", "EMPTY STOMACH", "BF") -> RxFood.BEFORE
            has("PC", "AFTER FOOD", "AFTER MEALS", "AFTER MEAL", "AF") -> RxFood.AFTER
            else -> RxFood.UNSAID
        }
        PATTERN.find(line)?.let { m ->
            val (a, b, c) = m.destructured
            fun some(part: String) = part != "0"
            if (!some(a) && !some(b) && !some(c)) return null
            return RxDose(some(a), some(b), some(c), food = food)
        }
        return when {
            has("SOS", "PRN", "AS NEEDED", "WHEN REQUIRED", "IF REQUIRED") -> RxDose(false, false, false, asNeeded = true, food = food)
            has("TDS", "TID", "THRICE", "THREE TIMES") -> RxDose(true, true, true, food = food)
            has("BD", "BID", "TWICE", "TWO TIMES") -> RxDose(true, false, true, food = food)
            has("HS", "AT NIGHT", "BEDTIME", "AT BEDTIME", "NIGHT") -> RxDose(false, false, true, food = food)
            has("OD", "QD", "ONCE", "ONCE DAILY", "DAILY", "MORNING") -> RxDose(true, false, false, food = food)
            else -> null
        }
    }
}

/**
 * Reads a prescription against the strips the family owns.
 *
 * Reading a doctor's handwriting as free text cannot be trusted, by a person or a model. The phone does not
 * try. It asks a smaller question it can answer: which of the saved medicines, if any, is this line about?
 * A badly written "Telmisartn 40" is close to one saved name and far from the others, so it can be placed even
 * though it could never be read alone. A line that is close to two saved medicines, or to none, is not placed:
 * the family is shown the choices.
 *
 * It also checks the other way. The strength on the prescription is compared with the strength printed on the
 * saved strip. "Prescription 80 mg, strip 40 mg" is a mistake made at the chemist's counter, and it is caught
 * here, at home, before the first dose.
 */
object RxReader {
    /** A written word must be at least this much like a saved one to count as that medicine at all. */
    const val LIKE = 0.72

    /** The best medicine must be this much more like the line than the next one, or the family chooses. */
    const val CLEAR = 0.12

    fun read(lines: List<String>, medicines: List<Medicine>, looks: List<Look>): List<RxLine> {
        val index = PrintIndex.build(medicines, looks)
        val active = medicines.filter { it.status == MedStatus.ACTIVE }
        // What can stand for each medicine in a doctor's hand: its name, its ingredients, and the brand words printed on its own strip.
        val names = active.associateWith { med ->
            val print = index.prints.firstOrNull { it.med.id == med.id }
            val own = (words(med.brand) + med.generics.flatMap(::words) + words(med.nickEn)).toSet()
            val printed = print?.words.orEmpty().filter { it.length >= 5 && index.distinctive(it) }.toSet()
            own to printed
        }
        return lines.mapNotNull { raw ->
            val line = raw.trim()
            if (line.length < 3) return@mapNotNull null
            val written = words(line)
            val dose = RxDoses.read(line)
            val scores = active.associateWith { med ->
                val (own, printed) = names.getValue(med)
                maxOf(written.maxOfOrNull { w -> own.maxOfOrNull { like(w, it) } ?: 0.0 } ?: 0.0,
                    (written.maxOfOrNull { w -> printed.maxOfOrNull { like(w, it) } ?: 0.0 } ?: 0.0) * 0.95)
            }.filterValues { it >= LIKE }
            if (scores.isEmpty()) {
                // A line with a dose and a medicine-like word that fits no saved strip: a medicine the family has not saved yet.
                return@mapNotNull if (dose != null && written.any { it.length >= 4 }) RxLine(line, null, emptyList(), dose) else null
            }
            val ranked = scores.entries.sortedByDescending { it.value }
            val best = ranked.first()
            // Medicines the line cannot tell apart, except by a strength it also gives.
            val close = ranked.filter { best.value - it.value < CLEAR }.map { it.key }
            val numbers = strengths(line)
            val byStrength = close.filter { med -> numbers.any { it in Strengths.numbersOf(med) } }
            val chosen = when {
                close.size == 1 -> close.single()
                byStrength.size == 1 -> byStrength.single()
                else -> null
            }
            if (chosen == null) return@mapNotNull RxLine(line, null, close, dose)
            val saved = Strengths.numbersOf(chosen)
            val conflict = numbers.takeIf { saved.isNotEmpty() && it.isNotEmpty() && it.none { n -> n in saved } }
                ?.let { it.first() to Strengths.display(chosen.strengths) }
            RxLine(line, chosen, listOf(chosen), dose, conflict)
        }
    }

    /**
     * Strengths the line gives: a number with its unit, or the number written straight after the name
     * ("Telma 40"). The 1-0-1 of the dose and the days of the course are not strengths.
     */
    private fun strengths(line: String): Set<String> {
        val withUnit = StripParser.strengthsIn(line).mapTo(LinkedHashSet()) { Strengths.number(it.value) }
        if (withUnit.isNotEmpty()) return withUnit
        val plain = line.replace(Regex("(?<![\\d/])([0-3]|1/2|½)\\s*[-–—_]\\s*([0-3]|1/2|½)\\s*[-–—_]\\s*([0-3]|1/2|½)(?![\\d/])"), " ")
            .replace(Regex("(?i)\\b(x|for|×)\\s*\\d+\\s*(days?|d|weeks?|wk|months?)?\\b"), " ")
            .replace(Regex("(?i)\\b\\d+\\s*(days?|weeks?|months?)\\b"), " ")
        return Regex("(?<=[A-Za-z]{3}[\\s-]{1,3})(\\d{2,4}(?:\\.\\d)?)(?!\\d)").findAll(plain).mapTo(LinkedHashSet()) { Strengths.number(it.groupValues[1]) }
    }

    private val NOT_NAMES = setOf("TAB", "TABS", "TABLET", "TABLETS", "CAP", "CAPS", "CAPSULE", "CAPSULES", "SYP", "SYRUP", "INJ", "DAYS", "DAY", "WEEKS",
        "AFTER", "BEFORE", "FOOD", "MEALS", "MEAL", "DAILY", "ONCE", "TWICE", "THRICE", "NIGHT", "MORNING", "WITH", "AND", "FOR", "MEDICINE", "TABLETS")

    private fun words(text: String): List<String> = TextNorm.tokens(text).flatMap { TextNorm.splitLetterDigit(it) }
        .filter { it.length >= 4 && it.all { c -> c in 'A'..'Z' } && it !in NOT_NAMES }

    /**
     * How alike a written word and a saved one are, 0 to 1. A doctor's short form counts: the first letters
     * of a long name ("Telmi" for Telmisartan) are that name.
     */
    fun like(written: String, saved: String): Double {
        if (written == saved) return 1.0
        if (written.length >= 5 && saved.length > written.length && saved.startsWith(written)) return 0.85
        val longest = maxOf(written.length, saved.length)
        // Short words are too easily alike by chance: "CARE" and "CURE".
        if (longest < 5) return 0.0
        return 1.0 - distance(written, saved).toDouble() / longest
    }

    private fun distance(a: String, b: String): Int {
        var before = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val now = IntArray(b.length + 1)
            now[0] = i
            for (j in 1..b.length) now[j] = minOf(before[j] + 1, now[j - 1] + 1, before[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            before = now
        }
        return before[b.length]
    }
}

/** What the on-phone model is asked about a prescription: to copy it, never to explain or complete it. */
object RxPrompt {
    const val SYSTEM = "You copy handwritten and printed text from a doctor's prescription. You write only what is on the page and nothing else."
    const val QUESTION = "Copy each medicine line of this prescription, one line for each medicine: its name, its strength, and how often it is taken " +
        "(for example 1-0-1, BD, OD, SOS), exactly as written. Do not correct spellings. Do not add a medicine that is not written. " +
        "If you cannot read the page, reply UNREADABLE."

    /** The reply as lines. A refusal, or a reply too long to be a copy of one page, gives nothing. */
    fun lines(reply: String): List<String> {
        if (reply.contains("UNREADABLE", ignoreCase = true) || reply.length > 2000) return emptyList()
        return reply.lines().map { it.trim().trim('-', '*', '•').replace(Regex("^\\d+[.)]\\s*"), "").trim() }.filter { it.length in 3..120 }.take(20)
    }
}

/** The lines as the family should see them: one card for each medicine, the fullest reading of it. */
object RxLines {
    fun tidy(lines: List<RxLine>): List<RxLine> {
        fun rank(line: RxLine) = (if (line.conflict != null) 4 else 0) + (if (line.dose != null) 2 else 0) + (if (line.match != null) 1 else 0)
        val placed = lines.filter { it.match != null }.groupBy { it.match!!.id }.values.map { same -> same.maxBy(::rank) }
        val open = lines.filter { it.match == null && it.candidates.isNotEmpty() }.distinctBy { it.candidates.map { med -> med.id }.toSet() }
            // A line the family must choose for is dropped when another line already placed every strip it could be.
            .filter { line -> line.candidates.any { med -> placed.none { it.match!!.id == med.id } } }
        val unknown = lines.filter { it.match == null && it.candidates.isEmpty() }.distinctBy { it.text.uppercase().filter(Char::isLetter).take(12) }.take(6)
        return placed + open + unknown
    }
}

