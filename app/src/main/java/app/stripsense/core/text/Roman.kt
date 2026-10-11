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
 * Hindi written in Roman letters, for a phone that has no Hindi voice. The English voice can read
 * "yah aapkee davaa hai" aloud and a Hindi speaker understands it; the same voice given Devanagari says nothing
 * useful. Medicine names, numbers and times pass through unchanged.
 *
 * The one rule beyond letter for letter is the silent "a": Hindi writes आपकी as aa-pa-kee and says aap-kee.
 * The "a" is dropped at the end of a word, and between two sounded syllables.
 */
object Roman {
    private val CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        'क़' to "q", 'ख़' to "kh", 'ग़' to "g", 'ज़' to "z", 'ड़' to "r", 'ढ़' to "rh", 'फ़' to "f", 'य़' to "y",
    )
    private val DOTTED = mapOf("k" to "q", "j" to "z", "ph" to "f", "d" to "r", "dh" to "rh")
    private val VOWELS = mapOf('अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o")
    private val SIGNS = mapOf('ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o")
    private const val NUKTA = '़'
    private const val VIRAMA = '्'

    /** One sound of a word: its consonant (may be empty), its vowel, and whether that vowel is the unwritten "a". */
    private class Unit(var consonant: String, var vowel: String, var silentable: Boolean, var tail: String = "")

    fun isDevanagari(c: Char): Boolean = c in 'ऀ'..'ॿ'

    fun fromDevanagari(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                // The full stop and the digits are not part of a word: a word's last sound must be seen as its last.
                c == '।' || c == '॥' -> { out.append('.'); i++ }
                c in '०'..'९' -> { out.append('0' + (c - '०')); i++ }
                !isDevanagari(c) -> { out.append(c); i++ }
                else -> {
                    val start = i
                    while (i < text.length && isLetter(text[i])) i++
                    out.append(word(text.substring(start, i)))
                }
            }
        }
        return out.toString()
    }

    private fun isLetter(c: Char) = isDevanagari(c) && c != '।' && c != '॥' && c !in '०'..'९'

    private fun word(word: String): String {
        val units = ArrayList<Unit>()
        var i = 0
        while (i < word.length) {
            val c = word[i]
            when {
                c in CONSONANTS -> {
                    var sound = CONSONANTS.getValue(c)
                    if (word.getOrNull(i + 1) == NUKTA) { sound = DOTTED[sound] ?: sound; i++ }
                    val next = word.getOrNull(i + 1)
                    when {
                        next == VIRAMA -> { units += Unit(sound, "", false); i++ }
                        next != null && next in SIGNS -> { units += Unit(sound, SIGNS.getValue(next), false); i++ }
                        else -> units += Unit(sound, "a", true)
                    }
                }
                c in VOWELS -> units += Unit("", VOWELS.getValue(c), false)
                c == 'ं' || c == 'ँ' -> units.lastOrNull()?.let { it.tail += "n" }
                c == 'ः' -> units.lastOrNull()?.let { it.tail += "h" }
            }
            i++
        }
        // The silent "a", from the end of the word backwards so that two in a row are not both dropped.
        for (n in units.indices.reversed()) {
            val unit = units[n]
            if (!unit.silentable || unit.tail.isNotEmpty()) continue
            val last = n == units.lastIndex
            val before = units.getOrNull(n - 1)
            val after = units.getOrNull(n + 1)
            if ((last && units.size > 1) || (before != null && before.vowel.isNotEmpty() && after != null && after.vowel.isNotEmpty())) unit.vowel = ""
        }
        return units.joinToString("") { it.consonant + it.vowel + it.tail }
    }
}
