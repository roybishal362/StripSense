package app.stripsense.core

import java.time.LocalDate
import java.time.LocalTime

/** Numbers, times, dates and units in words, English and Hindi. Spec 03 section 1. */
object Words {

    private val ONES_EN = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS_EN = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    private val HI_0_99 = (
        "शून्य एक दो तीन चार पाँच छह सात आठ नौ दस ग्यारह बारह तेरह चौदह पंद्रह सोलह सत्रह अठारह उन्नीस " +
            "बीस इक्कीस बाईस तेईस चौबीस पच्चीस छब्बीस सत्ताईस अट्ठाईस उनतीस तीस इकतीस बत्तीस तैंतीस चौंतीस " +
            "पैंतीस छत्तीस सैंतीस अड़तीस उनतालीस चालीस इकतालीस बयालीस तैंतालीस चवालीस पैंतालीस छियालीस सैंतालीस " +
            "अड़तालीस उनचास पचास इक्यावन बावन तिरपन चौवन पचपन छप्पन सत्तावन अट्ठावन उनसठ साठ इकसठ बासठ तिरसठ " +
            "चौंसठ पैंसठ छियासठ सड़सठ अड़सठ उनहत्तर सत्तर इकहत्तर बहत्तर तिहत्तर चौहत्तर पचहत्तर छिहत्तर सतहत्तर " +
            "अठहत्तर उन्यासी अस्सी इक्यासी बयासी तिरासी चौरासी पचासी छियासी सत्तासी अट्ठासी नवासी नब्बे इक्यानबे " +
            "बानबे तिरानबे चौरानबे पंचानबे छियानबे सत्तानबे अट्ठानबे निन्यानबे"
        ).split(' ')

    val MONTHS_EN = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    val MONTHS_HI = listOf(
        "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर",
    )

    /** From Monday, as java.time counts: index = dayOfWeek.value - 1. */
    val WEEKDAYS_EN = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val WEEKDAYS_HI = listOf("सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार")

    /** A whole number in words; "650" -> "six hundred and fifty". */
    fun number(n: Long, lang: String): String = if (lang == "hi") hindi(n) else english(n)

    /** A printed number in words; a decimal is read digit by digit after "point". */
    fun number(text: String, lang: String): String {
        val t = text.trim()
        val dot = t.indexOf('.')
        val whole = (if (dot < 0) t else t.substring(0, dot)).ifEmpty { "0" }
        val wholeN = whole.toLongOrNull() ?: return text
        val head = number(wholeN, lang)
        if (dot < 0) return head
        val tail = t.substring(dot + 1)
        if (tail.isEmpty() || tail.any { it !in '0'..'9' }) return head
        val point = if (lang == "hi") "दशमलव" else "point"
        return head + " " + point + " " + tail.map { number((it - '0').toLong(), lang) }.joinToString(" ")
    }

    private fun english(n: Long): String {
        if (n < 0) return "minus " + english(-n)
        if (n < 20) return ONES_EN[n.toInt()]
        if (n < 100) {
            val t = TENS_EN[(n / 10).toInt()]
            return if (n % 10 == 0L) t else t + "-" + ONES_EN[(n % 10).toInt()]
        }
        if (n < 1000) {
            val head = ONES_EN[(n / 100).toInt()] + " hundred"
            return if (n % 100 == 0L) head else head + " and " + english(n % 100)
        }
        if (n < 1_000_000) {
            val head = english(n / 1000) + " thousand"
            val rest = n % 1000
            return when {
                rest == 0L -> head
                rest < 100 -> head + " and " + english(rest)
                else -> head + " " + english(rest)
            }
        }
        return n.toString()
    }

    private fun hindi(n: Long): String {
        if (n < 0) return "माइनस " + hindi(-n)
        if (n < 100) return HI_0_99[n.toInt()]
        if (n < 1000) {
            val head = HI_0_99[(n / 100).toInt()] + " सौ"
            return if (n % 100 == 0L) head else head + " " + HI_0_99[(n % 100).toInt()]
        }
        if (n < 100_000) {
            val head = hindi(n / 1000) + " हज़ार"
            return if (n % 1000 == 0L) head else head + " " + hindi(n % 1000)
        }
        return n.toString()
    }

    // ---- units ----

