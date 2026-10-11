package app.stripsense.core.say

import app.stripsense.core.play.Badge
import app.stripsense.core.play.FeelItem
import app.stripsense.core.play.Journey
import app.stripsense.core.play.Journeys
import app.stripsense.core.play.Mood
import app.stripsense.core.play.QuestKind

/** What is shown and said on the journey: Pattu, the row of days, the level, today's goals, the badges, the game. Fixed sentences. */
object PlayText {
    /**
     * What Pattu says when it is tapped. A few lines for each mood, taken in turn ([turn] counts the taps).
     * It is cheerful and never scolds: a missed dose is "today we go again". It says when a dose is due, which the
     * family's own schedule decides; it never says what to take or how much.
     */
    fun pattu(mood: Mood, streak: Int, turn: Int, lang: String): String {
        val hi = lang == "hi"
        val lines = when (mood) {
            Mood.WAITING -> if (hi) listOf("सुनिए, दवा का समय हो गया है। मैं घड़ी देख रहा हूँ।", "एक पत्ता, एक स्कैन, और मैं बढ़ जाऊँगा। पक्का?")
                else listOf("Psst. It is medicine time. I am watching the clock.", "One strip, one scan, and I grow. Deal?")
            Mood.PROUD -> if (hi) listOf("आज की सब खुराकें हो गईं। ख़ुशी से फूल खिलने वाला है।", "आज पूरा हुआ। कल मिलते हैं।")
                else listOf("All done for today. I am so proud I might flower.", "Today is complete. See you tomorrow.")
            Mood.SAD -> if (hi) listOf("कल आपकी याद आई। आज से फिर शुरू करते हैं।", "एक खुराक छूट गई। होता है। आज फिर से।")
                else listOf("I missed you yesterday. Let us start again today.", "One dose slipped. It happens. Today we go again.")
            Mood.SLEEPY -> if (hi) listOf("रात हो गई। आराम कीजिए। सुबह की दवा के लिए मैं जगा दूँगा।")
                else listOf("It is late. Sleep well. I will wake you for the morning dose.")
            Mood.HAPPY -> if (streak >= 2) {
                if (hi) listOf("लगातार $streak दिन। मैं आपकी वजह से बढ़ रहा हूँ।", "देखिए मैं कितना बढ़ गया। यह आपने किया है।")
                else listOf("$streak days in a row. I am growing because of you.", "Look how I have grown. You did that.")
            } else {
                if (hi) listOf("नमस्ते, मैं पत्तू हूँ। हर खुराक के साथ मैं बढ़ता हूँ।", "फ़ोन को पत्ता दिखाइए, मुझे दुगने अंक मिलेंगे।")
                else listOf("Hi, I am Pattu. I grow with every dose.", "Show the phone your strip and I get double points.")
            }
        }
        return lines[Math.floorMod(turn, lines.size)]
    }

    /** Under the big number of days. */
    fun streak(j: Journey, lang: String): String {
        val hi = lang == "hi"
        return when {
            j.streak == 0 -> if (hi) "आज से शुरू कीजिए" else "Start your row today"
            j.atRisk -> if (hi) "आज पूरा कीजिए, सिलसिला बना रहेगा" else "Finish today to keep it"
            j.streak == 1 -> if (hi) "दिन। कल फिर।" else "day. Again tomorrow."
            else -> if (hi) "दिन लगातार" else "days in a row"
        }
    }

    fun level(j: Journey, lang: String): String =
        if (lang == "hi") "स्तर ${j.level.number} · ${j.level.hi}" else "Level ${j.level.number} · ${j.level.en}"

    /** How far it is to the next level, by its name. */
    fun toNext(j: Journey, lang: String): String {
        val left = j.toNextLevel ?: return if (lang == "hi") "सबसे ऊँचा स्तर" else "The highest level"
        val next = Journeys.level(j.level.next ?: j.points)
        return if (lang == "hi") "${next.hi} तक $left अंक" else "$left points to ${next.en}"
    }

    fun points(points: Int, lang: String): String = if (lang == "hi") "$points अंक" else "$points points"

