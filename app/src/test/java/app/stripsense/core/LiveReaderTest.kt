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

class LiveReaderTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private val med = Medicine("p", "Parawel", "", "PARAWEL", listOf("PARACETAMOL"), listOf(mg("325")))
    private val index = PrintIndex.build(listOf(med), emptyList())
    private fun ocr(vararg lines: String) = lines.mapIndexed { i, text -> OcrLine(text, 0, i * 40, 400, i * 40 + 30, 30, 0.9f) }

    @Test fun replyBecomesPrintedLinesAndARefusalBecomesNothing() {
        assertEquals(listOf("Parawel-325", "Paracetamol 325 mg"), LiveReader.lines("- Parawel-325\n\n* Paracetamol 325 mg\n"))
        assertTrue(LiveReader.lines("UNREADABLE").isEmpty())
        assertTrue(LiveReader.lines("x".repeat(700)).isEmpty())
    }
    @Test fun oneReadingAloneNeverNamesAStrip() {
        val evidence = PrintEvidence()
        evidence.addReading(listOf("Parawel-325", "Paracetamol 325 mg"), index, Tuning(), 1000)
        assertEquals(PrintResult.Reading, evidence.decide(index, 1000))
    }
    @Test fun aReadingAndACameraFrameTogetherCanCheckAStrip() {
        val evidence = PrintEvidence()
        evidence.add(ocr("Parawel"), index, Tuning(), 0)
        evidence.add(ocr("Parawel"), index, Tuning(), 100)
        assertEquals(PrintResult.NameOnly(med), evidence.decide(index, 100))
        // The camera never managed the small "325". Gemma reads it; the camera then reads it once as well.
        evidence.addReading(listOf("Parawel-325", "325 mg"), index, Tuning(), 2000)
        evidence.add(ocr("Parawel-325"), index, Tuning(), 2100)
        assertEquals(PrintResult.Checked(med), evidence.decide(index, 2100))
    }
    @Test fun twoSeparateReadingsCountAsTwoFrames() {
        val evidence = PrintEvidence()
        evidence.addReading(listOf("Parawel-325", "325 mg"), index, Tuning(), 1000)
        evidence.addReading(listOf("Parawel 325", "Paracetamol 325 mg"), index, Tuning(), 4000)
        assertEquals(PrintResult.Checked(med), evidence.decide(index, 4000))
        assertEquals(PrintResult.Reading, evidence.decide(index, 4000 + PrintEvidence.READER_WINDOW_MS + 1))
    }
    @Test fun aReadingCanSupportButNeverRaiseADifference() {
        val evidence = PrintEvidence()
        evidence.add(ocr("Parawel"), index, Tuning(), 0)
        evidence.add(ocr("Parawel"), index, Tuning(), 100)
        // The model sees a crop that may take in a neighbour, and can add what is not there. However often it says
        // 650 or names another ingredient, that alone is never a warning about this strip, and never a check either.
        for (t in 2..8) evidence.addReading(listOf("Parawel-650", "650 mg", "Metformin"), index, Tuning(), t * 100L)
        evidence.add(ocr("Parawel"), index, Tuning(), 900)
        assertEquals(PrintResult.NameOnly(med), evidence.decide(index, 900))
    }
    @Test fun onlyUnsettledPrintThatCouldBeASavedMedicineIsShownToTheModel() {
        assertTrue(LiveReader.worthAsking(PrintResult.NameOnly(med), false))
        assertTrue(LiveReader.worthAsking(PrintResult.Reading, true))
        assertFalse(LiveReader.worthAsking(PrintResult.Reading, false))
        assertFalse(LiveReader.worthAsking(PrintResult.Checked(med), true))
        assertFalse(LiveReader.worthAsking(PrintResult.Differs(med, "650", "325 mg"), true))
        val other = PrintEvidence().apply { add(ocr("Cetirizine Hydrochloride Tablets", "Okacet 10 mg"), index, Tuning(), 0) }
        assertFalse(other.promising(index, 0))
        val near = PrintEvidence().apply { add(ocr("Paravel-325"), index, Tuning(), 0) }
        assertTrue(near.promising(index, 0))
    }
}