    fun unitSpoken(unit: StrengthUnit, lang: String): String = if (lang == "hi") {
        when (unit) {
            StrengthUnit.MG -> "मिलीग्राम"
            StrengthUnit.MCG -> "माइक्रोग्राम"
            StrengthUnit.G -> "ग्राम"
            StrengthUnit.IU -> "आई-यू"
            StrengthUnit.ML -> "एम-एल"
            StrengthUnit.PERCENT -> "प्रतिशत"
        }
    } else {
        when (unit) {
            StrengthUnit.MG -> "milligrams"
            StrengthUnit.MCG -> "micrograms"
            StrengthUnit.G -> "grams"
            StrengthUnit.IU -> "international units"
            StrengthUnit.ML -> "millilitres"
            StrengthUnit.PERCENT -> "per cent"
        }
    }

    fun strengthSpoken(s: Strength, lang: String): String = number(s.value, lang) + " " + unitSpoken(s.unit, lang)

    fun strengthsSpoken(list: List<Strength>, lang: String): String =
        list.joinToString(if (lang == "hi") " और " else " and ") { strengthSpoken(it, lang) }

    private val UNIT_IN_TEXT = Regex("""(\d{1,4}(?:\.\d{1,3})?) ?(mg|mcg|g|IU|ml|%)(?![A-Za-z])""")

    /** "plain 650 mg" -> "plain six hundred and fifty milligrams"; " + " is read as "and". */
    fun displaySpoken(text: String, lang: String): String {
        val words = UNIT_IN_TEXT.replace(text) { m ->
            val unit = when (m.groupValues[2]) {
                "mg" -> StrengthUnit.MG
                "mcg" -> StrengthUnit.MCG
                "g" -> StrengthUnit.G
                "IU" -> StrengthUnit.IU
                "ml" -> StrengthUnit.ML
                else -> StrengthUnit.PERCENT
            }
            number(m.groupValues[1], lang) + " " + unitSpoken(unit, lang)
        }
        return words.replace(" + ", if (lang == "hi") " और " else " and ")
    }

    // ---- times ----

    private fun hour12(t: LocalTime): Int = (t.hour % 12).let { if (it == 0) 12 else it }

    private fun partHi(t: LocalTime): String = when (Slot.of(t)) {
        Slot.MORNING -> "सुबह"
        Slot.AFTERNOON -> "दोपहर"
        Slot.EVENING -> "शाम"
        Slot.NIGHT -> "रात"
    }

    private fun partSpokenEn(t: LocalTime): String = when (Slot.of(t)) {
        Slot.MORNING -> "in the morning"
        Slot.AFTERNOON -> "in the afternoon"
        Slot.EVENING -> "in the evening"
        Slot.NIGHT -> "at night"
    }

    /** "8:05 am" / "सुबह 8:05" / "सुबह 8 बजे". */
    fun timeShown(t: LocalTime, lang: String): String {
        val h = hour12(t)
        val mm = t.minute.toString().padStart(2, '0')
        if (lang != "hi") return "$h:$mm " + if (t.hour < 12) "am" else "pm"
        return if (t.minute == 0) "${partHi(t)} $h बजे" else "${partHi(t)} $h:$mm"
    }

    /** "eight oh five in the morning" / "सुबह आठ बजकर पाँच मिनट". */
    fun timeSpoken(t: LocalTime, lang: String): String {
        val h = hour12(t).toLong()
        if (lang == "hi") {
            return if (t.minute == 0) "${partHi(t)} ${hindi(h)} बजे"
            else "${partHi(t)} ${hindi(h)} बजकर ${hindi(t.minute.toLong())} मिनट"
        }
        val clock = when {
            t.minute == 0 -> english(h) + " o'clock"
            t.minute < 10 -> english(h) + " oh " + english(t.minute.toLong())
            else -> english(h) + " " + english(t.minute.toLong())
        }
        return clock + " " + partSpokenEn(t)
    }

    /** Hindi {at_time}: "सुबह 8:05 पर", or "सुबह 8 बजे" on the hour. */
    fun atTimeShown(t: LocalTime): String = if (t.minute == 0) timeShown(t, "hi") else timeShown(t, "hi") + " पर"

    fun atTimeSpoken(t: LocalTime): String = if (t.minute == 0) timeSpoken(t, "hi") else timeSpoken(t, "hi") + " पर"

    // ---- dates ----

    /** "1 October" / "1 अक्टूबर". */
    fun date(d: LocalDate, lang: String): String =
        "${d.dayOfMonth} " + (if (lang == "hi") MONTHS_HI else MONTHS_EN)[d.monthValue - 1]

    fun weekday(d: LocalDate, lang: String): String =
        (if (lang == "hi") WEEKDAYS_HI else WEEKDAYS_EN)[d.dayOfWeek.value - 1]
}
