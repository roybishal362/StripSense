package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.rx.*
import org.junit.Assert.*
import org.junit.Test

/** A prescription read against the strips the family owns. The medicines are the user's own three. */
class PrescriptionTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private val telma = Medicine("t", "Telmisartan and Azelnidipine 40 mg", "", "TELMISARTAN AND AZELNIDIPINE", listOf("TELMISARTAN", "AZELNIDIPINE"), listOf(mg("40")))
    private val crocin = Medicine("c", "Crocin Advance 500 mg", "", "CROCIN ADVANCE", listOf("PARACETAMOL"), listOf(mg("500")))
    private val p500 = Medicine("p", "P-500 500 mg", "", "P 500", listOf("PARACETAMOL"), listOf(mg("500")))
    private val meds = listOf(telma, crocin, p500)
    private fun read(vararg lines: String) = RxReader.read(lines.toList(), meds, emptyList())

    @Test fun aLineIsPlacedOnTheSavedStripItIsAbout() {
        val line = read("Tab Telmisartan + Azelnidipine 40 1-0-1 x 30 days").single()
        assertEquals(telma, line.match)
        assertEquals(listOf("08:00", "20:00"), line.dose!!.times())
        assertNull(line.conflict)
    }
    @Test fun badHandwritingIsPlacedBecauseOnlySavedStripsAreAsked() {
        // What a reader makes of a hurried hand: letters missing, letters wrong.
        assertEquals(telma, read("T. Telmisartn 40mg 1-0-0").single().match)
        assertEquals(telma, read("Telmisarton AZ 0-0-1").single().match)
        assertEquals(crocin, read("Crocin Advnce 1-1-1").single().match)
        // A doctor's short form.
        assertEquals(telma, read("Tab Telmi 40 OD").single().match)
    }
    @Test fun aLineThatFitsTwoStripsIsNotGuessed() {
        // Both Crocin and P-500 are paracetamol 500: the prescription cannot say which strip, so the family chooses.
        val line = read("Tab Paracetamol 500 mg SOS").single()
        assertNull(line.match)
        assertEquals(setOf(crocin, p500), line.candidates.toSet())
        assertTrue(line.dose!!.asNeeded)
        assertTrue(line.dose!!.times().isEmpty())
    }
    @Test fun aStrengthThatDiffersFromTheStripIsCaught() {
        val line = read("Tab Telmisartan 80 mg OD").single()
        assertEquals(telma, line.match)
        assertEquals("80" to "40 mg", line.conflict)
        // The same strength written without its unit, straight after the name.
        assertEquals("80", read("Telmisartan 80 1-0-0").single().conflict?.first)
        // The 1-0-1 and the number of days are not strengths.
        assertNull(read("Telmisartan 1-0-1 x 30 days").single().conflict)
        assertNull(read("Telmisartan 40 for 15 days BD").single().conflict)
    }
    @Test fun aMedicineTheFamilyHasNotSavedIsShownAsSuch() {
        val line = read("Tab Pantoprazole 40 1-0-0 before food").single()
        assertNull(line.match)
        assertTrue(line.candidates.isEmpty())
        assertEquals(RxFood.BEFORE, line.dose!!.food)
    }
    @Test fun theRestOfThePageIsNotAMedicine() {
        assertTrue(read("Dr. A. Sharma MBBS MD", "City Care Clinic, MG Road", "Date 10/10/2026", "Rx", "Review after 2 weeks").isEmpty())
    }
    @Test fun howOftenIsReadAsDoctorsWriteIt() {
        fun times(text: String) = RxDoses.read(text)?.times()
        assertEquals(listOf("08:00", "14:00", "20:00"), times("1-1-1"))
        assertEquals(listOf("20:00"), times("0 - 0 - 1"))
        assertEquals(listOf("08:00", "20:00"), times("Tab X BD after food"))
        assertEquals(listOf("08:00", "14:00", "20:00"), times("TDS"))
        assertEquals(listOf("20:00"), times("HS"))
        assertEquals(listOf("08:00"), times("once daily"))
        assertEquals(RxFood.AFTER, RxDoses.read("1-0-1 PC")!!.food)
        assertNull(RxDoses.read("0-0-0"))
        assertNull(RxDoses.read("Phone 98-76-54"))
        assertNull(RxDoses.read("Dr. Bose"))
    }
    @Test fun wordsThatAreOnlyAlikeByChanceDoNotCount() {
        assertEquals(0.0, RxReader.like("CARE", "CURE"), 0.0)
        assertTrue(RxReader.like("TELMISARTN", "TELMISARTAN") > 0.85)
        assertTrue(RxReader.like("AMLODIPINE", "AZELNIDIPINE") < RxReader.LIKE)
    }
    @Test fun theSameMedicineReadTwiceIsOneCardWithItsFullestReading() {
        // The fast reader got the name; the model's copy also got the dose and the wrong strength.
        val tidy = RxLines.tidy(read("Telmisartan", "Tab Telmisartn 80 mg 1-0-1", "Tab Paracetamol 500 SOS", "Tab Paracetamol 500 mg SOS", "Tab Pantoprazole 40 1-0-0"))
        assertEquals(3, tidy.size)
        assertEquals("80", tidy[0].conflict?.first)
        assertNotNull(tidy[0].dose)
        assertEquals(2, tidy[1].candidates.size)
        assertNull(tidy[2].match)
    }
    @Test fun theModelsCopyIsOnlyLinesAndARefusalIsNothing() {
        assertEquals(listOf("Tab Telma 40 1-0-1", "Crocin SOS"), RxPrompt.lines("1. Tab Telma 40 1-0-1\n- Crocin SOS\n"))
        assertTrue(RxPrompt.lines("UNREADABLE").isEmpty())
        assertFalse(RxPrompt.QUESTION.contains("saved", ignoreCase = true))
    }
}
