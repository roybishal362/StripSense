package app.stripsense.core

import app.stripsense.core.Fixtures.M1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** The eight checks of spec 03 section 6, and the value rules of section 1. */
class SentencesTest {

    private val maa = Person()
    private val fever = Bilingual(M1.nickEn, M1.nickHi)
    private fun en(p: Part, person: Person = maa) = Sentences.render(p, "en", person)
    private fun hi(p: Part, person: Person = maa) = Sentences.render(p, "hi", person)

    @Test fun check1_p1Morning() = assertEquals(
        "Maa, this is your fever tablet, 500 mg, the morning one. Nothing's marked for this morning yet.",
        en(Part("P1", mapOf("nickname" to fever, "strength" to M1.strengths, "slot" to Slot.MORNING))).shown,
    )

    @Test fun check2_a1OneMinuteAgo() = assertEquals(
        "That's your fever tablet, 500 mg. It's already marked for this morning, at 8:05 am, 1 minute ago.",
        en(
            Part(
                "A1",
                mapOf("nickname" to fever, "strength" to M1.strengths, "slot" to Slot.MORNING, "time" to LocalTime.of(8, 5), "ago" to 1),
            ),
        ).shown,
    )

    @Test fun check3_p2Yesterday() = assertEquals(
        "Last one was marked yesterday at 8:10 pm, 11 hours ago.",
        en(
            Part(
                "P2",
                mapOf("when" to WhenValue(1, LocalDate.of(2026, 10, 9)), "time" to LocalTime.of(20, 10), "ago" to 715),
            ),
        ).shown,
    )

    @Test fun check4_v1Boxed() {
        val r = en(Part("V1", mapOf("printed" to "650 mg", "listpart" to "500 mg")))
        assertEquals("Careful: this strip says 650 mg, but yours is 500 mg. This one isn't on your list.", r.shown)
        assertEquals("650 mg", r.boxed)
        assertEquals(
            "Careful: this strip says six hundred and fifty milligrams, but yours is five hundred milligrams. This one isn't on your list.",
            r.spoken,
        )
    }

    @Test fun check5_englishSpokenTimes() {
        assertEquals("eight oh five in the morning", Words.timeSpoken(LocalTime.of(8, 5), "en"))
        assertEquals("eight o'clock at night", Words.timeSpoken(LocalTime.of(20, 0), "en"))
        assertEquals("eight fifteen in the morning", Words.timeSpoken(LocalTime.of(8, 15), "en"))
        assertEquals("twelve o'clock in the afternoon", Words.timeSpoken(LocalTime.of(12, 0), "en"))
        assertEquals("twelve thirty at night", Words.timeSpoken(LocalTime.of(0, 30), "en"))
    }

    @Test fun check6_hindiSpokenAtTime() {
        assertEquals("सुबह आठ बजकर पाँच मिनट पर", Words.atTimeSpoken(LocalTime.of(8, 5)))
        assertEquals("दोपहर दो बजे", Words.atTimeSpoken(LocalTime.of(14, 0)))
        assertEquals("सुबह 8:05 पर", Words.atTimeShown(LocalTime.of(8, 5)))
        assertEquals("सुबह 8 बजे", Words.atTimeShown(LocalTime.of(8, 0)))
    }

    @Test fun check7_hindiSpokenStrengths() {
        assertEquals("पाँच सौ मिलीग्राम", Words.strengthsSpoken(listOf(Strength("500", StrengthUnit.MG)), "hi"))
        assertEquals("दो दशमलव पाँच मिलीग्राम", Words.strengthsSpoken(listOf(Strength("2.5", StrengthUnit.MG)), "hi"))
    }

    @Test fun check8_everyIdRendersInBothLanguages() {
        val values: Map<String, Any> = mapOf(
            "nickname" to fever, "note" to Bilingual("after food", "खाने के बाद"), "strength" to M1.strengths,
            "time" to LocalTime.of(8, 5), "slot" to Slot.MORNING, "ago" to 12,
            "when" to WhenValue(3, LocalDate.of(2026, 10, 7)), "date" to LocalDate.of(2026, 10, 1),
            "printed" to "650 mg", "listpart" to "500 mg",
            "list" to listOf(ListItem(fever, LocalTime.of(14, 0)), ListItem(fever, LocalTime.of(20, 0))),
        )
        val noName = maa.copy(callEn = "", callHi = "")
        for (id in Sentences.EN.keys) {
            assertTrue("hindi $id", id in Sentences.HI)
            for (person in listOf(maa, noName, maa.copy(isWoman = false))) for (lang in listOf("en", "hi")) {
                val r = Sentences.render(Part(id, values), lang, person)
                assertTrue("$id $lang empty", r.shown.isNotBlank())
                assertFalse("$id $lang shown: ${r.shown}", '{' in r.shown)
                assertFalse("$id $lang spoken: ${r.spoken}", '{' in r.spoken)
            }
        }
        assertEquals(Sentences.EN.keys, Sentences.HI.keys)
    }

