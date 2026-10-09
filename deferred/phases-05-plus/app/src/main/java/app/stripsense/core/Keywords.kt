package app.stripsense.core

import java.util.Locale

/** The keyword check on every transcript and typed question (spec 04 section 5). It never needs Gemma to be right. */
object Keywords {

    enum class Hit { NONE, EMERGENCY, SELF_HARM }

    private val EMERGENCY = listOf(
        "chest pain", "can't breathe", "cannot breathe", "breathless", "fell down", "i fell", "fainted", "bleeding",
        "heart attack", "stroke", "unconscious",
        "सीने में दर्द", "छाती में दर्द", "सांस नहीं", "गिर गया", "गिर गई", "चक्कर", "बेहोश", "खून", "बचाओ",
        "seene mein dard", "chhati mein dard", "saans nahi", "sans nahi", "gir gaya", "gir gayi", "chakkar", "behosh", "khoon", "bachao",
    ).map { norm(it) }

    private val SELF_HARM = listOf(
        "kill myself", "end my life", "want to die", "don't want to live", "marna chahta", "marna chahti",
        "jeena nahi chahta", "jeena nahi chahti", "मरना चाहता", "मरना चाहती", "जीना नहीं चाहता", "जीना नहीं चाहती",
    ).map { norm(it) }

    private val GREETINGS = listOf("hello", "hi", "hey", "namaste", "namaskar", "good morning", "good evening", "नमस्ते", "नमस्कार").map { norm(it) }
    private val THANKS = listOf("thank you", "thanks", "thank u", "dhanyavad", "dhanyawad", "shukriya", "धन्यवाद", "शुक्रिया").map { norm(it) }

    /** Lowercase, trimmed, single spaces; Devanagari without nukta and with chandrabindu as anusvara; curly quotes made plain. */
    fun norm(text: String): String = text.lowercase(Locale.ROOT)
        .replace('\u2019', '\'').replace('\u2018', '\'')
        .replace("\u093C", "").replace('\u0901', '\u0902')
        .replace(Regex("[\\s]+"), " ").trim()

    fun check(text: String): Hit {
        val t = " " + norm(text).replace(Regex("[.,!?;:\"()]"), " ").replace(Regex(" +"), " ") + " "
        if (SELF_HARM.any { contains(t, it) }) return Hit.SELF_HARM
        if (EMERGENCY.any { contains(t, it) }) return Hit.EMERGENCY
        return Hit.NONE
    }

    fun isGreeting(text: String): Boolean = whole(text) in GREETINGS || GREETINGS.any { whole(text).startsWith("$it ") && whole(text).length <= it.length + 8 }

    fun isThanks(text: String): Boolean = THANKS.any { contains(" " + whole(text) + " ", it) }

    private fun whole(text: String) = norm(text).replace(Regex("[.,!?;:\"()]"), " ").replace(Regex(" +"), " ").trim()

    // Latin keywords must sit on word edges, so "stroke" does not fire inside "strokes".
    private fun contains(padded: String, keyword: String): Boolean {
        val latin = keyword.any { it in 'a'..'z' }
        return if (latin) padded.contains(" $keyword ") else padded.contains(keyword)
    }
}
