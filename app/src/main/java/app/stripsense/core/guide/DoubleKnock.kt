package app.stripsense.core.guide

import kotlin.math.sqrt

/**
 * Feels two knocks on the phone's body, from its movement sensor. It is how a person says yes with no button to
 * find and no word to speak: the phone asks through the hand, and the hand answers.
 *
 * A knock is a short, hard jolt. Two of them, a fifth to three fifths of a second apart, with the phone quiet
 * before, between and just after, is the answer. Carrying the phone, putting it down, shaking it, or the phone's
 * own motors running are longer or messier than that and are not knocks.
 */
class DoubleKnock {
    private val mean = FloatArray(3)
    private var started = false
    private var lastAt = 0L

    // The stretch of movement going on now, if any.
    private var burstFrom = -1L
    private var burstPeak = 0f
    private var burstLast = 0L

    /** When the last stretch of movement ended, and when the first knock of a pair began. */
    private var quietFrom = Long.MIN_VALUE / 2
    private var first = -1L
    private var second = -1L

    /**
     * One reading in metres per second squared, with its time. [own]: the phone's own motor is running, so this
     * movement is the phone's. True once for each pair of knocks, a moment after the second.
     */
    fun reading(x: Float, y: Float, z: Float, atMs: Long, own: Boolean = false): Boolean {
        if (!started) { mean[0] = x; mean[1] = y; mean[2] = z; started = true; lastAt = atMs; quietFrom = atMs; return false }
        val dt = (atMs - lastAt).coerceIn(1, 100).toFloat()
        lastAt = atMs
        val dx = x - mean[0]; val dy = y - mean[1]; val dz = z - mean[2]
        val jolt = sqrt(dx * dx + dy * dy + dz * dz)
        // The slow part is followed, so tilting the phone is not a jolt. A jolt itself hardly moves it.
        val follow = dt / (FOLLOW_MS + dt)
        mean[0] += dx * follow; mean[1] += dy * follow; mean[2] += dz * follow
        if (own) { forget(atMs); return false }

        if (jolt >= MOVE) {
            if (burstFrom < 0) { burstFrom = atMs; burstPeak = 0f }
            burstPeak = maxOf(burstPeak, jolt)
            burstLast = atMs
            // Movement that goes on is not a knock, and ends any pair that was forming.
            if (atMs - burstFrom > KNOCK_MAX_MS) { first = -1; second = -1 }
            return false
        }
        if (burstFrom >= 0 && atMs - burstLast >= SETTLE_MS) {
            val from = burstFrom
            val knock = burstPeak >= KNOCK && burstLast - from <= KNOCK_MAX_MS
            val quietBefore = from - quietFrom
            burstFrom = -1
            quietFrom = burstLast
            when {
                !knock -> { first = -1; second = -1 }
                first < 0 || second >= 0 -> { first = if (quietBefore >= QUIET_BEFORE_MS) from else -1; second = -1 }
                from - first in PAIR_MIN_MS..PAIR_MAX_MS -> second = from
                // Too soon or too late to be the second of a pair: it may be the first of the next.
                else -> { first = if (quietBefore >= QUIET_BEFORE_MS) from else -1; second = -1 }
            }
        }
        // A pair counts once the phone has stayed quiet a moment after it: a third jolt at once would make it shaking.
        if (second >= 0 && burstFrom < 0 && atMs - quietFrom >= QUIET_AFTER_MS) {
            first = -1; second = -1
            return true
        }
        return false
    }

    private fun forget(atMs: Long) {
        burstFrom = -1; first = -1; second = -1; quietFrom = atMs
    }

    companion object {
        /** Above this the phone is being moved. A hand holding it still stays well under. */
        const val MOVE = 1.2f

        /** A knock reaches at least this. */
        const val KNOCK = 3.5f
        private const val FOLLOW_MS = 80f
        private const val SETTLE_MS = 40L
        private const val KNOCK_MAX_MS = 140L
        private const val PAIR_MIN_MS = 150L
        private const val PAIR_MAX_MS = 650L
        private const val QUIET_BEFORE_MS = 250L
        private const val QUIET_AFTER_MS = 200L
    }
}
