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

/** The print engine against text the phone really read from three strips. Taught from one photo, shown another. */
class PrintEvidenceTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private val parawel = Medicine("p", "Parawel", "", "PARAWEL", listOf("PARACETAMOL"), listOf(mg("325")))
    private val apex = Medicine("a", "apex", "", "APEX", listOf("PARACETAMOL"), listOf(mg("500")))
    private val neo = Medicine("n", "Neoberry", "", "NEOBERRY",
        listOf("CALCIUM CARBONATE", "BENFOTIAMINE", "ALPHA LIPOIC ACID", "INOSITOL", "CHROMIUM PICOLINATE", "METHYLCOBALAMIN", "FOLIC ACID", "PYRIDOXINE HYDROCHLORIDE", "VITAMIN D3"),
        listOf(mg("500"), mg("150"), mg("200"), mg("100"), Strength("200", StrengthUnit.MCG), Strength("1500", StrengthUnit.MCG), mg("1.5"), mg("3"), Strength("2000", StrengthUnit.IU)))
    private val meds = listOf(parawel, apex, neo)
    private fun look(med: Medicine, lines: List<String>) = Look("look-${med.id}", med.id, emptyList(), "", lines)

    // apex has no saved photo here, so its reviewed label alone must be enough.
    private val index = PrintIndex.build(meds, listOf(look(parawel, RealStrips.PARAWEL_1), look(neo, RealStrips.NEOBERRY_1)))

    private fun ocr(lines: List<String>) = lines.mapIndexed { i, text -> OcrLine(text, 0, i * 40, 400, i * 40 + 30, 30, 0.9f) }
    private fun show(lines: List<String>, frames: Int = 4, index: PrintIndex = this.index): PrintResult {
        val evidence = PrintEvidence()
        repeat(frames) { evidence.add(ocr(lines), index, Tuning(), it * 100L) }
        return evidence.decide(index, (frames - 1) * 100L)
    }

    @Test fun anotherPhotoOfTheSameRealStripIsChecked() {
        assertEquals(PrintResult.Checked(parawel), show(RealStrips.PARAWEL_2))
        assertEquals(PrintResult.Checked(neo), show(RealStrips.NEOBERRY_2))
        assertEquals(PrintResult.Checked(neo), show(RealStrips.NEOBERRY_3))
    }
    @Test fun reviewedLabelAloneIsEnoughWithoutASavedPhoto() {
        assertEquals(PrintResult.Checked(apex), show(RealStrips.APEX_1))
    }
    @Test fun oneFrameIsNeverEnough() {
        assertEquals(PrintResult.Reading, show(RealStrips.PARAWEL_2, frames = 1))
    }
    @Test fun theStripsOwnPrintIsNeverADifference() {
        // "DT" is printed on the real strip but was never typed on the saved label.
        assertTrue(RealStrips.PARAWEL_2.contains("DT"))
        assertTrue(show(RealStrips.PARAWEL_2) is PrintResult.Checked)
    }
    @Test fun wrongStrengthIsRefusedAndStaysRefused() {
        val wrong = RealStrips.PARAWEL_2.map { it.replace("325", "650") }
        val evidence = PrintEvidence()
        repeat(3) { evidence.add(ocr(wrong), index, Tuning(), it * 100L) }
        val refused = evidence.decide(index, 200) as PrintResult.Differs
        assertEquals(parawel, refused.med)
        assertEquals("650", refused.printed)
        // The number slips out of view. The refusal does not turn into a name.
        for (t in 3..60) evidence.add(ocr(listOf("Parawel")), index, Tuning(), t * 100L)
        assertEquals(refused, evidence.decide(index, 6000))
    }
    @Test fun oneOddNumberSaysNothingAndThenClears() {
        val evidence = PrintEvidence()
        evidence.add(ocr(listOf("Parawel-650")), index, Tuning(), 0)
        evidence.add(ocr(RealStrips.PARAWEL_2), index, Tuning(), 100)
        evidence.add(ocr(RealStrips.PARAWEL_2), index, Tuning(), 200)
        // 650 is not a misread of 325, however often 325 is read. One sighting is not yet a refusal either.
        assertEquals(PrintResult.Reading, evidence.decide(index, 200))
        for (t in 3..30) evidence.add(ocr(RealStrips.PARAWEL_2), index, Tuning(), t * 100L)
        assertEquals(PrintResult.Checked(parawel), evidence.decide(index, 3000))
    }
    @Test fun misreadDigitIsOutvotedByTheSavedNumberOnTheSameStrip() {
        // What the phone really did with this strip: one of its many "Parawel-325" came out as 225, another as "32 5".
        val frame = listOf("Parawel-225 Parawel-325", "Parawel325", "Parawel 32 5", "Paracetamol Dispersible Tablets B.P325 mg", "Parawel-325")
        assertEquals(PrintResult.Checked(parawel), show(frame))
    }
    @Test fun freshReadsOfTheRealParawelPhotosAreChecked() {
        // The lines the phone produced on 10 October when it read the two saved Parawel photos again.
        val first = listOf("Paracetamol Dispersible Tablets B.P. 325 mg", "Parawel225", "DT", "Parawel-325 Parael325")
        val second = listOf("Paacetamol Dispersible Tablets B.P 325 mg", "Parawel325", "DT", "MRP:2G.25",
            "SKU:PArawel-32 5m , Kh. No. 143 M/7, Vade: Raipur,", "Parawel-326 Parauel3")
        assertEquals(PrintResult.Checked(parawel), show(first))
        assertEquals(PrintResult.Checked(parawel), show(second))
    }
    @Test fun priceAndStockCodeLinesNeverGiveAStrength() {
        assertTrue(StripParser.parse(ocr(listOf("MRP:2G.25", "M.R.P.RS.26.25", "SKU:PArawel-32 5m , Kh. No. 143")), meds).let {
            it.strengths.isEmpty() && it.brandNumbers.isEmpty() && it.brands.isEmpty()
        })
    }
    @Test fun numbersElsewhereOnTheSavedPackNeverBecomeAllowedStrengths() {
        val saved = listOf("Fevora-500", "Paracetamol Tablets IP 500 mg", "Plot No. 650, Industrial Area", "MRP Rs. 650.00", "Dosage: 650 mg every 6 hours")
        val taught = PrintIndex.build(listOf(Fixtures.M1), listOf(look(Fixtures.M1, saved)))
        assertTrue(show(listOf("FEVORA-650", "Paracetamol Tablets IP 650 mg"), index = taught) is PrintResult.Differs)
        assertEquals(PrintResult.Checked(Fixtures.M1), show(listOf("FEVORA-500", "Paracetamol Tablets IP 500 mg"), index = taught))
    }
    @Test fun extraViewsAreRefusedOnlyForAnotherStripOrAnotherStrength() {
        fun key(vararg lines: String) = StripParser.parse(ocr(lines.toList()), meds)
        // The real strip's own "DT" no longer stops the family adding another photo of it.
        assertFalse(EnrollmentEvidence.wrongStrip(key("Parawel325", "DT", "Paracetamol Dispersible Tablets B.P 325 mg"), parawel))
        assertFalse(EnrollmentEvidence.wrongStrip(key("Each uncoated dispersible tablet contains"), parawel))
        assertTrue(EnrollmentEvidence.wrongStrip(key("Parawel-650", "Paracetamol 650 mg"), parawel))
        assertTrue(EnrollmentEvidence.wrongStrip(key("Neoberry", "Tablets"), parawel))
    }
    @Test fun aNumberReadAsOftenAsTheSavedOneIsNotAMisread() {
        val frame = listOf("Parawel-225", "Parawel-325", "Paracetamol Dispersible Tablets")
        assertTrue(show(frame) is PrintResult.Differs)
    }
    @Test fun twoStrengthsOfOneSavedNameInOnePatchAreTwoStripsTouching() {
        val low = Medicine("d5", "Dolo 500", "", "DOLO", listOf("PARACETAMOL"), listOf(mg("500")))
        val pair = PrintIndex.build(listOf(low, low.copy(id = "d6", strengths = listOf(mg("650")))), emptyList())
        assertEquals(PrintResult.TooClose, show(listOf("DOLO-500", "Paracetamol 500 mg", "DOLO-650", "Paracetamol 650 mg"), index = pair))
    }
    @Test fun nameWithoutStrengthIsOnlyAName() {
        assertEquals(PrintResult.NameOnly(neo), show(listOf("Neoberry", "Tablets")))
        assertEquals(PrintResult.NameOnly(parawel), show(listOf("Parawel", "Dispersible")))
    }
    @Test fun tornPieceWithNoNameIsACandidateNeverAnAnswer() {
        val piece = RealStrips.NEOBERRY_3.filterNot { it.contains("Neoberry", ignoreCase = true) }
        val result = show(piece)
        assertTrue(result.toString(), result is PrintResult.Piece)
        assertEquals(neo, (result as PrintResult.Piece).med)
    }
    @Test fun pieceShowingANumberTheSavedStripNeverHadIsNotACandidate() {
        val piece = RealStrips.NEOBERRY_3.filterNot { it.contains("Neoberry", ignoreCase = true) } + "750 mg"
        assertFalse(show(piece) is PrintResult.Piece)
    }
    @Test fun stripsThatAreNotSavedAreNeverNamed() {
        val cetirizine = listOf("OKACET", "Cetirizine Hydrochloride Tablets IP", "Each film coated tablet contains", "Cetirizine Hydrochloride IP 10 mg", "Cipla Ltd Verna Goa")
        val otherBrand = listOf("CALPOL 500", "Paracetamol Tablets IP 500 mg", "GlaxoSmithKline Pharmaceuticals Limited")
        for (lines in listOf(cetirizine, otherBrand)) {
            val result = show(lines, frames = 8)
            assertTrue(result.toString(), result == PrintResult.Reading || result == PrintResult.Unknown)
        }
    }
    @Test fun clearPrintFromNoSavedMedicineIsUnknown() {
        val lines = listOf("OKACET", "Cetirizine Tablets", "Levocetirizine Montelukast", "Sodium Fexofenadine", "Verna Salcette Goa")
        assertEquals(PrintResult.Unknown, show(lines, frames = 5))
    }
    @Test fun twoSavedNamesInOnePatchAreTwoStripsTouching() {
        assertEquals(PrintResult.TooClose, show(listOf("Parawel325", "Neoberry")))
    }
    @Test fun sameNameIsToldApartByItsStrength() {
        val low = Medicine("d5", "Dolo 500", "", "DOLO", listOf("PARACETAMOL"), listOf(mg("500")))
        val high = low.copy(id = "d6", nickEn = "Dolo 650", strengths = listOf(mg("650")))
        val pair = PrintIndex.build(listOf(low, high), emptyList())
        assertEquals(PrintResult.Checked(high), show(listOf("DOLO 650", "Paracetamol Tablets IP 650 mg"), index = pair))
        assertEquals(PrintResult.Checked(low), show(listOf("DOLO-500", "Paracetamol Tablets IP 500 mg"), index = pair))
        assertEquals(PrintResult.Ambiguous, show(listOf("DOLO", "Paracetamol Tablets IP"), index = pair))
        assertTrue(show(listOf("DOLO 1000", "Paracetamol 1000 mg"), index = pair) is PrintResult.Differs)
    }
    @Test fun releaseWordMustBeReadAndMustNotBeExtra() {
        val list = PrintIndex.build(listOf(Fixtures.M1, Fixtures.M4), emptyList())
        assertEquals(PrintResult.Checked(Fixtures.M4), show(listOf("GLYMET SR 500", "Metformin 500 mg"), index = list))
        assertEquals(PrintResult.NameOnly(Fixtures.M4), show(listOf("GLYMET 500", "Metformin 500 mg"), index = list))
        assertTrue(show(listOf("FEVORA SR", "Paracetamol 500 mg"), index = list) is PrintResult.Differs)
    }
    @Test fun anotherSavedMedicinesIngredientUnderThisNameIsADifference() {
        val list = PrintIndex.build(listOf(Fixtures.M1, Fixtures.M4), emptyList())
        assertTrue(show(listOf("FEVORA", "Metformin 500 mg"), index = list) is PrintResult.Differs)
    }
    @Test fun oldReadingsExpireSoASwappedStripCannotInheritAName() {
        val evidence = PrintEvidence()
        repeat(4) { evidence.add(ocr(RealStrips.PARAWEL_2), index, Tuning(), it * 100L) }
        assertEquals(PrintResult.Checked(parawel), evidence.decide(index, 300))
        // Something with no readable name now lies at the same spot.
        for (t in 4..40) evidence.add(ocr(listOf("blister foil")), index, Tuning(), t * 100L)
        val later = evidence.decide(index, 4000)
        assertEquals(PrintResult.Reading, later)
    }
    @Test fun misreadNameSupportsAnExactReadButNeverStandsAlone() {
        val evidence = PrintEvidence()
        evidence.add(ocr(listOf("Paravel-325")), index, Tuning(), 0)
        evidence.add(ocr(listOf("Paravel-325")), index, Tuning(), 100)
        assertEquals(PrintResult.Reading, evidence.decide(index, 100))
        evidence.add(ocr(listOf("Parawel-325")), index, Tuning(), 200)
        assertTrue(evidence.decide(index, 200) is PrintResult.NameOnly)
    }
}
