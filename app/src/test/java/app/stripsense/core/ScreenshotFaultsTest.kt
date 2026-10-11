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

/** The faults seen in the user's screenshots of 10 October, 15:20, as tests. */
class ScreenshotFaultsTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private val crocin = Medicine("c", "Crocin Advance 500 mg", "", "CROCIN ADVANCE", listOf("PARACETAMOL"), listOf(mg("500")))
    private val telma = Medicine("t", "Telmisartan and Azelnidipine 40 mg", "", "TELMISARTAN AND AZELNIDIPINE",
        listOf("TELMISARTAN", "AZELNIDIPINE"), listOf(mg("40")))
    private val p500 = Medicine("x", "P-500 500 mg", "", "P 500", listOf("PARACETAMOL"), listOf(mg("500")))
    private val index = PrintIndex.build(listOf(crocin, telma, p500), listOf(
        Look("l1", "c", emptyList(), "", listOf("Crocin", "Advance", "PARACETAMOL FAST RELEASE TABLETS", "Each uncoated tablet contains: Paracetamol I.P. 500 mg")),
        Look("l2", "t", emptyList(), "", listOf("Telmisartan and Azelnidipine Tablets", "Telma AZ", "Telmisartan IP 40 mg", "Azelnidipine IP 8 mg", "glenmark")),
        Look("l3", "x", emptyList(), "", listOf("P-500 apex P.500 apex", "Paracetamol Tablets I.P.", "apex laboratories private limited"))))
    private fun group(x: Int, y: Int, w: Int, h: Int, vararg text: String) = StripGroups.StripGroup(
        text.mapIndexed { i, t -> OcrLine(t, x, y + i * 34, x + w, y + i * 34 + 30, 30, 0.9f) }, CropBox(x, y, x + w, y + h))
    private fun run(scene: Scene, frames: IntRange, make: (Int) -> List<StripGroups.StripGroup>): List<SeenStrip> {
        var seen = emptyList<SeenStrip>()
        for (frame in frames) seen = scene.frame(make(frame), index, Tuning(), frame * 100L)
        return seen
    }

    @Test fun crocinsStrengthBesideItsColumnOfLogosCountsAndTheBoxIsTheWholeStrip() {
        // The pack: a narrow column of "Crocin Advance" logos on the left, the text block with the strength to its right.
        val seen = run(Scene(), 0..4) {
            listOf(group(200, 900, 220, 430, "Crocin", "Advance", "Crocin", "Advance"),
                group(380, 940, 520, 420, "PARACETAMOL FAST RELEASE TABLETS", "Each uncoated tablet contains: Paracetamol I P 500 mg", "Dosage: Adults 1 to 2 tablets"))
        }
        val strip = seen.single { it.result != PrintResult.Reading }
        assertEquals(PrintResult.Checked(crocin), strip.result)
        // One box across logos and text, so its middle is the strip's middle and not the logo column's.
        assertTrue(strip.box.toString(), strip.box.left <= 210 && strip.box.right >= 880)
        assertEquals(1, Scene.oneBoxEach(seen).size)
    }
    @Test fun iPFiveHundredOnAnotherStripIsNotTheSavedPFiveHundred() {
        // "I P 500 mg" holds the short saved name "P 500". Without the P-500 pack's own words it names nothing.
        assertTrue(index.namesIn(group(0, 0, 400, 60, "Each uncoated tablet contains: Paracetamol I P 500 mg").lines, Tuning()).isEmpty())
        assertEquals(setOf("P 500"), index.namesIn(group(0, 0, 400, 60, "P-500 apex P.500 apex").lines, Tuning()))
    }
    @Test fun movingFromOneStripToAnotherNeverWarnsAboutTheFirst() {
        // Screenshot: a fine P-500 strip flagged "Print: TELMISARTAN". The camera had just come from the Telma strip.
        val scene = Scene()
        run(scene, 0..4) { listOf(group(300, 600, 500, 300, "P-500 apex P.500 apex", "Paracetamol Tablets I.P.", "Paracetamol I.P. 500 mg")) }
        // The phone swings to the Telma strip. Its name is misread, so its print has no name on it, and it lies
        // where the P-500 strip was a moment ago.
        val during = run(scene, 5..12) { listOf(group(320, 620, 480, 280, "Telmisartam and Azelnidipime", "Telmisartam IP 40 mg", "glenmark")) }
        assertTrue(during.toString(), during.none { it.result is PrintResult.Differs })
        val back = run(scene, 13..18) { listOf(group(300, 600, 500, 300, "P-500 apex P.500 apex", "Paracetamol Tablets I.P.", "Paracetamol I.P. 500 mg")) }
        assertEquals(PrintResult.Checked(p500), back.single { it.result != PrintResult.Reading }.result)
    }
    @Test fun printThatOnlyTouchesAStripCanSupportItButNotAccuseIt() {
        // A neighbour's number and ingredient right beside the name. Not a difference; and no check either.
        val seen = run(Scene(), 0..6) {
            listOf(group(200, 900, 220, 200, "Crocin", "Advance"), group(380, 940, 400, 100, "Metformin Tablets 850 mg", "Telmisartam 40 mg"))
        }
        val strip = seen.single { (it.result as? PrintResult.NameOnly)?.med == crocin }
        assertTrue(seen.none { it.result is PrintResult.Differs })
        assertNotNull(strip)
    }
    @Test fun aWrongStrengthInTheNamesOwnPatchIsStillRefused() {
        val seen = run(Scene(), 0..5) { listOf(group(200, 900, 500, 200, "Crocin Advance 650", "Advance", "Paracetamol Tablets IP 650 mg")) }
        assertTrue(seen.toString(), seen.any { it.result is PrintResult.Differs })
    }
    @Test fun twoStripsLyingApartStayTwoStrips() {
        val seen = run(Scene(), 0..5) {
            listOf(group(100, 300, 400, 200, "P-500 apex P.500 apex", "Paracetamol I.P. 500 mg"),
                group(100, 900, 400, 200, "Telmisartan and Azelnidipine Tablets", "Telmisartan IP 40 mg", "Azelnidipine IP 8 mg"))
        }
        assertEquals(setOf("x", "t"), seen.mapNotNull { (it.result as? PrintResult.Checked)?.med?.id }.toSet())
    }
    @Test fun aStripLyingRightBesideAnotherKeepsItsPrintToItself() {
        // What the phone showed at 15:33: the Crocin strip, and the corner of the P-500 strip close beside it with
        // its maker's name out of view. The Crocin box took the P-500 corner in. Print is now joined to a strip
        // only from line to neighbouring line, so the gap of bare table between two strips is not crossed.
        fun line(text: String, x: Int, y: Int, w: Int, h: Int = 24, angle: Float = 0f) = OcrLine(text, x, y, x + w, y + h, h, 0.9f, angle)
        val crocinLines = listOf(line("Crocin", 150, 800, 110, 30), line("Advance", 150, 832, 110, 22),
            line("PARACETAMOL FAST RELEASE TABLETS", 300, 820, 520), line("Each uncoated tablet contains: Paracetamol I.P. 500 mg", 300, 848, 430, 18),
            line("Dosage: Adults 1 to 2 tablets", 300, 870, 400, 18),
            // The band of names printed down the strip's right side.
            OcrLine("Crocin Advance Crocin Advance", 960, 760, 990, 1300, 540, 0.9f, 90f))
        val neighbour = listOf(line("Paracetamol Tablets", 1090, 420, 220), line("Each uncoated tablet contains Paracetamol 650 mg", 1090, 450, 300, 18),
            line("Ibuprofen 40 mg", 1090, 474, 200, 18))
        var seen = emptyList<SeenStrip>()
        val scene = Scene()
        for (frame in 0..5) seen = scene.read(crocinLines + neighbour, index, Tuning(), frame * 100L)
        val strip = seen.single { (it.result as? PrintResult.Checked)?.med == crocin }
        // The box ends where the Crocin strip's own print ends; the neighbour's print lies outside it.
        assertTrue(strip.box.toString(), strip.box.right <= 1000 && strip.box.top >= 740)
        assertTrue(seen.none { it.result is PrintResult.Differs })
        // Only Crocin's own lines were handed to Crocin.
        val assembled = StripAssembly.assemble(crocinLines + neighbour, index, Tuning())
        assertEquals(neighbour.map { it.text }.toSet(), assembled.loose.map { it.text }.toSet())
    }
    @Test fun printThatTwoNamesCouldBothClaimIsGivenToNeither() {
        fun line(text: String, x: Int, y: Int) = OcrLine(text, x, y, x + 300, y + 24, 24, 0.9f)
        val lines = listOf(line("Crocin", 100, 100), line("Advance", 100, 130), line("500 mg", 100, 170), line("Telmisartan and Azelnidipine", 100, 210))
        val assembled = StripAssembly.assemble(lines, index, Tuning())
        assertEquals(listOf("500 mg"), assembled.loose.map { it.text })
    }
    @Test fun theLabelUnderANameIsShort() {
        val slot = DoseSlot("c", java.time.LocalDate.of(2026, 10, 10), java.time.LocalTime.of(8, 0))
        assertEquals("8:00 am not marked · strength not read", Sentences.chipStatus(MedicationTiming(TimingKind.UNCONFIRMED, slot), true, "en"))
        assertEquals("Due now", Sentences.chipStatus(MedicationTiming(TimingKind.NOW, slot), false, "en"))
    }
}