    @Test fun callNameMissing() {
        val noName = maa.copy(callEn = "", callHi = "")
        assertEquals("Hi. What would you like to know?", en(Part("HELLO"), noName).shown)
        assertEquals("You're welcome.", en(Part("THX"), noName).shown)
        assertEquals("नमस्ते, बताइए, क्या जानना है?", hi(Part("HELLO"), noName).shown)
        assertEquals("कोई बात नहीं।", hi(Part("THX"), noName).shown)
        assertTrue(en(Part("P1", mapOf("nickname" to fever, "strength" to M1.strengths, "slot" to Slot.MORNING)), noName).shown.startsWith("This is your"))
    }

    @Test fun womanForms() {
        assertEquals("अभी ले रही हैं?", hi(Part("Q1")).shown)
        assertEquals("अभी ले रहे हैं?", hi(Part("Q1"), maa.copy(isWoman = false)).shown)
    }

    @Test fun agoRules() {
        fun ago(m: Int, lang: String = "en") =
            Sentences.render(Part("AM1", mapOf("time" to LocalTime.of(8, 5), "ago" to m)), lang, maa).shown
        assertEquals("That's already marked, at 8:05 am, 12 minutes ago.", ago(12))
        assertEquals("That's already marked, at 8:05 am, 1 hour ago.", ago(60))
        assertEquals("That's already marked, at 8:05 am, 11 hours ago.", ago(715))
        assertEquals("That's already marked, at 8:05 am.", ago(1440))
        assertEquals("ये पहले से सुबह 8:05 पर दर्ज है, 11 घंटे पहले।", ago(715, "hi"))
    }

    @Test fun whenRules() {
        fun w(days: Int, lang: String = "en") = Sentences.render(
            Part("P2", mapOf("when" to WhenValue(days, LocalDate.of(2026, 10, 5)), "time" to LocalTime.of(20, 0))), lang, maa,
        ).shown
        assertEquals("Last one was marked today at 8:00 pm.", w(0))
        assertEquals("Last one was marked on Monday at 8:00 pm.", w(5))
        assertEquals("Last one was marked on 5 October at 8:00 pm.", w(7))
        assertEquals("पिछली खुराक सोमवार को रात 8 बजे दर्ज हुई थी।", w(5, "hi"))
    }

    @Test fun listJoining() {
        val vit = Bilingual("vitamin tablet", "विटामिन की गोली")
        val items = listOf(ListItem(fever, LocalTime.of(14, 0)), ListItem(vit, LocalTime.of(14, 0)), ListItem(fever, LocalTime.of(20, 0)))
        assertEquals(
            "Later today: your fever tablet at 2:00 pm, your vitamin tablet at 2:00 pm and your fever tablet at 8:00 pm.",
            en(Part("WL1", mapOf("list" to items))).shown,
        )
        assertEquals(
            "आज आगे: बुख़ार की गोली दोपहर 2 बजे, विटामिन की गोली दोपहर 2 बजे और बुख़ार की गोली रात 8 बजे।",
            hi(Part("WL1", mapOf("list" to items))).shown,
        )
    }

    @Test fun numbersInWords() {
        assertEquals("six hundred and fifty", Words.number(650, "en"))
        assertEquals("one thousand two hundred and fifty", Words.number(1250, "en"))
        assertEquals("two point five", Words.number("2.5", "en"))
        assertEquals("छह सौ पचास", Words.number(650, "hi"))
        assertEquals("निन्यानबे", Words.number(99, "hi"))
        assertEquals("एक हज़ार", Words.number(1000, "hi"))
    }

    @Test fun answersForVerdicts() {
        assertEquals(listOf("C1"), Answers.forVerdict(Verdict.Unreadable, 1).map { it.id })
        assertEquals(listOf("C2"), Answers.forVerdict(Verdict.Unreadable, 2).map { it.id })
        assertEquals(listOf("C3"), Answers.forVerdict(Verdict.Unreadable, 5).map { it.id })
        assertEquals(listOf("S1", "S0"), Answers.forVerdict(Verdict.Stopped(Fixtures.M3)).map { it.id })
        assertEquals(listOf("NAME"), Answers.forVerdict(Verdict.Match(M1)).map { it.id })
        assertEquals(listOf("K1"), Answers.forVerdict(Verdict.Unknown).map { it.id })
        assertEquals(listOf("U1"), Answers.forVerdict(Verdict.NotSure).map { it.id })
    }
}
