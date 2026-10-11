package app.stripsense.core

import app.stripsense.core.domain.MedStatus
import app.stripsense.core.domain.MedType
import app.stripsense.core.dose.DoseEngine
import app.stripsense.core.dose.ExampleFamily
import app.stripsense.core.dose.Supply
import app.stripsense.core.say.SupplyText
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

/** The example family a presenter can add: a wife and a baby with example medicines. */
class ExampleFamilyTest {
    private val engine = DoseEngine()
    private val now = ZonedDateTime.of(2026, 10, 11, 9, 30, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val meds = ExampleFamily.medicines(now, engine.dosingDate(now))

    @Test fun everyoneAndEverythingInItIsMarkedAsAnExample() {
        assertEquals(listOf("Wife", "Baby"), ExampleFamily.people().map { it.callEn })
        assertTrue(ExampleFamily.people().all { ExampleFamily.isExample(it.id) && it.callHi.isNotBlank() })
        assertTrue(meds.all { ExampleFamily.isExample(it.id) && it.noteEn == "Example" && it.noteHi.isNotBlank() })
        assertEquals(meds.size, meds.map { it.id }.toSet().size)
        // Each medicine belongs to one of the two, and each of them has at least one.
        assertEquals(ExampleFamily.people().map { it.id }.toSet(), meds.map { it.personId }.toSet())
        assertFalse(ExampleFamily.isExample("m1"))
        assertFalse(ExampleFamily.isExample(""))
    }

    @Test fun theyAreMarkedWithAButtonSoNoStripHasToBeTaught() {
        assertTrue(meds.all { it.type == MedType.BUTTON && it.status == MedStatus.ACTIVE && engine.times(it).isNotEmpty() && it.strengths.isNotEmpty() })
    }

    @Test fun theyShowWhatTheAppSaysAboutSupply() {
        val look = Supply.needingALook(meds, emptyList(), now, engine)
        // One is nearly finished, and one is past the days the doctor said. The baby's drops are fine.
        assertEquals(setOf("example-wife-d3", "example-wife-calcium"), look.map { it.first.id }.toSet())
        val lines = look.associate { it.first.id to SupplyText.line(it.second, "en") }
        assertEquals("Only 3 days left: 3 tablets. Tell your family to buy more.", lines["example-wife-d3"])
        assertEquals("The 15 days the doctor said are over. Ask the doctor before you go on.", lines["example-wife-calcium"])
    }
}
