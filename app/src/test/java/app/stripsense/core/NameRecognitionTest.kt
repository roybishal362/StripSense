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

class NameRecognitionTest {
    private fun find(text: String, meds: List<Medicine> = Fixtures.LIST) =
        NameRecognition.find(Fixtures.key(text, meds = meds), meds)

    @Test fun nameAloneFindsTheSavedMedicine() { assertEquals(Fixtures.M1, find("FEVORA")) }
    @Test fun missingIngredientsAndStrengthsDoNotBlockCombinationName() {
        assertEquals(Fixtures.M2, find("CALVORA"))
    }
    @Test fun partialConsistentPrintIsEnough() { assertEquals(Fixtures.M2, find("CALVORA / CALCIUM 500 mg")) }
    @Test fun unknownOrGenericOnlyPrintDoesNotChooseABrand() {
        assertNull(find("UNLISTED")); assertNull(find("PARACETAMOL 500 mg"))
    }
    @Test fun twoBrandsCannotBeCombinedIntoOneResult() { assertNull(find("FEVORA / CALVORA")) }
    @Test fun wrongStrengthOrBrandNumberBlocksNameMatch() {
        assertNull(find("FEVORA 650 mg")); assertNull(find("FEVORA 650"))
    }
    @Test fun conflictingGenericBlocksEvenWithoutStrength() { assertNull(find("FEVORA / METFORMIN")) }
    @Test fun visibleReleaseOrVariantConflictBlocksNameMatch() {
        assertNull(find("FEVORA SR")); assertNull(find("FEVORA DT"))
    }
    @Test fun missingReleaseCanShowASavedScheduleWithoutVerifyingRelease() {
        assertEquals(Fixtures.M4, find("GLYMET"))
    }
    @Test fun duplicateNamesRequireDisambiguatingPrint() {
        val other = Fixtures.M1.copy(id = "other", strengths = listOf(Fixtures.s("650")))
        val meds = listOf(Fixtures.M1, other)
        assertNull(find("FEVORA", meds)); assertEquals(other, find("FEVORA 650 mg", meds))
        assertTrue(NameRecognition.ambiguous(Fixtures.key("FEVORA", meds = meds), meds))
    }
    @Test fun duplicateReleaseVariantsRemainAmbiguousWithoutReleasePrint() {
        val meds = listOf(Fixtures.M4, Fixtures.M4.copy(id = "plain", release = null))
        assertNull(find("GLYMET 500 mg", meds)); assertEquals(Fixtures.M4, find("GLYMET SR 500 mg", meds))
    }
    @Test fun stoppedMedicineIsNotHiddenOrReplacedByAnActiveDuplicate() {
        assertEquals(Fixtures.M3, find("AMLOVA"))
        assertNull(find("AMLOVA", listOf(Fixtures.M3, Fixtures.M3.copy(id = "active", status = MedStatus.ACTIVE))))
    }
    @Test fun buttonMedicineDoesNotBecomeACameraMedicine() { assertNull(find("TEARVA")) }
    @Test fun repeatedNameSurvivesOtherFieldsChanging() {
        val session = NameScanSession(Fixtures.LIST)
        assertNull(session.feed(Fixtures.key("CALVORA")))
        assertEquals(Fixtures.M2, session.feed(Fixtures.key("CALVORA / CALCIUM 500 mg")))
    }
    @Test fun conflictingOrMissingFrameResetsNameStability() {
        val session = NameScanSession(Fixtures.LIST)
        assertNull(session.feed(Fixtures.key("FEVORA")))
        assertNull(session.feed(Fixtures.key("FEVORA 650 mg")))
        assertNull(session.feed(Fixtures.key("FEVORA")))
        assertNull(session.feed(Fixtures.key("nothing")))
        assertNull(session.feed(Fixtures.key("FEVORA")))
        assertEquals(Fixtures.M1, session.feed(Fixtures.key("FEVORA")))
    }
    @Test fun nameScheduleIsExplicitInBothLanguages() {
        val engine = DoseEngine()
        val timing = MedicationTimings.scene(Fixtures.M1, Fixtures.LIST,
            engine.millis(java.time.LocalDateTime.of(2026, 10, 10, 8, 0)), emptyList(), engine)
        assertTrue(Sentences.nameSchedule(Fixtures.M1, timing, "en").contains("Name matched"))
        assertTrue(Sentences.nameSchedule(Fixtures.M1, timing, "hi").contains("नाम मिला"))
        assertTrue(Sentences.nameCheck("en").contains("not verified"))
    }
    @Test fun medicineNameNormalizationUsesExistingParserRules() {
        assertEquals(Fixtures.M1, find("fev0ra"))
        assertNull(find("FEVORX"))
    }
    @Test fun separateStripsCanBeNamedWithoutIngredientLines() {
        val lines = listOf(
            OcrLine("FEVORA", 0, 0, 100, 30, 30, .9f),
            OcrLine("CALVORA", 300, 0, 400, 30, 30, .9f))
        val meds = StripGroups.group(lines, Fixtures.LIST).mapNotNull {
            NameRecognition.find(StripParser.parse(it.lines, Fixtures.LIST), Fixtures.LIST)
        }
        assertEquals(setOf(Fixtures.M1, Fixtures.M2), meds.toSet())
    }
}
