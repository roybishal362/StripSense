package app.stripsense.core

import app.stripsense.core.Fixtures.LIST
import app.stripsense.core.Fixtures.M1
import app.stripsense.core.Fixtures.M2
import app.stripsense.core.Fixtures.M5
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class VoiceRequestTest {
    @Test fun cleanObject() = assertEquals(
        VoiceRequest("status_of", 1, "none", "did I take my fever tablet", false),
        VoiceRequest.parse("""{"request":"status_of","medicine":1,"answer":"none","transcript":"did I take my fever tablet","emergency":false}""", 5),
    )

    @Test fun textAround() = assertEquals("whats_left", VoiceRequest.parse("Sure! ```json {\"request\":\"whats_left\"} ``` done", 5)?.request)
    @Test fun brokenObject() = assertNull(VoiceRequest.parse("{\"request\": \"status_of\", ", 5))
    @Test fun medicineOutOfRange() = assertNull(VoiceRequest.parse("""{"request":"status_of","medicine":7}""", 5))
    @Test fun unknownRequest() = assertNull(VoiceRequest.parse("""{"request":"order_pizza"}""", 5))
}

class KeywordsTest {
    @Test fun englishEmergency() = assertEquals(Keywords.Hit.EMERGENCY, Keywords.check("I have chest pain"))
    @Test fun hindiEmergencyWithNukta() = assertEquals(Keywords.Hit.EMERGENCY, Keywords.check("मुझे चक्कर आ रहा है"))
    @Test fun romanisedEmergency() = assertEquals(Keywords.Hit.EMERGENCY, Keywords.check("main gir gayi"))
    @Test fun chandrabinduNormalised() = assertEquals(Keywords.Hit.EMERGENCY, Keywords.check("साँस नहीं आ रही"))
    @Test fun selfHarm() = assertEquals(Keywords.Hit.SELF_HARM, Keywords.check("I want to die"))
    @Test fun hindiSelfHarm() = assertEquals(Keywords.Hit.SELF_HARM, Keywords.check("मैं जीना नहीं चाहती"))
    @Test fun mustNotTrigger() {
        assertEquals(Keywords.Hit.NONE, Keywords.check("did I take all my tablets"))
        assertEquals(Keywords.Hit.NONE, Keywords.check("what's left today"))
        assertEquals(Keywords.Hit.NONE, Keywords.check("सुबह की सारी गोलियाँ ले लीं?"))
    }
    @Test fun greetingsAndThanks() {
        assertTrue(Keywords.isGreeting("Hello!"))
        assertTrue(Keywords.isGreeting("namaste"))
        assertTrue(Keywords.isThanks("thank you so much"))
        assertFalse(Keywords.isGreeting("what's left today"))
    }
}

class VoiceRouterTest {
    private val engine = DoseEngine(ZoneId.of("Asia/Kolkata"))
    private val now = engine.millis(LocalDateTime.of(2026, 10, 10, 8, 6))
    private fun route(r: String, med: Int = 0, transcript: String = "", emergency: Boolean = false, phone: Boolean = true) =
        VoiceRouter.route(VoiceRequest(r, med, "none", transcript, emergency), LIST, now, emptyList(), engine, phone)
    private fun ids(a: VoiceAction) = (a as VoiceAction.Say).parts.map { it.id }

