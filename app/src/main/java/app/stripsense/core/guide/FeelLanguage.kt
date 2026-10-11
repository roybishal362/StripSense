package app.stripsense.core.guide

import app.stripsense.core.teach.PurposeKind

/** What one motor does: stretches of time in milliseconds, each with a strength from 0 (still) to 255. */
data class Buzz(val times: List<Long>, val strengths: List<Int>) {
    val lasts: Long get() = times.sum()

    companion object {
        /** Buzzes of [strength] for each time in [on], with [gap] milliseconds of stillness between them. */
        fun beats(strength: Int, gap: Long, vararg on: Long): Buzz {
            val times = ArrayList<Long>()
            val strengths = ArrayList<Int>()
            on.forEachIndexed { i, ms ->
                if (i > 0) { times += gap; strengths += 0 }
                times += ms; strengths += strength
            }
            return Buzz(times, strengths)
        }

        fun then(vararg parts: Buzz): Buzz = Buzz(parts.flatMap { it.times }, parts.flatMap { it.strengths })
        fun still(ms: Long) = Buzz(listOf(ms), listOf(0))
    }
}

/** A message felt in the hand. The phone has a motor at each end; null means that end stays still. */
data class Felt(val top: Buzz?, val bottom: Buzz?) {
    companion object { fun both(buzz: Buzz) = Felt(buzz, buzz) }
}

/**
 * What the phone says through the hand, for someone who cannot see the screen and where nothing can be heard.
 *
 * It is a small language, learned in a minute:
 * - a tick at one end of the phone: move the phone toward that end. The ticks come faster as the medicine comes nearer;
 * - one tick at both ends: it is to the left. Two ticks at both ends: it is to the right;
 * - one long, swelling buzz at both ends: yes, this is the one;
 * - two sharp knocks: no, not this one;
 * - a short tap and then a rising hum: the phone is asking "are you taking it now?" (answered by two knocks on the phone);
 * - three taps, each firmer than the last: done, it is marked;
 * - a rhythm: which medicine this is. A heartbeat is the BP or heart medicine, three quick taps the sugar medicine,
 *   a slow wave the fever or pain medicine, two long breaths the breathing or allergy medicine.
 *
 * Pure: these are only the patterns. The motors are driven in ui/shell/Feel.kt.
 */
object FeelLanguage {
    private const val FULL = 255

    /** Yes, this is the one: one buzz that swells to full strength. */
    fun yes(): Felt = Felt.both(Buzz(listOf(70, 70, 70, 70, 320), listOf(110, 150, 190, 225, FULL)))

    /** No, not this one: two sharp knocks. */
    fun no(): Felt = Felt.both(Buzz.beats(FULL, 110, 45, 45))

    /**
     * Which way to move the phone to bring the medicine to the middle. The tick is always at full strength: with the
     * camera open this phone plays vibrations at two thirds of their strength, and a soft tick was not felt at
     * all. How near the medicine is, is told by how fast the ticks come (Homing.intervalMs), not by how hard.
     */
    fun pull(toward: Toward, @Suppress("UNUSED_PARAMETER") closeness: Float): Felt {
        val strength = FULL
        // At one end the tick is longer: a click is felt everywhere, a short buzz is felt where it is.
        val end = Buzz.beats(strength, 0, 70)
        val tick = Buzz.beats(strength, 0, 35)
        return when (toward) {
            Toward.AWAY -> Felt(end, null)
            Toward.CLOSER -> Felt(null, end)
            Toward.LEFT -> Felt.both(tick)
            Toward.RIGHT -> Felt.both(Buzz.beats(strength, 80, 35, 35))
        }
    }

    /** A hand is moving toward the medicine: a plain tick at both ends. The ticks come faster as the finger comes nearer. */
    fun nearer(@Suppress("UNUSED_PARAMETER") closeness: Float): Felt = Felt.both(Buzz.beats(FULL, 0, 40))

    /**
     * The strip under the finger, as on a map made for the hand: first its own rhythm, so it is known which medicine
     * this is, then the answer when a dose is due. [due] true: yes, this is the one. False: no. Null: nothing is
     * due, the rhythm is all there is to say. [known] false: print the phone could not name, which is only ever a no.
     */
    fun touched(kind: PurposeKind?, due: Boolean?, known: Boolean = true): Felt {
        if (!known) return no()
        val answer = when (due) { true -> yes().top!!; false -> no().top!!; null -> return signature(kind) }
        return Felt.both(Buzz.then(rhythm(kind), Buzz.still(180), answer))
    }

    /** The phone is asking: a short tap, a breath, and a hum that rises like a question. */
    fun question(): Felt = Felt.both(Buzz(listOf(60, 110, 90, 90, 110), listOf(200, 0, 140, 195, FULL)))

    /** Done, it is marked: three taps, each firmer than the last. */
    fun done(): Felt = Felt.both(Buzz(listOf(70, 80, 70, 80, 220), listOf(150, 0, 200, 0, FULL)))

    /** Which medicine this is, by what it is for. A medicine with no purpose confirmed has the plain rhythm. */
    fun signature(kind: PurposeKind?): Felt = Felt.both(rhythm(kind))

    /** The alarm: one long buzz that cannot be missed, then the rhythm of the medicine it is for. */
    fun alarm(kind: PurposeKind?): Felt = Felt.both(Buzz.then(Buzz.beats(FULL, 0, 650), Buzz.still(350), rhythm(kind)))

    private fun rhythm(kind: PurposeKind?): Buzz = when (kind) {
        // A heartbeat, twice: strong, soft, and a rest.
        PurposeKind.BP, PurposeKind.HEART, PurposeKind.CHOLESTEROL, PurposeKind.BLOOD_THINNER ->
            Buzz.then(heartbeat(), Buzz.still(480), heartbeat())
        // Three quick taps, twice.
        PurposeKind.SUGAR -> Buzz.then(Buzz.beats(FULL, 90, 35, 35, 35), Buzz.still(380), Buzz.beats(FULL, 90, 35, 35, 35))
        // A slow wave that rises and falls, twice.
        PurposeKind.FEVER_PAIN, PurposeKind.PAIN, PurposeKind.COLD, PurposeKind.NERVE -> Buzz.then(wave(), Buzz.still(160), wave())
        // Two long soft breaths.
        PurposeKind.BREATHING, PurposeKind.ALLERGY -> Buzz.beats(190, 320, 480, 480)
        // Everything else, and a medicine whose purpose nobody has confirmed: two even beats.
        else -> Buzz.beats(FULL, 220, 160, 160)
    }

    private fun heartbeat() = Buzz(listOf(90, 110, 90), listOf(FULL, 0, 185))
    private fun wave() = Buzz(listOf(70, 70, 70, 110, 70, 70, 70), listOf(90, 150, 210, FULL, 210, 150, 90))
}
