package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*

import org.junit.Assert.*
import org.junit.Test

class HandAndCameraAgreeTest {
    private val med = Fixtures.M1
    private val timing = MedicationTiming(TimingKind.UNCONFIRMED)
    private val found = ScanMessage(ScanHint.FOUND, ScanTarget(1, med, timing, ScanSide.SINGLE, false))
    private val pointed = ScanMessage(ScanHint.HAND, hand = HandCue.Info(med, PlacedKind.CHECKED, timing))

    @Test fun aHandFlickeringInAndOutDoesNotMakeThePhoneSayTheSameStripAgainAndAgain() {
        val queue = ScanAnnouncements()
        queue.next(found, 0)
        assertNotNull(queue.next(found, ScanAnnouncements.FOUND_MS))
        var said = 0
        var t = 1000L
        // Fifteen seconds of a hand seen for half a second in every second.
        repeat(15) {
            for (step in 0 until 3) { if (queue.next(pointed, t) != null) said++; t += 150 }
            for (step in 0 until 3) { if (queue.next(found, t) != null) said++; t += 150 }
        }
        assertTrue("said $said times", said <= 1)
    }
    @Test fun aWarningTheFingerAnswerDidNotCarryIsStillSaidOnce() {
        val queue = ScanAnnouncements()
        queue.next(pointed, 0)
        assertNotNull(queue.next(pointed, ScanAnnouncements.TOUCH_MS))
        val shared = SharedIngredient(Fixtures.M2, "paracetamol", java.time.LocalTime.of(14, 49))
        val warned = found.copy(shared = shared)
        queue.next(warned, 4000)
        assertNotNull(queue.next(warned, 4000 + ScanAnnouncements.FOUND_MS))
        assertNull(queue.next(warned, 9000))
        assertNull(queue.next(pointed, 9100).also { queue.next(pointed, 9400) })
    }
}
