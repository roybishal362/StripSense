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
/** Everything printed on one saved medicine: its reviewed label plus the text read from its own saved photos. */
class MedPrint(
    val med: Medicine,
    val brand: String,
    /** Longer printed words that are not pack boilerplate. Used to recognise a piece with no name on it. */
    val words: Set<String>,
    /** Every token seen on the saved photos, of any length, so the strip's own "DT" or "SR" is never a difference. */
    val tokens: Set<String>,
    val strengths: Set<Pair<String, StrengthUnit>>,
    val numbers: Set<String>,
    /** The reviewed strengths. Some of these must be read before the print counts as checked. */
    val needed: Set<Pair<String, StrengthUnit>>,
    /** Words only this pack prints beside its name: the maker, the town. They back up a name too short to trust alone. */
    val marks: Set<String> = emptySet(),
    /**
     * The saved release word (SR, ER...) stands on the saved pack where a release word goes, or there is no
     * saved photo to check it against. False when the saved word is not on the pack at all.
     */
    val releaseOnPack: Boolean = true,
) {
    /**
     * A name like "P-500" is one letter and a common number. Alone it also matches "I P 500 mg" on other strips,
     * so it only counts together with one of this pack's own [marks].
     */
    val weakName: Boolean get() = brand.count { it in 'A'..'Z' } < PrintIndex.STRONG_NAME_LETTERS

    /** The words of the name that carry meaning: "CROCIN", "ADVANCE", not "AND". */
    val nameParts: List<String> = brand.split(' ').filter { part -> part.any { it in 'A'..'Z' } && part !in PrintIndex.CONNECTORS }

    /**
     * A name of several words. Packs print such names apart: "Crocin" large on one line, "Advance" small on
     * another. The phone read each of them often and never the two side by side, so the name is taken as read
     * when all its words are read on the strip, wherever they stand.
     */
    val spreadName: Boolean get() = nameParts.size >= 2 && !weakName

    fun allows(strength: Pair<String, StrengthUnit>) = strength in strengths
    fun allowsNumber(number: String) = number in numbers
    fun allowsWord(word: String) = word in tokens
}

/**
 * The family's medicines as print signatures. A strip is compared with what its own saved photos
 * actually say, so a label detail the family never typed (for example "DT") is not a false difference,
 * while a number the saved strip never showed still is a real one.
 */
class PrintIndex private constructor(val medicines: List<Medicine>, val prints: List<MedPrint>) {
    val brands: Set<String> = prints.mapTo(LinkedHashSet()) { it.brand }
    private val owners: Map<String, Int> = prints.flatMap { it.words }.groupingBy { it }.eachCount()

    fun byBrand(brand: String): List<MedPrint> = prints.filter { it.brand == brand }

    /** Every word of every several-word name, for the evidence to vote on. */
    val nameParts: Set<String> = prints.filter { it.spreadName }.flatMapTo(LinkedHashSet()) { it.nameParts }

    // A word that also belongs to another saved medicine's name or ingredients cannot say whose strip this is.
    private val wordOwners: Map<String, Int> = medicines.flatMap { med ->
        (allTokens(med.brand) + med.generics.flatMap { allTokens(it) }).toSet()
    }.groupingBy { it }.eachCount()

    /**
     * The saved names this patch of print could belong to: a name read whole, or a long word that only one
     * saved several-word name has. Deciding that the name really was read is left to the evidence.
     */
    fun namesIn(lines: List<OcrLine>, tuning: Tuning): Set<String> {
        val tokens = lines.flatMapTo(HashSet()) { allTokens(it.text) }
        // A short name like "P 500" also stands in "I P 500 mg" on other strips. It names a patch only together
        // with one of its own pack's words; without that the patch is somebody else's, and taking it kept
        // a Crocin strip from ever having its own strength counted.
        val whole = StripParser.parse(lines, medicines, tuning).brands.filter { brand ->
            brand in brands && byBrand(brand).any { print -> !print.weakName || print.marks.any { it in tokens } }
        }
        // One long word of a several-word name, read exactly or with one letter wrong. The misread counts here
        // so that print of one strip is never handed to another strip's memory just because a letter was off.
        val part = prints.filter { print ->
            print.spreadName && print.nameParts.any { part ->
                part.length >= MIN_WORD + 1 && wordOwners[part] == 1 &&
                    (part in tokens || (part.length >= 7 && tokens.any { TextNorm.editDistanceOne(it, part) }))
            }
        }.map { it.brand }
        return (whole + part).toSet()
    }

    /** True when only one saved medicine prints this word. Shared words cannot tell medicines apart. */
    fun distinctive(word: String): Boolean = owners[word] == 1

