package app.stripsense.core

import java.time.format.DateTimeFormatter

/** The week as a file, `week.csv` (spec 05 section 5). Pure; the Files tab saves it. */
object WeekFile {

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val HM = DateTimeFormatter.ofPattern("HH:mm")
    const val HEADER = "date,time,nickname,brand,strength,state,marked_at,source"

    fun build(meds: List<Medicine>, events: List<DoseEvent>, now: Long, person: Person, listSavedAtMillis: Long, engine: DoseEngine): String {
        val sb = StringBuilder()
        val saved = if (listSavedAtMillis > 0) engine.local(listSavedAtMillis).format(STAMP) else ""
        sb.append(line("exported_at", engine.local(now).format(STAMP), "person", person.callEn, "list_saved_at", saved, "woman", person.isWoman.toString()))
        sb.append(HEADER).append('\n')
        val today = engine.dosingDate(now)
        for (back in 6 downTo 0) {
            val date = today.minusDays(back.toLong())
            for (med in meds) {
                if (med.status != MedStatus.ACTIVE) continue
                for (slot in engine.slotsFor(med, date)) {
                    val mark = engine.markOf(med.id, slot.key, events)
                    val state = when {
                        mark != null -> "marked"
                        engine.notSureOf(med.id, slot.key, events) != null -> "not sure"
                        engine.slotMillis(slot) > now -> "later"
                        else -> "not marked"
                    }
                    sb.append(
                        line(
                            date.toString(), slot.time.format(HM), med.nickEn, med.brand, Strengths.display(med.strengths), state,
                            mark?.let { engine.local(it.atMillis).format(HM) }.orEmpty(), mark?.let { source(it.source) }.orEmpty(),
                        ),
                    )
                }
            }
        }
        return sb.toString()
    }

    fun source(s: EventSource): String = when (s) {
        EventSource.PROMPT_TAP -> "prompt"
        EventSource.PROMPT_VOICE -> "voice"
        EventSource.MARK_BUTTON -> "button"
        EventSource.DEMO -> ""
    }

    private fun line(vararg cells: String) = cells.joinToString(",") { cell(it) } + "\n"

    private fun cell(c: String) = if (c.any { it == ',' || it == '"' || it == '\n' }) "\"" + c.replace("\"", "\"\"") + "\"" else c

}
