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
/** What the print on one tracked strip adds up to. Timing and intake are decided elsewhere. */
sealed interface PrintResult {
    /** Not enough has been read yet, or an odd reading is being checked again before anything is said. */
    data object Reading : PrintResult

    /** The saved name and the strength that tells it apart were both read, and nothing read differs. */
    data class Checked(val med: Medicine) : PrintResult

    /** The saved name was read and nothing differs, but the strength has not been seen. */
    data class NameOnly(val med: Medicine) : PrintResult

    /** No name in view. The remaining print matches one saved strip. A comparison to confirm, never an answer. */
    data class Piece(val med: Medicine, val words: List<String>) : PrintResult

    /** Something printed here is not on the saved strip. Stays for as long as this strip is followed. */
    data class Differs(val med: Medicine, val printed: String, val saved: String) : PrintResult

    /** One saved name fits more than one saved medicine and the print in view cannot tell them apart. */
    data object Ambiguous : PrintResult

    /** Two saved strips in one patch of print: they are touching. */
    data object TooClose : PrintResult

    /** Plenty of clear print, none of it from a saved medicine. */
    data object Unknown : PrintResult
}

/**
 * The print of one tracked strip, gathered over frames. One frame rarely shows everything on shiny foil,
 * so each frame adds what it could read and the answer comes from the last few seconds together.
 * Old readings expire: a strip swapped in at the same spot cannot inherit the one before it.
 *
 * Every printed line votes. A strip prints its name and strength many times, and the phone misreads a digit
 * now and then. A number that differs from the saved strip is a real difference unless the saved number is
 * read at least [DOMINANCE] times as often on the same strip; then it is a misread and is ignored.
 */
class PrintEvidence {
    private val seen = HashMap<String, ArrayDeque<Long>>()
    private val labels = HashMap<String, String>()
    private var differs: PrintResult.Differs? = null

    /** Votes from the slower second reader (Gemma). They live longer because a reading takes seconds to arrive. */
    private val read = HashMap<String, ArrayDeque<Long>>()
    private var reading = false

    /**
     * A transcription by the on-phone language model, one printed item per line. It is one more reading and
     * goes through the same rules as every camera frame: it can add a vote, it can raise a difference, and
     * on its own it is one frame, so it can never name a strip that nothing else has read.
     */
    fun addReading(lines: List<String>, index: PrintIndex, tuning: Tuning, now: Long) {
        reading = true
        // The model is shown a crop that can take in the edge of a neighbouring strip, and a model can add a word
        // that is not there. Its reading may support what the camera read. It may never raise a difference.
        try { add(lines.map { OcrLine(it, 0, 0, 0, 0, 100, 1f) }, index, tuning, now, support = true) } finally { reading = false }
    }

