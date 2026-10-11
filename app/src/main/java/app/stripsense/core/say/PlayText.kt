package app.stripsense.core.say

import app.stripsense.core.play.FeelItem
import app.stripsense.core.play.Plant

/** What is shown and said on the Play screen: the plant, and "Feel and tell". Fixed sentences. */
object PlayText {
    /** The plant in one line: the row of full days, or what would make it grow. */
    fun plant(p: Plant, lang: String): String {
        val hi = lang == "hi"
        return when {
            p.streak >= 2 -> if (hi) "${p.streak} दिन लगातार। ऐसे ही चलने दीजिए।" else "${p.streak} days in a row. Keep it going."
            p.streak == 1 -> if (hi) "एक पूरा दिन। कल फिर कीजिए।" else "One full day. Do it again tomorrow."
            p.missed -> if (hi) "एक खुराक छूट गई। आज की खुराकें दर्ज कीजिए, पौधा फिर बढ़ेगा।" else "A dose was missed. Mark today's doses and it grows again."
            else -> if (hi) "आज की खुराकें दर्ज कीजिए, आपका पौधा बढ़ेगा।" else "Mark today's doses and your plant grows."
        }
    }

    /**
     * What Pattu says when it is tapped. A few lines for each mood, taken in turn ([turn] counts the taps).
     * It is cheerful and never scolds: a missed dose is "today we go again". It says when a dose is due, which the
     * family's own schedule decides; it never says what to take or how much.
     */
    fun pattu(mood: app.stripsense.core.play.Mood, p: Plant, turn: Int, lang: String): String {
        val hi = lang == "hi"
        val lines = when (mood) {
            app.stripsense.core.play.Mood.WAITING -> if (hi) listOf("सुनिए, दवा का समय हो गया है। मैं घड़ी देख रहा हूँ।", "एक पत्ता, एक स्कैन, और मैं बढ़ जाऊँगा। पक्का?")
                else listOf("Psst. It is medicine time. I am watching the clock.", "One strip, one scan, and I grow. Deal?")
            app.stripsense.core.play.Mood.PROUD -> if (hi) listOf("आज की सब खुराकें हो गईं। ख़ुशी से फूल खिलने वाला है।", "आज पूरा हुआ। कल मिलते हैं।")
                else listOf("All done for today. I am so proud I might flower.", "Today is complete. See you tomorrow.")
            app.stripsense.core.play.Mood.SAD -> if (hi) listOf("कल आपकी याद आई। आज से फिर शुरू करते हैं।", "एक खुराक छूट गई। होता है। आज फिर से।")
                else listOf("I missed you yesterday. Let us start again today.", "One dose slipped. It happens. Today we go again.")
            app.stripsense.core.play.Mood.SLEEPY -> if (hi) listOf("रात हो गई। आराम कीजिए। सुबह की दवा के लिए मैं जगा दूँगा।")
                else listOf("It is late. Sleep well. I will wake you for the morning dose.")
            app.stripsense.core.play.Mood.HAPPY -> if (p.streak >= 2) {
                if (hi) listOf("लगातार ${p.streak} दिन। मैं आपकी वजह से बढ़ रहा हूँ।", "मेरी पत्तियाँ देखिए। यह आपने किया है।")
                else listOf("${p.streak} days in a row. I am growing because of you.", "Look at my leaves. You did that.")
            } else {
                if (hi) listOf("नमस्ते, मैं पत्तू हूँ। हर खुराक के साथ मैं बढ़ता हूँ।", "फ़ोन को पत्ता दिखाइए, मुझे पत्ती मिलेगी।")
                else listOf("Hi, I am Pattu. I grow with every dose.", "Show the phone your strip and I get a leaf.")
            }
        }
        return lines[Math.floorMod(turn, lines.size)]
    }

    /** How it grows. The phone's own witness counts for more than a press. */
    fun plantRule(lang: String): String =
        if (lang == "hi") "बटन दबाने से कली आती है। फ़ोन पत्ता जाँचे तो पत्ती। फ़ोन गोली निकलते सुने तो फूल।"
        else "A button press grows a bud. A strip the phone checked grows a leaf. A tablet the phone heard grows a flower."

    fun plantCounts(p: Plant, lang: String): String =
        if (lang == "hi") "इस हफ़्ते: ${p.buds} कलियाँ · ${p.leaves} पत्तियाँ · ${p.flowers} फूल" else "This week: ${p.buds} buds · ${p.leaves} leaves · ${p.flowers} flowers"

    fun today(p: Plant, lang: String): String =
        if (lang == "hi") "आज ${p.todayAll} में से ${p.todayMarked} खुराकें दर्ज" else "Today: ${p.todayMarked} of ${p.todayAll} doses marked"

    fun right(streak: Int, lang: String): String = when {
        streak <= 1 -> if (lang == "hi") "सही।" else "Right."
        else -> if (lang == "hi") "सही। लगातार $streak।" else "Right. $streak in a row."
    }

    fun wrong(was: FeelItem, lang: String): String =
        if (lang == "hi") "वह था: ${was.hi}। फिर महसूस कीजिए।" else "That was: ${was.en}. Feel it again."

    fun level(level: Int, lang: String): String {
        val hi = lang == "hi"
        return when (level) {
            1 -> if (hi) "हाँ या नहीं?" else "Yes or no?"
            2 -> if (hi) "कौन सी दवा?" else "Which medicine?"
            3 -> if (hi) "किस तरफ़?" else "Which way?"
            else -> if (hi) "वह क्या था?" else "What was that?"
        }
    }
}
