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
/**
 * Read lines -> ReadKey. Spec 01 section 2, steps 1 to 8 in the order written there.
 * Numbers are never corrected; only letter tokens get the 0/1/5/8 fixes.
 */
object StripParser {

    /** One token of a line while it is being parsed. */
    private class Tok(val text: String) {
        val fixed: String? = TextNorm.letterFixed(text)
        var consumed = false
        var brandEnd = false
        var inStrength = false
    }

    /** What the lines of one frame add up to. */
    private class Acc {
        val brands = LinkedHashSet<String>()
        val generics = LinkedHashSet<String>()
        val strengths = LinkedHashSet<Strength>()
        val seen = HashMap<Strength, Int>()
        val brandNumbers = LinkedHashSet<String>()
        val release = LinkedHashSet<String>()
        val extra = LinkedHashSet<String>()
        var letters = 0

        fun hasKeyWord() = brands.isNotEmpty() || generics.isNotEmpty() || strengths.isNotEmpty()

        fun toKey() = ReadKey(brands, generics, strengths, seen, brandNumbers, release, extra, letters)
    }

    @Volatile
    private var cache: Pair<List<Medicine>, KnownWords.Lexicon>? = null

    fun parse(lines: List<OcrLine>, meds: List<Medicine>, tuning: Tuning = Tuning()): ReadKey {
        val lex = lexicon(meds)
        val acc = Acc()
        for (line in usable(lines, tuning)) scanLine(line.text, lex, acc)
        return acc.toKey()
    }

    /** One frame in one pass: the key, the smallest key line and the too-small count, for ScanSession.feed. */
    data class FrameRead(val key: ReadKey, val smallestKeyLinePx: Int?, val tooSmallLines: Int)

    fun read(lines: List<OcrLine>, meds: List<Medicine>, tuning: Tuning = Tuning()): FrameRead {
        val lex = lexicon(meds)
        val acc = Acc()
        var smallest: Int? = null
        for (line in usable(lines, tuning)) {
            // Each line is parsed on its own tokens, so "this line gave a key word" equals parsing it alone.
            if (scanLine(line.text, lex, acc) && (smallest == null || line.heightPx < smallest)) smallest = line.heightPx
        }
        return FrameRead(acc.toKey(), smallest, tooSmallLines(lines, tuning))
    }

    /** Of the usable lines whose own key has a brand, a generic or a strength, the smallest height. */
    fun smallestKeyLinePx(lines: List<OcrLine>, meds: List<Medicine>, tuning: Tuning = Tuning()): Int? {
        val lex = lexicon(meds)
        var smallest: Int? = null
        for (line in usable(lines, tuning)) {
            if (smallest != null && line.heightPx >= smallest) continue
            val acc = Acc()
            scanLine(line.text, lex, acc)
            if (acc.hasKeyWord()) smallest = line.heightPx
        }
        return smallest
    }

    /** Lines that pass minConf and fail only minHeightPx: print in view but too small. */
    fun tooSmallLines(lines: List<OcrLine>, tuning: Tuning = Tuning()): Int =
        lines.count { it.confidence >= tuning.minConf && it.heightPx < tuning.minHeightPx }

    /** Only the strength steps, on plain text, with no confidence or height test (second reader). */
    fun strengthsIn(text: String): Set<Strength> {
        val toks = ArrayList<Tok>()
        for (t in TextNorm.tokens(text)) for (p in TextNorm.splitLetterDigit(t)) toks.add(Tok(p))
        val out = LinkedHashSet<Strength>()
        readStrengths(toks, out)
        return out
    }

    // Step 1: usable lines, in reading order (top to bottom).
    private fun usable(lines: List<OcrLine>, tuning: Tuning): List<OcrLine> =
        lines.filter { it.confidence >= tuning.minConf && it.heightPx >= tuning.minHeightPx }.sortedBy { it.top }

    /** Parses one line into [acc]. True when the line itself gave a brand, a generic or a strength. */
    private fun scanLine(text: String, lex: KnownWords.Lexicon, acc: Acc): Boolean {
        val norm = TextNorm.normalise(text)                                  // step 2
        acc.letters += TextNorm.countLetters(norm)                           // step 8
        if (!LabelEvidence.identityLine(text)) return false
        // DT is often printed alone directly below an Indian strip's trade name.
        if (norm == "DT") acc.extra.add("DT")
        if (norm.isEmpty()) return false
        val toks = norm.split(' ').mapTo(ArrayList()) { Tok(it) }
        var found = matchKnown(toks, lex, acc)                               // step 3
        splitJoined(toks)                                                    // step 4
        found = matchKnown(toks, lex, acc) || found
        val lineStrengths = LinkedHashSet<Strength>()
        readStrengths(toks, lineStrengths)                                   // step 5
        for (s in lineStrengths) {
            acc.strengths.add(s)
            acc.seen[s] = (acc.seen[s] ?: 0) + 1
        }
        var hasBrand = false
        for (i in toks.indices) if (toks[i].brandEnd) {                      // step 6
            hasBrand = true
            afterBrand(toks, i, acc)
        }
        if (hasBrand || lineStrengths.isNotEmpty()) releaseWords(toks, acc)  // step 7
        return found || lineStrengths.isNotEmpty()
    }

