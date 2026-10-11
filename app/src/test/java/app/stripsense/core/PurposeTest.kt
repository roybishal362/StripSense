package app.stripsense.core

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import org.junit.Assert.*
import org.junit.Test

class PurposeTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    private fun med(nick: String, vararg generics: String, strengths: List<Strength> = listOf(mg("500")), purpose: String = "") =
        Medicine(nick, nick, "", nick.uppercase(), generics.toList(), strengths, purpose = purpose)

    @Test fun theIngredientsSayWhatItIsFor() {
        assertEquals(PurposeKind.FEVER_PAIN, Purposes.suggest(med("Crocin", "PARACETAMOL")))
        assertEquals(PurposeKind.BP, Purposes.suggest(med("Telma", "TELMISARTAN", "AZELNIDIPINE")))
        assertEquals(PurposeKind.SUGAR, Purposes.suggest(med("Glycomet", "Metformin Hydrochloride")))
        // Paracetamol with a cold ingredient is the cold tablet, not the plain fever one.
        assertEquals(PurposeKind.COLD, Purposes.suggest(med("Cold", "PARACETAMOL", "PHENYLEPHRINE HYDROCHLORIDE", "CHLORPHENIRAMINE MALEATE")))
    }
    @Test fun anUnknownIngredientIsNeverGuessed() {
        assertNull(Purposes.suggest(med("X", "XYZOLAMINE")))
        assertNull(Purposes.suggest(med("X", "TELMISARTAN", "XYZOLAMINE")))
        assertNull(Purposes.suggest(med("X")))
        // Two real purposes in one tablet: the family names it, not the phone.
        assertNull(Purposes.suggest(med("X", "TELMISARTAN", "METFORMIN")))
    }
    @Test fun aspirinDependsOnItsStrengthAndIsNotGuessedWithoutOne() {
        assertEquals(PurposeKind.BLOOD_THINNER, Purposes.suggest(med("Ecosprin", "ASPIRIN", strengths = listOf(mg("75")))))
        assertEquals(PurposeKind.PAIN, Purposes.suggest(med("Disprin", "ASPIRIN", strengths = listOf(mg("325")))))
        assertNull(Purposes.suggest(med("Aspirin", "ASPIRIN", strengths = emptyList())))
    }
    @Test fun nothingIsCalledByAPurposeUntilTheFamilyConfirms() {
        val telma = med("Telma 40", "TELMISARTAN")
        assertTrue(Purposes.unasked(telma))
        assertEquals("Telma 40", Answers.nickname(telma).en)
        assertEquals("BP medicine, Telma 40", Answers.nickname(telma.copy(purpose = "BP")).en)
        assertEquals("बीपी की दवा, Telma 40", Answers.nickname(telma.copy(purpose = "BP")).get("hi"))
        val declined = telma.copy(purpose = Purposes.NONE)
        assertFalse(Purposes.unasked(declined))
        assertEquals("Telma 40", Answers.nickname(declined).en)
        // A nickname that already says it is not said twice.
        assertEquals("BP tablet", Answers.nickname(med("BP tablet", "TELMISARTAN", purpose = "BP")).en)
    }
    @Test fun theModelMayOnlyNameOneOfTheFixedKinds() {
        assertEquals(PurposeKind.THYROID, PurposePrompt.parse("{\"group\": \"THYROID\"}"))
        assertNull(PurposePrompt.parse("{\"group\": \"UNKNOWN\"}"))
        assertNull(PurposePrompt.parse("{\"group\": \"CURES_EVERYTHING\"}"))
        assertNull(PurposePrompt.parse("It is probably for blood pressure."))
        assertTrue(PurposePrompt.SCHEMA.contains("\"UNKNOWN\""))
    }
    @Test fun theTableIsToldByPurposeAndSameKindsByTheirOwnNames() {
        val telma = med("Telma 40", "TELMISARTAN", purpose = "BP")
        val crocin = med("Crocin", "PARACETAMOL", purpose = "FEVER_PAIN")
        val p500 = med("P-500", "PARACETAMOL", purpose = "FEVER_PAIN")
        assertEquals("I can see 3 of your medicines. From left to right: your BP medicine; your fever and pain medicine, Crocin; and your fever and pain medicine, P-500.",
            Sentences.overview(listOf(telma, crocin, p500), false, "en"))
        assertEquals("I can see 2 of your medicines. From the far side to the near side: your BP medicine; and your fever and pain medicine.",
            Sentences.overview(listOf(telma, crocin), true, "en"))
        assertTrue(Sentences.overview(listOf(telma, crocin), false, "hi").contains("बाएँ से दाएँ: आपकी बीपी की दवा; और आपकी बुख़ार और दर्द की दवा।"))
    }
    @Test fun theOverviewIsSaidOnceIsNotCutOffByDirectionsAndGivesWayToAStripInTheFrame() {
        val a = med("Telma 40", "TELMISARTAN", purpose = "BP")
        val b = med("Crocin", "PARACETAMOL", purpose = "FEVER_PAIN")
        val table = ScanMessage(ScanHint.OVERVIEW, overview = listOf(a, b))
        val queue = ScanAnnouncements()
        assertNull(queue.next(table, 0))
        assertEquals(table, queue.next(table, ScanAnnouncements.GUESS_MS))
        assertNull(queue.next(table, 9000))
        val aim = ScanMessage(ScanHint.AIM, steerMed = a, steer = Toward.LEFT)
        queue.next(aim, 9500, busy = true)
        assertFalse(queue.stale(12_000))
        val found = ScanMessage(ScanHint.FOUND, ScanTarget(1, a, MedicationTiming(TimingKind.NEXT), ScanSide.SINGLE, false))
        queue.next(found, 13_000, busy = true)
        assertTrue(queue.stale(13_000 + ScanAnnouncements.CHANGED_MS))
    }
    @Test fun aSpokenQuestionCanNameAMedicineByItsPurpose() {
        assertTrue(VoicePrompt.medicines(listOf(med("Telma 40", "TELMISARTAN", purpose = "BP"))).startsWith("1. BP medicine, Telma 40 / "))
    }
}
