package app.stripsense.core.text

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

/** Reading and writing the family's `list.csv` (spec 05 section 3). Pure; the screen shows the errors. */
object Csv {

    val HEADER = listOf(
        "nickname_en", "nickname_hi", "type", "brand", "generics", "strengths", "release", "extra_words",
        "times", "note_en", "note_hi", "status", "stopped_on", "colour",
    )

    const val DEFAULT_COLOUR = "#2F6F9F"
    private val STRENGTH = Regex("""^(\d{1,4}(?:\.\d{1,3})?)\s?(mg|mcg|g|iu|ml|%)$""", RegexOption.IGNORE_CASE)
    private val TIME = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
    private val RELEASES = setOf("SR", "ER", "XR", "CR", "MR", "OD", "XL", "PR", "DR")

    sealed interface ReadResult {
        data class Ok(val medicines: List<Medicine>) : ReadResult
        data class Bad(val errors: List<String>) : ReadResult
    }

    // ---- writing ----

    fun write(meds: List<Medicine>): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append('\n')
        for (m in meds) {
            val cells = listOf(
                m.nickEn, m.nickHi, m.type.name, m.brand, m.generics.joinToString(";"),
                m.strengths.joinToString(";") { Strengths.display(it) }, m.release.orEmpty(), m.extraWords.joinToString(";"),
                m.times.joinToString(";"), m.noteEn, m.noteHi, m.status.name, m.stoppedOn.orEmpty(), m.colour,
            )
            sb.append(cells.joinToString(",") { quote(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun quote(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    // ---- reading ----

    /** Checks every row first. Any bad row: nothing is imported, and each problem is listed. */
    fun read(text: String, current: List<Medicine> = emptyList(), reservedIds: Set<String> = emptySet()): ReadResult {
        val rows = try {
            rows(text.removePrefix("\uFEFF")).filter { r -> r.any { it.isNotBlank() } }
        } catch (e: IllegalArgumentException) {
            return ReadResult.Bad(listOf(e.message ?: "bad CSV format"))
        }
        if (rows.isEmpty()) return ReadResult.Bad(listOf("the file is empty"))
        val header = rows[0].map { it.trim().lowercase() }
        val missing = HEADER.filter { it !in header }
        if (missing.isNotEmpty()) return ReadResult.Bad(listOf("missing column: " + missing.joinToString(", ")))
        if (header.distinct().size != header.size) return ReadResult.Bad(listOf("a column is used twice"))
        val col = HEADER.associateWith { header.indexOf(it) }

        val errors = mutableListOf<String>()
        val meds = mutableListOf<Medicine>()
        // Reserve old IDs before reading rows: a renamed medicine must not inherit old marks.
        val unavailableIds = reservedIds + current.map { it.id }
        val usedIds = HashSet<String>()
        val nicknames = HashSet<String>()
        for (i in 1 until rows.size) {
            val rowNo = i + 1
            val r = rows[i]
            if (r.size != header.size) {
                errors.add("row $rowNo: wrong number of cells")
                continue
            }
            fun cell(name: String) = r.getOrNull(col.getValue(name))?.trim().orEmpty()
            val before = errors.size
            fun bad(reason: String) = errors.add("row $rowNo: $reason")

            val nickEn = cell("nickname_en")
            if (nickEn.isEmpty()) bad("no nickname")
            else if (!nicknames.add(nickEn.lowercase())) bad("the nickname is used twice")
            val type = MedType.entries.firstOrNull { it.name.equals(cell("type"), ignoreCase = true) }
            if (type == null) bad("type must be CAMERA or BUTTON")
            val status = if (cell("status").isEmpty()) MedStatus.ACTIVE
            else MedStatus.entries.firstOrNull { it.name.equals(cell("status"), ignoreCase = true) }
            if (status == null) bad("status must be ACTIVE or STOPPED")

            val brand = cell("brand").uppercase()
            val generics = list(cell("generics")).map { it.uppercase() }
            val strengthCells = list(cell("strengths"))
            val strengths = strengthCells.mapNotNull { strength(it) }
            if (strengths.size != strengthCells.size) bad("a strength cannot be read: " + strengthCells.filter { strength(it) == null }.joinToString(", "))
            if (type == MedType.CAMERA) {
                if (brand.isEmpty()) bad("no brand")
                if (generics.isEmpty()) bad("no generic name")
                if (strengthCells.isEmpty()) bad("no strength")
            }
            val release = cell("release").uppercase().ifEmpty { null }
            if (release != null && release !in RELEASES) bad("release must be empty or one of " + RELEASES.joinToString(" "))
            val timeCells = list(cell("times"))
            val badTimes = timeCells.filter { !TIME.matches(it) || LocalTime.parse(it).hour < 4 }
            if (badTimes.isNotEmpty()) bad("bad time " + badTimes.joinToString(", ") + " (use HH:mm, from 04:00 to 23:59)")
            val stoppedOn = cell("stopped_on").ifEmpty { null }
            if (stoppedOn != null && runCatching { LocalDate.parse(stoppedOn) }.isFailure) bad("stopped_on must be a date like 2026-10-01")

            if (errors.size > before) continue
            val id = current.firstOrNull { it.nickEn == nickEn }?.id?.takeIf { it !in usedIds } ?: unique(idFor(nickEn), unavailableIds + usedIds)
            usedIds += id
            meds += Medicine(
                id = id, nickEn = nickEn, nickHi = cell("nickname_hi"), brand = brand, generics = generics,
                strengths = strengths, release = release, extraWords = list(cell("extra_words")).map { it.uppercase() },
                type = type!!, status = status!!, stoppedOn = stoppedOn,
                times = timeCells.distinct().sorted(), noteEn = cell("note_en"), noteHi = cell("note_hi"),
                colour = cell("colour").ifEmpty { DEFAULT_COLOUR },
            )
        }
        if (meds.isEmpty() && errors.isEmpty()) errors += "the file has no medicines"
        return if (errors.isEmpty()) ReadResult.Ok(meds) else ReadResult.Bad(errors)
    }

    /** "500 mg", "0.5%", "250 IU" in any letter case. A comma is never a decimal point. */
    fun strength(text: String): Strength? {
        val m = STRENGTH.matchEntire(text.trim()) ?: return null
        val unit = when (m.groupValues[2].lowercase()) {
            "mg" -> StrengthUnit.MG
            "mcg" -> StrengthUnit.MCG
            "g" -> StrengthUnit.G
            "iu" -> StrengthUnit.IU
            "ml" -> StrengthUnit.ML
            else -> StrengthUnit.PERCENT
        }
        return Strength(m.groupValues[1], unit)
    }

    /** Lower case, every run of other characters turned into "-". */
    fun idFor(nickname: String): String =
        nickname.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "medicine" }

    private fun unique(base: String, used: Set<String>): String {
        if (base !in used) return base
        var n = 2
        while ("$base-$n" in used) n++
        return "$base-$n"
    }

    private fun list(cell: String): List<String> = cell.split(';').map { it.trim() }.filter { it.isNotEmpty() }

    /** The usual CSV rule: quotes around a cell, "" inside quotes is one quote. */
    fun rows(text: String): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                quoted && c == '"' -> { quoted = false; closed = true }
                quoted -> cell.append(c)
                c == '"' -> {
                    require(cell.isEmpty() && !closed) { "a quote is in the wrong place" }
                    quoted = true
                }
                c == ',' -> { row.add(cell.toString()); cell.setLength(0); closed = false }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(cell.toString()); cell.setLength(0); closed = false
                    out.add(row); row = mutableListOf()
                }
                else -> {
                    require(!closed) { "text follows a closing quote" }
                    cell.append(c)
                }
            }
            i++
        }
        require(!quoted) { "a quoted cell is not closed" }
        if (cell.isNotEmpty() || row.isNotEmpty() || closed) {
            row.add(cell.toString())
            out.add(row)
        }
        return out
    }
}
