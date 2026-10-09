package app.stripsense.core

import kotlin.math.ceil

/** Median and 90th percentile for the Numbers screen. */
object Stats {
    /** For an even count, the mean of the two middle values. Null for no values. */
    fun median(values: List<Long>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2].toDouble() else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /** Nearest rank: the value at rank ceil(0.9 * n). */
    fun p90(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val rank = ceil(0.9 * s.size).toInt().coerceIn(1, s.size)
        return s[rank - 1]
    }
}
