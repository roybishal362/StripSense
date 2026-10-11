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

class OneBoxEachTest {
    private val a = Fixtures.M1
    private val b = Fixtures.M2
    private fun seen(id: Long, result: PrintResult, x: Int) = SeenStrip(id, CropBox(x, 100, x + 200, 200), emptyList(), result, true)

    @Test fun printThatIsNotAPlacedMedicineGetsNoBox() {
        val drawn = Scene.oneBoxEach(listOf(seen(1, PrintResult.Reading, 0), seen(2, PrintResult.Unknown, 300),
            seen(3, PrintResult.TooClose, 600), seen(4, PrintResult.Ambiguous, 900), seen(5, PrintResult.Checked(a), 1200)))
        assertEquals(listOf(5L), drawn.map { it.id })
    }
    @Test fun oneMedicineInSeveralPatchesIsOneBoxAroundThemAll() {
        val drawn = Scene.oneBoxEach(listOf(seen(1, PrintResult.Piece(a, emptyList()), 0), seen(2, PrintResult.Checked(a), 300), seen(3, PrintResult.NameOnly(b), 900)))
        assertEquals(2, drawn.size)
        val first = drawn.single { (it.result as? PrintResult.Checked)?.med == a }
        assertEquals(CropBox(0, 100, 500, 200), first.box)
        assertTrue(drawn.any { it.result == PrintResult.NameOnly(b) })
    }
    @Test fun aRefusedStripKeepsItsOwnBoxBesideAGoodStripOfTheSameMedicine() {
        val drawn = Scene.oneBoxEach(listOf(seen(1, PrintResult.Differs(a, "650", "500 mg"), 0), seen(2, PrintResult.Checked(a), 600)))
        assertEquals(2, drawn.size)
        assertEquals(CropBox(0, 100, 200, 200), drawn.single { it.result is PrintResult.Differs }.box)
    }
    @Test fun theStripsBoxCoversItsStrengthLineAndDoesNotTremble() {
        fun mg(v: String) = Strength(v, StrengthUnit.MG)
        val med = Medicine("p", "Parawel", "", "PARAWEL", listOf("PARACETAMOL"), listOf(mg("325")))
        val index = PrintIndex.build(listOf(med), emptyList())
        val scene = Scene()
        var seen = emptyList<SeenStrip>()
        for (frame in 0..5) {
            // The name reads a few pixels differently every frame, as it does on foil.
            val wobble = if (frame % 2 == 0) 0 else 12
            val name = StripGroups.StripGroup(listOf(OcrLine("Parawel", 100 + wobble, 100, 400, 130, 30, 0.9f)), CropBox(100 + wobble, 100, 400, 300))
            val strength = StripGroups.StripGroup(listOf(OcrLine("325 mg", 150, 190, 300, 220, 30, 0.9f)), CropBox(150, 190, 300, 220))
            seen = scene.frame(listOf(name, strength), index, Tuning(), frame * 100L)
        }
        val strip = seen.single()
        assertEquals(PrintResult.Checked(med), strip.result)
        // One strip, one box, down to the strength line, and its left edge settles between the two readings.
        assertEquals(220, strip.box.bottom)
        assertTrue(strip.box.left.toString(), strip.box.left in 100..112)
    }
}
