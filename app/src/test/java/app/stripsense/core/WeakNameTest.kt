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

class WeakNameTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    // Exactly what the phone had saved on 10 October: the product "P-500", stored as the name "P 500".
    private val p500 = Medicine("x", "P-500 500mg", "", "P 500", listOf("PARACETAMOL"), listOf(mg("500")))
    private val saved = listOf("P-500 apex P.500 apex P-500 apex", "Each uncoated tablet contains:Paracetamol P500mg",
        "apex laboratories private limited", "B-23, SIDCO Pharmaceutical Complex-Alathur-603110, Tamil Nad0.")
    private val index = PrintIndex.build(listOf(p500), listOf(Look("l", "x", emptyList(), "", saved)))
    private fun ocr(vararg lines: String) = lines.mapIndexed { i, text -> OcrLine(text, 0, i * 40, 400, i * 40 + 30, 30, 0.9f) }
    private fun show(vararg lines: String): PrintResult {
        val evidence = PrintEvidence()
        repeat(5) { evidence.add(ocr(*lines), index, Tuning(), it * 100L) }
        return evidence.decide(index, 400)
    }

    @Test fun anotherParacetamolStripIsNotTheSavedPFiveHundred() {
        // "I.P. 500 mg" is on every paracetamol 500 strip. It used to read as the name "P 500".
        for (other in listOf(
            arrayOf("CALPOL 500", "Paracetamol Tablets I.P. 500 mg", "GlaxoSmithKline Pharmaceuticals"),
            arrayOf("Dolo-500", "Paracetamol Tablets I. P. 500 mg", "Micro Labs Limited"),
            arrayOf("Crocin", "Paracetamol Tablets I P 500 mg", "Each tablet contains Paracetamol IP 500 mg"),
            arrayOf("Pacimol", "Paracetamol Tablets B.P 500 mg"))) {
            val result = show(*other)
            assertTrue(other.first() + " -> " + result, result == PrintResult.Reading || result == PrintResult.Unknown)
        }
    }
    @Test fun theRealStripIsStillChecked() {
        assertEquals(PrintResult.Checked(p500), show("P-500 apex P.500 apex P-500 apex", "Paracetamol Tablets I.P", "Each uncoated tablet contains: Paracetamol P500 mg", "apex laboratories private limited"))
    }
    @Test fun dottedPharmacopoeiaLettersAreOneWord() {
        assertEquals("PARACETAMOL TABLETS IP 500 MG", TextNorm.normalise("Paracetamol Tablets I.P. 500 mg"))
        assertEquals("TABLETS BP 325 MG", TextNorm.normalise("Tablets B.P325 mg").replace("BP325", "BP 325"))
        assertEquals("P 500", TextNorm.normalise("P.500"))
        assertEquals("USP NF", TextNorm.normalise("U.S.P-NF"))
    }
}