    /**
     * [support]: print that is only near the strip's name, not in the same patch as it. It can supply the strength
     * the name's own patch does not show. It can never raise a difference: on a real screen a neighbouring
     * strip's "Telmisartan" was counted against a P-500 strip and the phone warned about a strip that was fine.
     * Support votes are kept under small letters, which the difference rule does not look at.
     */
    fun add(lines: List<OcrLine>, index: PrintIndex, tuning: Tuning, now: Long, support: Boolean = false) {
        val words = HashSet<String>()
        val number = if (support) "n:" else "N:"
        for (line in lines) {
            val key = StripParser.parse(listOf(line), index.medicines, tuning)
            for (brand in key.brands) {
                vote("B:$brand", now)
                // A name that carries its number, like "P-500": reading the name is reading that number.
                brand.split(' ').filter { TextNorm.isNumber(it) }.forEach { vote(number + Strengths.number(it), now) }
            }
            // Strengths are read from the line itself, not from what is left after names are taken out of it:
            // the saved name "P 500" otherwise swallows the 500 of "IP 500 mg" on every other strip.
            val strengths = if (line.confidence >= tuning.minConf && line.heightPx >= tuning.minHeightPx && LabelEvidence.identityLine(line.text))
                StripParser.strengthsIn(line.text) else emptySet()
            val strength = if (support) "s:" else "S:"
            for (s in strengths) Strengths.normSet(listOf(s)).single().let { vote("$strength${it.first}|${it.second}", now); labels["S:${it.first}|${it.second}"] = Strengths.display(s) }
            key.brandNumbers.forEach { vote(number + Strengths.number(it), now) }
            if (!support) {
                key.generics.forEach { vote("G:$it", now) }
                key.extraWords.forEach { vote("X:$it", now) }
            }
            // A release word is wanted evidence too ("SR" must be read for a saved SR tablet), so support may supply it.
            key.release.forEach { vote(if (support) "x:$it" else "X:$it", now) }
            if (line.confidence >= tuning.minConf && line.heightPx >= tuning.minHeightPx) words += PrintIndex.words(line.text)
        }
        // The words of names that are printed apart, wherever they stand in this patch. Once per frame.
        if (index.nameParts.isNotEmpty()) {
            val tokens = lines.flatMapTo(HashSet()) { PrintIndex.allTokens(it.text) }
            for (part in index.nameParts) {
                if (part in tokens) vote("P:$part", now)
                else if (part.length >= FUZZY_WORD && tokens.any { TextNorm.editDistanceOne(it, part) }) vote("Q:$part", now)
            }
        }
        for (word in words) {
            vote("W:$word", now)
            // A name with one misread letter supports a name that was also read exactly. It never stands alone.
            for (brand in index.brands) if (brand.length >= FUZZY_BRAND && ' ' !in brand && TextNorm.editDistanceOne(word, brand)) vote("F:$brand", now)
        }
        for (queue in seen.values) while (queue.isNotEmpty() && now - queue.first() > WINDOW_MS) queue.removeFirst()
        seen.values.removeIf { it.isEmpty() }
        for (queue in read.values) while (queue.isNotEmpty() && now - queue.first() > READER_WINDOW_MS) queue.removeFirst()
        read.values.removeIf { it.isEmpty() }
    }

    private fun keys(): Set<String> = seen.keys + read.keys

    private fun stamps(key: String, now: Long): List<Long> =
        seen[key].orEmpty().filter { now - it <= WINDOW_MS } + read[key].orEmpty().filter { now - it <= READER_WINDOW_MS }

    fun decide(index: PrintIndex, now: Long): PrintResult {
        differs?.let { held ->
            // A refusal stays while the strip is followed, with one way out: the same strip read clean and whole.
            // Then the refusal was a misreading, and a red box on a correctly saved strip must not stand.
            val print = index.prints.firstOrNull { it.med.id == held.med.id }
            if (print != null && difference(print, now) == null && hasNeeded(print, now)) differs = null else return held
        }
        // An exact read of the name, backed by a second frame that read it exactly or with one letter wrong.
        val whole = index.brands.filter { votes("B:$it", now) >= 1 && frames(now, "B:$it", "F:$it") >= 2 && recent("B:$it", now) }
            .filter { brand -> index.byBrand(brand).any { !it.weakName || backedUp(it, index, now) } }
        // A name printed apart: every one of its words read exactly at least once and in two frames, one of them just now.
        val apart = index.prints.filter { print ->
            print.spreadName && print.nameParts.all { votes("P:$it", now) >= 1 && frames(now, "P:$it", "Q:$it") >= 2 } &&
                print.nameParts.any { recent("P:$it", now) }
        }.map { it.brand }
        val named = (whole + apart).distinct()
        if (named.size >= 2) return PrintResult.TooClose
        val brand = named.singleOrNull() ?: return piece(index, now)
        val candidates = index.byBrand(brand)
        val conflicts = candidates.associateWith { difference(it, now) }
        val viable = candidates.filter { conflicts[it] == null }
        if (viable.isEmpty()) {
            // Two strengths of one saved name in the same patch: two strips, not one wrong strip.
            if (candidates.size > 1 && candidates.all { c -> candidates.any { other -> other !== c && allowed(other, conflicts.getValue(c)!!) } }) return PrintResult.TooClose
            val best = candidates.first()
            val conflict = conflicts.getValue(best)!!
            // Seen again and again, it is a real difference and it stays. Seen once or twice, it is checked again first.
            if (votes(conflict, now) < LATCH_VOTES || frames(now, conflict) < LATCH_VOTES) return PrintResult.Reading
            return PrintResult.Differs(best.med, printed(conflict, best), Strengths.display(best.med.strengths)).also { differs = it }
        }
        val checked = viable.filter { hasNeeded(it, now) }
        return when {
            checked.size == 1 -> PrintResult.Checked(checked.single().med)
            checked.size > 1 -> PrintResult.Ambiguous
            viable.size == 1 -> PrintResult.NameOnly(viable.single().med)
            else -> PrintResult.Ambiguous
        }
    }

