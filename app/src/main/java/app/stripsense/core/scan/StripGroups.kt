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
/**
 * Groups one frame's OCR lines into per-strip clusters by spatial proximity.
 *
 * Why this exists (Kiro, 2026-10-10): when the person lays 2-4 medicine strips
 * on a table, ML Kit returns all their text as one flat list. Without grouping,
 * StripParser sees all lines at once and the Decider fires OneAtATime. Spatial
 * grouping separates the lines so each strip can be identified independently,
 * enabling the colored-box multi-strip view.
 *
 * Algorithm: union-find over lines. Two lines merge when their bounding boxes,
 * each grown by one line-height on every side, touch or overlap. This is robust
 * to varying inter-line spacing within a strip while keeping distinct strips apart.
 */
object StripGroups {

    /** One identified strip: its OCR lines and their tight union bounding box. */
    data class StripGroup(val lines: List<OcrLine>, val box: CropBox)

    /**
     * Groups [lines] into strips. Returns groups sorted left-to-right by box left edge.
     * Lines with heightPx == 0 fall back to (bottom - top) for the growth margin.
     */
    fun group(lines: List<OcrLine>, medicines: List<Medicine> = emptyList(), tuning: Tuning = Tuning()): List<StripGroup> {
        if (lines.isEmpty()) return emptyList()

        // Union-find with path compression
        val parent = IntArray(lines.size) { it }

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            // path compression
            var i = x
            while (parent[i] != i) {
                val next = parent[i]
                parent[i] = r
                i = next
            }
            return r
        }

        fun union(a: Int, b: Int) {
            parent[find(a)] = find(b)
        }

        fun lineHeight(l: OcrLine) = if (l.heightPx > 0) l.heightPx else (l.bottom - l.top).coerceAtLeast(1)

        // Grow a line's box by one line-height on every side for overlap testing
        fun grown(l: OcrLine): CropBox {
            val h = lineHeight(l)
            return CropBox(l.left - h, l.top - h, l.right + h, l.bottom + h)
        }

        fun overlaps(a: CropBox, b: CropBox): Boolean =
            a.left <= b.right && b.left <= a.right && a.top <= b.bottom && b.top <= a.bottom

        // Merge any pair of lines whose grown boxes overlap
        for (i in lines.indices) {
            val gi = grown(lines[i])
            for (j in i + 1 until lines.size) {
                if (find(i) != find(j) && overlaps(gi, grown(lines[j]))) {
                    union(i, j)
                }
            }
        }

        // Collect lines into groups keyed by their root
        val map = mutableMapOf<Int, MutableList<Int>>()
        for (i in lines.indices) map.getOrPut(find(i)) { mutableListOf() }.add(i)

        // Build StripGroup for each cluster; sort left-to-right for stable output
        return map.values.map { indices ->
            val groupLines = indices.map { lines[it] }
            val box = CropBox(
                left   = groupLines.minOf { it.left },
                top    = groupLines.minOf { it.top },
                right  = groupLines.maxOf { it.right },
                bottom = groupLines.maxOf { it.bottom },
            )
            StripGroup(groupLines, box)
        }.flatMap { splitKnownBrands(it, medicines, tuning) }.sortedBy { it.box.left }
    }

    private fun splitKnownBrands(group: StripGroup, meds: List<Medicine>, tuning: Tuning): List<StripGroup> {
        if (meds.isEmpty()) return listOf(group)
        val anchors = group.lines.mapNotNull { line ->
            val brands = StripParser.parse(listOf(line), meds, tuning).brands
            if (brands.size > 1) return listOf(group)
            brands.singleOrNull()?.let { it to line }
        }.groupBy({ it.first }, { it.second })
        if (anchors.size < 2) return listOf(group)
        val centers = anchors.mapValues { (_, lines) ->
            lines.map { (it.left + it.right) / 2.0 }.average() to lines.map { (it.top + it.bottom) / 2.0 }.average()
        }
        val separated = group.lines.groupBy { line ->
            val own = StripParser.parse(listOf(line), meds, tuning).brands.singleOrNull()
            own ?: centers.minBy { (_, center) ->
                val dx = (line.left + line.right) / 2.0 - center.first
                val dy = (line.top + line.bottom) / 2.0 - center.second
                dx * dx + dy * dy
            }.key
        }
        return separated.values.map { lines -> StripGroup(lines,
            CropBox(lines.minOf { it.left }, lines.minOf { it.top }, lines.maxOf { it.right }, lines.maxOf { it.bottom })) }
    }

}
