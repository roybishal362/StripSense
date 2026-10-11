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
import java.math.BigDecimal

/** Comparing and writing strengths. Spec 01 sections 1 and 3. */
object Strengths {

    /** True when both sets are equal after 0.5 g = 500 mg style normalising. */
    fun sameStrengths(a: Collection<Strength>, b: Collection<Strength>): Boolean =
        normSet(a) == normSet(b)

    /** A strength the medicine does not have, or a brand number that is none of its strengths. */
    fun conflict(key: ReadKey, med: Medicine): Boolean {
        val medNorms = normSet(med.strengths)
        if (key.strengths.any { norm(it) !in medNorms }) return true
        val medNumbers = numbersOf(med)
        return key.brandNumbers.any { number(it) !in medNumbers }
    }

    /** "500 mg + 250 IU". */
    fun display(strengths: Collection<Strength>): String = strengths.joinToString(" + ") { display(it) }

    fun display(s: Strength): String = "${s.value} ${unitLabel(s.unit)}"

    fun unitLabel(unit: StrengthUnit): String = when (unit) {
        StrengthUnit.MG -> "mg"
        StrengthUnit.MCG -> "mcg"
        StrengthUnit.G -> "g"
        StrengthUnit.IU -> "IU"
        StrengthUnit.ML -> "ml"
        StrengthUnit.PERCENT -> "%"
    }

    /** The bare numbers of a medicine's strengths, as written on the list ("500" for 500 mg). */
    internal fun numbersOf(med: Medicine): Set<String> = med.strengths.mapTo(HashSet()) { number(it.value) }

    internal fun normSet(strengths: Collection<Strength>): Set<Pair<String, StrengthUnit>> =
        strengths.mapTo(HashSet()) { norm(it) }

    /** A printed number in one canonical form, so "500" and "500.0" compare equal. Never corrects digits. */
    internal fun number(value: String): String {
        val n = value.trim().toBigDecimalOrNull() ?: return value.trim()
        return canonical(n)
    }

    private fun norm(s: Strength): Pair<String, StrengthUnit> {
        val n = s.value.trim().toBigDecimalOrNull() ?: return s.value.trim() to s.unit
        return if (s.unit == StrengthUnit.G) {
            canonical(n.multiply(THOUSAND)) to StrengthUnit.MG
        } else {
            canonical(n) to s.unit
        }
    }

    private fun canonical(n: BigDecimal): String =
        if (n.signum() == 0) "0" else n.stripTrailingZeros().toPlainString()

    private val THOUSAND = BigDecimal(1000)
}