    companion object {
        const val MIN_WORD = 4
        const val STRONG_NAME_LETTERS = 4
        val CONNECTORS = setOf("AND", "WITH", "OF", "THE", "FOR", "IN")

        fun build(medicines: List<Medicine>, looks: List<Look>): PrintIndex {
            val prints = medicines.filter { it.type == MedType.CAMERA && KnownWords.canon(it.brand).isNotEmpty() }.map { med ->
                val lines = looks.filter { it.medId == med.id }.flatMap { it.readLines }
                val label = listOf(med.brand) + med.generics + med.extraWords + listOfNotNull(med.release)
                val all = (lines + label).flatMap { allTokens(it) }
                // Numbers are taken only from lines the live scan also reads, and only where the live scan would
                // take them: a strength with its unit, or the number printed straight after the name.
                // A street number or a price on the pack must never become an allowed strength.
                val identity = lines.filter(LabelEvidence::identityLine).map { StripParser.parse(listOf(OcrLine(it, 0, 0, 0, 0, 100, 1f)), medicines) }
                val read = lines.filter(LabelEvidence::identityLine).flatMap { StripParser.strengthsIn(it) }
                val afterName = identity.flatMap { it.brandNumbers }.map { Strengths.number(it) }
                val release = med.release?.trim()?.uppercase().orEmpty()
                val typed = (med.generics + med.brand).flatMapTo(HashSet()) { words(it) }
                MedPrint(
                    med = med,
                    brand = KnownWords.canon(med.brand),
                    marks = lines.flatMapTo(LinkedHashSet()) { words(it) } - typed,
                    words = (lines + med.generics + med.brand).flatMapTo(LinkedHashSet()) { words(it) },
                    tokens = all.toSet(),
                    strengths = Strengths.normSet(med.strengths + read),
                    numbers = Strengths.numbersOf(med) + afterName + read.map { Strengths.number(it.value) },
                    needed = Strengths.normSet(med.strengths),
                    releaseOnPack = lines.isEmpty() || identity.any { release in it.release },
                )
            }
            return PrintIndex(medicines, prints)
        }

        /** The words of one printed line that can identify a product: letters only, long enough, not boilerplate. */
        fun words(text: String): List<String> = allTokens(text).filter { it.length >= MIN_WORD && it.all { c -> c in 'A'..'Z' } && it !in BOILERPLATE }

        internal fun allTokens(text: String): List<String> = TextNorm.tokens(text).flatMap { TextNorm.splitLetterDigit(it) }

        /** Words printed on almost every Indian strip. They say nothing about which medicine this is. */
        private val BOILERPLATE = setOf(
            "TABLET", "TABLETS", "CAPSULE", "CAPSULES", "CONTAINS", "COMPOSITION", "STORE", "STORAGE", "PROTECT", "PROTECTED",
            "LIGHT", "MOISTURE", "REACH", "CHILDREN", "DOSAGE", "DIRECTED", "PHYSICIAN", "MANUFACTURED", "INDIA", "LIMITED",
            "WARNING", "CAUTION", "SCHEDULE", "PRESCRIPTION", "REGISTERED", "MEDICAL", "PRACTITIONER", "EXCIPIENTS", "COLOUR",
            "COLOURS", "COATED", "UNCOATED", "BELOW", "PLACE", "MEDICINE", "MEDICINES", "RETAIL", "WITHOUT", "TRADE", "TRADEMARK",
            "BATCH", "PRICE", "TAXES", "PRIVATE", "LABORATORIES", "PHARMA", "PHARMACEUTICAL", "PHARMACEUTICALS", "INDUSTRIAL",
            "OVERDOSE", "HOURS", "TIMES", "MAXIMUM", "ADVICE", "TEMPERATURE", "TEMPERATURES", "EXCEED", "CONTINUE", "BEYOND",
            "FOLLOW", "APPROPRIATE", "OVERAGES", "VITAMINS", "ADDED", "COMPENSATE", "EQUIVALENT", "EVERY", "PLANT", "COMPLEX",
            "DISTRICT", "REACTIONS", "INJURIOUS", "HEALTH", "LIVER", "ALLERGIC", "INCLUSIVE", "INCLUDING",
            "EACH", "FILM", "KEEP", "FROM", "DATE", "WITH", "THIS", "ONLY", "SOLD", "DOSE", "DAYS", "MADE", "THAN", "THAT", "WHEN",
            "TAKE", "USED", "MORE", "AREA", "PLOT", "ROAD", "COOL", "DARK", "BEST", "BEFORE", "AFTER", "MARK", "LTD", "PVT",
        )
    }
}
