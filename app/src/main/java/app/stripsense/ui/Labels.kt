package app.stripsense.ui

/** Button and screen labels in English and Hindi (spec 03 section 5). Answers never live here. */
object Labels {
    private val table: Map<String, Pair<String, String>> = mapOf(
        "appName" to ("StripSense" to "StripSense"),
        "help" to ("Help" to "मदद"),
        "today" to ("Today" to "आज"),
        "askTitle" to ("Ask" to "पूछें"),
        "scan" to ("Scan" to "स्कैन"),
        "numbers" to ("Numbers" to "आँकड़े"),
        "family" to ("Family" to "परिवार"),
        "close" to ("Close" to "बंद करें"),
        "back" to ("Back" to "वापस"),
        "call112" to ("Call 112" to "112 पर फ़ोन करें"),
        "call" to ("Call {c}" to "{c} को फ़ोन करें"),
        "helpIntro" to ("Who do you want to call?" to "किसे फ़ोन करना है?"),
        // Labels added in P01 for the empty screens; later stages replace these screens.
        "soonToday" to ("Your medicines for today will be listed here." to "यहाँ आज की दवाएँ दिखेंगी।"),
        "soonAsk" to ("You will ask about your medicines here, by voice or by typing." to "यहाँ आप बोलकर या लिखकर अपनी दवाओं के बारे में पूछ सकेंगे।"),
        "soonScan" to ("The camera will read a strip here." to "यहाँ कैमरा पट्टी पढ़ेगा।"),
        "soonNumbers" to ("Trial results will be shown here." to "यहाँ परीक्षण के नतीजे दिखेंगे।"),
        "soonFamily" to ("The family will manage the list here." to "यहाँ परिवार सूची संभालेगा।"),
    )

    fun get(key: String, language: String): String {
        val pair = table[key] ?: return key
        return if (language == "hi") pair.second else pair.first
    }
}
