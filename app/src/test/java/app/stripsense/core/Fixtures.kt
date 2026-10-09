package app.stripsense.core

/** The list M1 to M5 of spec 01 section 7, and helpers that turn strings into OcrLines. */
object Fixtures {
    private fun mg(v: String) = Strength(v, StrengthUnit.MG)

    val M1 = Medicine(
        id = "m1", nickEn = "fever tablet", nickHi = "बुख़ार की गोली", brand = "FEVORA",
        generics = listOf("PARACETAMOL"), strengths = listOf(mg("500")),
        times = listOf("08:00", "14:00", "20:00"),
    )
    val M2 = Medicine(
        id = "m2", nickEn = "vitamin tablet", nickHi = "विटामिन की गोली", brand = "CALVORA",
        generics = listOf("CALCIUM", "VITAMIN D3"),
        strengths = listOf(mg("500"), Strength("250", StrengthUnit.IU)),
        extraWords = listOf("D"), times = listOf("14:00"),
    )
    val M3 = Medicine(
        id = "m3", nickEn = "BP tablet", nickHi = "बीपी की गोली", brand = "AMLOVA",
        generics = listOf("AMLODIPINE"), strengths = listOf(mg("5")),
        status = MedStatus.STOPPED, stoppedOn = "2026-10-01", times = listOf("08:00"),
    )
    val M4 = Medicine(
        id = "m4", nickEn = "sugar tablet", nickHi = "शुगर की गोली", brand = "GLYMET",
        generics = listOf("METFORMIN"), strengths = listOf(mg("500")), release = "SR",
        times = listOf("08:00", "20:00"),
    )
    val M5 = Medicine(
        id = "m5", nickEn = "eye drops", nickHi = "आँख की दवा", brand = "TEARVA",
        generics = listOf("CARBOXYMETHYLCELLULOSE"), strengths = listOf(Strength("0.5", StrengthUnit.PERCENT)),
        type = MedType.BUTTON, times = listOf("08:00", "20:00"),
    )
    val M6 = Medicine(
        id = "m6", nickEn = "pain tablet", nickHi = "दर्द की गोली", brand = "IBUVA",
        generics = listOf("IBUPROFEN"), strengths = listOf(mg("400")),
    )

    val LIST = listOf(M1, M2, M3, M4, M5)

    /** "a / b / c" -> one usable OcrLine per part (confidence 0.9, height 30, tops 0, 40, 80, ...). */
    fun lines(text: String, times: Int = 1, height: Int = 30, conf: Float = 0.9f): List<OcrLine> {
        val parts = text.split(" / ")
        val all = List(times) { parts }.flatten()
        return all.mapIndexed { i, t -> OcrLine(t, 0, i * 40, 400, i * 40 + height, height, conf) }
    }

    fun key(text: String, times: Int = 1, meds: List<Medicine> = LIST): ReadKey =
        StripParser.parse(lines(text, times), meds)

    fun decide(text: String, times: Int = 1, meds: List<Medicine> = LIST): Verdict =
        Decider.decide(key(text, times, meds), meds)

    fun s(v: String, u: StrengthUnit = StrengthUnit.MG) = Strength(v, u)
}
