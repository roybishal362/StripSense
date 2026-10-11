package app.stripsense.core

import app.stripsense.core.guide.DoubleKnock
import kotlin.math.exp
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** Two knocks on the phone say yes. Readings come two hundred times a second. */
class DoubleKnockTest {
    private val g = 9.81f
    private val random = Random(11)

    /**
     * Runs [ms] of readings of a phone held or lying still, with knocks at the given moments (each a hard jolt that
     * rings out over a few hundredths of a second) and anything [extra] adds. Returns the times a pair was felt.
     */
    private fun run(ms: Long, knocks: List<Long>, tremor: Float = 0.15f, own: (Long) -> Boolean = { false }, extra: (Long) -> Float = { 0f }): List<Long> {
        val knock = DoubleKnock()
        val felt = ArrayList<Long>()
        var at = 0L
        while (at < ms) {
            var jolt = extra(at)
            for (k in knocks) if (at >= k && at - k < 60) jolt += 9f * exp(-(at - k) / 12f) * (if (((at - k) / 5) % 2 == 0L) 1 else -1)
            fun noise() = (random.nextFloat() - 0.5f) * 2 * tremor
            if (knock.reading(noise() + jolt * 0.6f, noise() + jolt * 0.3f, g + noise() + jolt, at, own(at))) felt += at
            at += 5
        }
        return felt
    }

    @Test fun twoKnocksAreFeltOnceAMomentAfterTheSecond() {
        val felt = run(3000, listOf(1000, 1320))
        assertEquals(1, felt.size)
        assertTrue("at ${felt.single()}", felt.single() in 1500..1750)
    }

    @Test fun oneKnockIsNotAnAnswerAndTwoFarApartAreNotAPair() {
        assertTrue(run(3000, listOf(1000)).isEmpty())
        assertTrue(run(4000, listOf(1000, 2000)).isEmpty())
        // Two so close together that they are one rattle, not two knocks.
        assertTrue(run(3000, listOf(1000, 1060)).isEmpty())
    }

    @Test fun aHandThatTremblesStillGetsItsTwoKnocksThrough() {
        assertEquals(1, run(3000, listOf(1200, 1550), tremor = 0.5f).size)
    }

    @Test fun shakingCarryingAndPuttingThePhoneDownAreNotKnocks() {
        // Shaken for a second.
        assertTrue(run(3000, emptyList()) { at -> if (at in 800..1800) 4f * kotlin.math.sin(at / 40f) else 0f }.isEmpty())
        // Moved for half a second and set down with a bump and a bounce.
        assertTrue(run(3000, listOf(1500, 1680)) { at -> if (at in 900..1490) 2.5f * kotlin.math.sin(at / 60f) else 0f }.isEmpty())
    }

    @Test fun thePhonesOwnMotorIsNeverAKnock() {
        // The motor runs from 900 to 1500; the same two jolts inside that time are the phone's own.
        assertTrue(run(3000, listOf(1000, 1320), own = { it in 900..1500 }).isEmpty())
        // Two knocks after it has stopped do count.
        assertEquals(1, run(3500, listOf(2000, 2300), own = { it in 900..1500 }).size)
    }

    @Test fun twoAnswersAreTwoPairs() {
        assertEquals(2, run(6000, listOf(1000, 1300, 3500, 3800)).size)
    }
}
