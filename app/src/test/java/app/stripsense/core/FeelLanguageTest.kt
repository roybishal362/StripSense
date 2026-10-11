package app.stripsense.core

import app.stripsense.core.guide.*
import app.stripsense.core.teach.PurposeKind
import org.junit.Assert.*
import org.junit.Test

/** The small language the phone speaks through the hand, with a motor at each end. */
class FeelLanguageTest {
    private fun all(): List<Felt> = listOf(FeelLanguage.yes(), FeelLanguage.no(), FeelLanguage.nearer(0.5f), FeelLanguage.question(), FeelLanguage.done(),
        FeelLanguage.touched(PurposeKind.BP, true), FeelLanguage.touched(PurposeKind.SUGAR, false), FeelLanguage.touched(null, null)) +
        Toward.entries.map { FeelLanguage.pull(it, 0.5f) } + PurposeKind.entries.map { FeelLanguage.signature(it) } +
        FeelLanguage.signature(null) + FeelLanguage.alarm(PurposeKind.BP)

    @Test fun everyPatternIsWellFormed() {
        for (felt in all()) for (buzz in listOfNotNull(felt.top, felt.bottom)) {
            assertEquals(buzz.times.size, buzz.strengths.size)
            assertTrue(buzz.times.isNotEmpty() && buzz.times.all { it > 0 })
            assertTrue(buzz.strengths.all { it in 0..255 })
            assertTrue(buzz.strengths.any { it > 0 })
            assertTrue("lasts ${buzz.lasts}", buzz.lasts <= 3000)
        }
    }

    @Test fun theEndThatTicksIsTheWayToMove() {
        val away = FeelLanguage.pull(Toward.AWAY, 0.5f)
        assertNotNull(away.top); assertNull(away.bottom)
        val closer = FeelLanguage.pull(Toward.CLOSER, 0.5f)
        assertNull(closer.top); assertNotNull(closer.bottom)
        // Sideways is felt at both ends: one tick for left, two for right.
        val left = FeelLanguage.pull(Toward.LEFT, 0.5f)
        val right = FeelLanguage.pull(Toward.RIGHT, 0.5f)
        assertEquals(left.top, left.bottom)
        assertEquals(1, left.top!!.strengths.count { it > 0 })
        assertEquals(2, right.top!!.strengths.count { it > 0 })
    }

    @Test fun everyTickIsAtFullStrengthAndNearnessIsToldByHowFastTheyCome() {
        // With the camera open the phone plays vibrations at two thirds of their strength: a soft tick was not felt.
        for (closeness in listOf(0.05f, 0.5f, 0.95f)) {
            assertEquals(255, FeelLanguage.pull(Toward.AWAY, closeness).top!!.strengths.max())
            assertEquals(255, FeelLanguage.pull(Toward.RIGHT, closeness).bottom!!.strengths.max())
            assertEquals(255, FeelLanguage.nearer(closeness).top!!.strengths.max())
        }
        assertTrue(Homing.intervalMs(0.9f) * 3 < Homing.intervalMs(0.1f))
        // A tick at one end lasts long enough for the hand to tell which end it was.
        assertTrue(FeelLanguage.pull(Toward.AWAY, 0.5f).top!!.lasts >= 60)
    }

    @Test fun yesAndNoCannotBeMistakenForEachOther() {
        val yes = FeelLanguage.yes().top!!
        val no = FeelLanguage.no().top!!
        // Yes is one unbroken buzz that only grows. No is two short knocks with stillness between.
        assertTrue(yes.strengths.none { it == 0 })
        assertEquals(yes.strengths, yes.strengths.sorted())
        assertTrue(yes.lasts >= 400)
        assertEquals(listOf(255, 0, 255), no.strengths)
        assertTrue(no.times.filterIndexed { i, _ -> no.strengths[i] > 0 }.all { it <= 60 })
        assertTrue(no.lasts < yes.lasts)
    }

