package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.say.*
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ReminderRoundTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private val telma = Medicine("t", "Telma 40", "", "TELMA", listOf("TELMISARTAN"), listOf(mg("40")), purpose = "BP")
    private val crocin = Medicine("c", "Crocin", "", "CROCIN", listOf("PARACETAMOL"), listOf(mg("500")), purpose = "FEVER_PAIN")

    @Test fun theAlarmSaysTheHourEachMedicineByItsPurposeAndWhatToPress() {
        val text = Sentences.reminderRound(listOf(telma, crocin), LocalTime.of(20, 0), "Maa", "en")
        assertEquals("Maa, it is 8:00 pm. This is the time for 2 medicines: your BP medicine, Telma 40; and your fever and pain medicine, Crocin. " +
            "Pick up the phone, or press \"Which one now\", and I will lead you to each strip.", text)
        assertTrue(Sentences.reminderRound(listOf(telma), LocalTime.of(20, 0), "Maa", "en").contains("1 medicine: your BP medicine, Telma 40."))
    }
    @Test fun itNeverTellsHerToTakeAnything() {
        for (lang in listOf("en", "hi")) {
            val text = Sentences.reminderRound(listOf(telma, crocin), LocalTime.of(8, 0), "Maa", lang)
            assertFalse(text, text.contains("take ", ignoreCase = true) || text.contains("taken", ignoreCase = true) || text.contains("ले लीजिए"))
        }
        assertTrue(Sentences.reminderRound(listOf(telma), LocalTime.of(8, 0), "माँ", "hi").contains("अभी कौन-सी"))
    }
    @Test fun theScreenNamesThePartOfTheDayAsThePrototypeDid() {
        assertEquals("Night medicines", Sentences.reminderTitle(LocalTime.of(20, 0), "en"))
        assertEquals("Morning medicines", Sentences.reminderTitle(LocalTime.of(8, 0), "en"))
        assertEquals("Afternoon medicines", Sentences.reminderTitle(LocalTime.of(14, 35), "en"))
        assertEquals("रात की दवाइयाँ", Sentences.reminderTitle(LocalTime.of(20, 0), "hi"))
        assertEquals("Maa, it's time for your night medicines. Pick up the phone, or press \"Which one now?\" I will lead you to each strip.",
            Sentences.reminderLine("Maa", LocalTime.of(20, 0), true, "en"))
        assertEquals("Maa, it's time for your night medicines.", Sentences.reminderLine("Maa", LocalTime.of(20, 0), false, "en"))
    }
    @Test fun withNoNameSavedTheLineDoesNotStartWithAComma() {
        assertEquals("It's time for your night medicines.", Sentences.reminderLine("", LocalTime.of(20, 0), false, "en"))
        assertTrue(Sentences.reminderRound(listOf(telma), LocalTime.of(20, 0), "", "en").startsWith("It is 8:00 pm."))
        assertFalse(Sentences.reminderRound(listOf(telma), LocalTime.of(20, 0), "", "hi").startsWith(","))
    }
}
