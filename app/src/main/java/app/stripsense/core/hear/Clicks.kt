package app.stripsense.core.hear

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * One sharp sound picked out of the microphone's stream.
 * [atMs]: milliseconds since the first sample. [above]: how far it stood over the room, in decibels.
 * [level]: how loud it was by itself. [bright]: its high part against its low part.
 */
data class Click(val atMs: Long, val above: Float, val level: Float, val bright: Float)

/**
 * Picks sharp, bright sounds out of what the microphone hears: the crack of foil, not a voice.
 *
 * Every five milliseconds the loudness between 8 and 16 kHz is measured. A click is a jump of that loudness well
 * above what the room has sounded like for the last second, arriving within a hundredth of a second. A voice
 * rises more slowly and lives lower down; a thump has no high part. Neither is a click.
 *
 * Why so high: torn foil is loud up there, and rooms are not. Measured on the phone in a full hall, the room was
 * about 15 dB quieter between 8 and 16 kHz than between 4 and 12, and a recorded tablet lost about 3.
 *
 * Pure arithmetic on samples, so the same recordings can be put through it on a desk as on the phone.
 */
class ClickFinder(val rate: Int = RATE, private val clickDb: Double = CLICK_DB, private val riseDb: Double = RISE_DB) {
    private val ring = DoubleArray(WIN)
    private var taken = 0L
    private var hops = 0L
    private var lastClick = -100L
    private val hi = DoubleArray(KEEP)
    private val body = DoubleArray(KEEP)
    private var kept = 0
    private val re = DoubleArray(WIN)
    private val im = DoubleArray(WIN)
    private val sorted = DoubleArray(FLOOR_HOPS)
    private var dullFrom = -1L

    /** How loud the room is in the high band, as the middle value of the last second. */
    var floorDb = 0f
        private set

    /** How far the sound right now stands above the room, 0 to 1. For showing that the phone is listening. */
    var loudness = 0f
        private set

    /**
     * Takes the next samples and returns the clicks they completed.
     * [onDull] is told, while it lasts, of a long dark sound: a voice, a cough, a door. A click beside one of
     * those is part of it and is not foil. The two moments are milliseconds since the first sample.
     */
    fun feed(samples: ShortArray, count: Int = samples.size, onDull: ((Long, Long) -> Unit)? = null): List<Click> {
        var found: MutableList<Click>? = null
        for (i in 0 until count) {
            ring[(taken % WIN).toInt()] = samples[i].toDouble()
            taken++
            // The first measurement is the first full window; after that one for every HOP samples.
            if (taken < WIN || (taken - WIN) % HOP != 0L) continue
            hop(onDull)?.let { click -> (found ?: ArrayList<Click>().also { found = it }).add(click) }
        }
        return found ?: emptyList()
    }

    private fun hop(onDull: ((Long, Long) -> Unit)?): Click? {
        val oldest = (taken % WIN).toInt()
        for (i in 0 until WIN) { re[i] = ring[(oldest + i) % WIN] * HANN[i]; im[i] = 0.0 }
        Fft.run(re, im)
        var high = 1.0
        var low = 1.0
        for (bin in BODY_FROM until BODY_TO) low += re[bin] * re[bin] + im[bin] * im[bin]
        for (bin in HI_FROM until HI_TO) high += re[bin] * re[bin] + im[bin] * im[bin]
        if (kept == KEEP) { System.arraycopy(hi, 1, hi, 0, KEEP - 1); System.arraycopy(body, 1, body, 0, KEEP - 1); kept-- }
        hi[kept] = 10 * log10(high)
        body[kept] = 10 * log10(low)
        kept++
        hops++
        // The hop two back is judged, so its loudest moment can be taken from the two hops after it.
        val at = kept - 3
        val index = hops - 3
        if (at < 2 || index < WARM_HOPS) return null
        val from = maxOf(0, at - FLOOR_HOPS)
        val n = at - from
        System.arraycopy(hi, from, sorted, 0, n)
        java.util.Arrays.sort(sorted, 0, n)
        val floor = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        floorDb = floor.toFloat()
        dull(at, from, n, index, onDull)
        loudness = ((hi[kept - 1] - floor) / SHOWN_DB).toFloat().coerceIn(0f, 1f)
        val rise = hi[at] - maxOf(hi[at - 1], hi[at - 2])
        if (hi[at] - floor < clickDb || rise < riseDb || index - lastClick < REFRACTORY_HOPS) return null
        var peak = at
        for (k in at + 1..at + 2) if (hi[k] > hi[peak]) peak = k
        val bright = hi[peak] - body[peak]
        if (bright < BRIGHT_DB) return null
        lastClick = index
        return Click(index * HOP * 1000L / rate, (hi[peak] - floor).toFloat(), hi[peak].toFloat(), bright.toFloat())
    }

