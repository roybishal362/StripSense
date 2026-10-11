package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.scan.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ExpiryTest {
    private fun line(text: String) = OcrLine(text, 0, 0, 300, 30, 30, 0.9f)

    @Test fun theDateAfterTheWordForExpiryIsRead() {
        assertEquals(ExpiryDate(2027, 11), ExpiryText.read("EXPIRY DATE NOV.2027"))
        assertEquals(ExpiryDate(2027, 11), ExpiryText.read("Exp. Nov 2027"))
        assertEquals(ExpiryDate(2026, 3), ExpiryText.read("EXP:03/2026"))
        assertEquals(ExpiryDate(2026, 3), ExpiryText.read("EXP 3-26"))
        assertEquals(ExpiryDate(2028, 1), ExpiryText.read("USE BEFORE JANUARY 2028"))
    }
    @Test fun theDateItWasMadeIsNeverTakenForTheExpiry() {
        assertNull(ExpiryText.read("MFG.DATE DEC.2025"))
        assertNull(ExpiryText.read("B.No. 25S27TD133"))
        assertEquals(ExpiryDate(2027, 11), ExpiryText.read("MFG.DEC.2025 EXP.NOV.2027"))
        assertNull(ExpiryText.read("EXPORT QUALITY 12 TABLETS"))
        // A year that cannot be right is a misread, not a date.
        assertNull(ExpiryText.read("EXP 11/2097"))
    }
    @Test fun aDateCountsOnlyWhenReadTheSameWayInSeveralFrames() {
        val votes = ExpiryVotes()
        votes.add(listOf(line("EXP NOV.2027")), 0)
        votes.add(listOf(line("EXP NOV.2027")), 100)
        assertNull(votes.decided())
        votes.add(listOf(line("EXP NOV.2027")), 200)
        assertEquals(ExpiryDate(2027, 11), votes.decided())
        // A second reading nearly as frequent: the phone no longer knows, and says nothing.
        votes.add(listOf(line("EXP NOV.2021")), 300)
        votes.add(listOf(line("EXP NOV.2021")), 400)
        assertNull(votes.decided())
    }
    @Test fun aStripIsGoodToTheEndOfItsMonth() {
        assertFalse(ExpiryDate(2026, 10).passed(LocalDate.of(2026, 10, 31)))
        assertTrue(ExpiryDate(2026, 10).passed(LocalDate.of(2026, 11, 1)))
    }
    @Test fun aPassedDateIsSaidAsWhatIsPrintedAndWhoToAsk() {
        val text = Sentences.expired(ExpiryDate(2026, 3), "en")
        assertEquals("Careful. The expiry date printed on this strip, March 2026, has passed. Ask your family before using it.", text)
        assertTrue(Sentences.expired(ExpiryDate(2026, 3), "hi").contains("परिवार से पूछिए"))
        assertEquals("Exp Nov 2027", Sentences.expiryChip(ExpiryDate(2027, 11), false, "en"))
        assertEquals("Expired Mar 2026", Sentences.expiryChip(ExpiryDate(2026, 3), true, "en"))
    }
    @Test fun theSceneCarriesTheExpiryOfTheStripItWasReadOn() {
        val med = Medicine("t", "Telma", "", "TELMA", listOf("TELMISARTAN"), listOf(Strength("40", StrengthUnit.MG)))
        val index = PrintIndex.build(listOf(med), emptyList())
        val scene = Scene()
        var seen = emptyList<SeenStrip>()
        val lines = listOf(OcrLine("Telma 40", 100, 100, 400, 140, 40, 0.9f), OcrLine("EXPIRY DATE NOV.2027", 120, 160, 380, 185, 25, 0.9f))
        for (frame in 0..4) seen = scene.read(lines, index, Tuning(), frame * 100L)
        assertEquals(ExpiryDate(2027, 11), seen.single().expiry)
    }
}
