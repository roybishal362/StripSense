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

class HomingTest {
    private fun target(kind: TimingKind, nameOnly: Boolean = false, med: Medicine = Fixtures.M1) =
        ScanTarget(1, med, MedicationTiming(kind), ScanSide.SINGLE, nameOnly)

    @Test fun tickingIsFastestAtTheCentreAndSlowestAtTheEdge() {
        val centre = Homing.closeness(CropBox(450, 450, 550, 550), 1000, 1000)
        val edge = Homing.closeness(CropBox(0, 450, 20, 550), 1000, 1000)
        assertEquals(1f, centre, 0.001f)
        assertTrue(edge < 0.05f)
        assertEquals(Homing.NEAR_MS, Homing.intervalMs(centre))
        assertTrue(Homing.intervalMs(edge) > 850)
        assertTrue(Homing.intervalMs(0.5f) in (Homing.NEAR_MS + 1) until Homing.FAR_MS)
    }
    @Test fun centredOnlyNearTheMiddle() {
        assertTrue(Homing.locked(Homing.closeness(CropBox(460, 520, 560, 600), 1000, 1000)))
        assertFalse(Homing.locked(Homing.closeness(CropBox(700, 450, 800, 550), 1000, 1000)))
        assertEquals(0f, Homing.closeness(CropBox(0, 0, 10, 10), 0, 0), 0f)
    }
    @Test fun answerOpensOnlyAfterTheStripStaysCentred() {
        assertFalse(Homing.held(null, 10_000))
        assertFalse(Homing.held(1000, 1000 + Homing.HOLD_MS - 1))
        assertTrue(Homing.held(1000, 1000 + Homing.HOLD_MS))
    }
    @Test fun onlyTheCheckedDueStripIsHomedOn() {
        assertTrue(Homing.wanted(target(TimingKind.NOW), null))
        assertFalse(Homing.wanted(target(TimingKind.NOW, nameOnly = true), null))
        for (kind in listOf(TimingKind.NEXT, TimingKind.LATER, TimingKind.MARKED, TimingKind.NOT_SURE, TimingKind.STOPPED, TimingKind.NO_SCHEDULE))
            assertFalse(kind.name, Homing.wanted(target(kind), Fixtures.M1.id))
        // The dose the reminder asked about is still homed on after its fifteen minutes of "now".
        assertTrue(Homing.wanted(target(TimingKind.UNCONFIRMED), Fixtures.M1.id))
        assertFalse(Homing.wanted(target(TimingKind.UNCONFIRMED), Fixtures.M2.id))
    }
    @Test fun pieceAndTouchingStripsHaveTheirOwnGuidance() {
        val piece = ScanGuidance.message(null, false, false, false, false, false, piece = Fixtures.M1)
        assertEquals(ScanHint.PIECE, piece.hint)
        assertTrue(Sentences.scanGuidance(piece, "en").startsWith("This looks like your fever tablet"))
        val apart = ScanGuidance.message(null, false, false, false, false, false, tooClose = true)
        assertEquals("Two strips are touching. Move them a little apart.", Sentences.scanGuidance(apart, "en"))
        // A real difference still comes before either.
        assertEquals(ScanHint.CONFLICT, ScanGuidance.message(null, true, false, false, false, false, piece = Fixtures.M1, tooClose = true).hint)
    }
}