    @Test fun medicinesOfDifferentKindsFeelDifferentAndTheSameKindFeelsTheSame() {
        val bp = FeelLanguage.signature(PurposeKind.BP)
        val sugar = FeelLanguage.signature(PurposeKind.SUGAR)
        val fever = FeelLanguage.signature(PurposeKind.FEVER_PAIN)
        val breath = FeelLanguage.signature(PurposeKind.BREATHING)
        val plain = FeelLanguage.signature(null)
        assertEquals(5, setOf(bp, sugar, fever, breath, plain).size)
        assertEquals(bp, FeelLanguage.signature(PurposeKind.HEART))
        assertEquals(fever, FeelLanguage.signature(PurposeKind.PAIN))
        assertEquals(plain, FeelLanguage.signature(PurposeKind.VITAMIN))
        // A heartbeat is strong, soft, rest, strong, soft. Sugar is three taps, a rest, three taps.
        assertEquals(listOf(255, 185, 255, 185), bp.top!!.strengths.filter { it > 0 })
        assertEquals(6, sugar.top!!.strengths.count { it > 0 })
        // None of them is the yes or the no.
        for (felt in listOf(bp, sugar, fever, breath, plain)) { assertNotEquals(FeelLanguage.yes(), felt); assertNotEquals(FeelLanguage.no(), felt) }
    }

    @Test fun theStripUnderTheFingerIsItsRhythmAndThenTheAnswer() {
        val rhythm = FeelLanguage.signature(PurposeKind.BP).top!!
        val yes = FeelLanguage.touched(PurposeKind.BP, true).top!!
        val no = FeelLanguage.touched(PurposeKind.BP, false).top!!
        // The rhythm comes first in both, and the end is the yes or the no.
        assertEquals(rhythm.strengths, yes.strengths.take(rhythm.strengths.size))
        assertEquals(rhythm.strengths, no.strengths.take(rhythm.strengths.size))
        assertEquals(FeelLanguage.yes().top!!.strengths, yes.strengths.takeLast(5))
        assertEquals(FeelLanguage.no().top!!.strengths, no.strengths.takeLast(3))
        // Nothing due: the rhythm alone. Print with no name: only ever a no.
        assertEquals(FeelLanguage.signature(PurposeKind.BP), FeelLanguage.touched(PurposeKind.BP, null))
        assertEquals(FeelLanguage.no(), FeelLanguage.touched(null, false, known = false))
        assertEquals(FeelLanguage.no(), FeelLanguage.touched(null, null, known = false))
    }

    @Test fun theQuestionAndDoneAreTheirOwnPatterns() {
        val others = listOf(FeelLanguage.yes(), FeelLanguage.no(), FeelLanguage.signature(null), FeelLanguage.signature(PurposeKind.BP), FeelLanguage.signature(PurposeKind.SUGAR))
        assertTrue(FeelLanguage.question() !in others && FeelLanguage.done() !in others)
        assertNotEquals(FeelLanguage.question(), FeelLanguage.done())
        // The question ends rising; done is three taps that grow.
        val q = FeelLanguage.question().top!!.strengths
        assertTrue(q.last() > q[2])
        assertEquals(listOf(150, 200, 255), FeelLanguage.done().top!!.strengths.filter { it > 0 })
    }

    @Test fun theAlarmIsALongBuzzAndThenTheMedicinesOwnRhythm() {
        val alarm = FeelLanguage.alarm(PurposeKind.BP).top!!
        assertEquals(650L, alarm.times.first())
        assertEquals(255, alarm.strengths.first())
        val rhythm = FeelLanguage.signature(PurposeKind.BP).top!!
        assertEquals(rhythm.strengths, alarm.strengths.takeLast(rhythm.strengths.size))
        assertNotEquals(FeelLanguage.alarm(PurposeKind.BP), FeelLanguage.alarm(PurposeKind.SUGAR))
    }
}
