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

class SceneTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private val parawel = Medicine("p", "Parawel", "", "PARAWEL", listOf("PARACETAMOL"), listOf(mg("325")))
    private val neo = Medicine("n", "Neoberry", "", "NEOBERRY", listOf("BENFOTIAMINE"), listOf(mg("150"), mg("100")))
    private val index = PrintIndex.build(listOf(parawel, neo), emptyList())
    private fun line(text: String, x: Int, y: Int) = OcrLine(text, x, y, x + 300, y + 30, 30, 0.9f)
    private fun strip(x: Int, y: Int, vararg text: String): StripGroups.StripGroup {
        val lines = text.mapIndexed { i, t -> line(t, x, y + i * 40) }
        return StripGroups.StripGroup(lines, CropBox(x, y, x + 300, y + text.size * 40))
    }
    private fun named(seen: List<SeenStrip>) = seen.mapNotNull { (it.result as? PrintResult.Checked)?.med?.id }

    @Test fun aShakingHandDoesNotStartTheStripAgain() {
        val scene = Scene()
        var seen = emptyList<SeenStrip>()
        // The picture jumps by far more than the strip's own size from frame to frame.
        for ((frame, x) in listOf(0, 700, 150, 900, 40, 600).withIndex())
            seen = scene.frame(listOf(strip(x, 100 + frame * 37, "Parawel-325", "Paracetamol Tablets 325 mg")), index, Tuning(), frame * 100L)
        assertEquals(listOf("p"), named(seen))
        // The same strip kept one identity the whole time.
        assertEquals(1, seen.count { it.result is PrintResult.Checked })
    }
    @Test fun aNamedStripIsHeldThroughMissedFramesAndThenLetGo() {
        val scene = Scene()
        repeat(4) { scene.frame(listOf(strip(100, 100, "Parawel-325", "Paracetamol 325 mg")), index, Tuning(), it * 100L) }
        val held = scene.frame(emptyList(), index, Tuning(), 1000)
        assertEquals(listOf("p"), named(held))
        assertFalse(held.single().live)
        assertTrue(scene.frame(emptyList(), index, Tuning(), 300 + Scene.HOLD_MS + 100).isEmpty())
    }
    @Test fun strengthPrintedApartFromTheNameStillCountsForItsStrip() {
        val scene = Scene()
        var seen = emptyList<SeenStrip>()
        repeat(4) {
            val name = StripGroups.StripGroup(listOf(line("Parawel", 100, 100)), CropBox(100, 100, 400, 300))
            // Two lines' height below the name: on the strip, not across the table.
            val strength = StripGroups.StripGroup(listOf(line("325 mg", 150, 190)), CropBox(150, 190, 300, 220))
            seen = scene.frame(listOf(name, strength), index, Tuning(), it * 100L)
        }
        assertEquals(listOf("p"), named(seen))
    }
    @Test fun aNeighboursNumberIsNotTakenForThisStrip() {
        val scene = Scene()
        var seen = emptyList<SeenStrip>()
        repeat(5) {
            val other = StripGroups.StripGroup(listOf(line("650 mg", 900, 100)), CropBox(900, 100, 1100, 130))
            seen = scene.frame(listOf(strip(100, 100, "Parawel-325", "Paracetamol 325 mg"), other), index, Tuning(), it * 100L)
        }
        assertEquals(listOf("p"), named(seen))
        assertTrue(seen.none { it.result is PrintResult.Differs })
    }
    @Test fun twoStripsAreTwoStripsWhereverTheyMove() {
        val scene = Scene()
        var seen = emptyList<SeenStrip>()
        for (frame in 0..4) seen = scene.frame(listOf(
            strip(100 + frame * 200, 100, "Parawel-325", "Paracetamol 325 mg"),
            strip(1500 - frame * 200, 600, "Neoberry", "Benfotiamine 150 mg", "Inositol 100 mg")), index, Tuning(), frame * 100L)
        assertEquals(setOf("p", "n"), named(seen).toSet())
    }
    @Test fun theModelsReadingReachesTheStripItWasAskedAbout() {
        val scene = Scene()
        var seen = emptyList<SeenStrip>()
        repeat(3) { seen = scene.frame(listOf(strip(100, 100, "Parawel", "Dispersible Tablets")), index, Tuning(), it * 100L) }
        val id = seen.single { it.result is PrintResult.NameOnly }.id
        scene.addReading(id, listOf("Parawel-325", "325 mg"), index, Tuning(), 400)
        scene.addReading(id, listOf("Parawel 325", "Paracetamol 325 mg"), index, Tuning(), 900)
        assertEquals(listOf("p"), named(scene.frame(listOf(strip(100, 100, "Parawel", "Dispersible Tablets")), index, Tuning(), 1000)))
    }
    @Test fun cameraMovementIsMeasuredFromThePrintItself() {
        val before = mapOf("PARAWEL 325" to (100 to 100), "NEOBERRY" to (500 to 300), "BENFOTIAMINE" to (520 to 360))
        val after = before.mapValues { (_, at) -> at.first + 40 to at.second - 25 } + ("NEW WORDS" to (9 to 9))
        assertEquals(40 to -25, SceneMotion.estimate(before, after))
        assertNull(SceneMotion.estimate(before, mapOf("PARAWEL 325" to (140 to 75))))
    }
}
