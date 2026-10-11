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

class StripTrackerTest {
    private val meds = Fixtures.LIST
    private fun obs(text: String, x: Int = 0) = StripObservation(CropBox(x, 0, x + 100, 60), Fixtures.lines(text), Fixtures.key(text))

    @Test fun ingredientFlickerDoesNotResetTheName() {
        val tracker = StripTracker()
        val a = tracker.update(listOf(obs("CALVORA")), meds, 100).single()
        val b = tracker.update(listOf(obs("CALVORA / CALCIUM 500 mg", 8)), meds, 200).single()
        assertEquals(a.id, b.id); assertEquals(2, b.nameFrames); assertEquals(1, b.frames)
    }
    @Test fun simultaneousCopiesHaveSeparateTrackIds() {
        val tracker = StripTracker()
        val a = tracker.update(listOf(obs("FEVORA"), obs("FEVORA", 300)), meds, 100)
        val b = tracker.update(listOf(obs("FEVORA", 302), obs("FEVORA", 2)), meds, 200)
        assertNotEquals(a[0].id, a[1].id)
        assertEquals(a[1].id, b[0].id); assertEquals(a[0].id, b[1].id)
    }
    @Test fun ambiguousCrossingCannotInheritEitherIdentity() {
        val tracker = StripTracker()
        val old = tracker.update(listOf(obs("FEVORA", 0), obs("FEVORA", 80)), meds, 100)
        val next = tracker.update(listOf(obs("FEVORA", 40)), meds, 200).single()
        assertFalse(next.id in old.map { it.id }); assertEquals(1, next.nameFrames)
    }
    @Test fun disappearedStripMustBeAcquiredAgain() {
        val tracker = StripTracker()
        val old = tracker.update(listOf(obs("FEVORA")), meds, 100).single()
        tracker.update(emptyList(), meds, 200)
        val next = tracker.update(listOf(obs("FEVORA")), meds, 300).single()
        assertNotEquals(old.id, next.id); assertEquals(1, next.nameFrames)
    }
    @Test fun timeGapCannotReuseASelection() {
        val tracker = StripTracker()
        val a = tracker.update(listOf(obs("FEVORA")), meds, 100).single()
        val b = tracker.update(listOf(obs("FEVORA")), meds, 900).single()
        assertNotEquals(a.id, b.id)
    }
    @Test fun wrongStrengthRemainsBlockedWhenItsNumberDisappears() {
        val tracker = StripTracker()
        tracker.update(listOf(obs("FEVORA")), meds, 100)
        val conflict = tracker.update(listOf(obs("FEVORA 650 mg")), meds, 200).single()
        val partial = tracker.update(listOf(obs("FEVORA")), meds, 300).single()
        assertTrue(conflict.blocked); assertTrue(partial.blocked); assertNull(partial.named)
    }
    @Test fun differentBrandAtTheSamePositionStartsANewTrack() {
        val tracker = StripTracker()
        val a = tracker.update(listOf(obs("FEVORA")), meds, 100).single()
        val b = tracker.update(listOf(obs("CALVORA")), meds, 200).single()
        assertNotEquals(a.id, b.id); assertEquals(1, b.nameFrames)
    }
    @Test fun onePreviousTrackCannotConfirmTwoNewRegions() {
        val tracker = StripTracker()
        tracker.update(listOf(obs("FEVORA", 40)), meds, 100)
        val next = tracker.update(listOf(obs("FEVORA", 0), obs("FEVORA", 80)), meds, 200)
        assertTrue(next.all { it.nameFrames == 1 }); assertNotEquals(next[0].id, next[1].id)
    }
    @Test fun resetCannotReuseAStaleTrackId() {
        val tracker = StripTracker()
        val a = tracker.update(listOf(obs("FEVORA")), meds, 100).single()
        tracker.reset()
        assertNotEquals(a.id, tracker.update(listOf(obs("FEVORA")), meds, 200).single().id)
    }
}
