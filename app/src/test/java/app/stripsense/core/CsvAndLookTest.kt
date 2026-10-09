package app.stripsense.core

import app.stripsense.core.Fixtures.M1
import app.stripsense.core.Fixtures.M3
import app.stripsense.data.DemoData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {

    private val header = Csv.HEADER.joinToString(",")
    private fun ok(text: String, current: List<Medicine> = emptyList()) = (Csv.read(text, current) as Csv.ReadResult.Ok).medicines
    private fun bad(text: String) = (Csv.read(text) as Csv.ReadResult.Bad).errors

    @Test fun roundTripOfTheDemoList() = assertEquals(DemoData.medicines, ok(Csv.write(DemoData.medicines)))

    @Test fun quotedCellWithComma() {
        val meds = ok("$header\nfever tablet,,CAMERA,FEVORA,PARACETAMOL,500 mg,,,08:00,\"after food, with water\",,ACTIVE,,\n")
        assertEquals("after food, with water", meds[0].noteEn)
        assertEquals(meds, ok(Csv.write(meds)))
    }

    @Test fun hindiText() {
        val meds = ok("$header\nfever tablet,बुख़ार की गोली,CAMERA,FEVORA,PARACETAMOL,500 mg,,,08:00,after food,खाने के बाद,ACTIVE,,\n")
        assertEquals("बुख़ार की गोली", meds[0].nickHi)
        assertEquals("खाने के बाद", meds[0].noteHi)
    }

    @Test fun missingColumn() {
        val errors = bad(Csv.HEADER.dropLast(1).joinToString(",") + "\nx,,BUTTON,,,,,,08:00,,,ACTIVE,\n")
        assertTrue(errors.single().contains("colour"))
    }

    @Test fun badTime() = assertTrue(bad("$header\nfever tablet,,CAMERA,FEVORA,PARACETAMOL,500 mg,,,8am,,,ACTIVE,,\n").single().startsWith("row 2: bad time"))

    @Test fun cameraRowWithNoStrength() =
        assertEquals(listOf("row 2: no strength"), bad("$header\nfever tablet,,CAMERA,FEVORA,PARACETAMOL,,,,08:00,,,ACTIVE,,\n"))

    @Test fun lettersCaseColourAndIds() {
        val meds = ok("$header\nFever Tablet!,,camera,fevora,paracetamol,0.5G,sr,,08:00;06:30,,,active,,\n", current = emptyList())
        val m = meds[0]
        assertEquals("fever-tablet", m.id)
        assertEquals(Strength("0.5", StrengthUnit.G), m.strengths[0])
        assertEquals("SR", m.release)
        assertEquals(listOf("06:30", "08:00"), m.times)
        assertEquals(Csv.DEFAULT_COLOUR, m.colour)
        val kept = ok(Csv.write(listOf(M1.copy(id = "old-id"))), current = listOf(M1.copy(id = "old-id")))
        assertEquals("old-id", kept[0].id)
    }

    @Test fun commaIsNotADecimalPoint() = assertEquals(null, Csv.strength("0,5 %"))

    @Test fun stoppedRowKeepsItsDate() = assertEquals("2026-10-01", ok(Csv.write(listOf(M3)))[0].stoppedOn)
}

class LookCheckTest {
    private val match = Verdict.Match(M1)
    private val look = Look("l", M1.id, listOf(1f, 0f, 0f), "")

    @Test fun cosineOfSameAndOrthogonal() {
        assertEquals(1f, LookCheck.cosine(listOf(1f, 2f), listOf(2f, 4f)), 1e-6f)
        assertEquals(0f, LookCheck.cosine(listOf(1f, 0f), listOf(0f, 1f)), 1e-6f)
    }

    @Test fun noTeachingKeepsMatch() = assertEquals(match, LookCheck.apply(match, listOf(1f, 0f, 0f), emptyList(), true, 0.55f))

    @Test fun offKeepsMatch() = assertEquals(match, LookCheck.apply(match, listOf(0f, 1f, 0f), listOf(look), false, 0.55f))

    @Test fun untaughtMedicine() =
        assertEquals(Verdict.NotTaught(M1), LookCheck.apply(match, listOf(1f, 0f, 0f), listOf(look.copy(medId = "other")), true, 0.55f))

    @Test fun similarLookKeepsMatch() = assertEquals(match, LookCheck.apply(match, listOf(0.9f, 0.1f, 0f), listOf(look), true, 0.55f))

    @Test fun differentLookIsNotShown() =
        assertEquals(Verdict.NotShown(M1), LookCheck.apply(match, listOf(0f, 1f, 0f), listOf(look), true, 0.55f))

    @Test fun lookNeverNamesARefusal() {
        val refusal = Verdict.Different(M1, "650 mg", "500 mg")
        assertEquals(refusal, LookCheck.apply(refusal, listOf(1f, 0f, 0f), listOf(look), true, 0.55f))
    }

    @Test fun cropBoxAddsTwelvePercent() {
        val lines = listOf(OcrLine("a", 100, 100, 300, 140, 40, 0.9f), OcrLine("b", 120, 160, 280, 200, 40, 0.9f))
        assertEquals(CropBox(76, 88, 324, 212), LookCheck.cropBox(lines, 1000, 1000))
        assertEquals(CropBox(0, 0, 50, 50), LookCheck.cropBox(listOf(OcrLine("x", 1, 1, 4, 4, 3, 0.9f)), 50, 50))
    }

    @Test fun bestNamesOnlyAboveThreshold() {
        assertEquals(LookScore(M1.id, 1f), LookCheck.best(listOf(1f, 0f, 0f), listOf(look), 0.55f))
        assertEquals(null, LookCheck.best(listOf(0f, 1f, 0f), listOf(look), 0.55f).medId)
    }
}
