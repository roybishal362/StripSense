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

class PointerFinderTest {
    private val w = 72
    private val h = 96
    private val table = 0xFFB68E67.toInt()
    private val hand = 0xFF6E3C28.toInt()
    private fun picture(draw: (Int, Int) -> Int?) = IntArray(w * h) { i -> draw(i % w, i / w) ?: table }

    @Test fun aFingerComingInFromTheLeftIsFoundAtItsTip() {
        // A fist at the left edge and one finger held out to the right.
        val found = PointerFinder.find(picture { x, y -> if ((x < 14 && y in 20..50) || (x < 38 && y in 30..34)) hand else null }, w, h)!!
        assertEquals(37.5f / w, found.x, 0.02f)
        assertEquals(32f / h, found.y, 0.04f)
        assertTrue(found.dx > 0 && kotlin.math.abs(found.dy) < found.dx)
        assertTrue(found.pointing)
    }
    @Test fun anEmptyTableHasNoFinger() {
        assertNull(PointerFinder.find(picture { _, _ -> null }, w, h))
    }
    @Test fun skinColouredPrintInTheMiddleOfTheTableIsNotAFinger() {
        // It does not come in from the edge: it is something lying on the table.
        assertNull(PointerFinder.find(picture { x, y -> if (x in 30..45 && y in 40..55) hand else null }, w, h))
    }
    @Test fun aStripOfAnotherColourAtTheEdgeIsNotAFinger() {
        val blue = 0xFF2040C0.toInt()
        assertNull(PointerFinder.find(picture { x, y -> if (x < 40 && y in 30..40) blue else null }, w, h))
    }
    @Test fun aWholeHandLaidFlatHasATipButIsNotPointing() {
        val found = PointerFinder.find(picture { x, y -> if (x < 36 && y in 20..70) hand else null }, w, h)!!
        assertFalse(found.pointing)
    }
}