    @Test fun statusWithMedicine() = assertEquals(listOf("VS1", "CHK", "V2"), ids(route("status_of", 1)))
    @Test fun statusWithoutMedicine() = assertEquals("WL2", ids(route("status_of")).first())
    @Test fun whatsLeft() = assertEquals("CHK", ids(route("whats_left")).last())
    @Test fun whichOne() = assertEquals(VoiceAction.OpenScan, route("which_one"))
    @Test fun whichIsCamera() = assertEquals(VoiceAction.ShowPhoto(M2), route("which_is_x", 2))
    @Test fun whichIsButton() = assertEquals(listOf("V9"), ids(route("which_is_x", 5)))
    @Test fun takenNowMarksNothing() = assertEquals(listOf("VS1", "CHK", "V2"), ids(route("taken_now", 1)))
    @Test fun yesNoWithoutMedicine() = assertEquals(listOf("V0"), ids(route("yes_no")))
    @Test fun missedDose() = assertEquals(listOf("MD1"), ids(route("missed_dose")))
    @Test fun extraDose() {
        val a = route("extra_dose") as VoiceAction.Say
        assertEquals(listOf("MD1", "CL1"), a.parts.map { it.id }); assertTrue(a.callButton)
    }
    @Test fun callFamily() = assertTrue((route("call_family") as VoiceAction.Say).callButton)
    @Test fun callFamilyWithoutPhoneOpensHelp() = assertEquals(VoiceAction.Help(false), route("call_family", phone = false))
    @Test fun repeat() = assertEquals(VoiceAction.Repeat, route("repeat"))
    @Test fun medicalQuestion() = assertEquals(listOf("MQ1"), ids(route("medical_question")))
    @Test fun emergency() = assertEquals(VoiceAction.Help(false), route("emergency"))
    @Test fun emergencyFlagInsideOther() = assertEquals(VoiceAction.Help(false), route("status_of", 1, emergency = true))
    @Test fun keywordWinsOverRequest() = assertEquals(VoiceAction.Help(false), route("whats_left", transcript = "I fell down"))
    @Test fun selfHarm() = assertEquals(VoiceAction.Help(true), route("self_harm"))
    @Test fun otherGreeting() = assertEquals(listOf("HELLO"), ids(route("other", transcript = "hello")))
    @Test fun otherThanks() = assertEquals(listOf("THX"), ids(route("other", transcript = "thank you")))
    @Test fun otherElse() = assertEquals(listOf("V0"), ids(route("other", transcript = "the weather is nice")))
    @Test fun noParse() = assertEquals(listOf("V0"), ids(VoiceRouter.route(null, LIST, now, emptyList(), engine, true)))
    @Test fun unparsedRawStillChecked() = assertEquals(VoiceAction.Help(false), VoiceRouter.unparsed("chest pain {"))
    @Test fun typedBeforeModel() {
        assertEquals(VoiceAction.Help(false), VoiceRouter.beforeModel("I have chest pain"))
        assertNull(VoiceRouter.beforeModel("Did I take my fever tablet?"))
    }
    @Test fun promptListsEveryMedicine() {
        val s = VoicePrompt.system(LIST)
        assertTrue(s.contains("1. fever tablet / बुख़ार की गोली; 2. vitamin tablet"))
        assertFalse(s.contains("{medicines}"))
    }
}

class SecondReaderTest {
    @Test fun plain() = assertTrue(SecondReader.agrees("500 mg", M1))
    @Test fun spacesAndCase() = assertTrue(SecondReader.agrees(" 500 MG\n", M1))
    @Test fun twoStrengths() = assertTrue(SecondReader.agrees("500 mg, 250 IU", M2))
    @Test fun otherStrength() = assertFalse(SecondReader.agrees("650 mg", M1))
    @Test fun unreadable() = assertFalse(SecondReader.agrees("UNREADABLE", M1))
    @Test fun empty() = assertFalse(SecondReader.agrees("", M1))
    @Test fun sentence() = assertTrue(SecondReader.agrees("The strength is 500 mg.", M1))
    @Test fun fifty() = assertFalse(SecondReader.agrees("50 mg", M1))
    @Test fun percent() = assertTrue(SecondReader.agrees("0.5%", M5))
    @Test fun outcomes() {
        assertEquals(SecondReader.AGREED, SecondReader.outcome("500 mg", M1))
        assertEquals(SecondReader.NOT_AGREED, SecondReader.outcome("650 mg", M1))
        assertEquals(SecondReader.UNREADABLE, SecondReader.outcome("no idea", M1))
    }
}

class StatsAndWavTest {
    @Test fun median() {
        assertEquals(5.0, Stats.median(listOf(5L))!!, 0.0)
        assertEquals(1.5, Stats.median(listOf(2L, 1L))!!, 0.0)
        assertNull(Stats.median(emptyList()))
    }
    @Test fun p90() {
        assertEquals(7L, Stats.p90(listOf(7L)))
        assertEquals(9L, Stats.p90(listOf(3L, 9L)))
        assertEquals(9L, Stats.p90((1L..10L).toList()))
    }
    @Test fun wavHeader() {
        val w = Wav.wav(ByteArray(10), 16000)
        assertEquals(54, w.size)
        assertArrayEquals("RIFF".toByteArray(), w.copyOfRange(0, 4))
        assertArrayEquals("data".toByteArray(), w.copyOfRange(36, 40))
        assertEquals(10, w[40].toInt())
    }
}
