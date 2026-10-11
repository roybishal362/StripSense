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

class ScanGuidanceTest {
    private val next = ScanTarget(1, Fixtures.M1, MedicationTiming(TimingKind.NOW), ScanSide.RIGHT, true)
    @Test fun dueSetNeverCreatesAnUnobservedTarget() {
        assertNull(ScanGuidance.target(emptyList(), Fixtures.M1.id))
        assertEquals(Fixtures.M2, ScanGuidance.target(listOf(next.copy(med = Fixtures.M2, timing = MedicationTiming(TimingKind.LATER))), Fixtures.M1.id)?.med)
    }
    @Test fun dueTargetWinsOverLaterMedicine() {
        assertEquals(next, ScanGuidance.target(listOf(next.copy(trackId = 2, timing = MedicationTiming(TimingKind.LATER)), next)))
    }
    @Test fun singleStripDoesNotInventAHandPosition() {
        assertEquals(ScanSide.SINGLE, ScanGuidance.side(CropBox(800, 0, 1000, 100), 1000, 1))
        assertEquals(ScanSide.RIGHT, ScanGuidance.side(CropBox(800, 0, 1000, 100), 1000, 2))
        assertEquals(ScanSide.CENTRE, ScanGuidance.side(CropBox(450, 0, 550, 100), 1000, 2))
    }
    @Test fun unstablePositionIsNotAnnounced() {
        val queue = ScanAnnouncements()
        val found = ScanMessage(ScanHint.FOUND, next)
        assertNull(queue.next(found, 0))
        assertNull(queue.next(found.copy(target = next.copy(side = ScanSide.LEFT)), 200))
        assertEquals(found, queue.next(found, 1000, repeat = true))
    }
    @Test fun stableTargetIsAnnouncedOnceAndCanBeRepeated() {
        val queue = ScanAnnouncements()
        val found = ScanMessage(ScanHint.FOUND, next)
        queue.next(found, 0)
        assertEquals(found, queue.next(found, ScanAnnouncements.FOUND_MS))
        assertNull(queue.next(found, 5000))
        assertEquals(found, queue.next(found, 5100, repeat = true))
    }
    @Test fun refusedStripDoesNotHideACheckedStripBesideIt() {
        val message = ScanGuidance.message(next, conflict = true, lostTarget = false, ambiguous = false, starting = false, smallPrint = false)
        assertEquals(ScanMessage(ScanHint.FOUND, next, otherConflict = true), message)
        assertTrue(Sentences.scanGuidance(message, "en").endsWith("Do not rely on it."))
        assertTrue(Sentences.scanGuidance(message, "hi").endsWith("उस पर भरोसा न करें।"))
        assertFalse(Sentences.scanGuidance(message.copy(otherConflict = false), "en").contains("Another strip"))
    }
    @Test fun refusalAloneIsStillTheConflictMessage() {
        assertEquals(ScanMessage(ScanHint.CONFLICT),
            ScanGuidance.message(null, conflict = true, lostTarget = true, ambiguous = true, starting = true, smallPrint = true))
    }
    @Test fun helpFallsBackInOrderWhenNothingIsFound() {
        fun of(lost: Boolean, ambiguous: Boolean, starting: Boolean, small: Boolean) =
            ScanGuidance.message(null, false, lost, ambiguous, starting, small).hint
        assertEquals(ScanHint.LOST, of(true, true, true, true))
        assertEquals(ScanHint.AMBIGUOUS, of(false, true, true, true))
        assertEquals(ScanHint.START, of(false, false, true, true))
        assertEquals(ScanHint.CLOSER, of(false, false, false, true))
        assertEquals(ScanHint.SEARCHING, of(false, false, false, false))
    }
    @Test fun refusalAppearingBesideTheTargetInterruptsTheCooldown() {
        val queue = ScanAnnouncements()
        val found = ScanMessage(ScanHint.FOUND, next)
        queue.next(found, 0, repeat = true)
        val warned = found.copy(otherConflict = true)
        assertNull(queue.next(warned, 100))
        assertEquals(warned, queue.next(warned, 100 + ScanAnnouncements.FOUND_MS))
        assertNull(queue.next(warned, 1200))
    }
    @Test fun lostAndConflictingTargetsInterruptWithoutWaitingForCooldown() {
        val queue = ScanAnnouncements()
        queue.next(ScanMessage(ScanHint.FOUND, next), 0, repeat = true)
        assertEquals(ScanMessage(ScanHint.LOST), queue.next(ScanMessage(ScanHint.LOST), 1))
        assertEquals(ScanMessage(ScanHint.CONFLICT), queue.next(ScanMessage(ScanHint.CONFLICT), 2))
    }
}
