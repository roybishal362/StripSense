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

import app.stripsense.data.Store
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LabelEnrollmentTest {
    @get:Rule val dir = TemporaryFolder()
    private fun lines(vararg text: String) = text.mapIndexed { i, t -> OcrLine(t, 0, i * 45, 800, i * 45 + 40, 40, 1f) }
    private val draft = LabelDraft("P-500", listOf("Paracetamol"), listOf("500 mg"))
    @Test fun productBrandIsNotTheManufacturer() {
        for (brand in listOf("P-500", "P - 500", "P- 500")) {
        val d = LabelDrafts.ocr(lines("$brand apex $brand apex", "Paracetamol Tablets I.P.", "Paracetamol I.P. 500 mg", "apex laboratories private limited"))
        assertEquals(brand, d.brand)
        assertEquals(listOf("Paracetamol"), d.generics)
        assertEquals(listOf("500 mg"), d.strengths)
        }
    }
    @Test fun brandNumberDoesNotInventIngredientStrength() =
        assertTrue(LabelDrafts.ocr(lines("P-500", "Paracetamol Tablets I.P.")).strengths.isEmpty())
    @Test fun modelManufacturerIsReconciledWithPrintedProduct() {
        val model = LabelDraft("APEX", listOf("Paracetamol"), listOf("500 mg"))
        assertEquals("P - 500", LabelDrafts.merge(model, lines("P - 500 apex", "Paracetamol Tablets I.P.", "apex laboratories private limited")).brand)
        assertEquals("P-500", LabelDrafts.merge(model.copy(brand = "Paracetamol"), lines("P-500", "Paracetamol Tablets I.P.")).brand)
        assertTrue(LabelDrafts.reply("""{"brand":"","generics":[""],"strengths":[""],"variants":[""]}""")!!.generics.isEmpty())
        assertNull(LabelDrafts.reply("""{"brand":"X","generics":["Y"],"strengths":["500 mg"],"variants":["unknown"]}"""))
    }
    @Test fun dispersibleFormIsNotAnIngredientAndMustMatch() {
        val lines = lines("Parawel-325", "DT", "Paracetamol Dispersible Tablets B.P 325 mg", "B.P 325 mg")
        val d = LabelDrafts.merge(LabelDraft("Parawel", listOf("Paracetamol"), listOf("325 mg")), lines)
        assertEquals("Parawel-325", d.brand)
        assertEquals(listOf("Paracetamol"), LabelDrafts.ocr(lines).generics)
        assertEquals(listOf("DT"), d.variants)
        val med = d.medicine("dt")
        val key = StripParser.parse(lines, listOf(med))
        assertTrue(EnrollmentEvidence.verified(key, med, Tuning()))
        assertFalse(EnrollmentEvidence.verified(key, med.copy(extraWords = emptyList()), Tuning()))
        val joined = d.copy(brand = "Parawel325").medicine("joined")
        assertTrue(EnrollmentEvidence.verified(StripParser.parse(lines, listOf(joined)), joined, Tuning()))
    }
    @Test fun instructionsAreNotStrengthEvidence() {
        val d = LabelDrafts.ocr(lines("P-500", "Paracetamol Tablets I.P.", "Dosage: 10-15 mg/kg per dose", "maximum daily 4 g"))
        assertTrue(d.strengths.isEmpty())
    }
    @Test fun instructionStrengthDoesNotConflictWithCompositionDuringMatching() {
        val med = draft.medicine("real")
        val key = StripParser.parse(lines("P-500", "PARACETAMOL 500 mg", "Dosage: 10-15 mg/kg per dose"), listOf(med))
        assertEquals(setOf(Strength("500", StrengthUnit.MG)), key.strengths)
        assertTrue(EnrollmentEvidence.verified(key, med, Tuning()))
    }
    @Test fun structuredReplyCannotSupplyMalformedStrength() =
        assertNull(LabelDrafts.reply("""{"brand":"X","generics":["Y"],"strengths":["5,0 mg"]}"""))
    @Test fun emptyModelFieldsRemainUnresolved() =
        assertFalse(LabelDrafts.reply("""{"brand":"","generics":[],"strengths":[]}""")!!.complete())
    @Test fun ingredientDoesNotIncludeThePharmacopoeiaOrDosageForm() =
        assertEquals(listOf("Paracetamol"), LabelDrafts.reply("""{"brand":"P-500","generics":["Paracetamol Tablets I.P."],"strengths":["500 mg"]}""")!!.generics)
    @Test fun markdownWrappedReplyCanBeReviewed() =
        assertTrue(LabelDrafts.reply("```json\n{\"brand\":\"P-500\",\"generics\":[\"Paracetamol\"],\"strengths\":[\"500 mg\"]}\n```")!!.complete())
    @Test fun labelDraftNeverCreatesAPrescribedSchedule() = assertTrue(draft.medicine("new").times.isEmpty())
    @Test fun reviewedExistingIdentityKeepsItsIdAndPrescription() {
        val old = draft.medicine("keep").copy(times = listOf("06:25"))
        assertEquals(old, draft.medicine(old.id, old))
    }
    @Test fun enrollmentPersistsLabelAndAllViewsTogether() {
        val folder = dir.newFolder()
        val store = Store(folder) { StoreData(setupVersion = 1) }
        val med = draft.medicine("real")
        val views = listOf(Look("front", med.id, emptyList(), "front.jpg"), Look("back", med.id, emptyList(), "back.jpg", verifiedPrint = false))
        assertTrue(store.saveEnrollment(med, views)); store.close()
        val reopened = Store(folder) { StoreData() }
        assertEquals(listOf(med), reopened.data.value.medicines)
        assertEquals(views, reopened.data.value.looks); reopened.close()
    }
    @Test fun diskFailureDoesNotPublishTheDraftMedicine() {
        val store = Store(dir.newFile()) { StoreData(setupVersion = 1) }
        assertFalse(store.saveEnrollment(draft.medicine("real"), listOf(Look("v", "real", emptyList(), "v.jpg"))))
        assertTrue(store.data.value.medicines.isEmpty()); store.close()
    }
    @Test fun changedVariantDropsOldIdentityReferences() {
        val med = draft.medicine("real")
        val store = Store(dir.newFolder()) { StoreData(medicines = listOf(med), looks = listOf(Look("old", med.id, emptyList(), "old.jpg")), setupVersion = 1) }
        assertTrue(store.saveEnrollment(med.copy(extraWords = listOf("PLUS")), listOf(Look("new", med.id, emptyList(), "new.jpg"))))
        assertEquals(listOf("new"), store.data.value.looks.map { it.id }); store.close()
    }
}