    /** How points are earned. The phone's own witness counts for more than a press. */
    fun rule(lang: String): String =
        if (lang == "hi") "बटन दबाने पर ${Journeys.PRESSED} अंक। फ़ोन पत्ता जाँचे तो ${Journeys.SEEN}। फ़ोन गोली निकलते सुने तो ${Journeys.HEARD}। पूरा दिन: ${Journeys.FULL_DAY} और।"
        else "A press earns ${Journeys.PRESSED} points. A strip the phone checked earns ${Journeys.SEEN}. A tablet the phone heard earns ${Journeys.HEARD}. A full day: ${Journeys.FULL_DAY} more."

    fun today(j: Journey, lang: String): String =
        if (lang == "hi") "आज: ${j.today.all} में से ${j.today.marked} खुराक" else "Today: ${j.today.marked} of ${j.today.all} doses"

    fun quest(kind: QuestKind, lang: String): String {
        val hi = lang == "hi"
        return when (kind) {
            QuestKind.ALL_TODAY -> if (hi) "आज की हर खुराक दर्ज कीजिए" else "Mark every dose of today"
            QuestKind.SHOW_STRIP -> if (hi) "फ़ोन से एक पत्ता जँचवाइए" else "Let the phone check one strip"
            QuestKind.GIFT -> if (hi) "आज का तोहफ़ा खोलिए" else "Open today's gift"
        }
    }

    // ---- tic-tac-toe with Pattu ----

    fun tttTurn(lang: String): String = if (lang == "hi") "आपकी बारी" else "Your turn"
    fun tttThinking(lang: String): String = if (lang == "hi") "पत्तू सोच रहा है..." else "Pattu is thinking..."

    /** [points]: what this win added to the journey, 0 when today's win was already counted. */
    fun tttEnd(winner: app.stripsense.core.play.Mark, points: Int, lang: String): String {
        val hi = lang == "hi"
        return when (winner) {
            app.stripsense.core.play.Mark.YOU -> (if (hi) "आप जीत गए!" else "You win!") + if (points > 0) " " + gained(points, lang) else ""
            app.stripsense.core.play.Mark.PATTU -> if (hi) "यह बाज़ी पत्तू की। फिर से?" else "Pattu wins this one. Again?"
            app.stripsense.core.play.Mark.NONE -> if (hi) "बराबरी। अच्छा खेले।" else "A draw. Well played."
        }
    }

    // ---- the day's gift ----

    /** Why today's gift is still closed. */
    fun giftLocked(j: Journey, lang: String): String {
        val hi = lang == "hi"
        return when {
            j.today.state == app.stripsense.core.play.DayState.MISSED -> if (hi) "खुराक छूटने पर आज का तोहफ़ा बंद रहता है। कल नया आएगा।" else "Today's gift stays closed after a missed dose. A new one comes tomorrow."
            j.dueNow > 0 -> if (hi) "जो खुराक अभी लेनी है उसे दर्ज कीजिए, आज का तोहफ़ा खुल जाएगा।" else "Mark the dose that is due and today's gift opens."
            else -> if (hi) "दिन की पहली खुराक के साथ आज का तोहफ़ा खुलेगा।" else "Today's gift opens with your first dose of the day."
        }
    }

    fun giftChoose(lang: String): String = if (lang == "hi") "आज आपकी दवा समय पर है। अपना तोहफ़ा चुनिए।" else "Your medicine is on track today. Choose your gift."
    fun giftLoved(lang: String): String = if (lang == "hi") "आपको पसंद आया था" else "You loved one"
    fun collection(count: Int, lang: String): String = if (lang == "hi") "पत्तू ने अब तक जो दिया ($count)" else "Everything Pattu has given you ($count)"

    // ---- today's chit chat ----

