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

import app.stripsense.core.Fixtures.M1
import app.stripsense.core.Fixtures.M2
import org.junit.Assert.*
import org.junit.Test

class CoreRecoveryTest {
    private fun key(text: String, meds: List<Medicine> = listOf(M1, M2)) =
        StripParser.parse(listOf(OcrLine(text, 0, 0, 700, 40, 40, 1f)), meds, Tuning())

    @Test fun aUsedViewCanBeSavedAsSupportingEvidence() {
        val fragment = key("FEVORA")
        assertFalse(EnrollmentEvidence.conflict(fragment, M1))
        assertFalse(EnrollmentEvidence.verified(fragment, M1, Tuning()))
    }
    @Test fun fullPrintedIdentityCanBeVerified() =
        assertTrue(EnrollmentEvidence.verified(key("FEVORA PARACETAMOL 500 mg"), M1, Tuning()))
    @Test fun wrongStrengthBlocksEnrollment() =
        assertTrue(EnrollmentEvidence.conflict(key("FEVORA PARACETAMOL 650 mg"), M1))
    @Test fun wrongBrandBlocksEnrollment() =
        assertTrue(EnrollmentEvidence.conflict(key("CALVORA CALCIUM 500 mg"), M1))
    @Test fun conflictingVariantBlocksSupportingEnrollmentAndRetrieval() {
        val fragment = key("FEVORA PLUS")
        assertTrue(EnrollmentEvidence.conflict(fragment, M1))
        val look = Look("l", M1.id, listOf(1f, 0f), "a.jpg")
        assertNull(PartialReferences.find(fragment, listOf(1f, 0f), emptyList(), listOf(M1), listOf(look)))
    }
    @Test fun canonicalBrandNumbersDoNotRejectTheSameStrength() =
        assertFalse(EnrollmentEvidence.conflict(key("FEVORA 500.0"), M1))
    @Test fun matchingSavedVariantCanBeSupportingEvidence() =
        assertFalse(EnrollmentEvidence.conflict(key("FEVORA PLUS"), M1.copy(extraWords = listOf("PLUS"))))
    @Test fun bareFoilNeverNamesAMedicine() {
        val look = Look("l", M1.id, listOf(1f, 0f), "a.jpg", fingerprint = listOf(1f, 0f))
        assertNull(PartialReferences.find(ReadKey(), listOf(1f, 0f), listOf(1f, 0f), listOf(M1), listOf(look)))
    }
    @Test fun partialPrintSuggestsAReferenceOnly() {
        val look = Look("l", M1.id, listOf(1f, 0f), "a.jpg")
        val found = PartialReferences.find(key("FEVORA"), listOf(1f, 0f), emptyList(), listOf(M1), listOf(look))
        assertEquals(M1, found?.med)
    }
    @Test fun similarStrengthVariantsStayAmbiguous() {
        val other = M1.copy(id = "other", strengths = listOf(Strength("650", StrengthUnit.MG)))
        val looks = listOf(Look("a", M1.id, listOf(1f, 0f), "a.jpg"), Look("b", other.id, listOf(1f, 0f), "b.jpg"))
        assertNull(PartialReferences.find(key("FEVORA"), listOf(1f, 0f), emptyList(), listOf(M1, other), looks))
    }
    @Test fun visibleConflictingStrengthOverridesVisualSimilarity() {
        val look = Look("l", M1.id, listOf(1f, 0f), "a.jpg")
        assertNull(PartialReferences.find(key("FEVORA 650 mg"), listOf(1f, 0f), emptyList(), listOf(M1), listOf(look)))
    }
    @Test fun migrationPreservesEditsAndTaughtReferences() {
        val edited = M2.copy(brand = "REAL BRAND")
        val look = Look("a", M1.id, listOf(1f), "a.jpg")
        val result = UserDataMigration.clean(StoreData(medicines = listOf(M1, edited), looks = listOf(look)), listOf(M1, M2))
        assertEquals(listOf(M1, edited), result.medicines)
        assertEquals(listOf(look), result.looks)
    }
    @Test fun migrationRemovesOnlyUntaughtUnchangedSamples() {
        val result = UserDataMigration.clean(StoreData(medicines = listOf(M1, M2), clockOffsetMs = 123), listOf(M1, M2))
        assertTrue(result.medicines.isEmpty())
        assertEquals(0L, result.clockOffsetMs)
        assertEquals(1, result.setupVersion)
    }
    @Test fun migrationIsOneTimeAndKeepsLaterFamilyEntries() {
        val data = StoreData(medicines = listOf(M1), setupVersion = 1)
        assertEquals(data, UserDataMigration.clean(data, listOf(M1)))
    }
    @Test fun nearbyDifferentBrandsAreSplitBeforeTheirPrintIsCompared() {
        val lines = listOf(
            OcrLine("FEVORA", 0, 0, 100, 30, 30, 1f),
            OcrLine("PARACETAMOL 500 mg", 0, 35, 110, 65, 30, 1f),
            OcrLine("CALVORA D", 140, 0, 260, 30, 30, 1f),
            OcrLine("CALCIUM 500 mg VITAMIN D3 250 IU", 140, 35, 270, 65, 30, 1f),
        )
        val groups = StripGroups.group(lines, listOf(M1, M2))
        assertEquals(2, groups.size)
        assertTrue(EnrollmentEvidence.verified(StripParser.parse(groups[0].lines, listOf(M1, M2), Tuning()), M1, Tuning()))
        assertTrue(EnrollmentEvidence.verified(StripParser.parse(groups[1].lines, listOf(M1, M2), Tuning()), M2, Tuning()))
    }
    @Test fun oneLineContainingTwoBrandsIsNotInventedAsTwoObjects() {
        val lines = listOf(OcrLine("FEVORA CALVORA", 0, 0, 200, 30, 30, 1f))
        assertEquals(1, StripGroups.group(lines, listOf(M1, M2)).size)
    }
}
