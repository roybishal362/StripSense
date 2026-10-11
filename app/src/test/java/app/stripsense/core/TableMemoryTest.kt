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

class TableMemoryTest {
    private val box = CropBox(100, 100, 300, 200)
    private val med = Fixtures.M1

    @Test fun aStripIsForgottenSoonUnlessAHandIsInView() {
        val table = TableMemory()
        table.see(med.id, med, PlacedKind.CHECKED, box, 0)
        // Lying open in the middle of the picture and not read: gone after a moment, hand or no hand elsewhere.
        val open = CropBox(400, 400, 600, 500)
        table.see(med.id, med, PlacedKind.CHECKED, open, 0)
        assertEquals(1, table.current(TableMemory.UNSEEN_MS, emptySet(), 1000, 1000).size)
        assertTrue(table.current(TableMemory.UNSEEN_MS + 1, emptySet(), 1000, 1000).isEmpty())
        // Under a hand it is remembered, because the hand is why it cannot be read.
        table.see(med.id, med, PlacedKind.CHECKED, open, 10_000)
        assertEquals(med, table.current(10_000 + TableMemory.COVERED_MS, setOf(med.id), 1000, 1000).single().med)
    }
    @Test fun evenWithAHandInViewAReadingGoesStale() {
        val table = TableMemory()
        table.see(med.id, med, PlacedKind.CHECKED, CropBox(400, 400, 600, 500), 0)
        assertTrue(table.current(TableMemory.COVERED_MS + 1, setOf(med.id), 1000, 1000).isEmpty())
    }
    @Test fun theStripTakenAwayIsNotTheOneUnderTheFinger() {
        // P-500 was read here; it was swapped for another strip that overlaps its old place only partly.
        val table = TableMemory()
        table.see(med.id, med, PlacedKind.CHECKED, CropBox(300, 300, 700, 500), 0)
        table.see(Fixtures.M2.id, Fixtures.M2, PlacedKind.NAME, CropBox(500, 350, 900, 600), 400)
        assertEquals(Fixtures.M2, table.current(400, setOf(med.id), 1000, 1000).single().med)
    }
    @Test fun checkedStaysCheckedWhileAReachingHandHidesTheStrength() {
        val table = TableMemory()
        table.see(med.id, med, PlacedKind.CHECKED, box, 0)
        table.see(med.id, med, PlacedKind.NAME, box, 1000)
        assertEquals(PlacedKind.CHECKED, table.current(1000).single().kind)
        table.see(med.id, med, PlacedKind.NAME, box, TableMemory.KEEP_CHECKED_MS + 1)
        assertEquals(PlacedKind.NAME, table.current(TableMemory.KEEP_CHECKED_MS + 1).single().kind)
    }
    @Test fun aNewStripAtTheSamePlaceReplacesTheOldOne() {
        val table = TableMemory()
        table.see(med.id, med, PlacedKind.CHECKED, box, 0)
        table.see(Fixtures.M2.id, Fixtures.M2, PlacedKind.CHECKED, CropBox(110, 105, 310, 205), 100)
        assertEquals(Fixtures.M2, table.current(100).single().med)
        table.see(med.id, med, PlacedKind.CHECKED, CropBox(600, 100, 800, 200), 200)
        assertEquals(2, table.current(200).size)
    }
}
