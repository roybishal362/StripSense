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

/** Names printed apart on the pack, and telling the person which way to move the phone. */
class SpreadNameTest {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)
    // As saved on the phone on 10 October. On the pack "Crocin" and "Advance" are never on one line.
    private val crocin = Medicine("c", "Crocin Advance 500 mg", "", "CROCIN ADVANCE", listOf("PARACETAMOL"), listOf(mg("500")))
    private val telma = Medicine("t", "Telmisartan and Azelnidipine 40 mg", "", "TELMISARTAN AND AZELNIDIPINE",
        listOf("TELMISARTAN", "AZELNIDIPINE"), listOf(mg("40")))
    private val p500 = Medicine("x", "P-500 500 mg", "", "P 500", listOf("PARACETAMOL"), listOf(mg("500")))
    private val index = PrintIndex.build(listOf(crocin, telma, p500), listOf(
        Look("l1", "c", emptyList(), "", listOf("Crocin", "ADVANCE", "Paracetamol Tablets IP 500 mg", "GlaxoSmithKline Consumer Healthcare")),
        Look("l2", "t", emptyList(), "", listOf("Telmisartan and Azelnidipine Tablets", "Telmisartan IP 40 mg", "Azelnidipine IP 8 mg")),
        Look("l3", "x", emptyList(), "", listOf("P-500 apex P.500 apex", "apex laboratories private limited", "SIDCO Alathur"))))
    private fun ocr(vararg lines: String) = lines.mapIndexed { i, text -> OcrLine(text, 0, i * 40, 400, i * 40 + 30, 30, 0.9f) }
    private fun show(vararg lines: String): PrintResult {
        val evidence = PrintEvidence()
        repeat(4) { evidence.add(ocr(*lines), index, Tuning(), it * 100L) }
        return evidence.decide(index, 300)
    }

    @Test fun aNameOnTwoLinesIsRead() {
        assertEquals(PrintResult.Checked(crocin), show("Crocin", "ADVANCE", "Paracetamol Tablets IP 500 mg"))
        assertEquals(PrintResult.NameOnly(crocin), show("ADVANCE", "fast release", "Crocin"))
    }
    @Test fun halfANameIsNotTheName() {
        // Another Crocin product, or the word alone, is not Crocin Advance.
        for (result in listOf(show("Crocin", "Pain Relief", "Paracetamol 650 mg"), show("Crocin"), show("ADVANCE", "formula")))
            assertTrue(result.toString(), result !is PrintResult.Checked && result !is PrintResult.NameOnly)
    }
    @Test fun aNameWithAndInItNeedsBothRealWords() {
        assertEquals(PrintResult.Checked(telma), show("Telmisartan and", "Azelnidipine Tablets", "Telmisartan IP 40 mg", "Azelnidipine IP 8 mg"))
        val alone = show("Telmisartan Tablets IP 40 mg")
        assertTrue(alone.toString(), alone !is PrintResult.Checked && alone !is PrintResult.NameOnly)
    }
    @Test fun theOtherTwoStripsAreNeverCalledPFiveHundred() {
        assertEquals(PrintResult.Checked(crocin), show("Crocin", "ADVANCE", "Paracetamol Tablets I.P. 500 mg"))
        assertEquals(PrintResult.Checked(p500), show("P-500 apex P.500 apex", "Paracetamol Tablets I.P", "Each uncoated tablet contains: Paracetamol P500 mg", "apex laboratories private limited"))
    }
    @Test fun aReleaseWordSavedByMistakeDoesNotBlockTheStripForGood() {
        // The real saved Crocin Advance carried release "SR". Its pack prints no such thing.
        val wrong = crocin.copy(release = "SR")
        val saved = PrintIndex.build(listOf(wrong, p500), listOf(
            Look("l1", "c", emptyList(), "", listOf("Crocin", "ADVANCE", "Paracetamol Tablets IP 500 mg", "Sr. No. 12 Dr Annie Besant Road"))))
        val evidence = PrintEvidence()
        repeat(4) { evidence.add(ocr("Crocin", "ADVANCE", "Paracetamol Tablets IP 500 mg"), saved, Tuning(), it * 100L) }
        assertEquals(PrintResult.Checked(wrong), evidence.decide(saved, 300))
    }
    @Test fun aShortSavedNameDoesNotSwallowAnotherStripsStrength() {
        // "I P 500 mg" on the Crocin pack holds the saved name "P 500". Crocin's own 500 mg must still count.
        assertEquals(PrintResult.Checked(crocin), show("Crocin", "ADVANCE", "Paracetamol Tablets I P 500 mg"))
    }
    @Test fun theLabelReaderTakesAReleaseWordOnlyFromTheNameOrStrengthLine() {
        val pack = listOf("Crocin-500", "Paracetamol Tablets IP 500 mg", "Mfd. by Dr Reddys", "SR No 44").mapIndexed { i, t -> OcrLine(t, 0, i * 40, 400, i * 40 + 30, 30, 0.9f) }
        assertEquals("", LabelDrafts.ocr(pack).release)
        val sustained = listOf("Glymet-500 SR", "Metformin Tablets IP 500 mg").mapIndexed { i, t -> OcrLine(t, 0, i * 40, 400, i * 40 + 30, 30, 0.9f) }
        assertEquals("SR", LabelDrafts.ocr(sustained).release)
    }
    @Test fun aPatchWithOneLongWordOfANameBelongsToThatStrip() {
        assertEquals(setOf("CROCIN ADVANCE"), index.namesIn(ocr("Crocin", "500 mg"), Tuning()))
        assertEquals(setOf("TELMISARTAN AND AZELNIDIPINE"), index.namesIn(ocr("Azelnidipine IP 8 mg"), Tuning()))
        assertTrue(index.namesIn(ocr("Paracetamol Tablets IP 500 mg"), Tuning()).isEmpty())
    }
    @Test fun sceneFollowsAStripWhoseNameIsPrintedApart() {
        val scene = Scene()
        var seen = emptyList<SeenStrip>()
        for ((frame, x) in listOf(0, 600, 100, 800, 50).withIndex()) {
            val lines = listOf("Crocin", "ADVANCE", "Paracetamol Tablets IP 500 mg").mapIndexed { i, t -> OcrLine(t, x, 100 + i * 40, x + 300, 130 + i * 40, 30, 0.9f) }
            seen = scene.frame(listOf(StripGroups.StripGroup(lines, CropBox(x, 100, x + 300, 220))), index, Tuning(), frame * 100L)
        }
        assertEquals(listOf("c"), seen.mapNotNull { (it.result as? PrintResult.Checked)?.med?.id })
    }
}
