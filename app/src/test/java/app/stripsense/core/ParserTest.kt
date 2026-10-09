package app.stripsense.core

import app.stripsense.core.Fixtures.key
import app.stripsense.core.Fixtures.lines
import app.stripsense.core.Fixtures.s
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tokens of vectors V1, V7, V9, V12, V17, V19, V20, and the parser's smaller steps. */
class ParserTest {

    private fun assertKey(
        k: ReadKey,
        brands: Set<String>,
        generics: Set<String>,
        strengths: Set<Strength>,
        brandNumbers: Set<String> = emptySet(),
        release: Set<String> = emptySet(),
        extra: Set<String> = emptySet(),
    ) {
        assertEquals("brands", brands, k.brands)
        assertEquals("generics", generics, k.generics)
        assertEquals("strengths", strengths, k.strengths)
        assertEquals("brand numbers", brandNumbers, k.brandNumbers)
        assertEquals("release", release, k.release)
        assertEquals("extra words", extra, k.extraWords)
    }

    @Test fun v01_tokens() {
        val k = key("FEVORA 500 / Paracetamol Tablets IP 500 mg / FEVORA 500 / Paracetamol Tablets IP 500 mg")
        assertKey(k, setOf("FEVORA"), setOf("PARACETAMOL"), setOf(s("500")), setOf("500"))
        assertEquals(2, k.strengthSeen[s("500")])
    }

    @Test fun v07_tokens() {
        val k = key("FEV0RA 500 / PARACETAM0L TABLETS IP 500MG", times = 2)
        assertKey(k, setOf("FEVORA"), setOf("PARACETAMOL"), setOf(s("500")), setOf("500"))
    }

    @Test fun v09_tokens() {
        val k = key("CALVORA-D / Calcium 500 mg + Vitamin D3 250 IU", times = 2)
        assertKey(
            k, setOf("CALVORA"), setOf("CALCIUM", "VITAMIN D3"),
            setOf(s("500"), s("250", StrengthUnit.IU)), extra = setOf("D"),
        )
        assertEquals(listOf(s("500"), s("250", StrengthUnit.IU)), k.strengths.toList())
    }

    @Test fun v12_tokens() {
        val k = key("GLYMET SR 500 / Metformin Hydrochloride Sustained Release Tablets 500 mg", times = 2)
        assertKey(k, setOf("GLYMET"), setOf("METFORMIN"), setOf(s("500")), setOf("500"), setOf("SR"))
    }

    @Test fun v17_tokens() {
        val k = key("TEARVA / Carboxymethylcellulose Eye Drops 0.5 %", times = 2)
        assertKey(k, setOf("TEARVA"), setOf("CARBOXYMETHYLCELLULOSE"), setOf(s("0.5", StrengthUnit.PERCENT)))
    }

    @Test fun v19_tokens() {
        val k = key("FEVORA 500 / Paracetamol Tablets IP 5OO mg")
        assertKey(k, setOf("FEVORA"), setOf("PARACETAMOL"), emptySet(), setOf("500"))
    }

    @Test fun v20_tokens() {
        val k = key("fevora 500 / paracetamol tablets ip 0.5 g", times = 2)
        assertKey(k, setOf("FEVORA"), setOf("PARACETAMOL"), setOf(s("0.5", StrengthUnit.G)), setOf("500"))
    }

    @Test fun normalise_followsSpecOrder() {
        assertEquals("CALVORA D", TextNorm.normalise("Calvora-D"))
        assertEquals("0.5 % W V", TextNorm.normalise("0.5% w/v"))
        assertEquals("I P 500MG", TextNorm.normalise("I.P. 500mg."))
        assertEquals("10 MCG", TextNorm.normalise("10 µg"))
        assertEquals("10 MCG", TextNorm.normalise("10 μG"))
        assertEquals("A + B", TextNorm.normalise("A+B"))
    }

    @Test fun genericWithOneWrongLetterIsRead() {
        val k = key("Paracetamoi Tablets 500 mg")
        assertEquals(setOf("PARACETAMOL"), k.generics)
    }

    @Test fun brandWithOneWrongLetterIsNotRead() {
        val k = key("FEVORE 500 / Paracetamol 500 mg")
        assertTrue(k.brands.isEmpty())
    }

    @Test fun joinedBrandAndNumber() {
        val k = key("FEVORA500 / Paracetamol 500 mg")
        assertEquals(setOf("FEVORA"), k.brands)
        assertEquals(setOf("500"), k.brandNumbers)
    }

    @Test fun misreadsOfMg() {
        assertEquals(setOf(s("500")), StripParser.strengthsIn("500 rng"))
        assertEquals(setOf(s("500")), StripParser.strengthsIn("500 MQ"))
        assertEquals(setOf(s("25", StrengthUnit.MCG)), StripParser.strengthsIn("25ug"))
        assertEquals(setOf(s("1", StrengthUnit.G)), StripParser.strengthsIn("1 gm"))
    }

    @Test fun weakAndSmallLinesAreNotUsed() {
        val weak = lines("FEVORA 500", conf = 0.3f)
        val small = lines("FEVORA 500", height = 10)
        assertTrue(StripParser.parse(weak, Fixtures.LIST).brands.isEmpty())
        assertEquals(0, StripParser.parse(small, Fixtures.LIST).letters)
        assertEquals(1, StripParser.tooSmallLines(small))
        assertEquals(0, StripParser.tooSmallLines(weak))
    }

    @Test fun smallestKeyLine() {
        val ls = listOf(
            OcrLine("FEVORA 500", 0, 0, 100, 40, 40, 0.9f),
            OcrLine("Paracetamol 500 mg", 0, 50, 100, 66, 16, 0.9f),
            OcrLine("Batch 22", 0, 70, 100, 85, 15, 0.9f),
        )
        assertEquals(16, StripParser.smallestKeyLinePx(ls, Fixtures.LIST))
        assertNull(StripParser.smallestKeyLinePx(lines("Batch 22"), Fixtures.LIST))
    }

    @Test fun onePassReadEqualsTheSeparateCalls() {
        val ls = listOf(
            OcrLine("FEVORA 500", 0, 0, 100, 40, 40, 0.9f),
            OcrLine("Paracetamol 500 mg", 0, 50, 100, 66, 16, 0.9f),
            OcrLine("tiny", 0, 70, 100, 80, 10, 0.9f),
        )
        val r = StripParser.read(ls, Fixtures.LIST)
        assertEquals(StripParser.parse(ls, Fixtures.LIST), r.key)
        assertEquals(StripParser.smallestKeyLinePx(ls, Fixtures.LIST), r.smallestKeyLinePx)
        assertEquals(1, r.tooSmallLines)
    }

    @Test fun lettersCountAfterNormalising() = assertEquals(10, key("Store 25 C / ab-cd").letters)
}
