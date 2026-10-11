package app.stripsense.core.guide

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Another saved medicine with the same ingredient as this one. [markedAt]: when that other medicine's dose
 * was marked today, if it was.
 */
data class SharedIngredient(val other: Medicine, val ingredient: String, val markedAt: LocalTime?)

/**
 * Two strips with different names and the same ingredient. An older person sees "Crocin" and "P-500" as two
 * medicines; both are paracetamol. The phone knows, because the family saved the ingredient of each.
 * It says the fact and sends the person to the family. It gives no advice about doses.
 */
object SameIngredient {
    /**
     * The saved, active medicine that shares an ingredient with [med]. One whose dose was marked today comes
     * first, the latest mark before the others: that is the moment the fact matters.
     */
    fun of(med: Medicine, medicines: List<Medicine>, events: List<DoseEvent>, now: Long, zone: ZoneId = ZoneId.systemDefault()): SharedIngredient? {
        val own = ingredients(med)
        if (own.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val undone = events.filter { it.kind == EventKind.UNDONE }.mapNotNullTo(HashSet()) { it.undoes }
        fun markedToday(other: Medicine): Long? = events.filter {
            it.medId == other.id && it.kind == EventKind.TAKEN && it.id !in undone && Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate() == today
        }.maxOfOrNull { it.atMillis }
        return medicines.filter { it.id != med.id && it.status == MedStatus.ACTIVE }.mapNotNull { other ->
            val same = ingredients(other).firstOrNull { it in own } ?: return@mapNotNull null
            Triple(other, same, markedToday(other))
        }.maxByOrNull { it.third ?: -1L }?.let { (other, same, at) ->
            SharedIngredient(other, same.lowercase(), at?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() })
        }
    }

    /** Every pair of active medicines that share an ingredient, for the family's list. Each pair once. */
    fun pairs(medicines: List<Medicine>): List<Triple<Medicine, Medicine, String>> {
        val active = medicines.filter { it.status == MedStatus.ACTIVE }
        return active.flatMapIndexed { i, a ->
            active.drop(i + 1).mapNotNull { b -> ingredients(b).firstOrNull { it in ingredients(a) }?.let { Triple(a, b, it.lowercase()) } }
        }
    }

    private fun ingredients(med: Medicine): Set<String> = med.generics.mapTo(HashSet()) { TextNorm.normalise(it) }.filterTo(HashSet()) { it.length >= 4 }
}
