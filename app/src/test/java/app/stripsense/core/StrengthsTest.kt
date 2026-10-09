package app.stripsense.core

import app.stripsense.core.Fixtures.M1
import app.stripsense.core.Fixtures.M2
import app.stripsense.core.Fixtures.s
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthsTest {

    @Test fun halfGramEqualsFiveHundredMg() =
        assertTrue(Strengths.sameStrengths(setOf(s("0.5", StrengthUnit.G)), listOf(s("500"))))

    @Test fun twoPointFiveIsNotTwentyFive() = assertFalse(Strengths.sameStrengths(setOf(s("2.5")), listOf(s("25"))))

    @Test fun fiveHundredIsNotSixFifty() = assertFalse(Strengths.sameStrengths(setOf(s("500")), listOf(s("650"))))

    @Test fun trailingZerosDoNotMatter() = assertTrue(Strengths.sameStrengths(setOf(s("500.0")), listOf(s("500"))))

    @Test fun unitMatters() =
        assertFalse(Strengths.sameStrengths(setOf(s("500", StrengthUnit.MCG)), listOf(s("500"))))

    @Test fun mcgIsNotConvertedToMg() =
        assertFalse(Strengths.sameStrengths(setOf(s("500", StrengthUnit.MCG)), listOf(s("0.5"))))

    @Test fun conflictOnForeignStrength() =
        assertTrue(Strengths.conflict(ReadKey(strengths = setOf(s("650"))), M1))

    @Test fun conflictOnForeignBrandNumber() =
        assertTrue(Strengths.conflict(ReadKey(strengths = setOf(s("500")), brandNumbers = setOf("650")), M1))

    @Test fun noConflictOnSubset() =
        assertFalse(Strengths.conflict(ReadKey(strengths = setOf(s("500")), brandNumbers = setOf("500")), M2))

    @Test fun displayJoinsWithPlus() {
        assertEquals("500 mg + 250 IU", Strengths.display(M2.strengths))
        assertEquals("0.5 %", Strengths.display(listOf(s("0.5", StrengthUnit.PERCENT))))
        assertEquals("10 mcg", Strengths.display(listOf(s("10", StrengthUnit.MCG))))
        assertEquals("5 ml", Strengths.display(listOf(s("5", StrengthUnit.ML))))
        assertEquals("1 g", Strengths.display(listOf(s("1", StrengthUnit.G))))
    }
}
