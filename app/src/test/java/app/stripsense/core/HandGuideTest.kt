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

class HandGuideTest {
    private val due = Fixtures.M1
    private val other = Fixtures.M2
    private val left = CropBox(100, 400, 300, 600)
    private val right = CropBox(700, 400, 900, 600)
    private fun strip(med: Medicine?, kind: PlacedKind, box: CropBox, key: String = med?.id ?: "track-1") = PlacedStrip(key, med, kind, box, 0, 0)
    private val table = listOf(strip(other, PlacedKind.CHECKED, left), strip(due, PlacedKind.CHECKED, right))
    private val timing: (Medicine) -> MedicationTiming = { MedicationTiming(if (it.id == due.id) TimingKind.NOW else TimingKind.LATER) }
    private fun cue(x: Int, y: Int, strips: List<PlacedStrip> = table, wanted: Set<String> = setOf(due.id)) = HandGuide.cue(x, y, strips, wanted, timing)

    @Test fun handOnTheWrongStripIsToldWhichWayTheDueOneIs() {
        val no = cue(200, 500) as HandCue.No
        assertEquals(other, no.med)
        assertEquals(Toward.RIGHT, no.toward)
        val said = Sentences.handCue(no, "en")
        assertTrue(said, said.startsWith("Not this one. This is ") && said.endsWith("Yours is to the right."))
        assertTrue(Sentences.handCue(no, "hi").startsWith("यह नहीं।"))
    }
    @Test fun handOnTheDueStripIsYes() {
        assertEquals(HandCue.Yes(due), cue(800, 500))
        assertEquals("Yes. This is the one. fever tablet.", Sentences.handCue(HandCue.Yes(due), "en"))
    }
    @Test fun handInTheAirIsLedTowardTheDueStrip() {
        assertEquals(HandCue.Move(Toward.RIGHT), cue(500, 500))
        assertEquals(HandCue.Move(Toward.LEFT), cue(1100, 520))
        assertEquals(HandCue.Move(Toward.AWAY), cue(790, 950))
        assertEquals(HandCue.Move(Toward.CLOSER), cue(810, 50))
        assertEquals("Left.", Sentences.handCue(HandCue.Move(Toward.LEFT), "en"))
    }
    @Test fun aNameWhoseStrengthWasNotReadIsNeverYes() {
        val nameOnly = listOf(strip(due, PlacedKind.NAME, right))
        // Not "yes", and not "not this one" either: it is that medicine by name. The phone says what it read.
        val named = cue(800, 500, nameOnly) as HandCue.Info
        assertEquals(PlacedKind.NAME, named.kind)
        assertTrue(Sentences.handCue(named, "en").contains("Its strength was not read."))
        assertFalse(Sentences.handCue(named, "en").contains("Not this one"))
        // It is not led to either: there is nothing checked to lead the hand to.
        assertNull(cue(500, 500, nameOnly))
    }
    @Test fun refusedAndUnknownStripsAreNamedAsSuch() {
        val refused = cue(200, 500, listOf(strip(due, PlacedKind.REFUSED, left, "track-7"), strip(due, PlacedKind.CHECKED, right))) as HandCue.No
        assertTrue(Sentences.handCue(refused, "en").contains("differs from the saved medicine"))
        assertEquals(Toward.RIGHT, refused.toward)
        val unknown = cue(200, 500, listOf(strip(null, PlacedKind.UNKNOWN, left))) as HandCue.No
        assertTrue(Sentences.handCue(unknown, "en").contains("not one of your saved medicines"))
    }
    @Test fun withNothingDueTouchingAStripSimplyAsksWhatItIs() {
        val info = cue(200, 500, wanted = emptySet()) as HandCue.Info
        assertEquals(other, info.med)
        assertTrue(Sentences.handCue(info, "en").startsWith("This is "))
        assertNull(cue(500, 500, wanted = emptySet()))
    }
    @Test fun aFingerJustOffTheEdgeStillCountsAndFarOffDoesNot() {
        assertTrue(HandGuide.inside(left.right + 20, 500, left))
        assertFalse(HandGuide.inside(left.right + 120, 500, left))
    }
    @Test fun tickingFollowsTheHand() {
        val near = HandGuide.closeness(800, 500, right, 1000, 1000)
        val far = HandGuide.closeness(100, 100, right, 1000, 1000)
        assertEquals(1f, near, 0.001f)
        assertTrue(far < 0.2f)
    }
    @Test fun aTouchWaitsAMomentAndDirectionsRepeat() {
        val queue = ScanAnnouncements()
        val yes = ScanMessage(ScanHint.HAND, hand = HandCue.Yes(due))
        assertNull(queue.next(yes, 0))
        assertEquals(yes, queue.next(yes, ScanAnnouncements.TOUCH_MS))
        // The hand stays: the answer is not said again, however long it stays.
        for (t in 400..5000 step 150) assertNull(queue.next(yes, t.toLong()))
        // A hand that left and comes back after a while is asking again.
        val back = 5000 + ScanAnnouncements.REASK_MS + 100
        assertEquals(yes, queue.next(yes, back))
        val leftward = ScanMessage(ScanHint.HAND, hand = HandCue.Move(Toward.LEFT))
        assertEquals(leftward, queue.next(leftward, back + ScanAnnouncements.MOVE_NEW_MS))
        assertNull(queue.next(leftward, back + ScanAnnouncements.MOVE_NEW_MS + 500))
        assertEquals(leftward, queue.next(leftward, back + ScanAnnouncements.MOVE_NEW_MS + ScanAnnouncements.MOVE_AGAIN_MS))
    }
    @Test fun aHandInViewIsAnsweredBeforeAnythingElse() {
        val target = ScanTarget(1, due, MedicationTiming(TimingKind.NOW), ScanSide.RIGHT, false)
        assertEquals(ScanHint.HAND, ScanGuidance.message(target, true, true, true, true, true, hand = HandCue.Yes(due)).hint)
    }
}
