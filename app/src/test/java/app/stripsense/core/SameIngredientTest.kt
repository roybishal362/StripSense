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

class SameIngredientTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private val zone = java.time.ZoneId.of("Asia/Kolkata")
    private val crocin = Medicine("c", "Crocin", "", "CROCIN ADVANCE", listOf("PARACETAMOL"), listOf(mg("500")))
    private val p500 = Medicine("p", "P-500", "", "P 500", listOf("Paracetamol"), listOf(mg("500")))
    private val telma = Medicine("t", "Telma", "", "TELMA", listOf("TELMISARTAN"), listOf(mg("40")))
    private val now = java.time.ZonedDateTime.of(2026, 10, 10, 17, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun marked(med: Medicine, hour: Int, day: Int = 10, id: String = "e$hour") = DoseEvent(id, med.id, "slot",
        java.time.ZonedDateTime.of(2026, 10, day, hour, 49, 0, 0, zone).toInstant().toEpochMilli(), EventKind.TAKEN, EventSource.PROMPT_TAP)

    @Test fun twoNamesOneIngredientIsNoticed() {
        val shared = SameIngredient.of(crocin, listOf(crocin, p500, telma), emptyList(), now, zone)!!
        assertEquals(p500, shared.other)
        assertEquals("paracetamol", shared.ingredient)
        assertNull(shared.markedAt)
        assertNull(SameIngredient.of(telma, listOf(crocin, p500, telma), emptyList(), now, zone))
        assertEquals(1, SameIngredient.pairs(listOf(crocin, p500, telma)).size)
    }
    @Test fun theOtherOnesMarkTodayIsSaidWithItsTimeAndNeverAsAdvice() {
        val shared = SameIngredient.of(crocin, listOf(crocin, p500), listOf(marked(p500, 14)), now, zone)!!
        assertEquals(java.time.LocalTime.of(14, 49), shared.markedAt)
        val text = Sentences.sameIngredient(shared, "en")
        assertEquals("Careful. It has paracetamol, the same as your P-500, which was marked today at 2:49 pm. Ask your family before taking both.", text)
        assertFalse(text.contains("do not take", ignoreCase = true))
        assertTrue(Sentences.sameIngredient(shared, "hi").contains("परिवार से पूछिए"))
    }
    @Test fun aMarkFromYesterdayOrAnUndoneMarkDoesNotCount() {
        assertNull(SameIngredient.of(crocin, listOf(crocin, p500), listOf(marked(p500, 14, day = 9)), now, zone)!!.markedAt)
        val undone = DoseEvent("u", p500.id, "slot", now, EventKind.UNDONE, EventSource.PROMPT_TAP, undoes = "e14")
        assertNull(SameIngredient.of(crocin, listOf(crocin, p500), listOf(marked(p500, 14), undone), now, zone)!!.markedAt)
    }
    @Test fun aStoppedMedicineIsNotCompared() {
        assertNull(SameIngredient.of(crocin, listOf(crocin, p500.copy(status = MedStatus.STOPPED)), emptyList(), now, zone))
    }
    @Test fun theStripInTheFrameCarriesTheFactOnce() {
        val shared = SameIngredient.of(crocin, listOf(crocin, p500), listOf(marked(p500, 14)), now, zone)
        val found = ScanMessage(ScanHint.FOUND, ScanTarget(1, crocin, MedicationTiming(TimingKind.UNCONFIRMED), ScanSide.SINGLE, false), shared = shared)
        assertTrue(Sentences.scanGuidance(found, "en").contains("the same as your P-500"))
        val queue = ScanAnnouncements()
        queue.next(found, 0)
        assertNotNull(queue.next(found, ScanAnnouncements.FOUND_MS))
        assertNull(queue.next(found, 9000))
    }
}
