package app.stripsense.core.dose

import app.stripsense.core.domain.Medicine
import app.stripsense.core.domain.MedType
import app.stripsense.core.domain.Profile
import app.stripsense.core.domain.Strength
import app.stripsense.core.domain.StrengthUnit
import java.time.LocalDate

/**
 * An example family, for showing the app on a phone that has one or two real people on it: a wife and a baby, each
 * with example medicines. It is put in and taken out by whoever is showing the app, never by the app itself, and
 * every medicine in it carries the note "Example".
 *
 * The medicines are ones marked with a button, so no strip has to be taught. They also show what the app says
 * about supply: one is nearly finished, and one is past the number of days the doctor said.
 */
object ExampleFamily {
    const val PREFIX = "example-"
    val WIFE = Profile(PREFIX + "wife", "Wife", "पत्नी", isWoman = true)
    val BABY = Profile(PREFIX + "baby", "Baby", "बेबी")

    fun isExample(id: String): Boolean = id.startsWith(PREFIX)

    fun people(): List<Profile> = listOf(WIFE, BABY)

    /** [now]: when the tablets were counted. [today]: the day the example is put in. */
    fun medicines(now: Long, today: LocalDate): List<Medicine> = listOf(
        Medicine(PREFIX + "wife-d3", "vitamin D tablet", "विटामिन डी की गोली", "VITAMIN D3", listOf("CHOLECALCIFEROL"), listOf(Strength("1000", StrengthUnit.IU)),
            type = MedType.BUTTON, times = listOf("09:00"), noteEn = "Example", noteHi = "उदाहरण", purpose = "VITAMIN", personId = WIFE.id,
            stock = 3, stockAtMillis = now),
        Medicine(PREFIX + "wife-calcium", "calcium tablet", "कैल्शियम की गोली", "CALCIUM", listOf("CALCIUM CARBONATE"), listOf(Strength("500", StrengthUnit.MG)),
            type = MedType.BUTTON, times = listOf("21:00"), noteEn = "Example", noteHi = "उदाहरण", purpose = "NONE", personId = WIFE.id,
            courseDays = 15, courseStart = today.minusDays(16).toString()),
        Medicine(PREFIX + "baby-d3", "vitamin D drops", "विटामिन डी की बूँदें", "VITAMIN D3 DROPS", listOf("CHOLECALCIFEROL"), listOf(Strength("400", StrengthUnit.IU)),
            type = MedType.BUTTON, times = listOf("09:00"), noteEn = "Example", noteHi = "उदाहरण", purpose = "VITAMIN", personId = BABY.id,
            stock = 20, stockAtMillis = now),
    )
}
