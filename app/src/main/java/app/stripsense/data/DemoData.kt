package app.stripsense.data

import app.stripsense.core.domain.MedStatus
import app.stripsense.core.domain.MedType
import app.stripsense.core.domain.Medicine
import app.stripsense.core.domain.Person
import app.stripsense.core.domain.StoreData
import app.stripsense.core.domain.Strength
import app.stripsense.core.domain.StrengthUnit

/**
 * The demo list, copied row by row from docs/sample/list.csv (spec 05 section 2), and the default person.
 * Ids are made from the English nickname, as the list import does.
 */
object DemoData {

    private fun mg(v: String) = Strength(v, StrengthUnit.MG)

    val medicines: List<Medicine> = listOf(
        Medicine(
            id = "fever-tablet", nickEn = "fever tablet", nickHi = "बुख़ार की गोली",
            brand = "FEVORA", generics = listOf("PARACETAMOL"), strengths = listOf(mg("500")),
            type = MedType.CAMERA, status = MedStatus.ACTIVE, times = listOf("08:00", "14:00", "20:00"),
            noteEn = "after food", noteHi = "खाने के बाद", colour = "#C0562D",
        ),
        Medicine(
            id = "vitamin-tablet", nickEn = "vitamin tablet", nickHi = "विटामिन की गोली",
            brand = "CALVORA", generics = listOf("CALCIUM", "VITAMIN D3"),
            strengths = listOf(mg("500"), Strength("250", StrengthUnit.IU)), extraWords = listOf("D"),
            type = MedType.CAMERA, status = MedStatus.ACTIVE, times = listOf("14:00"), colour = "#2F6F9F",
        ),
        Medicine(
            id = "bp-tablet", nickEn = "BP tablet", nickHi = "बीपी की गोली",
            brand = "AMLOVA", generics = listOf("AMLODIPINE"), strengths = listOf(mg("5")),
            type = MedType.CAMERA, status = MedStatus.STOPPED, stoppedOn = "2026-10-01",
            times = listOf("08:00"), colour = "#6B4FA0",
        ),
        Medicine(
            id = "sugar-tablet", nickEn = "sugar tablet", nickHi = "शुगर की गोली",
            brand = "GLYMET", generics = listOf("METFORMIN"), strengths = listOf(mg("500")), release = "SR",
            type = MedType.CAMERA, status = MedStatus.ACTIVE, times = listOf("08:00", "20:00"),
            noteEn = "with food", noteHi = "खाने के साथ", colour = "#1F8A5B",
        ),
        Medicine(
            id = "eye-drops", nickEn = "eye drops", nickHi = "आँख की दवा",
            brand = "TEARVA", generics = listOf("CARBOXYMETHYLCELLULOSE"),
            strengths = listOf(Strength("0.5", StrengthUnit.PERCENT)),
            type = MedType.BUTTON, status = MedStatus.ACTIVE, times = listOf("08:00", "20:00"), colour = "#7A5BC0",
        ),
    )

    val person = Person()

    /** The first data of a new install, and after "Reset everything". */
    fun fresh(): StoreData = StoreData(person = person, medicines = medicines)
}
