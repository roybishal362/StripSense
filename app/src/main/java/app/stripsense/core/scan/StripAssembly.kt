package app.stripsense.core.scan

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
import kotlin.math.abs
import kotlin.math.hypot

/** One saved name found in a frame, with the print that belongs to its strip. */
data class AssembledStrip(
    val name: String,
    /** Lines that carry the name itself. Only these may show that the strip differs from the saved one. */
    val anchors: List<OcrLine>,
    /** Lines beside them on the same strip: the strength, the ingredients. They may support, never accuse. */
    val support: List<OcrLine>,
) {
    /** Where the name is printed. */
    val nameBox: CropBox get() = StripAssembly.around(anchors)

    /** The strip, as far as its print shows it. */
    val box: CropBox get() = StripAssembly.around(anchors + support)
}

/**
 * Which print belongs to which strip, worked out line by line from the name outward.
 *
 * Print used to be gathered into patches by nearness alone, and each patch was given to the name found in it.
 * Two strips lying side by side became one patch: on a real screen the box around a Crocin strip took in the
 * corner of the P-500 strip beside it, and one strip's print was counted for the other. Now a strip starts at
 * the lines that carry its name and grows only to lines that lie right against a line it already has, by the
 * measure of the print itself (small print reaches a small way) and running in the same direction. Print that
 * two names could both claim is given to neither.
 */
object StripAssembly {
    /** A line joins a strip when the gap to one of its lines is at most this many times the smaller print height. */
    const val REACH = 2.5

    /** Lines printed more than this many degrees apart are not the same block of print. */
    const val SAME_DIRECTION = 30f

    class Result(val strips: List<AssembledStrip>, val loose: List<OcrLine>)

    fun assemble(lines: List<OcrLine>, index: PrintIndex, tuning: Tuning): Result {
        val owner = HashMap<Int, String>()
        val contested = HashSet<Int>()
        for ((i, line) in lines.withIndex()) {
            val names = index.namesIn(listOf(line), tuning)
            // A line with two saved names on it says nothing safe about either.
            if (names.size == 1) owner[i] = names.single() else if (names.size > 1) contested += i
        }
        val anchors = owner.keys.toSet()
        // Grow each strip outward one line at a time, all strips together, so none gets ahead and takes a line
        // that lies just as close to another.
        var grew = true
        while (grew) {
            grew = false
            val claims = HashMap<Int, MutableSet<String>>()
            for (i in lines.indices) {
                if (i in owner || i in contested) continue
                for ((j, name) in owner) if (beside(lines[i], lines[j], fromName = j in anchors)) claims.getOrPut(i) { HashSet() }.add(name)
            }
            for ((i, names) in claims) {
                if (names.size == 1) owner[i] = names.single() else contested += i
                grew = true
            }
        }
        val strips = owner.entries.groupBy({ it.value }, { it.key }).map { (name, members) ->
            AssembledStrip(name, members.filter { it in anchors }.sorted().map { lines[it] }, members.filter { it !in anchors }.sorted().map { lines[it] })
        }
        return Result(strips, lines.indices.filter { it !in owner }.map { lines[it] })
    }

    fun around(lines: List<OcrLine>): CropBox =
        if (lines.isEmpty()) CropBox(0, 0, 0, 0) else CropBox(lines.minOf { it.left }, lines.minOf { it.top }, lines.maxOf { it.right }, lines.maxOf { it.bottom })

    /** The height of the letters. A line printed sideways has a tall box, and its letters are as high as the box is wide. */
    fun letterHeight(line: OcrLine): Int = minOf(line.bottom - line.top, line.right - line.left).coerceAtLeast(1)

    /**
     * [fromName]: [b] carries the name. A name is printed large and stands a little apart from the small print
     * under it, so from the name the reach is measured by the larger letters. Small print reaches on only by
     * its own small measure, which is what keeps a block of text from running on across the table.
     */
    private fun beside(a: OcrLine, b: OcrLine, fromName: Boolean): Boolean {
        if (!sameDirection(a.angle, b.angle)) return false
        val dx = maxOf(0, a.left - b.right, b.left - a.right)
        val dy = maxOf(0, a.top - b.bottom, b.top - a.bottom)
        val letters = if (fromName) maxOf(letterHeight(a), letterHeight(b)) else minOf(letterHeight(a), letterHeight(b))
        return hypot(dx.toDouble(), dy.toDouble()) <= REACH * letters
    }

    private fun sameDirection(a: Float, b: Float): Boolean {
        val turn = abs(a - b) % 180f
        return minOf(turn, 180f - turn) <= SAME_DIRECTION
    }
}
