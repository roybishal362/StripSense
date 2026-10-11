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

/** What the voice must and must not do when one strip is swapped for another, from the trial on 10 October. */
class FreshVoiceTest {
    private val a = Fixtures.M1
    private val b = Fixtures.M2
    private fun found(med: Medicine, nameOnly: Boolean = false, kind: TimingKind = TimingKind.MARKED) =
        ScanMessage(ScanHint.FOUND, ScanTarget(1, med, MedicationTiming(kind), ScanSide.SINGLE, nameOnly))

    @Test fun aSentenceAboutAStripThatWasSwappedAwayIsCutOff() {
        val queue = ScanAnnouncements()
        queue.next(found(a), 0)
        assertNotNull(queue.next(found(a), ScanAnnouncements.FOUND_MS, busy = false))
        // Still speaking about the first strip; the second is now in front of the camera.
        queue.next(found(b), 1000, busy = true)
        assertFalse(queue.stale(1000 + ScanAnnouncements.CHANGED_MS - 1))
        assertTrue(queue.stale(1000 + ScanAnnouncements.CHANGED_MS))
        // Once the voice is stopped the second strip is named, without waiting out the usual pause.
        assertEquals(found(b), queue.next(found(b), 1000 + ScanAnnouncements.FOUND_MS, busy = false))
        assertFalse(queue.stale(3000))
    }
    @Test fun theSameStripIsNotCutOffAndNotSaidTwiceWhenItsStrengthComesAndGoes() {
        val queue = ScanAnnouncements()
        queue.next(found(a), 0)
        assertNotNull(queue.next(found(a), ScanAnnouncements.FOUND_MS))
        var t = 1000L
        repeat(20) {
            assertNull(queue.next(found(a, nameOnly = it % 2 == 0), t, busy = false))
            assertFalse(queue.stale(t))
            t += 400
        }
    }
    @Test fun nothingInViewForAMomentIsNotAnnouncedDuringASwap() {
        val queue = ScanAnnouncements()
        queue.next(found(a), 0)
        queue.next(found(a), ScanAnnouncements.FOUND_MS)
        val empty = ScanMessage(ScanHint.SEARCHING)
        assertNull(queue.next(empty, 5000))
        assertNull(queue.next(empty, 5000 + ScanAnnouncements.EMPTY_MS - 1))
        assertEquals(empty, queue.next(empty, 5000 + ScanAnnouncements.EMPTY_MS))
    }
    @Test fun theStripInTheFrameIsTheSubjectEvenWhenAnotherIsDue() {
        val framed = ScanTarget(1, a, MedicationTiming(TimingKind.MARKED), ScanSide.CENTRE, false)
        val dueAtEdge = ScanTarget(2, b, MedicationTiming(TimingKind.NOW), ScanSide.LEFT, false)
        assertEquals(framed, ScanGuidance.subject(listOf(framed, dueAtEdge), mapOf(1L to 0.9f, 2L to 0.2f), null, null))
        // With nothing in the frame the due strip is the one the phone leads to.
        assertEquals(dueAtEdge, ScanGuidance.subject(listOf(framed, dueAtEdge), mapOf(1L to 0.3f, 2L to 0.2f), null, null))
        // Two strips about equally near the middle: the one already spoken of stays.
        assertEquals(dueAtEdge, ScanGuidance.subject(listOf(framed, dueAtEdge), mapOf(1L to 0.70f, 2L to 0.65f), null, b.id))
    }
    @Test fun theDueStripElsewhereIsSaidWithTheStripInTheFrame() {
        val text = Sentences.scanGuidance(found(a).copy(elsewhere = b to Toward.LEFT), "en")
        assertTrue(text, text.contains("is the one due now. It is on the left."))
        assertTrue(Sentences.scanGuidance(found(a).copy(elsewhere = b to Toward.LEFT), "hi").contains("बाईं तरफ़"))
    }
    @Test fun aStraightFingerPointsAtTheStripAheadOfIt() {
        val left = PlacedStrip(a.id, a, PlacedKind.CHECKED, CropBox(100, 400, 300, 500), 0, 0)
        val right = PlacedStrip(b.id, b, PlacedKind.CHECKED, CropBox(700, 400, 900, 500), 0, 0)
        // The fingertip is between the strips, on neither. It points right.
        assertNull(HandGuide.pointed(500, 450, listOf(left, right)))
        assertEquals(right, HandGuide.pointed(500, 450, listOf(left, right), 10f to 0f, 400))
        assertEquals(left, HandGuide.pointed(500, 450, listOf(left, right), -10f to 1f, 400))
        // Pointing away from both, or at a strip too far off, is pointing at nothing.
        assertNull(HandGuide.pointed(500, 450, listOf(left, right), 0f to -10f, 400))
        assertNull(HandGuide.pointed(500, 450, listOf(left, right), 10f to 0f, 100))
        val cue = HandGuide.cue(500, 450, listOf(left, right), emptySet(), { MedicationTiming(TimingKind.MARKED) }, 10f to 0f, 400)
        assertEquals(b, (cue as HandCue.Info).med)
    }
    @Test fun aSecondStrengthOnATwoIngredientPackIsNotADifference() {
        fun mg(v: String) = Strength(v, StrengthUnit.MG)
        // Saved as the family typed it: two ingredients, one strength. The strip prints both strengths.
        val telma = Medicine("t", "Telma", "", "TELMISARTAN AND AZELNIDIPINE", listOf("TELMISARTAN", "AZELNIDIPINE"), listOf(mg("40")))
        val index = PrintIndex.build(listOf(telma), emptyList())
        val evidence = PrintEvidence()
        val lines = listOf("Telmisartan 40 mg and", "Azelnidipine 8 mg Tablets").mapIndexed { i, text -> OcrLine(text, 0, i * 40, 400, i * 40 + 30, 30, 0.9f) }
        repeat(6) { evidence.add(lines, index, Tuning(), it * 100L) }
        assertEquals(PrintResult.Checked(telma), evidence.decide(index, 500))
        // Only the second strength read: still the same strip, its own strength simply not read yet.
        val other = PrintEvidence()
        repeat(6) { other.add(lines.drop(1) + OcrLine("Telmisartan and", 0, 0, 400, 30, 30, 0.9f), index, Tuning(), it * 100L) }
        assertEquals(PrintResult.NameOnly(telma), other.decide(index, 500))
    }
}
