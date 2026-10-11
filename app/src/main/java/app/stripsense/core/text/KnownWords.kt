package app.stripsense.core.text

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
/** The fixed words of spec 01 section 2, and the brand and generic words built from the list. */
internal object KnownWords {

    val RELEASE_CODES: Set<String> = setOf("SR", "ER", "XR", "CR", "MR", "OD", "XL", "PR", "DR")

    val EXTRA_WORDS: Set<String> =
        setOf("PLUS", "FORTE", "COLD", "EXTRA", "DUO", "TRIO", "AM", "H", "D", "M", "LS", "CV", "DS", "XT", "MAX", "DT")

    val RELEASE_PHRASES: List<Pair<List<String>, String>> = listOf(
        listOf("SUSTAINED", "RELEASE") to "SR",
        listOf("EXTENDED", "RELEASE") to "ER",
        listOf("PROLONGED", "RELEASE") to "PR",
        listOf("MODIFIED", "RELEASE") to "MR",
    )

    /** "RNG" and "MQ" are the two common misreads of "mg". */
    val UNITS: Map<String, StrengthUnit> = mapOf(
        "MG" to StrengthUnit.MG,
        "RNG" to StrengthUnit.MG,
        "MQ" to StrengthUnit.MG,
        "MCG" to StrengthUnit.MCG,
        "UG" to StrengthUnit.MCG,
        "G" to StrengthUnit.G,
        "GM" to StrengthUnit.G,
        "IU" to StrengthUnit.IU,
        "ML" to StrengthUnit.ML,
        "%" to StrengthUnit.PERCENT,
    )

    /** Generic words this long may be read with one wrong letter. */
    const val FUZZY_MIN_LETTERS = 8

    /** A brand or a generic name, split into the tokens it must match, one token per word. */
    class Word(val key: String, val parts: List<String>, val isBrand: Boolean) {
        val fuzzy: BooleanArray = BooleanArray(parts.size) { i ->
            !isBrand && parts[i].count { it.isLetter() } >= FUZZY_MIN_LETTERS
        }
    }

    /** Brands first, then generics; longer words before shorter ones in each group. */
    class Lexicon(val words: List<Word>)

    fun lexicon(meds: List<Medicine>): Lexicon {
        val brands = meds.mapNotNullTo(LinkedHashSet()) { canon(it.brand).ifEmpty { null } }
        val generics = meds.flatMap { it.generics }.mapNotNullTo(LinkedHashSet()) { canon(it).ifEmpty { null } }
        val order = compareByDescending<Word> { it.parts.size }.thenByDescending { it.key.length }
        val words = brands.map { Word(it, it.split(' '), isBrand = true) }.sortedWith(order) +
            generics.map { Word(it, it.split(' '), isBrand = false) }.sortedWith(order)
        return Lexicon(words)
    }

    /** A list word in the form the parser reports it: normalised tokens joined by one space. */
    fun canon(word: String): String = TextNorm.tokens(word).joinToString(" ")
}
