package app.stripsense.core.dose

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
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** One time of one medicine on one dosing date. Spec 02 section 2. */
data class DoseSlot(val medId: String, val date: LocalDate, val time: LocalTime) {
    /** "2026-10-10T08:00" */
    val key: String get() = slotKey(date, time)
    val dateTime: LocalDateTime get() = LocalDateTime.of(date, time)
}

fun slotKey(date: LocalDate, time: LocalTime): String =
    String.format(Locale.ROOT, "%sT%02d:%02d", date, time.hour, time.minute)

enum class SlotState { MARKED, NOT_SURE, UNMARKED }

/** Which slot "this dose" is, and why. Spec 02 section 4. */
enum class DoseWhere { IN_WINDOW, PASSED, NEXT, LAST_MARKED }

data class ThisDose(val slot: DoseSlot, val where: DoseWhere, val state: SlotState)

/** Spec 02 section 5. */
data class StripAnswer(val parts: List<Part>, val askNow: Boolean, val slotKey: String)

/** Spec 02 section 6. */
enum class PromptAnswer { YES_TAP, YES_VOICE, HEARD, NO, NOT_SURE, NOT_SURE_VOICE, TIMEOUT }

data class PromptResult(val event: DoseEvent?, val parts: List<Part>, val showUndo: Boolean = false)

/** Spec 02 section 7. ask is true only with MB1. */
data class MarkAnswer(val parts: List<Part>, val ask: Boolean, val slotKey: String)

/** One row of the Today screen. */
data class TodayRow(val med: Medicine, val slot: DoseSlot, val state: SlotState, val markAt: Long?, val due: Boolean, val passed: Boolean)
