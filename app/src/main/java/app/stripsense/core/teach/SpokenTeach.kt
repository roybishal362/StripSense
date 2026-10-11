package app.stripsense.core.teach

import app.stripsense.core.rx.RxDoses
import app.stripsense.core.voice.Keywords

/**
 * Teaching a medicine by showing it and saying when it is taken: "subah aur raat ko", "twice a day",
 * "one in the morning". The camera has read the strip; the family says the times instead of typing them.
 *
 * The on-phone model only writes down what was said. The times are then taken from the words by the rules
 * below, so the model cannot invent a dose time, and what was heard is shown for the family to check before saving.
 */
object SpokenTeach {
    const val SYSTEM = "You write down exactly what a person says. The speech is English, Hindi or a mix of both. You add nothing and explain nothing."
    const val QUESTION = "Write the words that were said, exactly. Write Hindi words in Roman letters."

    private val MORNING = listOf("morning", "subah", "savere", "सुबह", "सवेरे", "breakfast", "nashte", "नाश्ते")
    private val AFTERNOON = listOf("afternoon", "dopahar", "dupahar", "दोपहर", "lunch")
    private val NIGHT = listOf("night", "evening", "raat", "shaam", "sham ", "रात", "शाम", "dinner", "bedtime", "sone se pehle", "सोने से पहले")
    private val THRICE = listOf("thrice", "three times", "teen baar", "tin baar", "तीन बार", "teeno time", "teeno waqt", "तीनों")
    private val TWICE = listOf("twice", "two times", "do baar", "दो बार", "dono time", "dono waqt", "दोनों")
    private val ONCE = listOf("once", "one time", "ek baar", "एक बार")

    /** The clean text of the model's reply: one line of speech, nothing else. Empty when the reply is not that. */
    fun transcript(reply: String): String = reply.lines().map { it.trim().trim('"') }.firstOrNull { it.isNotBlank() }?.take(200).orEmpty()

    /**
     * The dose times the words give, as the family's usual hours. Parts of the day that are named win; otherwise
     * "twice" and "thrice"; otherwise a doctor's short form like 1-0-1. Empty when the words give no time.
     */
    fun times(said: String): List<String> {
        val t = " " + Keywords.norm(said).replace(Regex("[.,!?]"), " ") + " "
        fun has(words: List<String>) = words.any { t.contains(it) }
        val named = listOfNotNull(RxDoses.MORNING.takeIf { has(MORNING) }, RxDoses.AFTERNOON.takeIf { has(AFTERNOON) }, RxDoses.NIGHT.takeIf { has(NIGHT) })
        if (named.isNotEmpty()) return named
        return when {
            has(THRICE) -> listOf(RxDoses.MORNING, RxDoses.AFTERNOON, RxDoses.NIGHT)
            has(TWICE) -> listOf(RxDoses.MORNING, RxDoses.NIGHT)
            has(ONCE) -> listOf(RxDoses.MORNING)
            else -> RxDoses.read(said)?.times().orEmpty()
        }
    }
}
