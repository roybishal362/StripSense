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
/** One strip, or one loose patch of print, as the scene holds it after this frame. */
data class SeenStrip(val id: Long, val box: CropBox, val lines: List<OcrLine>, val result: PrintResult,
    /** Read in this very frame. False while a strip is held through a missed frame or behind a hand. */
    val live: Boolean, val promising: Boolean = false,
    /** The expiry date printed on this strip, once it has been read the same way in several frames. */
    val expiry: ExpiryDate? = null)

/**
 * The table as the phone understands it, built up over frames and steady under a moving hand.
 *
 * A strip is followed by its printed name, not by where it is in the picture. A phone held in the hand
 * shakes, and anything followed by position is lost and started again with every shake, so its print never
 * adds up. A saved name read anywhere in the picture always feeds the same memory, wherever the strip has
 * moved to. Print with no saved name on it is still followed by position, for torn pieces and unknown strips.
 *
 * The camera's own movement is measured from the print itself: the same words seen in two frames have moved
 * by how much the phone moved. Remembered places are shifted by that, so they stay on their strips.
 */
class Scene {
    private class Slot(val id: Long) {
        val evidence = PrintEvidence()
        val expiry = ExpiryVotes()

        /** Where the name is printed. Decides which loose print belongs to this strip. */
        var box = CropBox(0, 0, 0, 0)

        /** The name together with all the print taken as this strip's in this frame: the strip, near enough. */
        var extent = CropBox(0, 0, 0, 0)

        /** [extent] steadied over frames. This is the one box drawn for the strip. */
        var shown: CropBox? = null

        /** The last few frames' extents with their times, already moved with the camera. [shown] is their middle. */
        val recent = ArrayDeque<Pair<Long, CropBox>>()
        var lines: List<OcrLine> = emptyList()
        var seenAt = 0L
    }

    private val slots = LinkedHashMap<String, Slot>()
    private val tracker = StripTracker()
    private val loose = HashMap<Long, PrintEvidence>()
    private var anchors: Map<String, Pair<Int, Int>> = emptyMap()

    private var lastAssembly: StripAssembly.Result? = null

    /**
     * Where each strip's lines lie in the last frame, for finding out why a box is the wrong size:
     * each line's box and its direction. Never the text that was read.
     */
    fun layout(): String = lastAssembly?.strips?.joinToString("\n") { strip ->
        fun show(line: OcrLine) = "[${line.left},${line.top},${line.right},${line.bottom} a=${line.angle.toInt()}]"
        "${strip.name}: box=${strip.box} name=" + strip.anchors.joinToString(" ", transform = ::show) + " near=" + strip.support.joinToString(" ", transform = ::show)
    }.orEmpty()

    /** Each named strip's votes, for a device check: names, numbers and label words, never the full text. */
    fun traces(now: Long): String = slots.entries.joinToString("\n") { (name, slot) -> "$name: " + slot.evidence.trace(now) }

    /** How far the picture moved since the frame before, in pixels. Zero when it could not be measured. */
    var shift: Pair<Int, Int> = 0 to 0
        private set

    fun reset() { slots.clear(); loose.clear(); tracker.reset(); anchors = emptyMap(); shift = 0 to 0 }

    /** The same, for print already gathered into patches. The patches are not used: strips are assembled line by line. */
    fun frame(groups: List<StripGroups.StripGroup>, index: PrintIndex, tuning: Tuning, now: Long): List<SeenStrip> =
        read(groups.flatMap { it.lines }, index, tuning, now)

