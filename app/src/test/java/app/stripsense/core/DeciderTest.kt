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

import app.stripsense.core.Fixtures.LIST
import app.stripsense.core.Fixtures.M1
import app.stripsense.core.Fixtures.M2
import app.stripsense.core.Fixtures.M3
import app.stripsense.core.Fixtures.M4
import app.stripsense.core.Fixtures.M5
import app.stripsense.core.Fixtures.M6
import app.stripsense.core.Fixtures.decide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Every vector V1 to V20 of spec 01 section 7. */
class DeciderTest {

    @Test fun v01_match() = assertEquals(
        Verdict.Match(M1),
        decide("FEVORA 500 / Paracetamol Tablets IP 500 mg / FEVORA 500 / Paracetamol Tablets IP 500 mg"),
    )

    // The key moment of the demo: the 650 strip against the 500 on the list.
    @Test fun v02_differentStrength() = assertEquals(
        Verdict.Different(M1, "650 mg", "500 mg"),
        decide("FEVORA 650 / Paracetamol Tablets IP 650 mg / FEVORA 650 / Paracetamol Tablets IP 650 mg"),
    )

    @Test fun v03_noStrength() = assertEquals(Verdict.Unreadable, decide("FEVORA / Paracetamol Tablets IP"))

    @Test fun v04_brandNumberOnly() =
        assertEquals(Verdict.Different(M1, "650 mg", "500 mg"), decide("FEVORA 650 / Paracetamol Tablets IP"))

    @Test fun v05_brandNumberAgreesButNoUnit() =
        assertEquals(Verdict.Unreadable, decide("FEVORA 500 / Paracetamol Tablets IP"))

    @Test fun v06_unknown() = assertEquals(
        Verdict.Unknown,
        decide("ZYNTRA 10 / Cetirizine Hydrochloride Tablets IP 10 mg / ZYNTRA 10"),
    )

    @Test fun v07_letterFixesAndJoinedToken() =
        assertEquals(Verdict.Match(M1), decide("FEV0RA 500 / PARACETAM0L TABLETS IP 500MG", times = 2))

    @Test fun v08_twoStrips() = assertEquals(
        Verdict.OneAtATime,
        decide("FEVORA 500 / Paracetamol Tablets IP 500 mg / FEVORA 650 / Paracetamol 650 mg"),
    )

    @Test fun v09_twoGenericsTwoStrengths() =
        assertEquals(Verdict.Match(M2), decide("CALVORA-D / Calcium 500 mg + Vitamin D3 250 IU", times = 2))

    @Test fun v10_secondStrengthNotRead() =
        assertEquals(Verdict.Unreadable, decide("CALVORA-D / Calcium 500 mg"))

    @Test fun v11_stopped() =
        assertEquals(Verdict.Stopped(M3), decide("AMLOVA 5 / Amlodipine Tablets IP 5 mg", times = 2))

    @Test fun v12_sustainedRelease() = assertEquals(
        Verdict.Match(M4),
        decide("GLYMET SR 500 / Metformin Hydrochloride Sustained Release Tablets 500 mg", times = 2),
    )

    @Test fun v13_plainInsteadOfSr() = assertEquals(
        Verdict.Different(M4, "plain 500 mg", "500 mg SR"),
        decide("GLYMET 500 / Metformin Hydrochloride Tablets IP 500 mg", times = 2),
    )

    @Test fun v14_otherBrandSameMedicine() =
        assertEquals(Verdict.NotShown(M1), decide("PYRAMOL 500 / Paracetamol Tablets IP 500 mg", times = 2))

    @Test fun v15_coldVariant() = assertEquals(
        Verdict.Different(M1, "325 mg + 5 mg", "500 mg"),
        decide("FEVORA COLD / Paracetamol 325 mg + Phenylephrine 5 mg", times = 2),
    )

    @Test fun v16_wrongMedicineInside() = assertEquals(
        Verdict.WrongMedicine(M1),
        decide("FEVORA 500 / Ibuprofen Tablets IP 400 mg", times = 2, meds = LIST + M6),
    )

    @Test fun v16b_withoutM6IsDifferent() = assertEquals(
        Verdict.Different(M1, "400 mg", "500 mg"),
        decide("FEVORA 500 / Ibuprofen Tablets IP 400 mg", times = 2),
    )

    @Test fun v17_buttonOnly() = assertEquals(
        Verdict.ButtonOnly(M5),
        decide("TEARVA / Carboxymethylcellulose Eye Drops 0.5 %", times = 2),
    )

    @Test fun v18_littleTextNothingKnown() = assertEquals(Verdict.Searching, decide("Mfg Lic 123 / Store 25 C"))

    @Test fun v19_lettersInNumberAreNotFixed() {
        val v = decide("FEVORA 500 / Paracetamol Tablets IP 5OO mg")
        assertNotEquals(Verdict.Match(M1), v)
        assertEquals(Verdict.Unreadable, v)
    }

    @Test fun v20_gramsEqualMilligrams() =
        assertEquals(Verdict.Match(M1), decide("fevora 500 / paracetamol tablets ip 0.5 g", times = 2))

    // Extra checks of single rules, beyond the vectors.

    @Test fun d4_twoFullMatchesIsNotSure() {
        val twin = M1.copy(id = "twin", nickEn = "fever tablet 2")
        assertEquals(Verdict.NotSure, decide("FEVORA 500 / Paracetamol 500 mg", times = 2, meds = listOf(M1, twin)))
    }

    @Test fun d9_extraWordNotOnList() = assertEquals(
        Verdict.Different(M1, "FEVORA PLUS", "FEVORA"),
        decide("FEVORA PLUS / Paracetamol Tablets IP 500 mg", times = 2),
    )

    @Test fun d10_releaseSeenWhenListHasNone() = assertEquals(
        Verdict.Different(M1, "500 mg SR", "plain 500 mg"),
        decide("FEVORA SR / Paracetamol Tablets IP 500 mg", times = 2),
    )

    @Test fun d12_brandSeenGenericMissingIsNotSure() =
        assertEquals(Verdict.NotSure, decide("FEVORA / Tablets IP 500 mg", times = 2))

    @Test fun listWordsAreComparedInParserForm() {
        val typed = M1.copy(brand = "Fevora", generics = listOf("paracetamol"))
        assertEquals(Verdict.Match(typed), decide("FEVORA 500 / Paracetamol 500 mg", times = 2, meds = listOf(typed)))
    }
    @Test fun extraKnownGenericCannotNameTheStrip() = assertEquals(
        Verdict.NotSure,
        decide("FEVORA / Paracetamol 500 mg / Metformin 500 mg", times = 2),
    )

    @Test fun decimalCommaCannotBecomeFiveMilligrams() {
        for (med in listOf(M3, M3.copy(status = MedStatus.ACTIVE))) {
            assertEquals(Verdict.Unreadable, decide("AMLOVA / Amlodipine 2,5 mg", times = 2, meds = listOf(med)))
        }
    }

    @Test fun commasBetweenCompleteStrengthsStillParse() = assertEquals(
        setOf(Strength("500", StrengthUnit.MG), Strength("250", StrengthUnit.IU)),
        StripParser.strengthsIn("500mg,250IU"),
    )

}