    /**
     * Whether this print could be a saved medicine at all: a saved name read once or nearly, or a word only
     * one saved pack prints. The language model is asked about nothing else, so it is not kept busy reading
     * every other thing with letters on it.
     */
    fun promising(index: PrintIndex, now: Long): Boolean =
        keys().any { key ->
            votes(key, now) >= 1 && (key[0] == 'B' || key[0] == 'F' || key[0] == 'P' ||
                (key[0] == 'W' && index.prints.any { print -> nearest(key.substring(2), print)?.let(index::distinctive) == true }))
        }

    /** One of this pack's own words, read in two frames, that no other saved pack prints. */
    private fun backedUp(print: MedPrint, index: PrintIndex, now: Long): Boolean =
        keys().any { key ->
            key.startsWith("W:") && frames(now, key) >= 2 &&
                nearest(key.substring(2), print)?.let { it in print.marks && index.distinctive(it) } == true
        }

    /** The strongest reading in view that the saved strip never showed and that is not outvoted as a misread. */
    private fun difference(print: MedPrint, now: Long): String? {
        val foreign = keys().filter { it[0] in "SNXG" && votes(it, now) >= 1 && !allowed(print, it) }
        if (foreign.isEmpty()) return null
        val saved = keys().filter { it[0] in "SN" && allowed(print, it) }
        // A pack with two ingredients prints two strengths. Where the family saved fewer strengths than ingredients,
        // the other printed strength is the other ingredient's: a saved "Telmisartan and Azelnidipine 40 mg" strip
        // prints "8 mg" as well, and that turned a correctly saved strip red.
        val incomplete = print.med.generics.size > print.needed.size
        // Only a number can be a misread, and only of a saved number it nearly is: 225 for 325, or 32 and 5 for 325.
        // 650 is never a misread of 500. A word like PLUS or SR is never outvoted.
        return foreign.filter { key ->
            // Only for such a pack. Where every ingredient's strength is saved, another strength under the same name
            // stays a difference however often the saved one is read: that is a 650 strip lying beside a 500 one.
            if (key[0] == 'S' && incomplete) return@filter false
            key[0] !in "SN" || saved.filter { nearly(number(key), number(it)) }.sumOf { votes(it, now) } < DOMINANCE * votes(key, now)
            // On a real Parawel 325 strip one "Parawel325" in three came out as 225 or 326, with high confidence.
        }.maxByOrNull { votes(it, now) }
    }

    private fun number(key: String): String = key.substring(2).substringBefore('|')

    /** One digit wrong, dropped or added, or a part of the saved number read on its own. */
    private fun nearly(read: String, saved: String): Boolean =
        read != saved && (TextNorm.editDistanceOne(read, saved) || (read.length < saved.length && saved.contains(read)))

    private fun allowed(print: MedPrint, key: String): Boolean {
        val value = key.substring(2)
        return when (key[0]) {
            'S' -> value.split('|').let { (number, unit) -> print.allows(number to StrengthUnit.valueOf(unit)) }
            'N' -> print.allowsNumber(value)
            'X' -> print.allowsWord(value)
            // Another saved medicine's ingredient under this name is a different product in a familiar pack.
            'G' -> PrintIndex.allTokens(value).all(print::allowsWord)
            else -> true
        }
    }

    private fun printed(key: String, print: MedPrint): String = when (key[0]) {
        'S' -> labels[key] ?: key.substring(2).substringBefore('|')
        'X' -> "${print.med.brand} ${key.substring(2)}"
        else -> key.substring(2)
    }