    /**
     * One camera frame's lines of print in, the table as it now stands out.
     * [outline]: given where each strip's name was read, the outline of the thing each name is printed on, when the
     * picture shows one (see TableLook). Without it a strip is as large as the print gathered around its name.
     */
    fun read(lines: List<OcrLine>, index: PrintIndex, tuning: Tuning, now: Long,
        outline: ((Map<String, CropBox>) -> Map<String, CropBox>)? = null): List<SeenStrip> {
        val current = anchorsOf(lines)
        shift = SceneMotion.estimate(anchors, current) ?: (0 to 0)
        anchors = current
        for (slot in slots.values) {
            slot.box = slot.box.moved(shift.first, shift.second)
            slot.extent = slot.extent.moved(shift.first, shift.second)
            slot.shown = slot.shown?.moved(shift.first, shift.second)
            for (i in slot.recent.indices) slot.recent[i] = slot.recent[i].first to slot.recent[i].second.moved(shift.first, shift.second)
        }

        // Each strip is built from its name outward (see StripAssembly). The lines that carry the name are full
        // evidence: they can check the strip and they can show it differs. Lines beside them can supply the
        // strength; they can never accuse the strip, because nearness is not proof of belonging.
        val assembled = StripAssembly.assemble(lines, index, tuning)
        lastAssembly = assembled
        // A strip whose name was missed in this frame is still lying where it was a moment ago. Its print must not be
        // taken by the strip beside it: that drew one strip's box over two strips for as long as one name was unread.
        // The strip itself, seen as a thing on the table. Only an outline clearly larger than the name is believed:
        // on a strip held so close that it fills the picture, the "thing" found is only the ink of the name.
        val shapes = outline?.invoke(assembled.strips.associate { it.name to it.nameBox }).orEmpty().filter { (name, shape) ->
            val nameBox = assembled.strips.first { it.name == name }.nameBox
            shape.width.toLong() * shape.height >= nameBox.width.toLong() * nameBox.height * OUTLINE_GAIN
        }
        val nameLines = assembled.strips.flatMapTo(HashSet()) { it.anchors }
        val claimed = HashSet<OcrLine>()
        val namedNow = assembled.strips.mapTo(HashSet()) { it.name }
        val waiting = slots.filter { (name, slot) -> name !in namedNow && now - slot.seenAt <= HOLD_MS }
        for (assembledStrip in assembled.strips) {
            val own = slots[assembledStrip.name]?.extent
            val kept = if (waiting.isEmpty()) assembledStrip else assembledStrip.copy(support = assembledStrip.support.filter { line ->
                waiting.values.none { within(line, it.extent) } || (own != null && within(line, own))
            })
            // Every line of print lying on this strip's own outline is its print, whichever way it runs: a strip
            // that prints its name sideways never had its strength counted. Print on another strip's outline is not.
            val shape = shapes[kept.name]
            val onIt = if (shape == null) emptyList() else lines.filter { line ->
                line !in nameLines && within(line, shape) && shapes.none { (other, box) -> other != kept.name && within(line, box) }
            }
            val strip = kept.copy(support = (kept.support.filter { line -> shapes.none { (other, box) -> other != kept.name && within(line, box) } } + onIt).distinct())
            claimed += strip.support
            val slot = slots.getOrPut(strip.name) { Slot(slotId(strip.name)) }
            slot.evidence.add(strip.anchors, index, tuning, now)
            if (strip.support.isNotEmpty()) slot.evidence.add(strip.support, index, tuning, now, support = true)
            slot.expiry.add(strip.anchors + strip.support, now)
            slot.box = strip.nameBox
            slot.extent = shape?.union(strip.nameBox) ?: strip.box
            slot.lines = strip.anchors + strip.support
            slot.seenAt = now
        }
        slots.values.removeIf { now - it.seenAt > FORGET_MS }
        for (slot in slots.values) if (slot.seenAt == now) slot.shown = steady(slot, now)

        // What no name claimed: torn pieces and strips that are not saved. These are followed by where they are.
        val free = StripGroups.group(assembled.loose.filter { it !in claimed }, index.medicines, tuning)
        val tracks = tracker.update(free.map { StripObservation(it.box, it.lines, ReadKey()) }.take(MAX_LOOSE), index.medicines, now)
        loose.keys.retainAll(tracks.mapTo(HashSet()) { it.id })
        val pieces = tracks.map { track ->
            val evidence = loose.getOrPut(track.id) { PrintEvidence() }
            evidence.add(track.observation.lines, index, tuning, now)
            SeenStrip(LOOSE_ID + track.id, track.observation.box, track.observation.lines, evidence.decide(index, now), true, evidence.promising(index, now))
        }
        val named = slots.values.filter { now - it.seenAt <= HOLD_MS }.map { slot ->
            SeenStrip(slot.id, slot.shown ?: slot.extent, slot.lines, slot.evidence.decide(index, now), slot.seenAt == now, true, slot.expiry.decided())
        }
        return named + pieces
    }

    /** A reading by the language model for the strip with this id, if the scene still holds it. */
    fun addReading(id: Long, lines: List<String>, index: PrintIndex, tuning: Tuning, now: Long) {
        (slots.values.firstOrNull { it.id == id }?.evidence ?: loose[id - LOOSE_ID])?.addReading(lines, index, tuning, now)
    }

    private fun anchorsOf(lines: List<OcrLine>): Map<String, Pair<Int, Int>> {
        val byText = lines.filter { it.confidence >= 0.4f }.groupBy { TextNorm.normalise(it.text) }
        // Only print that appears once can say where the picture went.
        return byText.filter { it.key.length >= ANCHOR_LETTERS && it.value.size == 1 }
            .mapValues { (_, line) -> (line.single().left + line.single().right) / 2 to (line.single().top + line.single().bottom) / 2 }
    }

