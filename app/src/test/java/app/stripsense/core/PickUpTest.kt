package app.stripsense.core

import app.stripsense.core.guide.PickUp
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** Picking the phone up answers the alarm. Readings come 50 times a second. */
class PickUpTest {
    private val g = 9.81f
    private val random = Random(5)
    private fun noise(size: Float) = (random.nextFloat() - 0.5f) * 2 * size

    /** Feeds [ms] of readings around one resting direction; returns the time the pick-up was noticed, if it was. */
    private fun feed(pick: PickUp, from: Long, ms: Long, x: Float, y: Float, z: Float, shake: Float = 0.03f): Long? {
        var at = from
        while (at < from + ms) {
            if (pick.reading(x + noise(shake), y + noise(shake), z + noise(shake), at)) return at
            at += 20
        }
        return null
    }

    @Test fun lyingFlatThenLiftedAndTiltedIsAPickUp() {
        val pick = PickUp()
        assertNull(feed(pick, 0, 2000, 0f, 0f, g))
        val noticed = feed(pick, 2000, 1500, 0f, g * 0.7f, g * 0.7f, shake = 0.4f)
        assertNotNull(noticed)
        assertTrue("noticed at $noticed", noticed!! < 3000)
        // It is told once.
        assertNull(feed(pick, 3500, 1000, 0f, g * 0.7f, g * 0.7f))
    }

    @Test fun faceDownOnTheTableCountsAsLyingToo() {
        val pick = PickUp()
        assertNull(feed(pick, 0, 1500, 0f, 0f, -g))
        assertNotNull(feed(pick, 1500, 1500, g * 0.6f, 0f, -g * 0.8f, shake = 0.3f))
    }

    @Test fun theAlarmsOwnBuzzingAndAKnockOnTheTableAreNotAPickUp() {
        val pick = PickUp()
        assertNull(feed(pick, 0, 1500, 0f, 0f, g))
        // The buzz shakes the phone hard but does not tilt it.
        assertNull(feed(pick, 1500, 600, 0f, 0f, g, shake = 3f))
        assertNull(feed(pick, 2100, 3000, 0f, 0f, g))
        // One hard knock.
        assertFalse(pick.reading(4f, -3f, g + 6f, 5100))
        assertNull(feed(pick, 5120, 1500, 0f, 0f, g))
    }

    @Test fun aPhoneAlreadyInTheHandIsNotPickedUpByMovingIt() {
        val pick = PickUp()
        // Held at an angle, as a phone being read is. It never lay flat, so tilting it further means nothing.
        assertNull(feed(pick, 0, 2000, 0f, g * 0.64f, g * 0.77f, shake = 0.15f))
        assertNull(feed(pick, 2000, 2000, g * 0.5f, g * 0.7f, g * 0.5f, shake = 0.3f))
    }

    @Test fun aSmallNudgeOnTheTableIsNotAPickUp() {
        val pick = PickUp()
        assertNull(feed(pick, 0, 1500, 0f, 0f, g))
        // Tilted by about ten degrees, as when one corner is lifted to look.
        assertNull(feed(pick, 1500, 2000, 0f, g * 0.17f, g * 0.985f, shake = 0.2f))
    }
}
