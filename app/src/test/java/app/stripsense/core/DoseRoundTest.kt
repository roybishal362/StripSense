package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.dose.*
import app.stripsense.core.guide.*
import app.stripsense.core.say.*
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class DoseRoundTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private fun med(id: String, purpose: String = "") = Medicine(id, id, "", id.uppercase(), listOf("X"), listOf(mg("5")), times = listOf("20:00"), purpose = purpose)
    private val a = med("telma", "BP")
    private val b = med("glyco", "SUGAR")
    private val c = med("ator")
    private val engine = DoseEngine()
    private val now = ZonedDateTime.of(2026, 10, 10, 20, 2, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun key(m: Medicine) = engine.slotsFor(m, engine.local(now).toLocalDate()).single().key
    private fun mark(m: Medicine, kind: EventKind = EventKind.TAKEN) = DoseEvent("e-${m.id}", m.id, key(m), now, kind, EventSource.PROMPT_TAP)
    private val items = listOf(a, b, c).map { it.id to key(it) }

    @Test fun theRoundGoesOnToTheNextWhenTheOneLookedForIsAnswered() {
        var round = DoseRound.of(items, emptyList(), engine)!!
        assertEquals(a.id, round.current.first)
        assertFalse(round.answered(emptyList(), engine))
        val events = mutableListOf(mark(a))
        assertTrue(round.answered(events, engine))
        round = round.next(events, engine)!!
        assertEquals(b.id to 1, round.current.first to round.at)
        // "Not sure" is an answer too: the round moves on, and that dose is not counted as marked.
        events += mark(b, EventKind.NOT_SURE)
        assertTrue(round.answered(events, engine))
        round = round.next(events, engine)!!
        assertEquals(c.id, round.current.first)
        events += mark(c)
        assertNull(round.next(events, engine))
        assertEquals(2, round.marked(events, engine))
    }
    @Test fun dosesAlreadyAnsweredAreLeftOutAndAnEmptyRoundIsNoRound() {
        assertEquals(listOf(b.id, c.id), DoseRound.of(items, listOf(mark(a)), engine)!!.items.map { it.first })
        assertNull(DoseRound.of(items, listOf(mark(a), mark(b), mark(c)), engine))
        // One marked while another was being looked for is passed over.
        val round = DoseRound.of(items, emptyList(), engine)!!
        assertEquals(c.id, round.next(listOf(mark(a), mark(b)), engine)!!.current.first)
    }
    @Test fun theVoiceSaysWhichOneOfHowManyAndHowItEnded() {
        val start = ScanMessage(ScanHint.START, steerMed = b, roundAt = 2 to 3)
        assertEquals("Medicine 2 of 3. Looking for your sugar medicine, glyco. Move the phone slowly over your medicines.", Sentences.scanGuidance(start, "en"))
        assertTrue(Sentences.scanGuidance(start, "hi").startsWith("3 में से दवा 2। "))
        // A single medicine is not announced as "1 of 1".
        assertTrue(Sentences.scanGuidance(start.copy(roundAt = 1 to 1), "en").startsWith("Looking for your"))
        assertEquals("All 3 medicines for this time are marked.", Sentences.roundDone(3, 3, "en"))
        assertEquals("2 of 3 are marked. The others are still on the Today page.", Sentences.roundDone(2, 3, "en"))
        assertFalse(Sentences.roundDone(2, 3, "en").contains("taken"))
    }
}
