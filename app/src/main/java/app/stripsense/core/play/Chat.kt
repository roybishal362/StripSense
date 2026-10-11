package app.stripsense.core.play

import java.time.LocalDate

/** How a gift was, in the person's own answer. */
enum class Liked { LOVED, OKAY, NOT_YET }

/** How the day is going, in the person's own answer. */
enum class DayFeel { GOOD, SO_SO, TOUGH }

/** What Pattu asks about today: a gift it gave before, or how the day is going. */
sealed interface ChatTopic {
    data class AboutGift(val given: Given) : ChatTopic
    data object AboutDay : ChatTopic
}

/**
 * Today's chit chat. Pattu remembers what it gave: on a later day it asks how that was, once, and what the person
 * loved decides which kind of gift it offers first next time. On a day with nothing to ask about, it asks how the
 * day is going. It is small talk; it asks nothing about health and gives no advice.
 * Answers about gifts are kept as "giftId|LOVED".
 */
object Chats {
    fun answers(talked: List<String>): Map<String, Liked> = talked.mapNotNull { entry ->
        runCatching { entry.substringBefore('|') to Liked.valueOf(entry.substringAfter('|')) }.getOrNull()
    }.toMap()

    /**
     * [kept]: the gifts given. [talked]: the answers about them. [dayAnswered]: today's "how is your day" was answered.
     * A thought is not asked about. A gift of today is asked about from tomorrow. "Not yet" is asked again another day.
     */
    fun topic(kept: List<String>, talked: List<String>, today: LocalDate, dayAnswered: Boolean, askedToday: Boolean = false): ChatTopic? {
        val answered = answers(talked)
        val waiting = Gifts.given(kept).lastOrNull { it.gift.kind != GiftKind.THOUGHT && it.day < today.toString() && answered[it.gift.id].let { a -> a == null || a == Liked.NOT_YET } }
        return when {
            waiting != null && !askedToday -> ChatTopic.AboutGift(waiting)
            !dayAnswered -> ChatTopic.AboutDay
            else -> null
        }
    }

    /** The kind of gift the person has loved most often, to be offered first. Null until something was loved. */
    fun favourite(kept: List<String>, talked: List<String>): GiftKind? {
        val answered = answers(talked)
        return Gifts.given(kept).filter { answered[it.gift.id] == Liked.LOVED }.groupingBy { it.gift.kind }.eachCount().maxByOrNull { it.value }?.key
    }

    fun entry(gift: Gift, liked: Liked): String = "${gift.id}|${liked.name}"
}
