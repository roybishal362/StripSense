package app.stripsense.core

import app.stripsense.core.dose.Proof
import app.stripsense.core.play.Mood
import app.stripsense.core.play.Plants
import app.stripsense.core.report.DoseMark
import app.stripsense.core.report.WeekDose
import app.stripsense.core.report.WeekView
import app.stripsense.core.say.PlayText
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

/** Pattu, the plant: it grows by how the phone knows about each dose, and it has a mood. */
class PlantTest {
    private val today = LocalDate.of(2026, 10, 11)
    private fun dose(day: Int, hour: Int, mark: DoseMark, proof: Proof? = null) =
        WeekDose(LocalDate.of(2026, 10, day), LocalTime.of(hour, 0), Fixtures.M4, mark, if (mark == DoseMark.MARKED) LocalTime.of(hour, 5) else null, proof)
    private fun week(vararg doses: WeekDose) = WeekView((5..11).map { d -> LocalDate.of(2026, 10, d) to doses.filter { it.date.dayOfMonth == d } })
    private fun full(day: Int, proof: Proof = Proof.SEEN) = arrayOf(dose(day, 8, DoseMark.MARKED, proof), dose(day, 20, DoseMark.MARKED, proof))

    @Test fun howThePhoneKnowsDecidesWhatGrows() {
        val plant = Plants.of(week(
            dose(9, 8, DoseMark.MARKED, Proof.PRESSED), dose(9, 20, DoseMark.MARKED, Proof.PRESSED),
            dose(10, 8, DoseMark.MARKED, Proof.SEEN), dose(10, 20, DoseMark.MARKED, Proof.SEEN), dose(11, 8, DoseMark.MARKED, Proof.SEEN),
            dose(11, 20, DoseMark.MARKED, Proof.HEARD)), today)
        assertEquals(2, plant.buds)
        assertEquals(3, plant.leaves)
        assertEquals(1, plant.flowers)
        assertEquals(2 + 3 * 2 + 1 * 3, plant.growth)
        assertEquals(2, plant.stage)
        assertEquals(3, plant.toNextStage)
    }

    @Test fun aWeekThePhoneWitnessedGrowsMoreThanAWeekOfButtonPresses() {
        val pressed = Plants.of(week(*(5..11).flatMap { full(it, Proof.PRESSED).toList() }.toTypedArray()), today)
        val heard = Plants.of(week(*(5..11).flatMap { full(it, Proof.HEARD).toList() }.toTypedArray()), today)
        assertEquals(7, pressed.streak)
        assertEquals(7, heard.streak)
        assertTrue(heard.growth == 3 * pressed.growth)
        assertTrue(heard.stage > pressed.stage)
        assertNull(heard.toNextStage)
    }

    @Test fun fullDaysInARowAreCountedAndAMissedDoseEndsTheRow() {
        val going = Plants.of(week(*full(9), *full(10), dose(11, 8, DoseMark.MARKED, Proof.SEEN), dose(11, 20, DoseMark.LATER)), today)
        assertEquals(3, going.streak)
        assertEquals(1, going.todayMarked)
        assertEquals(2, going.todayAll)
        assertFalse(going.missed)
        val broken = Plants.of(week(*full(9), dose(10, 8, DoseMark.MARKED, Proof.SEEN), dose(10, 20, DoseMark.NOT_MARKED),
            dose(11, 8, DoseMark.MARKED, Proof.SEEN), dose(11, 20, DoseMark.LATER)), today)
        assertEquals(1, broken.streak)
        assertTrue(broken.missed)
        // "Not sure" is not a mark either.
        assertEquals(0, Plants.of(week(dose(11, 8, DoseMark.NOT_SURE)), today).streak)
    }

    @Test fun anEarlyMorningNeitherAddsToTheRowNorBreaksIt() {
        val early = Plants.of(week(*full(9), *full(10), dose(11, 8, DoseMark.LATER), dose(11, 20, DoseMark.LATER)), today)
        assertEquals(2, early.streak)
        assertEquals(0, early.todayMarked)
    }

    @Test fun withNothingMarkedItIsASeed() {
        val seed = Plants.of(week(), today)
        assertEquals(0, seed.growth)
        assertEquals(0, seed.stage)
        assertEquals(0, seed.streak)
        assertEquals(1, seed.toNextStage)
    }

    @Test fun pattuHasAMoodAndSomethingToSayInBothLanguages() {
        val going = Plants.of(week(*full(9), *full(10), dose(11, 8, DoseMark.MARKED, Proof.SEEN), dose(11, 20, DoseMark.LATER)), today)
        val done = Plants.of(week(*full(10), *full(11)), today)
        val missed = Plants.of(week(dose(10, 8, DoseMark.NOT_MARKED), dose(10, 20, DoseMark.NOT_MARKED), dose(11, 8, DoseMark.NOT_MARKED), dose(11, 20, DoseMark.LATER)), today)
        // A dose that is due comes before everything: it looks at you.
        assertEquals(Mood.WAITING, Plants.mood(done, dueNow = true, hour = 23))
        assertEquals(Mood.PROUD, Plants.mood(done, dueNow = false, hour = 21))
        assertEquals(Mood.SAD, Plants.mood(missed, dueNow = false, hour = 12))
        assertEquals(Mood.SLEEPY, Plants.mood(going, dueNow = false, hour = 23))
        assertEquals(Mood.HAPPY, Plants.mood(going, dueNow = false, hour = 12))
        for (mood in Mood.entries) for (lang in listOf("en", "hi")) for (turn in listOf(-1, 0, 1, 2, 7)) {
            val line = PlayText.pattu(mood, going, turn, lang)
            assertTrue(line.isNotBlank())
            // It never scolds and never says what to take.
            assertFalse(line, line.contains("must", true) || line.contains("should", true) || line.contains("take ", true))
        }
        assertNotEquals(PlayText.pattu(Mood.HAPPY, going, 0, "en"), PlayText.pattu(Mood.HAPPY, going, 1, "en"))
        assertTrue(PlayText.pattu(Mood.HAPPY, going, 0, "en").contains("3 days in a row"))
        assertTrue(PlayText.plant(going, "en").contains("3 days in a row"))
        assertTrue(PlayText.plant(missed, "en").contains("missed"))
    }
}
