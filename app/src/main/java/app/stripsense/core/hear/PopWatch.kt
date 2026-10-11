package app.stripsense.core.hear

/** A tablet was pushed out of its strip: the loudest crack of it, and how many cracks came with it. */
data class Pop(val atMs: Long, val clicks: Int, val above: Float, val level: Float)

/**
 * Decides whether the clicks the phone hears add up to a tablet pushed out of its strip.
 *
 * Foil tears with a crack and crackles around it, close to the phone. So a tablet is: a loud crack, with at
 * least one more click within half a second of it, while the phone itself was neither knocked nor speaking,
 * and with no voice, cough or door around it. One clap or one thing put down is a single sound and is not a tablet.
 *
 * Where sharp sounds keep coming, music with a beat, typing, plates, the crack must stand out much further:
 * a row of sharp sounds is what a tablet sounds like too. There the phone hears nothing rather than something
 * wrong, and the person presses Yes. A room that is only loud, people talking, a fan, is not such a room.
 *
 * It is only ever asked in the minute after the camera has checked the right strip and the phone has asked
 * "are you taking it now?". By itself a crack near the phone proves nothing; there, it answers the question.
 *
 * All times are milliseconds on one clock, the microphone's.
 */
class PopWatch(private val minLevel: Float = DEFAULT_LEVEL, private val strongDb: Float = STRONG_DB) {
    private class Heard(val click: Click, var judged: Boolean = false)
    private val heard = ArrayList<Heard>()
    private val jolts = ArrayList<Long>()
    private val deaf = ArrayList<LongRange>()

    /** The loudest crack with company heard so far, whether or not it was loud enough to count. For learning. */
    var loudest: Pop? = null
        private set

    @Synchronized fun click(click: Click) {
        heard += Heard(click)
        if (heard.size > KEEP) heard.removeAt(0)
    }

    /** The phone was knocked, picked up or put down at this moment. What it heard then was itself. */
    @Synchronized fun jolt(atMs: Long) {
        jolts += atMs
        if (jolts.size > KEEP) jolts.removeAt(0)
    }

    /** The phone was speaking or buzzing between these moments, or a voice or another long dark sound was heard. */
    @Synchronized fun deaf(fromMs: Long, untilMs: Long) {
        val last = deaf.lastOrNull()
        if (last != null && fromMs <= last.last + 1 && untilMs >= last.first) deaf[deaf.size - 1] = minOf(last.first, fromMs)..maxOf(last.last, untilMs)
        else deaf += fromMs..untilMs
        if (deaf.size > KEEP) deaf.removeAt(0)
    }

    /** Asked several times a second. Answers once a crack is old enough for everything around it to be known. */
    @Synchronized fun poll(nowMs: Long): Pop? {
        for (one in heard) {
            val click = one.click
            val needed = if (clicky(click.atMs)) maxOf(strongDb, STRONG_BUSY_DB) else strongDb
            if (one.judged || click.above < needed || nowMs - click.atMs < DECIDE_MS) continue
            one.judged = true
            if (own(click.atMs)) continue
            val company = heard.count { it !== one && kotlin.math.abs(it.click.atMs - click.atMs) <= COMPANY_MS && !own(it.click.atMs) }
            if (company < 1) continue
            val pop = Pop(click.atMs, company + 1, click.above, click.level)
            if (loudest.let { it == null || pop.level > it.level }) loudest = pop
            if (click.level >= minLevel) return pop
        }
        return null
    }

    /**
     * Sharp sounds have kept coming in the seconds before this moment: at three or more separate moments, each a
     * second or more after the one before. A beat does that. A strip turned over in the fingers crackles once or
     * twice, many clicks close together, and that is not it. The last second and a half is left out altogether:
     * a strip crackles just before its tablet comes out.
     */
    @Synchronized fun clicky(atMs: Long): Boolean {
        var moments = 0
        var last = Long.MIN_VALUE / 2
        for (one in heard) {
            val at = one.click.atMs
            if (at < atMs - BUSY_BACK_MS || at > atMs - BUSY_SKIP_MS || at - last < BUSY_APART_MS) continue
            last = at
            if (++moments >= BUSY_MOMENTS) return true
        }
        return false
    }

    /** The phone made or caused this sound itself. */
    private fun own(atMs: Long): Boolean =
        deaf.any { atMs >= it.first && atMs <= it.last } || jolts.any { kotlin.math.abs(it - atMs) <= JOLT_MS }

    companion object {
        /** The crack itself stands this far over the room. */
        const val STRONG_DB = 18f

        /** Sharp sounds at this many separate moments in the ten seconds before make a room of sharp sounds; there the crack must stand this far over it. */
        const val BUSY_MOMENTS = 3
        const val BUSY_APART_MS = 1_000L
        const val BUSY_BACK_MS = 10_000L
        const val BUSY_SKIP_MS = 1_500L
        const val STRONG_BUSY_DB = 26f

        /** A room louder than this in the high band drowns a tablet whatever else is true of it. */
        const val DROWNED_DB = 90f

        /** Its company: another click this close to it in time. */
        const val COMPANY_MS = 400L

        /** A knock on the phone this close to a click makes the click the phone's own. */
        const val JOLT_MS = 300L

        /** A crack is judged this long after it was heard, when what followed it is known. */
        const val DECIDE_MS = 450L

        /** How loud a crack must be on a phone that has not yet heard a tablet come out of this person's strips. */
        const val DEFAULT_LEVEL = 86f

        /** Never listened for more quietly than this, whatever has been learned. */
        const val LOWEST_LEVEL = 78f

        /** A learned loudness allows this much quieter than usual. */
        const val LEARNED_MARGIN = 9f
        private const val KEEP = 400

        /** The loudness to listen for, from what the phone has learned ([learned] is 0 before the first tablet). */
        fun levelFor(learned: Float): Float =
            if (learned <= 0f) DEFAULT_LEVEL else maxOf(LOWEST_LEVEL, learned - LEARNED_MARGIN)

        /** The learned loudness after one more tablet was heard: it follows new examples slowly. */
        fun learn(learned: Float, level: Float): Float = if (learned <= 0f) level else learned * 0.7f + level * 0.3f
    }
}
