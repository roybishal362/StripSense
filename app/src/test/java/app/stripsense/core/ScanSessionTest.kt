package app.stripsense.core

import app.stripsense.core.Fixtures.LIST
import app.stripsense.core.Fixtures.M1
import app.stripsense.core.Fixtures.key
import app.stripsense.core.Fixtures.s
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S1 to S7 of spec 01 section 7, and one test per hint. */
class ScanSessionTest {

    private val v1 = key("FEVORA 500 / Paracetamol Tablets IP 500 mg / FEVORA 500 / Paracetamol Tablets IP 500 mg")
    private val v2 = key("FEVORA 650 / Paracetamol Tablets IP 650 mg / FEVORA 650 / Paracetamol Tablets IP 650 mg")
    private val v3 = key("FEVORA / Paracetamol Tablets IP")
    private val v14 = key("PYRAMOL 500 / Paracetamol Tablets IP 500 mg", times = 2)

    private fun session() = ScanSession(LIST, Tuning())
    private fun ScanSession.at(k: ReadKey, t: Long) = feed(k, t, 30)
    private fun final(v: Verdict) = ScanResult.Final(v)

    @Test fun s1_matchAfterTwoFrames() {
        val s = session()
        assertTrue(s.at(v1, 0) is ScanResult.Looking)
        assertEquals(final(Verdict.Match(M1)), s.at(v1, 100))
    }

    @Test fun s2_oneLineStrengthNeedsThreeFrames() {
        val k = key("FEVORA 500 / Paracetamol Tablets IP 500 mg")
        assertEquals(1, k.strengthSeen[s("500")])
        val s = session()
        assertTrue(s.at(k, 0) is ScanResult.Looking)
        assertTrue(s.at(k, 100) is ScanResult.Looking)
        assertEquals(final(Verdict.Match(M1)), s.at(k, 200))
    }

    @Test fun s3_changedKeyRestartsCount() {
        val s = session()
        assertTrue(s.at(v1, 0) is ScanResult.Looking)
        assertTrue(s.at(v2, 100) is ScanResult.Looking)
        assertEquals(final(Verdict.Different(M1, "650 mg", "500 mg")), s.at(v2, 200))
    }

    @Test fun s4_unreadableOnlyAfterSlowMs() {
        val s = session()
        for (t in 0L until 1200L step 100) assertTrue("at $t", s.at(v3, t) is ScanResult.Looking)
        assertEquals(final(Verdict.Unreadable), s.at(v3, 1200))
    }

    @Test fun s5_timeoutGivesUnreadable() {
        val k = ReadKey(letters = 12)
        val s = session()
        for (t in 0L until 6000L step 100) assertTrue("at $t", s.at(k, t) is ScanResult.Looking)
        assertEquals(final(Verdict.Unreadable), s.at(k, 6000))
        assertEquals(final(Verdict.Unreadable), s.at(k, 6100))
    }

    @Test fun s6_notShownOnlyAfterSlowMs() {
        val s = session()
        for (t in 0L until 1200L step 100) assertTrue("at $t", s.at(v14, t) is ScanResult.Looking)
        assertEquals(final(Verdict.NotShown(M1)), s.at(v14, 1200))
    }

    @Test fun s7_quickVerdictWinsOverSlow() {
        val s = session()
        for (t in 0L..500L step 100) assertTrue(s.at(v14, t) is ScanResult.Looking)
        assertTrue(s.at(v1, 600) is ScanResult.Looking)
        assertEquals(final(Verdict.Match(M1)), s.at(v1, 700))
    }

    @Test fun otherVerdictRestartsSlowCount() {
        val s = session()
        for (t in 0L..1000L step 100) s.at(v3, t)
        s.at(ReadKey(), 1100)
        assertTrue(s.at(v3, 1200) is ScanResult.Looking)
        assertEquals(final(Verdict.Unreadable), s.at(v3, 2400))
    }

    @Test fun fewLettersClearTimeout() {
        val s = session()
        for (t in 0L..5000L step 100) s.at(ReadKey(letters = 12), t)
        s.at(ReadKey(letters = 2), 5100)
        assertTrue(s.at(ReadKey(letters = 12), 6100) is ScanResult.Looking)
    }

    @Test fun finalStaysFinal() {
        val s = session()
        s.at(v1, 0)
        s.at(v1, 100)
        assertEquals(final(Verdict.Match(M1)), s.at(v2, 200))
        assertTrue(s.isFinished)
    }

    @Test fun hint_closerWhenOnlyTinyPrint() =
        assertEquals(ScanResult.Looking(Hint.CLOSER), session().feed(ReadKey(), 0, null, tooSmallLines = 3))

    @Test fun hint_holdInsideWhenNothing() =
        assertEquals(ScanResult.Looking(Hint.HOLD_INSIDE), session().feed(ReadKey(), 0, null, 0))

    @Test fun hint_closerWhenKeyLineIsSmall() =
        assertEquals(ScanResult.Looking(Hint.CLOSER), session().feed(v1, 0, 17))

    @Test fun hint_readingOtherwise() =
        assertEquals(ScanResult.Looking(Hint.READING), session().feed(v1, 0, 18))
}