    fun chatAsk(topic: app.stripsense.core.play.ChatTopic, lang: String): String {
        val hi = lang == "hi"
        return when (topic) {
            is app.stripsense.core.play.ChatTopic.AboutGift -> {
                val title = topic.given.gift.title
                if (topic.given.gift.kind == app.stripsense.core.play.GiftKind.BOOK) (if (hi) "मैंने आपको \"$title\" बताई थी। पढ़ना शुरू किया क्या?" else "I gave you \"$title\". Did you start reading it?")
                else (if (hi) "मैंने आपको \"$title\" बताया था। देखा क्या?" else "I gave you \"$title\". Did you watch it?")
            }
            app.stripsense.core.play.ChatTopic.AboutDay -> if (hi) "आज का दिन कैसा चल रहा है?" else "How is your day going?"
        }
    }

    fun liked(liked: app.stripsense.core.play.Liked, lang: String): String {
        val hi = lang == "hi"
        return when (liked) {
            app.stripsense.core.play.Liked.LOVED -> if (hi) "बहुत पसंद आया" else "Loved it"
            app.stripsense.core.play.Liked.OKAY -> if (hi) "ठीक था" else "It was okay"
            app.stripsense.core.play.Liked.NOT_YET -> if (hi) "अभी नहीं" else "Not yet"
        }
    }

    fun dayFeel(feel: app.stripsense.core.play.DayFeel, lang: String): String {
        val hi = lang == "hi"
        return when (feel) {
            app.stripsense.core.play.DayFeel.GOOD -> if (hi) "अच्छा" else "Good"
            app.stripsense.core.play.DayFeel.SO_SO -> if (hi) "ठीक-ठाक" else "So-so"
            app.stripsense.core.play.DayFeel.TOUGH -> if (hi) "मुश्किल" else "Tough"
        }
    }

    fun chatReply(liked: app.stripsense.core.play.Liked, lang: String): String {
        val hi = lang == "hi"
        return when (liked) {
            app.stripsense.core.play.Liked.LOVED -> if (hi) "मुझे पता था। ऐसा ही एक और ढूँढ़ूँगा।" else "I knew it. I will find you another one like it."
            app.stripsense.core.play.Liked.OKAY -> if (hi) "ठीक है। अगली बार कुछ अलग दूँगा।" else "Fair. Next time I will try a different kind."
            app.stripsense.core.play.Liked.NOT_YET -> if (hi) "कोई जल्दी नहीं। वह कहीं नहीं जा रहा। मैं फिर पूछूँगा।" else "No hurry. It will wait for you. I will ask again."
        }
    }

    fun dayReply(feel: app.stripsense.core.play.DayFeel, lang: String): String {
        val hi = lang == "hi"
        return when (feel) {
            app.stripsense.core.play.DayFeel.GOOD -> if (hi) "अच्छे दिन बाँटने के लिए होते हैं। आज किसी को बताइए।" else "Good days are for sharing. Tell someone today."
            app.stripsense.core.play.DayFeel.SO_SO -> if (hi) "ठीक-ठाक दिन भी बीत जाते हैं। आपने अपना हिस्सा कर दिया।" else "So-so days pass. You did your part today."
            app.stripsense.core.play.DayFeel.TOUGH -> if (hi) "मुश्किल दिन दुगने गिने जाते हैं। आप आए, मुझे गर्व है।" else "Tough days count double. I am proud of you for showing up."
        }
    }

    // ---- what Pattu offers with the alarm ----

    /** On the alarm's screen, before the dose: what is waiting after it. Only words. */
    fun teaser(kind: app.stripsense.core.play.InviteKind, lang: String): String {
        val hi = lang == "hi"
        return when (kind) {
            app.stripsense.core.play.InviteKind.CHAT -> if (hi) "इसके बाद: मेरे पास आपके लिए एक अच्छा सुझाव है।" else "After this one: I have something good for you."
            app.stripsense.core.play.InviteKind.TIC_TAC_TOE -> if (hi) "इसके बाद: मेरे साथ ज़ीरो-काटा की एक बाज़ी?" else "After this one: a hand of tic-tac-toe with me?"
            app.stripsense.core.play.InviteKind.CODE -> if (hi) "इसके बाद: आज का पैटर्न पहचान पाएँगे?" else "After this one: can you guess today's pattern?"
        }
    }

