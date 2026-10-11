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

class StripOutlineTest {
    private val w = 96
    private val h = 128
    private val table = 0xFFB68E67.toInt()
    private val blue = 0xFF2A5CA8.toInt()
    private val foil = 0xFFD8DCE0.toInt()
    private val hand = 0xFF4A2A1E.toInt()
    private fun look(draw: (Int, Int) -> Int?) = TableLook.of(IntArray(w * h) { i -> draw(i % w, i / w) ?: table }, w, h)!!

    @Test fun theBoxIsTheStripNotTheLineItsNameIsPrintedOn() {
        // A blue strip in the middle; its name was read on a narrow band at its right end. Picture 960 x 1280.
        val strip = look { x, y -> if (x in 30..70 && y in 50..80) blue else null }
        val box = strip.outlines(mapOf("CROCIN" to CropBox(660, 520, 700, 780)), 960, 1280).getValue("CROCIN")
        assertEquals(CropBox(300, 500, 710, 810), box)
    }
    @Test fun aFingerLyingOnTheStripIsNotPartOfIt() {
        // The hand comes up from the bottom edge onto the strip.
        val strip = look { x, y -> if (x in 40..46 && y >= 70) hand else if (x in 30..70 && y in 50..80) blue else null }
        val box = strip.outlines(mapOf("CROCIN" to CropBox(660, 520, 700, 780)), 960, 1280).getValue("CROCIN")
        assertEquals(810, box.bottom)
    }
    @Test fun twoStripsThatTouchAreDividedBetweenTheirNames() {
        // One strip above the other, touching. Each name is in the middle of its own strip.
        val both = look { x, y -> if (x in 30..70 && y in 30..59) foil else if (x in 30..70 && y in 60..90) blue else null }
        val boxes = both.outlines(mapOf("UPPER" to CropBox(450, 430, 550, 460), "LOWER" to CropBox(450, 740, 550, 770)), 960, 1280)
        assertTrue(boxes.toString(), boxes.getValue("UPPER").bottom <= 620 && boxes.getValue("LOWER").top >= 580)
        assertEquals(300, boxes.getValue("UPPER").left)
    }
    @Test fun aNamePrintedOnTheTableColourHasNoOutline() {
        val empty = look { _, _ -> null }
        assertTrue(empty.outlines(mapOf("X" to CropBox(400, 400, 500, 440)), 960, 1280).isEmpty())
    }
    @Test fun theSceneDrawsTheOutlineAndCountsTheStrengthPrintedAcrossIt() {
        fun mg(v: String) = Strength(v, StrengthUnit.MG)
        val med = Medicine("c", "Crocin", "", "CROCIN", listOf("PARACETAMOL"), listOf(mg("500")))
        val index = PrintIndex.build(listOf(med), emptyList())
        val scene = Scene()
        // The name runs up the right end of the strip; the strength is printed across it, far to the left.
        val name = OcrLine("Crocin", 660, 520, 700, 780, 40, 0.9f, 90f)
        val strength = OcrLine("Paracetamol Tablets IP 500 mg", 320, 600, 600, 630, 30, 0.9f, 0f)
        var seen = emptyList<SeenStrip>()
        for (frame in 0..4) seen = scene.read(listOf(name, strength), index, Tuning(), frame * 100L) { mapOf("CROCIN" to CropBox(300, 500, 710, 810)) }
        val strip = Scene.oneBoxEach(seen).single()
        assertEquals(PrintResult.Checked(med), strip.result)
        assertEquals(CropBox(300, 500, 710, 810), strip.box)
    }
}
