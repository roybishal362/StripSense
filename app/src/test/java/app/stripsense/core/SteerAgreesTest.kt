package app.stripsense.core

import app.stripsense.core.guide.*
import app.stripsense.core.say.Sentences
import app.stripsense.core.scan.CropBox
import org.junit.Assert.*
import org.junit.Test

/**
 * Where the strip is in the picture, what the phone says, and what the hand feels must be the same way.
 * One value (Toward) drives the voice, the arrow on the screen and the pattern in the hand; this pins all of
 * them to the place of the strip, for each of the four ways, in both languages.
 */
class SteerAgreesTest {
    private val w = 1440
    private val h = 1920
    private fun strip(cx: Int, cy: Int) = CropBox(cx - 150, cy - 80, cx + 150, cy + 80)
    private fun shape(buzz: Buzz) = buzz.times.zip(buzz.strengths).filter { it.second > 0 }.map { if (it.first >= 150) "buzz" else "tick" }

    private data class Case(val where: CropBox, val way: Toward, val saidEn: String, val shortEn: String, val saidHi: String, val felt: List<String>)

    @Test fun thePlaceTheVoiceAndTheHandAgreeForEveryWay() {
        val cases = listOf(
            // The strip lies at the left of the picture: move the phone left.
            Case(strip(200, 960), Toward.LEFT, "is on the left. Move the phone left.", "Left.", "बाईं ओर ले जाइए", listOf("buzz")),
            Case(strip(1240, 960), Toward.RIGHT, "is on the right. Move the phone right.", "Right.", "दाईं ओर ले जाइए", listOf("buzz", "buzz")),
            // The top of the picture is the far side of the table: move the phone forward.
            Case(strip(720, 200), Toward.AWAY, "further ahead. Move the phone forward.", "Forward.", "आगे ले जाइए", listOf("tick", "buzz")),
            Case(strip(720, 1720), Toward.CLOSER, "closer to you. Move the phone back.", "Back.", "अपनी ओर लाइए", listOf("buzz", "tick")),
        )
        for (case in cases) {
            assertEquals(case.way, ScanGuidance.steer(case.where, w, h, wasCentred = false))
            val message = ScanMessage(ScanHint.AIM, steerMed = Fixtures.M1, steer = case.way)
            val first = Sentences.scanGuidance(message.copy(intro = true), "en")
            assertTrue(first, first.endsWith(case.saidEn))
            assertEquals(case.shortEn, Sentences.scanGuidance(message.copy(intro = false), "en"))
            val hindi = Sentences.scanGuidance(message.copy(intro = true), "hi")
            assertTrue(hindi, hindi.contains(case.saidHi))
            assertEquals(case.felt, shape(FeelLanguage.pull(case.way, 0.5f).merged()!!))
        }
        // No two ways are said or felt alike.
        assertEquals(4, cases.map { it.shortEn }.toSet().size)
        assertEquals(4, cases.map { it.felt }.toSet().size)
    }

    @Test fun onceTheStripIsInTheMiddleNoWayIsGiven() {
        assertNull(ScanGuidance.steer(strip(720, 960), w, h, wasCentred = false))
        // A strip a little off the middle is still "here" once it was centred, and is steered to when it was not.
        assertNull(ScanGuidance.steer(strip(720 + 330, 960), w, h, wasCentred = true))
        assertEquals(Toward.RIGHT, ScanGuidance.steer(strip(720 + 330, 960), w, h, wasCentred = false))
    }

    @Test fun theLargerGapDecidesBetweenSidewaysAndForward() {
        // Far to the right and a little up: right. A little right and far up: forward.
        assertEquals(Toward.RIGHT, ScanGuidance.steer(strip(1300, 800), w, h, false))
        assertEquals(Toward.AWAY, ScanGuidance.steer(strip(860, 150), w, h, false))
    }
}