    /** A sound with a loud low body that lasts: told from its first 60 ms on, with a fifth of a second either side. */
    private fun dull(at: Int, from: Int, n: Int, index: Long, onDull: ((Long, Long) -> Unit)?) {
        System.arraycopy(body, from, sorted, 0, n)
        java.util.Arrays.sort(sorted, 0, n)
        val usual = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        if (body[at] - usual >= DULL_OVER_DB && body[at] - hi[at] >= DULL_DARK_DB) {
            if (dullFrom < 0) dullFrom = index
            if (index - dullFrom + 1 >= DULL_HOPS) onDull?.invoke((dullFrom - DULL_NEAR_HOPS) * HOP * 1000L / rate, (index + DULL_NEAR_HOPS) * HOP * 1000L / rate)
        } else dullFrom = -1
    }

    companion object {
        const val RATE = 48000

        /** One measurement every 5 ms, each over the last 512 samples. */
        const val HOP = 240
        const val WIN = 512
        private const val FLOOR_HOPS = 200
        private const val KEEP = FLOOR_HOPS + 3
        private const val WARM_HOPS = 40
        private const val REFRACTORY_HOPS = 6

        // At 48 kHz one bin is 93.75 Hz: the body of a sound is 190 Hz to 4 kHz, its high part 8 to 16 kHz.
        private const val BODY_FROM = 2
        private const val BODY_TO = 43
        private const val HI_FROM = 85
        private const val HI_TO = 171

        /** A click stands this far over the room, and got there within two measurements. */
        const val CLICK_DB = 14.0
        const val RISE_DB = 8.0

        /** Its high part is at least this strong against its body. A bump or a cough is far darker. */
        const val BRIGHT_DB = -18.0
        private const val SHOWN_DB = 30.0

        // A long dark sound: its body well over the room's and over its own high part, for 60 ms or more.
        private const val DULL_OVER_DB = 15.0
        private const val DULL_DARK_DB = 8.0
        private const val DULL_HOPS = 12
        private const val DULL_NEAR_HOPS = 40
        private val HANN = DoubleArray(WIN) { 0.5 - 0.5 * cos(2 * PI * it / (WIN - 1)) }
    }
}

/** A plain radix-2 transform for the 512 samples of one measurement. */
private object Fft {
    private const val N = ClickFinder.WIN
    private val COS = DoubleArray(N / 2) { cos(2 * PI * it / N) }
    private val SIN = DoubleArray(N / 2) { sin(2 * PI * it / N) }
    private val REVERSED = IntArray(N) { Integer.reverse(it) ushr (32 - Integer.numberOfTrailingZeros(N)) }

    fun run(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until N) {
            val j = REVERSED[i]
            if (j > i) { val r = re[i]; re[i] = re[j]; re[j] = r; val m = im[i]; im[i] = im[j]; im[j] = m }
        }
        var size = 2
        while (size <= N) {
            val half = size / 2
            val step = N / size
            var start = 0
            while (start < N) {
                for (k in 0 until half) {
                    val c = COS[k * step]
                    val s = -SIN[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = re[b] * c - im[b] * s
                    val ti = re[b] * s + im[b] * c
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                }
                start += size
            }
            size *= 2
        }
    }
}