    /** One or two strengths: every one must be read. A long composition: any two, since no frame shows them all. */
    private fun hasNeeded(print: MedPrint, now: Long): Boolean {
        if (print.needed.isEmpty()) return false
        // "SR" on the saved label must be read too: a plain tablet of the same name is not the same medicine.
        // Only where the pack really prints it. A release word the label reader picked up by mistake (a real saved
        // Crocin Advance carried "SR") cannot be read on the strip, and must not keep it from ever being checked.
        val release = print.med.release?.trim()?.uppercase().orEmpty()
        if (release.isNotEmpty() && print.releaseOnPack && frames(now, "X:$release", "x:$release") < 2) return false
        val read = print.needed.count { (number, unit) -> frames(now, "S:$number|$unit", "N:$number", "s:$number|$unit", "n:$number") >= 2 }
        return read >= minOf(print.needed.size, 2)
    }

    private fun piece(index: PrintIndex, now: Long): PrintResult {
        val live = keys().filter { it.startsWith("W:") && votes(it, now) >= 2 }.map { it.substring(2) }
        if (live.isEmpty()) return PrintResult.Reading
        val matches = index.prints.associateWith { print -> live.filter { word -> index.distinctive(nearest(word, print) ?: return@filter false) } }
        val ranked = matches.entries.sortedByDescending { it.value.size }
        val best = ranked.first()
        val runnerUp = ranked.getOrNull(1)?.value?.size ?: 0
        val strangers = live.count { word -> word.length >= STRANGER_LETTERS && votes("W:$word", now) >= 3 && index.prints.none { nearest(word, it) != null } }
        if (best.value.size >= PIECE_WORDS && runnerUp * 2 <= best.value.size && strangers <= best.value.size &&
            difference(best.key, now) == null) return PrintResult.Piece(best.key.med, best.value.take(3))
        return if (best.value.isEmpty() && strangers >= UNKNOWN_WORDS) PrintResult.Unknown else PrintResult.Reading
    }

    /** The saved word this reading stands for: itself, or a long word with one letter misread. */
    private fun nearest(word: String, print: MedPrint): String? {
        if (word in print.words) return word
        if (word.length < FUZZY_WORD) return null
        // The same misread comes back frame after frame; comparing it with every saved word once is enough.
        val key = print.med.id + "|" + word
        if (nearCache.containsKey(key)) return nearCache[key]
        return print.words.firstOrNull { kotlin.math.abs(it.length - word.length) <= 1 && TextNorm.editDistanceOne(word, it) }
            .also { if (nearCache.size < NEAR_CACHE) nearCache[key] = it }
    }

    private val nearCache = HashMap<String, String?>()

    /** Names, numbers and label words with their votes, for a device check. Never the full text that was read. */
    fun trace(now: Long): String = keys().filter { it[0] != 'W' }.sorted().joinToString(" ") { "$it=${votes(it, now)}" }

    private fun vote(key: String, now: Long) { (if (reading) read else seen).getOrPut(key) { ArrayDeque() }.addLast(now) }

    /** How many printed lines said this in the last few seconds. */
    private fun votes(key: String, now: Long): Int = stamps(key, now).size

    /** Different frames in which any of these readings appeared. Many lines in one frame are still one frame. */
    private fun frames(now: Long, vararg keys: String): Int = keys.flatMap { stamps(it, now) }.distinct().size

    private fun recent(key: String, now: Long): Boolean =
        seen[key]?.lastOrNull()?.let { now - it <= RECENT_MS } == true || read[key]?.lastOrNull()?.let { now - it <= READER_WINDOW_MS } == true

    companion object {
        /** Readings older than this no longer count. */
        const val WINDOW_MS = 2500L

        /** The name must have been read this recently, so the answer is about the strip in view now. */
        const val RECENT_MS = 1200L

        /** A reading by the language model counts for this long. */
        const val READER_WINDOW_MS = 6000L

        /** The saved number must be read this many times as often before a different number counts as a misread. */
        const val DOMINANCE = 2

        /** A difference stays for good once it has this many votes over this many frames. */
        const val LATCH_VOTES = 3
        const val NEAR_CACHE = 2000
        const val FUZZY_BRAND = 6
        const val FUZZY_WORD = 7
        /** A piece with no name is offered only on this many words that one saved pack alone prints. */
        const val PIECE_WORDS = 4
        const val STRANGER_LETTERS = 6
        const val UNKNOWN_WORDS = 4
    }
}