    /**
     * The drawn box follows the strip without trembling and without falling behind. Each frame reads slightly
     * different print, so the raw box changes size all the time; and once in a while one frame reads far too
     * much or too little. Each edge is the middle value of the last three frames: one odd frame changes nothing,
     * and the box is never more than a frame behind. The earlier rule moved each edge half way per frame, which
     * at three frames a second left the box trailing a moving strip by a second or more.
     */
    private fun steady(slot: Slot, now: Long): CropBox {
        val read = slot.extent
        val before = slot.shown
        val jumped = before != null && (kotlin.math.abs((before.left + before.right) - (read.left + read.right)) / 2 > maxOf(before.width, read.width) / 2 ||
            kotlin.math.abs((before.top + before.bottom) - (read.top + read.bottom)) / 2 > maxOf(before.height, read.height) / 2)
        if (jumped) slot.recent.clear()
        slot.recent.addLast(now to read)
        while (slot.recent.size > STEADY_FRAMES || now - slot.recent.first().first > STEADY_MS) slot.recent.removeFirst()
        fun middle(edge: (CropBox) -> Int): Int = slot.recent.map { edge(it.second) }.sorted().let { values ->
            if (values.size % 2 == 1) values[values.size / 2] else (values[values.size / 2 - 1] + values[values.size / 2]) / 2
        }
        return CropBox(middle { it.left }, middle { it.top }, middle { it.right }, middle { it.bottom })
    }

    private fun within(line: OcrLine, box: CropBox): Boolean {
        val x = (line.left + line.right) / 2
        val y = (line.top + line.bottom) / 2
        return box.width > 0 && x >= box.left && x <= box.right && y >= box.top && y <= box.bottom
    }

    private fun slotId(brand: String): Long = brand.hashCode().toLong() and 0x7fffffffL

    companion object {
        /**
         * What is worth a box on the screen: one per medicine. Print still being read, print from no saved
         * medicine and print the phone cannot place get no box; a screen full of boxes around every line of
         * text told the person nothing. When a medicine shows as several patches, the best one stands for it
         * and its box covers them all. A refused strip keeps a box of its own: the refusal is about that strip.
         */
        fun oneBoxEach(seen: List<SeenStrip>): List<SeenStrip> {
            fun rank(result: PrintResult) = when (result) {
                is PrintResult.Differs -> 0; is PrintResult.Checked -> 1; is PrintResult.NameOnly -> 2; is PrintResult.Piece -> 3; else -> 9
            }
            fun medOf(result: PrintResult) = when (result) {
                is PrintResult.Checked -> result.med; is PrintResult.NameOnly -> result.med; is PrintResult.Piece -> result.med
                is PrintResult.Differs -> result.med; else -> null
            }
            return seen.filter { rank(it.result) < 9 }
                .groupBy { if (it.result is PrintResult.Differs) "refused-${it.id}" else medOf(it.result)!!.id }.values.map { same ->
                    val best = same.minBy { rank(it.result) }
                    best.copy(box = same.map { it.box }.reduce { a, b -> a.union(b) })
                }
        }

        /** A named strip is held this long through frames in which its name was not read. */
        const val HOLD_MS = 1200L
        /** An outline is used only when it is this many times the size of the name printed on it. */
        const val OUTLINE_GAIN = 1.2
        const val STEADY_FRAMES = 3
        const val STEADY_MS = 700L
        const val FORGET_MS = 3000L
        const val TOUCH_GAP = 0.08
        const val ANCHOR_LETTERS = 5
        const val MAX_LOOSE = 6
        const val LOOSE_ID = 1L shl 40
        const val TOUCHING_ID = 1L shl 41
    }
}

fun CropBox.moved(dx: Int, dy: Int) = CropBox(left + dx, top + dy, right + dx, bottom + dy)
fun CropBox.union(other: CropBox) = CropBox(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))

/** How far the picture moved between two frames, from words that appear in both. */
object SceneMotion {
    const val MIN_ANCHORS = 2

    /** The middle movement of the shared words, so one word on a strip that was itself moved does not decide. */
    fun estimate(before: Map<String, Pair<Int, Int>>, after: Map<String, Pair<Int, Int>>): Pair<Int, Int>? {
        val moves = after.mapNotNull { (text, now) -> before[text]?.let { now.first - it.first to now.second - it.second } }
        if (moves.size < MIN_ANCHORS) return null
        return moves.map { it.first }.sorted()[moves.size / 2] to moves.map { it.second }.sorted()[moves.size / 2]
    }
}
