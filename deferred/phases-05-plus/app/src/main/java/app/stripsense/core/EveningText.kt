package app.stripsense.core

/** The evening message for the family (spec 05 section 5). English only; built from the log, never sent by the app. */
object EveningText {

    fun build(meds: List<Medicine>, events: List<DoseEvent>, now: Long, person: Person, engine: DoseEngine): String {
        val date = engine.dosingDate(now)
        val parts = mutableListOf<String>()
        for (slot in Slot.entries) {
            val items = mutableListOf<String>()
            for (med in meds) {
                if (med.status != MedStatus.ACTIVE) continue
                for (s in engine.slotsFor(med, date)) {
                    if (Slot.of(s.time) != slot || engine.slotMillis(s) > now) continue
                    val mark = engine.markOf(med.id, s.key, events)
                    items += when {
                        mark != null -> {
                            val how = how(mark.source)
                            "${med.nickEn} marked ${Words.timeShown(engine.local(mark.atMillis).toLocalTime(), "en")}" + (if (how != null) " ($how)" else "")
                        }
                        engine.notSureOf(med.id, s.key, events) != null -> "${med.nickEn} not sure"
                        else -> "${med.nickEn} not marked"
                    }
                }
            }
            if (items.isNotEmpty()) parts += "${title(slot)}: ${items.joinToString("; ")}."
        }
        val who = if (person.isWoman) "her" else "him"
        val call = person.callEn.ifBlank { if (person.isWoman) "Her" else "Him" }
        val head = "${person.callEn.ifBlank { "Today" }}, ${Words.date(date, "en")}."
        val tail = "\"Not marked\" doesn't always mean missed. Please check with $call before asking $who to take another."
        return (listOf(head) + parts + tail).joinToString(" ")
    }

    private fun how(s: EventSource): String? = when (s) {
        EventSource.PROMPT_TAP -> "on the prompt"
        EventSource.PROMPT_VOICE -> "by voice"
        EventSource.MARK_BUTTON -> "with its button"
        EventSource.DEMO -> null
    }

    private fun title(s: Slot) = when (s) {
        Slot.MORNING -> "Morning"
        Slot.AFTERNOON -> "Afternoon"
        Slot.EVENING -> "Evening"
        Slot.NIGHT -> "Night"
    }
}
