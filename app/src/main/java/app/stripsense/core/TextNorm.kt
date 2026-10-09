package app.stripsense.core

import java.util.Locale

/** Text clean-up and token helpers for the parser. Spec 01 section 2, steps 2 and 4. */
internal object TextNorm {

    private const val SEPARATORS = "-_/,:;()[]*"
    private val NUMBER = Regex("""\d{1,4}(\.\d{1,3})?""")

    /** Step 2: one line of print, normalised, as tokens. */
    fun tokens(text: String): List<String> {
        val norm = normalise(text)
        if (norm.isEmpty()) return emptyList()
        return norm.split(' ')
    }

    /** Step 2, in the order written in the spec. Returns tokens joined by single spaces. */
    fun normalise(text: String): String {
        val micro = StringBuilder(text.length + 4)
        for (i in text.indices) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            if ((c == '\u00B5' || c == '\u03BC') && (next == 'g' || next == 'G')) micro.append("MC") else micro.append(c)
        }
        val up = micro.toString().uppercase(Locale.ROOT)
        val out = StringBuilder(up.length + 8)
        for (i in up.indices) {
            val c = up[i]
            when {
                c in SEPARATORS || c.isWhitespace() -> out.append(' ')
                c == '.' -> out.append(if (isDigit(up.getOrNull(i - 1)) && isDigit(up.getOrNull(i + 1))) '.' else ' ')
                c == '+' || c == '%' -> out.append(' ').append(c).append(' ')
                else -> out.append(c)
            }
        }
        return collapse(out)
    }

    /** Step 4: "DOLO650" -> DOLO, 650. A '.' inside a number counts as a digit. */
    fun splitLetterDigit(token: String): List<String> {
        if (token.length < 2) return listOf(token)
        val parts = ArrayList<String>(2)
        var start = 0
        for (i in 1 until token.length) {
            val a = kind(token[i - 1])
            val b = kind(token[i])
            if ((a == LETTER && b == DIGIT) || (a == DIGIT && b == LETTER)) {
                parts.add(token.substring(start, i))
                start = i
            }
        }
        if (start == 0) return listOf(token)
        parts.add(token.substring(start))
        return parts
    }

    /** A NUMBER token: 1 to 4 digits, optionally a '.' and 1 to 3 digits. Never corrected. */
    fun isNumber(token: String): Boolean = token.length <= 8 && NUMBER.matches(token)

    /** Letter fixes 0->O, 1->I, 5->S, 8->B, only for tokens with at least one letter. */
    fun letterFixed(token: String): String? {
        if (token.none { it.isLetter() }) return null
        if (token.none { it == '0' || it == '1' || it == '5' || it == '8' }) return token
        val chars = token.toCharArray()
        for (i in chars.indices) {
            chars[i] = when (chars[i]) {
                '0' -> 'O'
                '1' -> 'I'
                '5' -> 'S'
                '8' -> 'B'
                else -> chars[i]
            }
        }
        return String(chars)
    }

    /** Edit distance of exactly one (one change, one extra letter or one missing letter). */
    fun editDistanceOne(a: String, b: String): Boolean {
        val la = a.length
        val lb = b.length
        if (kotlin.math.abs(la - lb) > 1 || a == b) return false
        if (la == lb) {
            var diff = 0
            for (i in 0 until la) if (a[i] != b[i] && ++diff > 1) return false
            return diff == 1
        }
        val (s, l) = if (la < lb) a to b else b to a
        var i = 0
        var j = 0
        var skipped = false
        while (i < s.length && j < l.length) {
            if (s[i] == l[j]) {
                i++; j++
            } else {
                if (skipped) return false
                skipped = true
                j++
            }
        }
        return true
    }

    fun countLetters(normalised: String): Int = normalised.count { it in 'A'..'Z' }

    private fun collapse(sb: CharSequence): String {
        val out = StringBuilder(sb.length)
        var space = true
        for (c in sb) {
            if (c == ' ') {
                if (!space) out.append(' ')
                space = true
            } else {
                out.append(c)
                space = false
            }
        }
        if (out.isNotEmpty() && out[out.length - 1] == ' ') out.setLength(out.length - 1)
        return out.toString()
    }

    private fun isDigit(c: Char?): Boolean = c != null && c in '0'..'9'

    private const val OTHER = 0
    private const val LETTER = 1
    private const val DIGIT = 2

    private fun kind(c: Char): Int = when {
        c in '0'..'9' || c == '.' -> DIGIT
        c.isLetter() -> LETTER
        else -> OTHER
    }
}