    /** After the dose is marked: Pattu asks. */
    fun invite(kind: app.stripsense.core.play.InviteKind, lang: String): String {
        val hi = lang == "hi"
        return when (kind) {
            app.stripsense.core.play.InviteKind.CHAT -> if (hi) "थोड़ी गपशप करें? मेरे पास आपके लिए एक अच्छा सुझाव है।" else "Want a little chit chat? I have a good recommendation for you."
            app.stripsense.core.play.InviteKind.TIC_TAC_TOE -> if (hi) "ज़ीरो-काटा की एक बाज़ी खेलें?" else "Want to play a hand of tic-tac-toe?"
            app.stripsense.core.play.InviteKind.CODE -> if (hi) "आज का पैटर्न पहचानें?" else "Want to guess today's pattern?"
        }
    }

    /** With nothing to ask about: an open door. */
    fun chatOpen(lang: String): String = if (lang == "hi") "अपने दिन के बारे में बताइए, या पूछिए कि क्या देखें।" else "Tell me about your day, or ask me what to watch."

    /** When the model on the phone is busy or gave nothing that can be shown. */
    fun chatBusy(lang: String): String = if (lang == "hi") "इस पर ज़रा सोचने दीजिए। थोड़ी देर में फिर पूछिए।" else "Let me think about that one. Ask me again in a moment."

    /** Under a Pattu that is not open yet. */
    fun skinLocked(level: Int, lang: String): String = if (lang == "hi") "स्तर $level" else "Level $level"

    /** When the person asks Pattu about medicines or health: it does not answer, and says where to ask. */
    fun chatNotMine(lang: String): String =
        if (lang == "hi") "मैं सिर्फ़ फ़िल्मों, किताबों और आपके दिन की बात करता हूँ। दवा के बारे में \"पूछिए\" टैब या अपने डॉक्टर से पूछिए।"
        else "I only chat about films, books and your day. For anything about medicine, use the Ask tab or ask your doctor."

    // ---- the daily code ----

    fun codeTitle(code: app.stripsense.core.play.DailyCode, lang: String): String =
        if (lang == "hi") "आज का कोड #${code.number}" else "Today's code #${code.number}"

    fun twistName(twist: app.stripsense.core.play.CodeTwist, lang: String): String {
        val hi = lang == "hi"
        return when (twist) {
            app.stripsense.core.play.CodeTwist.WAYS -> if (hi) "दिशाओं का दिन" else "Ways day"
            app.stripsense.core.play.CodeTwist.MEDICINES -> if (hi) "दवाओं की ताल का दिन" else "Rhythms day"
            app.stripsense.core.play.CodeTwist.MIXED -> if (hi) "मिला-जुला दिन" else "Mixed day"
            app.stripsense.core.play.CodeTwist.BACKWARDS -> if (hi) "उल्टा दिन" else "Backwards day"
        }
    }

    /** What to do, in one line, for this kind of day. */
    fun codeHow(twist: app.stripsense.core.play.CodeTwist, lang: String): String {
        val hi = lang == "hi"
        return if (twist == app.stripsense.core.play.CodeTwist.BACKWARDS) {
            if (hi) "पत्तू हाथ में कोड बजाएगा। उसे उल्टे क्रम में दबाइए।" else "Pattu plays a code in your hand. Press it back the other way round."
        } else {
            if (hi) "पत्तू हाथ में कोड बजाएगा। उसी क्रम में दबाइए। हर बार एक और जुड़ेगा।" else "Pattu plays a code in your hand. Press it back in the same order. It grows by one each time."
        }
    }

    fun codeWatch(lang: String): String = if (lang == "hi") "महसूस कीजिए..." else "Feel it..."
    fun codeTurn(lang: String): String = if (lang == "hi") "अब आपकी बारी" else "Your turn"
    fun codeStep(shown: Int, all: Int, lang: String): String = if (lang == "hi") "$all में से $shown" else "$shown of $all"
    fun codeWrong(lang: String): String = if (lang == "hi") "यह नहीं। फिर महसूस कीजिए।" else "Not that one. Feel it again."

