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

class ModelRuleTest {
    private val model = 2_588_147_712L

    @Test fun aMedicineListCanNeverReplaceTheModel() {
        // What happened: the 414-byte list file was picked and replaced the installed model.
        assertFalse(ModelRule.accept(414, installed = model, expected = model))
        assertFalse(ModelRule.accept(414, installed = 0, expected = model))
        assertFalse(ModelRule.accept(50L * 1024 * 1024, installed = 0, expected = model))
    }
    @Test fun theRightFileIsAlwaysTaken() {
        assertTrue(ModelRule.accept(model, installed = 0, expected = model))
        assertTrue(ModelRule.accept(model, installed = model, expected = model))
        assertTrue(ModelRule.accept(model, installed = 414, expected = model))
    }
    @Test fun aLargeFileOfAnotherSizeIsTakenOnlyWhenThereIsNoGoodModel() {
        val other = 3_100_000_000L
        assertTrue(ModelRule.accept(other, installed = 0, expected = model))
        assertTrue(ModelRule.accept(other, installed = 414, expected = model))
        assertFalse(ModelRule.accept(other, installed = model, expected = model))
    }
}

class RomanTest {
    private fun r(text: String) = Roman.fromDevanagari(text)

    @Test fun wordsComeOutTheWayTheyAreSaid() {
        assertEquals("aapkee", r("आपकी"))
        assertEquals("aglee", r("अगली"))
        assertEquals("khuraak", r("खुराक"))
        assertEquals("abhee naheen", r("अभी नहीं"))
        assertEquals("samay", r("समय"))
        assertEquals("darj", r("दर्ज"))
        assertEquals("yah", r("यह"))
        assertEquals("hai", r("है"))
        assertEquals("davaa", r("दवा"))
        assertEquals("taraf", r("तरफ़"))
        assertEquals("phon", r("फ़ोन").replace("f", "ph"))
        assertEquals("na", r("न"))
        // A full stop straight after a word must not stop its last sound being its last.
        assertEquals("samay. 25", r("समय। २५"))
    }
    @Test fun medicineNamesNumbersAndTimesPassThrough() {
        assertEquals("yah aapkee Crocin Advance 500 mg hai. aglee khuraak 2:35 pm par hai. abhee naheen.",
            r("यह आपकी Crocin Advance 500 mg है। अगली खुराक 2:35 pm पर है। अभी नहीं।"))
    }
    @Test fun everySpokenScanSentenceBecomesPlainLetters() {
        val med = Fixtures.M1.copy(nickHi = "")
        val slot = DoseSlot(med.id, java.time.LocalDate.of(2026, 10, 10), java.time.LocalTime.of(14, 35))
        val sentences = TimingKind.entries.map { Sentences.scanGuidance(ScanMessage(ScanHint.FOUND, ScanTarget(1, med, MedicationTiming(it, slot), ScanSide.LEFT, true), otherConflict = true), "hi") } +
            Toward.entries.flatMap { listOf(
                Sentences.scanGuidance(ScanMessage(ScanHint.AIM, steerMed = med, steer = it, intro = true), "hi"),
                Sentences.scanGuidance(ScanMessage(ScanHint.AIM, steerMed = med, steer = it), "hi"),
                Sentences.handCue(HandCue.No(med, PlacedKind.NAME, MedicationTiming(TimingKind.LATER, slot), it), "hi"),
                Sentences.handCue(HandCue.Move(it), "hi")) } +
            ScanHint.entries.map { Sentences.scanGuidance(ScanMessage(it), "hi") } +
            listOf(Sentences.handCue(HandCue.Yes(med), "hi"), Sentences.referenceCandidate(med, "hi"), Sentences.reminderFollowUp(med, slot, "hi"))
        for (sentence in sentences) {
            val roman = r(sentence)
            assertTrue(sentence, sentence.any(Roman::isDevanagari))
            assertFalse(roman, roman.any(Roman::isDevanagari))
            assertTrue(roman, roman.isNotBlank() && roman.any { it in 'a'..'z' })
        }
    }
}
