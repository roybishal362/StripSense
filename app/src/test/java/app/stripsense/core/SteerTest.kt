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

class SteerTest {
    private val med = Fixtures.M1
    private val w = 1000
    private val h = 1000

    @Test fun aStripOffToOneSideSaysWhichWayToMoveThePhone() {
        assertEquals(Toward.RIGHT, ScanGuidance.steer(CropBox(800, 450, 980, 550), w, h, false))
        assertEquals(Toward.LEFT, ScanGuidance.steer(CropBox(10, 450, 200, 550), w, h, false))
        assertEquals(Toward.AWAY, ScanGuidance.steer(CropBox(450, 10, 550, 120), w, h, false))
        assertEquals(Toward.CLOSER, ScanGuidance.steer(CropBox(450, 880, 550, 990), w, h, false))
        assertNull(ScanGuidance.steer(CropBox(450, 450, 550, 550), w, h, false))
        // A strip that has gone out of the picture altogether still has a direction.
        assertEquals(Toward.RIGHT, ScanGuidance.steer(CropBox(1300, 450, 1500, 550), w, h, false))
    }
    @Test fun theMiddleIsStickySoItDoesNotFlipBetweenRightAndHere() {
        val nearEdge = CropBox(700, 450, 800, 550)
        assertNotNull(ScanGuidance.steer(nearEdge, w, h, wasCentred = false))
        assertNull(ScanGuidance.steer(nearEdge, w, h, wasCentred = true))
    }
    @Test fun firstTheMedicineIsNamedThenOnlyTheDirectionThenHereItIs() {
        val queue = ScanAnnouncements()
        val right = ScanGuidance.message(null, false, false, false, false, false, steer = med to Toward.RIGHT)
        val first = queue.next(right, 0)!!
        assertEquals("Your fever tablet is on the right. Move the phone right.", Sentences.scanGuidance(first, "en"))
        assertNull(queue.next(right, 500))
        assertEquals("Right.", Sentences.scanGuidance(queue.next(right, ScanAnnouncements.MOVE_AGAIN_MS)!!, "en"))
        val left = right.copy(steer = Toward.LEFT)
        assertEquals("Left.", Sentences.scanGuidance(queue.next(left, ScanAnnouncements.MOVE_AGAIN_MS + ScanAnnouncements.MOVE_NEW_MS)!!, "en"))
        // Arrived: the answer follows the last direction without the usual pause, once it has been steady for a moment.
        val t = ScanAnnouncements.MOVE_AGAIN_MS + ScanAnnouncements.MOVE_NEW_MS
        val found = ScanMessage(ScanHint.FOUND, ScanTarget(1, med, MedicationTiming(TimingKind.NOW), ScanSide.SINGLE, false))
        assertNull(queue.next(found, t + 100))
        assertEquals("This is your fever tablet. It's time for this one now.", Sentences.scanGuidance(queue.next(found, t + 100 + ScanAnnouncements.FOUND_MS)!!, "en"))
    }
    @Test fun aDirectionWaitsForTheSentenceBeforeIt() {
        val queue = ScanAnnouncements()
        val right = ScanGuidance.message(null, false, false, false, false, false, steer = med to Toward.RIGHT)
        assertNull(queue.next(right, 0, busy = true))
        assertNotNull(queue.next(right, 100, busy = false))
    }
    @Test fun spokenSentencesAreShortAndSayWhatMatters() {
        fun found(kind: TimingKind, nameOnly: Boolean = false, side: ScanSide = ScanSide.SINGLE) =
            Sentences.scanGuidance(ScanMessage(ScanHint.FOUND, ScanTarget(1, med, MedicationTiming(kind, DoseSlot(med.id, java.time.LocalDate.of(2026, 10, 10), java.time.LocalTime.of(14, 35))), side, nameOnly)), "en")
        assertEquals("This is your fever tablet. The next dose is at 2:35 pm. Not yet.", found(TimingKind.NEXT))
        assertEquals("On the left. This is your fever tablet. The 2:35 pm dose is already marked.", found(TimingKind.MARKED, side = ScanSide.LEFT))
        assertTrue(found(TimingKind.NOW, nameOnly = true).endsWith("I have read its name, but not its strength yet."))
        // Never an instruction to take a late dose, and never "taken".
        for (kind in TimingKind.entries) assertFalse(found(kind).contains("taken", ignoreCase = true))
    }
    @Test fun aStripThatSlidOutOfViewIsRememberedWhereTheTableSaysItIs() {
        val table = TableMemory()
        table.see(med.id, med, PlacedKind.CHECKED, CropBox(700, 450, 900, 550), 0)
        // The phone moved left, so everything in the picture moved right: the strip is now beyond the right edge.
        table.drift(400, 0, live = emptySet())
        val ghost = table.current(1000).single()
        assertEquals(CropBox(1100, 450, 1300, 550), ghost.box)
        assertEquals(Toward.RIGHT, ScanGuidance.steer(ghost.box, w, h, false))
        // A strip read in this frame is where it was read, not where it drifted to.
        table.drift(50, 0, live = setOf(med.id))
        assertEquals(1100, table.current(1100, emptySet(), w, h).single().box.left)
    }
}
