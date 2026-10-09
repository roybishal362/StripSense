package app.stripsense.ui

import app.stripsense.core.Slot
import app.stripsense.core.Words
import java.time.LocalTime

/** Button and screen labels in English and Hindi (spec 03 section 5). Answers never live here. */
object Labels {

    fun get(key: String, language: String): String {
        val pair = EXTRA_LABELS[key] ?: LABELS[key] ?: return key
        return if (language == "hi") pair.second else pair.first
    }

    /** A label with its {x} values filled in: fill("call", lang, "c" to "Rahul"). */
    fun fill(key: String, language: String, vararg values: Pair<String, Any>): String {
        var text = get(key, language)
        for ((k, v) in values) text = text.replace("{$k}", v.toString())
        return text
    }

    fun slot(slot: Slot, language: String): String = get(
        when (slot) {
            Slot.MORNING -> "slotMorning"
            Slot.AFTERNOON -> "slotAfternoon"
            Slot.EVENING -> "slotEvening"
            Slot.NIGHT -> "slotNight"
        },
        language,
    )

    /** "Morning dose · 8:00 am". */
    fun doseLine(time: LocalTime, language: String): String =
        fill("doseLine", language, "s" to slot(Slot.of(time), language), "t" to Words.timeShown(time, language))

    /** The greeting by the clock: morning 04:00 to 11:59, afternoon 12:00 to 15:59, evening otherwise. */
    fun greeting(time: LocalTime, call: String, language: String): String {
        val key = when (time.hour) {
            in 4..11 -> "greetMorning"
            in 12..15 -> "greetAfternoon"
            else -> "greetEvening"
        }
        val text = fill(key, language, "c" to call)
        return if (call.isBlank()) text.trimEnd(' ', ',') else text
    }
    private val EXTRA_LABELS = mapOf(
        "fileWorking" to ("Opening or saving the file. Please wait." to "फ़ाइल खुल या सेव हो रही है। थोड़ा इंतज़ार कीजिए।"),
        "fileReadFailed" to ("The file could not be read. Choose the list file again." to "फ़ाइल नहीं पढ़ी गई। सूची की फ़ाइल फिर चुनिए।"),
        "cameraFailed" to ("The camera could not start. Go back and try Scan again." to "कैमरा शुरू नहीं हुआ। वापस जाकर फिर स्कैन कीजिए।"),
    )
}
