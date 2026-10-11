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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for StripGroups spatial grouping (Kiro, 2026-10-10). */
class StripGroupsTest {

    /** Helper: makes an OcrLine with the given box; text and confidence are irrelevant for grouping. */
    private fun line(left: Int, top: Int, right: Int, bottom: Int) =
        OcrLine("x", left, top, right, bottom, bottom - top, 0.9f)

    @Test
    fun emptyInput() {
        assertEquals(0, StripGroups.group(emptyList()).size)
    }

    @Test
    fun singleLine() {
        val groups = StripGroups.group(listOf(line(10, 10, 100, 30)))
        assertEquals(1, groups.size)
        assertEquals(1, groups[0].lines.size)
    }

    @Test
    fun twoLinesFarApart_vertical() {
        // Line heights are 20 px; grown margin is 20 px each side.
        // Gap between strips is 400 px, far beyond 40 px overlap zone.
        val l1 = line(0, 0, 200, 20)
        val l2 = line(0, 440, 200, 460)
        val groups = StripGroups.group(listOf(l1, l2))
        assertEquals(2, groups.size)
    }

    @Test
    fun fourLinesOneStrip() {
        // Four lines stacked with 10 px gaps — within one line-height (20 px) so they merge
        val lines = listOf(
            line(0,  0, 200,  20),
            line(0, 30, 200,  50),
            line(0, 60, 200,  80),
            line(0, 90, 200, 110),
        )
        val groups = StripGroups.group(lines)
        assertEquals(1, groups.size)
        assertEquals(4, groups[0].lines.size)
    }

    @Test
    fun threeStripsVertical() {
        // Three strips stacked, each two lines, separated by 200 px gaps
        val a = listOf(line(0,   0, 200,  20), line(0,  30, 200,  50))
        val b = listOf(line(0, 250, 200, 270), line(0, 280, 200, 300))
        val c = listOf(line(0, 500, 200, 520), line(0, 530, 200, 550))
        val groups = StripGroups.group(a + b + c)
        assertEquals(3, groups.size)
    }

    @Test
    fun twoStripsSideBySide_horizontal() {
        // Two strips side by side; gap of 300 px in X, same Y position
        val left  = listOf(line(0,   100, 150, 120), line(0,   130, 150, 150))
        val right = listOf(line(450, 100, 600, 120), line(450, 130, 600, 150))
        val groups = StripGroups.group(left + right)
        assertEquals(2, groups.size)
        // Groups must be returned left-to-right
        assertEquals(0,   groups[0].box.left)
        assertEquals(450, groups[1].box.left)
    }

    @Test
    fun groupBoxIsTightUnion() {
        // Two lines in the same group; the result box is the tight union (no padding)
        val lines = listOf(line(10, 5, 100, 25), line(20, 35, 80, 55))
        val groups = StripGroups.group(lines)
        assertEquals(1, groups.size)
        val box = groups[0].box
        assertEquals(10,  box.left)
        assertEquals(5,   box.top)
        assertEquals(100, box.right)
        assertEquals(55,  box.bottom)
    }

    @Test
    fun middleStripConnectsIfClose() {
        // Three strips where the middle is close to both ends — all merge into one
        val top    = listOf(line(0,  0, 200, 20))   // height 20
        val middle = listOf(line(0, 30, 200, 50))   // 10 px gap from top — within 20 px, merges
        val bottom = listOf(line(0, 60, 200, 80))   // 10 px gap from middle — merges too
        val groups = StripGroups.group(top + middle + bottom)
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].lines.size)
    }

    @Test
    fun sortedLeftToRight() {
        // Lines added in reverse order; output must still be sorted left-to-right
        val far   = listOf(line(800, 0, 900, 20))
        val near  = listOf(line(100, 0, 200, 20))
        val close = listOf(line(450, 0, 550, 20))
        val groups = StripGroups.group(far + close + near)
        assertEquals(3, groups.size)
        assertTrue(groups[0].box.left < groups[1].box.left)
        assertTrue(groups[1].box.left < groups[2].box.left)
    }
}
