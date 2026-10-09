package app.stripsense.core

import app.stripsense.data.DemoData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P04's four cases through parsing, stabilization, answer selection and speech rendering. */
class PhaseFourTest {
    private val meds = DemoData.medicines
    private val person = DemoData.person

    private fun result(text: String, times: List<Long>): Pair<Verdict, Rendered> {
        val session = ScanSession(meds, Tuning())
        val lines = Fixtures.lines(text)
        val frame = StripParser.read(lines, meds)
        times.dropLast(1).forEach {
            assertTrue(session.feed(frame.key, it, frame.smallestKeyLinePx) is ScanResult.Looking)
        }
        val final = session.feed(frame.key, times.last(), frame.smallestKeyLinePx) as ScanResult.Final
        return final.verdict to Sentences.render(Answers.forVerdict(final.verdict, 1), "en", person).single()
    }

    @Test fun listedStripNamesOnlyAfterRepeatedPrint() {
        val (verdict, text) = result("FEVORA 500 / Paracetamol 500 mg / FEVORA 500 / Paracetamol 500 mg", listOf(0, 100))
        assertEquals(Verdict.Match(meds[0]), verdict)
        assertEquals("That's your fever tablet, 500 mg.", text.shown)
        assertEquals("That's your fever tablet, five hundred milligrams.", text.spoken)
        assertEquals(listOf("NAME"), Answers.forVerdict(verdict).map { it.id })
    }

    @Test fun otherStrengthRefusesAndBoxesPrintedStrength() {
        val (verdict, text) = result("FEVORA 650 / Paracetamol 650 mg / FEVORA 650 / Paracetamol 650 mg", listOf(0, 100))
        assertEquals(Verdict.Different(meds[0], "650 mg", "500 mg"), verdict)
        assertEquals("650 mg", text.boxed)
        assertEquals("Careful: this strip says six hundred and fifty milligrams, but yours is five hundred milligrams. This one isn't on your list.", text.spoken)
    }

    @Test fun coveredStrengthRefusesAtTwelveHundredMilliseconds() {
        val (verdict, text) = result("FEVORA / Paracetamol Tablets IP", listOf(0, 1100, 1199, 1200))
        assertEquals(Verdict.Unreadable, verdict)
        assertEquals("I can't read this one. Could you turn it over?", text.spoken)
    }

    @Test fun unknownStripRefusesAtTwelveHundredMilliseconds() {
        val (verdict, text) = result("ZYNTRA 10 / Cetirizine Hydrochloride Tablets IP 10 mg / ZYNTRA 10", listOf(0, 1199, 1200))
        assertEquals(Verdict.Unknown, verdict)
        assertEquals("I don't recognise this one. It isn't on anyone's list here.", text.spoken)
    }

    @Test fun unreadableRetriesProgressAndStayAtC3() {
        val history = AnswerHistory()
        val ids = (1..5).map { Answers.forVerdict(Verdict.Unreadable, history.accept(Verdict.Unreadable)).single().id }
        assertEquals(listOf("C1", "C2", "C3", "C3", "C3"), ids)
    }

    @Test fun everyOtherFinalVerdictResetsUnreadableRun() {
        val other = listOf(Verdict.Match(meds[0]), Verdict.Unknown, Verdict.NotSure, Verdict.OneAtATime,
            Verdict.Different(meds[0], "650 mg", "500 mg"), Verdict.WrongMedicine(meds[0]),
            Verdict.NotShown(meds[0]), Verdict.Stopped(meds[2]), Verdict.ButtonOnly(meds[4]), Verdict.NotTaught(meds[0]))
        other.forEach { verdict ->
            val history = AnswerHistory()
            history.accept(Verdict.Unreadable)
            assertEquals(0, history.accept(verdict))
            assertEquals(1, history.accept(Verdict.Unreadable))
        }
    }

    @Test fun mainTabResetStartsAtC1() {
        val history = AnswerHistory()
        repeat(3) { history.accept(Verdict.Unreadable) }
        history.reset()
        assertEquals(0, history.unreadableCount)
        assertEquals(1, history.accept(Verdict.Unreadable))
    }

    @Test fun separateScanDoesNotReuseFinalVerdict() {
        val first = ScanSession(meds, Tuning())
        val key = StripParser.parse(Fixtures.lines("FEVORA 500 / Paracetamol 500 mg", times = 2), meds)
        first.feed(key, 0, 30)
        assertTrue(first.feed(key, 100, 30) is ScanResult.Final)
        assertFalse(ScanSession(meds, Tuning()).feed(key, 200, 30) is ScanResult.Final)
    }

    @Test fun demoRowsEqualSampleCsvInBothLanguages() {
        val rows = specFile("sample/list.csv").readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }
        assertEquals(meds.size, rows.size)
        for ((row, med) in rows.zip(meds)) {
            val values = row.split(',')
            assertEquals(listOf(med.nickEn, med.nickHi, med.type.name, med.brand,
                med.generics.joinToString(";"), med.strengths.joinToString(";") { Strengths.display(it) },
                med.release.orEmpty(), med.extraWords.joinToString(";"), med.times.joinToString(";"),
                med.noteEn, med.noteHi, med.status.name, med.stoppedOn.orEmpty(), med.colour), values)
        }
        assertEquals(Person(), person)
    }

    @Test fun everySentenceIsCopiedExactlyFromSpec() {
        val section = specFile("spec/03-sentences.md").readText(Charsets.UTF_8)
            .substringAfter("## 2.").substringBefore("## 4.")
        val rows = section.lineSequence().filter { it.startsWith("| ") }.map { it.split('|').map(String::trim) }
            .filter { it.size == 5 && it[1] != "ID" }.toList()
        assertEquals(rows.map { it[1] }.toSet(), Sentences.EN.keys)
        for (row in rows) {
            val id = row[1]
            assertEquals("English $id", row[2], Sentences.EN[id])
            val hindi = row[3].split(" / (to a woman) ")
            assertEquals("Hindi $id", hindi[0], Sentences.HI[id])
            if (hindi.size == 2) assertEquals("Hindi woman $id", hindi[1], Sentences.HI_WOMAN[id])
        }
    }

    private fun specFile(name: String): File = listOf(File("../docs/$name"), File("docs/$name"))
        .first { it.isFile }
}
