package app.stripsense.core.play

import app.stripsense.core.guide.FeelLanguage
import app.stripsense.core.guide.Felt
import app.stripsense.core.guide.Toward
import app.stripsense.core.teach.PurposeKind
import kotlin.random.Random

/** One thing the phone can say through the hand, with its name in both languages. */
enum class FeelItem(val en: String, val hi: String) {
    YES("Yes", "हाँ"), NO("No", "नहीं"),
    BP("BP medicine", "बीपी की दवा"), SUGAR("Sugar medicine", "शुगर की दवा"), FEVER("Fever medicine", "बुख़ार की दवा"),
    LEFT("Left", "बाएँ"), RIGHT("Right", "दाएँ"), FORWARD("Forward", "आगे"), BACK("Back", "पीछे");

    fun name(lang: String) = if (lang == "hi") hi else en
}

/** One question: the pattern that is played is [answer], and [choices] are what the person may say it was. */
data class FeelRound(val choices: List<FeelItem>, val answer: FeelItem)

/**
 * "Feel and tell": the phone plays a pattern in the hand and the person says what it was.
 *
 * It is a game, and it is also how the language of touch is learned: the same patterns lead the hand to a strip,
 * name a medicine and say yes or no during a real dose. The questions get harder as the row of right answers
 * grows: yes or no, then which medicine, then which way to move, then any of them.
 */
object FeelQuiz {
    private val YES_NO = listOf(FeelItem.YES, FeelItem.NO)
    private val MEDICINES = listOf(FeelItem.BP, FeelItem.SUGAR, FeelItem.FEVER)
    private val WAYS = listOf(FeelItem.LEFT, FeelItem.RIGHT, FeelItem.FORWARD, FeelItem.BACK)

    /** 1: yes or no. 2: which medicine. 3: which way. 4: any of them. */
    fun level(streak: Int): Int = when { streak < 2 -> 1; streak < 5 -> 2; streak < 9 -> 3; else -> 4 }

    /** The next question. [last]: what was asked just before, so three choices or more never give the same answer twice running. */
    fun round(streak: Int, random: Random, last: FeelItem? = null): FeelRound {
        val pool = when (level(streak)) { 1 -> YES_NO; 2 -> MEDICINES; 3 -> WAYS; else -> listOf(YES_NO, MEDICINES, WAYS).random(random) }
        val fresh = if (pool.size > 2) pool.filter { it != last } else pool
        return FeelRound(pool, fresh.random(random))
    }

    /** Exactly what the scan and a real dose play for it. */
    fun felt(item: FeelItem): Felt = when (item) {
        FeelItem.YES -> FeelLanguage.yes()
        FeelItem.NO -> FeelLanguage.no()
        FeelItem.BP -> FeelLanguage.signature(PurposeKind.BP)
        FeelItem.SUGAR -> FeelLanguage.signature(PurposeKind.SUGAR)
        FeelItem.FEVER -> FeelLanguage.signature(PurposeKind.FEVER_PAIN)
        FeelItem.LEFT -> FeelLanguage.pull(Toward.LEFT, 1f)
        FeelItem.RIGHT -> FeelLanguage.pull(Toward.RIGHT, 1f)
        FeelItem.FORWARD -> FeelLanguage.pull(Toward.AWAY, 1f)
        FeelItem.BACK -> FeelLanguage.pull(Toward.CLOSER, 1f)
    }
}