    // Step 3: brands before generics, longer words first. Matched tokens are consumed.
    private fun matchKnown(toks: List<Tok>, lex: KnownWords.Lexicon, acc: Acc): Boolean {
        var found = false
        for (word in lex.words) {
            val n = word.parts.size
            var i = 0
            while (i + n <= toks.size) {
                if (matchesAt(toks, i, word)) {
                    found = true
                    for (j in i until i + n) toks[j].consumed = true
                    if (word.isBrand) {
                        toks[i + n - 1].brandEnd = true
                        acc.brands.add(word.key)
                    } else {
                        acc.generics.add(word.key)
                    }
                    i += n
                } else {
                    i++
                }
            }
        }
        return found
    }

    private fun matchesAt(toks: List<Tok>, start: Int, word: KnownWords.Word): Boolean {
        for (p in word.parts.indices) {
            val t = toks[start + p]
            if (t.consumed || !tokenMatches(t, word.parts[p], word.fuzzy[p])) return false
        }
        return true
    }

    private fun tokenMatches(t: Tok, part: String, fuzzy: Boolean): Boolean {
        if (t.text == part || t.fixed == part) return true
        if (!fuzzy) return false
        return TextNorm.editDistanceOne(t.text, part) || (t.fixed != null && TextNorm.editDistanceOne(t.fixed, part))
    }

    // Step 4: split tokens that were not consumed between letters and digits.
    private fun splitJoined(toks: MutableList<Tok>) {
        var i = 0
        while (i < toks.size) {
            val t = toks[i]
            if (!t.consumed) {
                val parts = TextNorm.splitLetterDigit(t.text)
                if (parts.size > 1) {
                    toks.removeAt(i)
                    toks.addAll(i, parts.map { Tok(it) })
                    i += parts.size
                    continue
                }
            }
            i++
        }
    }

    // Step 5: a NUMBER followed by a UNIT, both not consumed.
    private fun readStrengths(toks: List<Tok>, out: MutableSet<Strength>) {
        var i = 0
        while (i < toks.size - 1) {
            val num = toks[i]
            val unitTok = toks[i + 1]
            val unit = if (num.consumed || unitTok.consumed) null else KnownWords.UNITS[unitTok.text]
            if (unit != null && TextNorm.isNumber(num.text)) {
                out.add(Strength(num.text, unit))
                num.inStrength = true
                unitTok.inStrength = true
                i += 2
            } else {
                i++
            }
        }
    }

    // Step 6: up to three tokens after a brand: release code, extra word, or a bare number.
    private fun afterBrand(toks: List<Tok>, brandEnd: Int, acc: Acc) {
        var j = brandEnd + 1
        while (j < toks.size && j <= brandEnd + 3) {
            val t = toks[j]
            if (t.consumed) return
            when {
                t.text in KnownWords.RELEASE_CODES -> acc.release.add(t.text)
                t.text in KnownWords.EXTRA_WORDS -> acc.extra.add(t.text)
                !t.inStrength && TextNorm.isNumber(t.text) -> acc.brandNumbers.add(t.text)
                else -> return
            }
            j++
        }
    }

    // Step 7: release codes as whole tokens, and the release phrases.
    private fun releaseWords(toks: List<Tok>, acc: Acc) {
        for (i in toks.indices) {
            val t = toks[i]
            if (t.consumed) continue
            if (t.text in KnownWords.RELEASE_CODES) acc.release.add(t.text)
            for ((phrase, code) in KnownWords.RELEASE_PHRASES) {
                if (i + phrase.size <= toks.size &&
                    phrase.indices.all { p -> !toks[i + p].consumed && toks[i + p].text == phrase[p] }
                ) acc.release.add(code)
            }
        }
    }

    private fun lexicon(meds: List<Medicine>): KnownWords.Lexicon {
        val c = cache
        if (c != null && c.first == meds) return c.second
        return KnownWords.lexicon(meds).also { cache = meds.toList() to it }
    }
}
