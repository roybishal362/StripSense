package app.stripsense.core.say

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
import java.time.LocalDate
import java.time.LocalTime

internal object SentenceValues {
    // "{Call}, " at the start goes with a capital letter; anywhere else it goes with its comma and spaces.
    fun fillCall(t: String, call: String): String {
        if (call.isNotBlank()) return t.replace("{Call}", call)
        if (t.startsWith("{Call}, ")) {
            val rest = t.removePrefix("{Call}, ")
            return rest.replaceFirstChar { it.uppercaseChar() }
        }
        return CALL_ANYWHERE.replace(t, "")
    }

    private val CALL_ANYWHERE = Regex("""\s*,?\s*\{Call\}""")
    private val PLACEHOLDER = Regex("""\{([a-z_]+)\}""")

    fun fill(t: String, v: Map<String, Any>, lang: String, person: Person, spoken: Boolean): String =
        PLACEHOLDER.replace(t) { m -> value(m.groupValues[1], v, lang, person, spoken) }

    @Suppress("UNCHECKED_CAST")
    private fun value(name: String, v: Map<String, Any>, lang: String, person: Person, spoken: Boolean): String {
        val hi = lang == "hi"
        return when (name) {
            "contact", "family" -> person.contact(lang)
            "nickname" -> (v["nickname"] as? Bilingual)?.get(lang).orEmpty()
            "note" -> (v["note"] as? Bilingual)?.get(lang).orEmpty()
            "strength" -> (v["strength"] as? List<Strength>)?.let {
                if (spoken) Words.strengthsSpoken(it, lang) else Strengths.display(it)
            }.orEmpty()
            "time" -> (v["time"] as? LocalTime)?.let {
                if (spoken) Words.timeSpoken(it, lang) else Words.timeShown(it, lang)
            }.orEmpty()
            "at_time" -> (v["time"] as? LocalTime)?.let {
                if (spoken) Words.atTimeSpoken(it) else Words.atTimeShown(it)
            }.orEmpty()
            "slot" -> (v["slot"] as? Slot)?.let { slotWord(it, hi) }.orEmpty()
            "now_part" -> (v["slot"] as? Slot)?.let { nowPart(it, hi) }.orEmpty()
            "this_slot" -> (v["slot"] as? Slot)?.let { thisSlot(it, hi) }.orEmpty()
            "ago" -> (v["ago"] as? Int)?.let { ago(it, lang, spoken) }.orEmpty()
            "when" -> (v["when"] as? WhenValue)?.let { whenText(it, lang) }.orEmpty()
            "date" -> (v["date"] as? LocalDate)?.let { Words.date(it, lang) }.orEmpty()
            "printed", "listpart" -> (v[name] as? String)?.let { if (spoken) Words.displaySpoken(it, lang) else it }.orEmpty()
            "list" -> (v["list"] as? List<ListItem>)?.let { list(it, lang, spoken) }.orEmpty()
            else -> ""
        }
    }

    private fun slotWord(s: Slot, hi: Boolean) = when (s) {
        Slot.MORNING -> if (hi) "सुबह" else "morning"
        Slot.AFTERNOON -> if (hi) "दोपहर" else "afternoon"
        Slot.EVENING -> if (hi) "शाम" else "evening"
        Slot.NIGHT -> if (hi) "रात" else "night"
    }

    private fun nowPart(s: Slot, hi: Boolean) = when (s) {
        Slot.MORNING -> if (hi) "आज सुबह" else "this morning"
        Slot.AFTERNOON -> if (hi) "आज दोपहर" else "this afternoon"
        Slot.EVENING -> if (hi) "आज शाम" else "this evening"
        Slot.NIGHT -> if (hi) "आज रात" else "tonight"
    }

    private fun thisSlot(s: Slot, hi: Boolean) = when (s) {
        Slot.MORNING -> if (hi) "सुबह की खुराक" else "this morning's dose"
        Slot.AFTERNOON -> if (hi) "दोपहर की खुराक" else "this afternoon's dose"
        Slot.EVENING -> if (hi) "शाम की खुराक" else "this evening's dose"
        Slot.NIGHT -> if (hi) "रात की खुराक" else "tonight's dose"
    }

    // Marks under 24 hours old only. Under 60 minutes in minutes, then whole hours rounded down.
    private fun ago(minutesIn: Int, lang: String, spoken: Boolean): String {
        val minutes = minutesIn.coerceAtLeast(1)
        if (minutes >= 1440) return ""
        val hours = minutes >= 60
        val n = if (hours) minutes / 60 else minutes
        val num = if (spoken) Words.number(n.toLong(), lang) else n.toString()
        if (lang == "hi") return ", $num ${if (hours) "घंटे" else "मिनट"} पहले"
        val unit = if (hours) "hour" else "minute"
        return ", $num $unit${if (n == 1) "" else "s"} ago"
    }

    private fun whenText(w: WhenValue, lang: String): String {
        val hi = lang == "hi"
        return when {
            w.daysAgo <= 0 -> if (hi) "आज" else "today"
            w.daysAgo == 1 -> if (hi) "कल" else "yesterday"
            w.daysAgo <= 6 -> if (hi) Words.weekday(w.date, lang) + " को" else "on " + Words.weekday(w.date, lang)
            else -> if (hi) Words.date(w.date, lang) + " को" else "on " + Words.date(w.date, lang)
        }
    }

    private fun list(items: List<ListItem>, lang: String, spoken: Boolean): String {
        val hi = lang == "hi"
        val each = items.map {
            val time = if (spoken) Words.timeSpoken(it.time, lang) else Words.timeShown(it.time, lang)
            if (hi) "${it.nickname.get(lang)} $time" else "your ${it.nickname.get(lang)} at $time"
        }
        if (each.size <= 1) return each.firstOrNull().orEmpty()
        val last = if (hi) " और " else " and "
        return each.dropLast(1).joinToString(", ") + last + each.last()
    }
}
