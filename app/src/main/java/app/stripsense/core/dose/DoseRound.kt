package app.stripsense.core.dose

import app.stripsense.core.domain.*

/**
 * The medicines of one dose time, gone through one after another.
 *
 * A reminder names a time, and a time often means three or four strips. Until now the phone found one strip and
 * stopped; the person had to start again for each of the others, and a person who cannot read the screen did
 * not know how many were left. In a round the phone says how many there are, leads to the first, and when that
 * one is answered goes on to the next by itself, until it can say that all of them are marked.
 *
 * [items]: each medicine with the key of its dose. [at]: the one being looked for now.
 */
data class DoseRound(val items: List<Pair<String, String>>, val at: Int = 0) {
    val current: Pair<String, String> get() = items[at]

    /** True once the dose being looked for has an answer: marked, or "not sure". */
    fun answered(events: List<DoseEvent>, engine: DoseEngine): Boolean =
        engine.slotState(current.first, current.second, events) != SlotState.UNMARKED

    /** The round standing at the next medicine that still has no answer; null when none is left. */
    fun next(events: List<DoseEvent>, engine: DoseEngine): DoseRound? =
        (at + 1 until items.size).firstOrNull { engine.slotState(items[it].first, items[it].second, events) == SlotState.UNMARKED }?.let { copy(at = it) }

    /** How many of the round's doses are marked as taken now. Skipped and "not sure" ones are not counted. */
    fun marked(events: List<DoseEvent>, engine: DoseEngine): Int =
        items.count { (med, key) -> engine.slotState(med, key, events) == SlotState.MARKED }

    companion object {
        /** A round over these doses, leaving out any that already have an answer. Null when nothing is left to do. */
        fun of(items: List<Pair<String, String>>, events: List<DoseEvent>, engine: DoseEngine): DoseRound? =
            items.filter { (med, key) -> engine.slotState(med, key, events) == SlotState.UNMARKED }.takeIf { it.isNotEmpty() }?.let { DoseRound(it) }
    }
}
