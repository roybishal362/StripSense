package app.stripsense.core

import app.stripsense.core.hear.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** The listener that hears a tablet pushed out of its strip. The recordings are in src/test/resources/sounds. */
class HearTabletTest {
    private val rate = ClickFinder.RATE

    /** The samples of a 48 kHz, 16-bit, one-channel recording. */
    private fun recording(name: String): ShortArray {
        val bytes = javaClass.getResourceAsStream("/sounds/$name")!!.use { it.readBytes() }
        val data = (12 until bytes.size - 8).first { bytes[it] == 'd'.code.toByte() && bytes[it + 1] == 'a'.code.toByte() && bytes[it + 2] == 't'.code.toByte() && bytes[it + 3] == 'a'.code.toByte() } + 8
        val buffer = ByteBuffer.wrap(bytes, data, bytes.size - data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun quieter(samples: ShortArray, by: Double) = ShortArray(samples.size) { (samples[it] * by).toInt().toShort() }

    /** Feeds the samples in pieces as a microphone would, and asks the watch after every piece. */
    private fun listen(samples: ShortArray, watch: PopWatch = PopWatch(), finder: ClickFinder = ClickFinder()): Pair<List<Click>, Pop?> {
        val clicks = ArrayList<Click>()
        var pop: Pop? = null
        var at = 0
        while (at < samples.size) {
            val n = minOf(480, samples.size - at)
            finder.feed(samples.copyOfRange(at, at + n), n) { from, until -> watch.deaf(from, until) }.forEach { clicks += it; watch.click(it) }
            at += n
            if (pop == null) pop = watch.poll(at * 1000L / rate)
        }
        return clicks to (pop ?: watch.poll(samples.size * 1000L / rate + 1000))
    }

    /** A quiet room with sharp bright cracks at the given moments. */
    private fun room(seconds: Double, cracks: List<Double>, loud: Int = 9000, seed: Int = 7): ShortArray {
        val random = Random(seed)
        val out = ShortArray((seconds * rate).toInt()) { random.nextInt(-20, 21).toShort() }
        for (at in cracks) {
            val from = (at * rate).toInt()
            for (i in 0 until rate / 50) if (from + i < out.size)
                out[from + i] = (out[from + i] + random.nextInt(-loud, loud + 1) * exp(-i / (rate * 0.004))).toInt().toShort()
        }
        return out
    }

    @Test fun aRealTabletPushedOutOfItsStripIsHeard() {
        val (clicks, pop) = listen(recording("tablet-out.wav"))
        assertTrue("clicks ${clicks.size}", clicks.size >= 15)
        assertNotNull(pop)
        // The first crack of it, well inside the first second of the recording.
        assertTrue("at ${pop!!.atMs}", pop.atMs < 1000)
        assertTrue(pop.clicks >= 2)
    }

    @Test fun aBusyRoomAndPeopleTalkingAreNotATablet() {
        for (name in listOf("busy-room.wav", "people-talking.wav")) {
            val (clicks, pop) = listen(recording(name))
            assertNull(name, pop)
            assertTrue("$name clicks ${clicks.size}", clicks.size <= 1)
        }
    }

    @Test fun theTabletIsStillHeardInTheBusyRoom() {
        val room = recording("busy-room.wav")
        val tablet = recording("tablet-out.wav")
        val both = ShortArray(room.size) { i -> (room[i] + (if (i >= rate && i - rate < tablet.size) tablet[i - rate].toInt() else 0)).coerceIn(-32768, 32767).toShort() }
        val (_, pop) = listen(both)
        assertNotNull(pop)
        assertTrue("at ${pop!!.atMs}", pop.atMs in 1000..5500)
    }

    @Test fun oneSharpSoundAloneIsNotATabletTwoTogetherAre() {
        assertNull(listen(room(3.0, listOf(1.5))).second)
        val (clicks, pop) = listen(room(3.0, listOf(1.5, 1.62)))
        assertEquals(2, clicks.size)
        assertEquals(2, pop!!.clicks)
        assertTrue(pop.atMs in 1480..1640)
        // Two sounds a second apart are two separate sounds, not one tablet.
        assertNull(listen(room(4.0, listOf(1.0, 2.0))).second)
    }

    @Test fun aKnockOnThePhoneOrItsOwnVoiceIsNotATablet() {
        val knocked = PopWatch().apply { jolt(1550) }
        assertNull(listen(room(3.0, listOf(1.5, 1.62)), knocked).second)
        val speaking = PopWatch().apply { deaf(1000, 2000) }
        assertNull(listen(room(3.0, listOf(1.5, 1.62)), speaking).second)
        // The phone stopped speaking before the tablet came out: that one counts.
        val spoke = PopWatch().apply { deaf(0, 1200) }
        assertNotNull(listen(room(3.0, listOf(1.5, 1.62)), spoke).second)
    }

    @Test fun cracksInsideAVoiceOrACoughAreNotATablet() {
        // The same two cracks, with a loud low sound of a third of a second starting among them: somebody spoke.
        val samples = room(3.0, listOf(1.5, 1.62))
        val from = (1.55 * rate).toInt()
        for (i in 0 until rate / 3) {
            val swell = minOf(1.0, i / (rate * 0.02)) * minOf(1.0, (rate / 3 - i) / (rate * 0.02))
            val voice = 9000 * swell * (sin(2 * PI * 220 * i / rate) + 0.5 * sin(2 * PI * 440 * i / rate) + 0.3 * sin(2 * PI * 660 * i / rate))
            samples[from + i] = (samples[from + i] + voice).toInt().coerceIn(-32768, 32767).toShort()
        }
        assertNull(listen(samples).second)
        // A voice that ended a second before the cracks does not hide them.
        val later = room(4.0, listOf(2.9, 3.02))
        for (i in 0 until rate / 3) later[rate + i] = (later[rate + i] + 9000 * sin(2 * PI * 220 * i / rate)).toInt().coerceIn(-32768, 32767).toShort()
        assertNotNull(listen(later).second)
    }

    @Test fun whereSharpSoundsKeepComingACrackMustStandOutMuchFurther() {
        // The same pair of cracks. After silence that is a tablet; after ten seconds of a song's beat it is the beat.
        fun pair(above: Float, beat: Boolean): Pop? = PopWatch(0f).apply {
            if (beat) for (at in 1000L..8000L step 1000) click(Click(at, 16f, 100f, 0f))
            click(Click(11000, above, 108f, 0f)); click(Click(11150, 15f, 103f, 0f))
        }.poll(12000)
        assertNotNull(pair(above = 22f, beat = false))
        assertNull(pair(above = 22f, beat = true))
        assertNotNull(pair(above = 30f, beat = true))
        // The strip crackling in the fingers just before the tablet comes out is not a room of sharp sounds.
        val handled = PopWatch(0f).apply {
            for (at in listOf(9800L, 10100L, 10400L)) click(Click(at, 15f, 95f, 0f))
            click(Click(11000, 22f, 108f, 0f)); click(Click(11150, 15f, 103f, 0f))
        }
        assertNotNull(handled.poll(12000))
        // Nor is a strip picked up and turned over: two bursts of crackle, many clicks each.
        val turned = PopWatch(0f).apply {
            for (at in listOf(5000L, 5080L, 5200L, 5350L, 8000L, 8120L, 8300L)) click(Click(at, 16f, 96f, 0f))
            click(Click(11000, 22f, 108f, 0f)); click(Click(11150, 15f, 103f, 0f))
        }
        assertNotNull(turned.poll(12000))
        // A room that is only loud, with no sharp sounds in it, is not one either.
        assertTrue(PopWatch(0f).apply { click(Click(11000, 22f, 112f, 0f)) }.clicky(11000).not())
    }

    @Test fun aLowThumpAndASlowHissAreNotClicks() {
        val random = Random(3)
        val thump = ShortArray(3 * rate) { i ->
            val t = i - rate
            (random.nextInt(-20, 21) + if (t in 0 until rate / 10) 15000 * sin(2 * PI * 260 * t / rate) * exp(-t / (rate * 0.03)) else 0.0).toInt().toShort()
        }
        assertTrue(listen(thump).first.isEmpty())
        // A hiss that swells over a tenth of a second, as an "s" does, in a room that is not silent.
        val hiss = ShortArray(3 * rate) { i ->
            val t = (i - rate).toDouble() / rate
            val swell = if (t < 0 || t > 0.5) 0.0 else minOf(1.0, Math.pow(t / 0.1, 2.0)) * minOf(1.0, (0.5 - t) / 0.1)
            (random.nextInt(-300, 301) + random.nextInt(-6000, 6001) * swell).toInt().toShort()
        }
        assertTrue(listen(hiss).first.isEmpty())
    }

    @Test fun aQuietCrackIsRememberedButDoesNotCountUntilThePhoneHasLearnedIt() {
        val quiet = quieter(recording("tablet-out.wav"), 0.02)
        val first = PopWatch()
        assertNull(listen(quiet, first).second)
        val heard = first.loudest
        assertNotNull(heard)
        assertTrue(heard!!.level < PopWatch.DEFAULT_LEVEL)
        // The person pressed Yes: what was heard then becomes what a tablet sounds like on this phone.
        val learned = PopWatch.learn(0f, heard.level)
        assertEquals(heard.level, learned, 0.001f)
        assertNotNull(listen(quiet, PopWatch(PopWatch.levelFor(learned))).second)
        // What is learned never lets the phone listen for almost nothing.
        assertEquals(PopWatch.LOWEST_LEVEL, PopWatch.levelFor(40f), 0.001f)
        assertEquals(PopWatch.DEFAULT_LEVEL, PopWatch.levelFor(0f), 0.001f)
        assertEquals(100f * 0.7f + 90f * 0.3f, PopWatch.learn(100f, 90f), 0.001f)
    }
}