    fun codeWon(game: app.stripsense.core.play.CodeGame, lang: String): String =
        if (lang == "hi") "कोड खुल गया! ${game.hearts} दिल बचे।" else "Code cracked! ${game.hearts} ${if (game.hearts == 1) "heart" else "hearts"} left."

    fun codeLost(lang: String): String = if (lang == "hi") "आज का कोड नहीं खुला। कल नया कोड आएगा।" else "Not cracked today. A new code comes tomorrow."

    fun codeRow(row: Int, lang: String): String = when {
        row <= 0 -> if (lang == "hi") "आज खोलिए और सिलसिला शुरू कीजिए" else "Crack it today to start a row"
        row == 1 -> if (lang == "hi") "1 दिन खोला" else "Cracked 1 day"
        else -> if (lang == "hi") "लगातार $row दिन खोला" else "Cracked $row days in a row"
    }

    fun codeTomorrow(twist: app.stripsense.core.play.CodeTwist, lang: String): String =
        if (lang == "hi") "कल: ${twistName(twist, lang)}" else "Tomorrow: ${twistName(twist, lang)}"

    /** What is sent when the person shares today's result. It gives nothing of the code away. */
    fun codeShare(game: app.stripsense.core.play.CodeGame, lang: String): String {
        val closed = if (game.blind) (if (lang == "hi") " आँखें बंद करके।" else " With my eyes closed.") else ""
        return if (lang == "hi") "StripSense कोड #${game.code.number}: ${game.code.signals.size} संकेत, ${game.hearts} दिल बचे।$closed मैंने इसे हाथ से महसूस करके खोला।"
        else "StripSense code #${game.code.number}: ${game.code.signals.size} signals, ${game.hearts} hearts left.$closed I cracked it by feeling it in my hand."
    }

    fun badge(badge: Badge, lang: String): String {
        val hi = lang == "hi"
        return when (badge) {
            Badge.FIRST_DOSE -> if (hi) "पहली खुराक" else "First dose"
            Badge.PHONE_SAW -> if (hi) "फ़ोन ने देखा" else "The phone saw it"
            Badge.PHONE_HEARD -> if (hi) "फ़ोन ने सुना" else "The phone heard it"
            Badge.THREE_DAYS -> if (hi) "लगातार 3 दिन" else "3 days in a row"
            Badge.SEVEN_DAYS -> if (hi) "पूरा हफ़्ता" else "A full week"
            Badge.CODE_CRACKED -> if (hi) "कोड खोला" else "Code cracked"
            Badge.BEAT_PATTU -> if (hi) "पत्तू को हराया" else "Beat Pattu"
            Badge.COLLECTOR -> if (hi) "5 तोहफ़े" else "5 gifts"
        }
    }

    /** Shown for a moment when a dose has just been marked. */
    fun gained(points: Int, lang: String): String = if (lang == "hi") "+$points अंक" else "+$points points"

    fun dayDone(streak: Int, lang: String): String = when {
        streak >= 2 -> if (lang == "hi") "आज पूरा हुआ। लगातार $streak दिन।" else "Today is complete. $streak days in a row."
        else -> if (lang == "hi") "आज पूरा हुआ।" else "Today is complete."
    }

    fun levelUp(j: Journey, lang: String): String = if (lang == "hi") "नया स्तर: ${j.level.hi}" else "New level: ${j.level.en}"

    fun right(row: Int, lang: String): String = when {
        row <= 1 -> if (lang == "hi") "सही।" else "Right."
        else -> if (lang == "hi") "सही। लगातार $row।" else "Right. $row in a row."
    }

    fun wrong(was: FeelItem, lang: String): String =
        if (lang == "hi") "वह था: ${was.hi}। फिर महसूस कीजिए।" else "That was: ${was.en}. Feel it again."

    fun ask(level: Int, lang: String): String {
        val hi = lang == "hi"
        return when (level) {
            1 -> if (hi) "हाँ या नहीं?" else "Yes or no?"
            2 -> if (hi) "कौन सी दवा?" else "Which medicine?"
            3 -> if (hi) "किस तरफ़?" else "Which way?"
            else -> if (hi) "वह क्या था?" else "What was that?"
        }
    }
}
