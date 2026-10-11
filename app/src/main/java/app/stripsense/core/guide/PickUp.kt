package app.stripsense.core.guide

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Notices the phone being picked up from where it lay, from its movement sensor's readings.
 *
 * The alarm rings with the phone lying on a table. Picking it up is the answer: the ringing stops and the camera
 * opens, with no button to find. For that the phone must first have lain flat and still, and then be tilted and
 * stay tilted. A knock, the alarm's own buzzing, or a phone already in the hand is not a pick-up.
 */
class PickUp {
    private val smooth = FloatArray(3)
    private var started = false
    private var stillSince = -1L
    private var rest: FloatArray? = null
    private var turnedSince = -1L
    private var done = false

    /** One reading in metres per second squared, with its time. True once: at the moment the phone has been picked up. */
    fun reading(x: Float, y: Float, z: Float, atMs: Long): Boolean {
        if (done) return false
        if (!started) { smooth[0] = x; smooth[1] = y; smooth[2] = z; started = true }
        val shake = norm(x - smooth[0], y - smooth[1], z - smooth[2])
        smooth[0] += (x - smooth[0]) * SMOOTH; smooth[1] += (y - smooth[1]) * SMOOTH; smooth[2] += (z - smooth[2]) * SMOOTH
        val pull = norm(smooth[0], smooth[1], smooth[2])
        if (pull < 1f) return false
        val lying = rest
        if (lying == null) {
            // Flat on a table, face up or face down, and stiller than a hand can hold it.
            val flat = kotlin.math.abs(smooth[2]) / pull >= FLAT
            if (!flat || shake > STILL) { stillSince = -1; return false }
            if (stillSince < 0) stillSince = atMs
            if (atMs - stillSince >= REST_MS) rest = floatArrayOf(smooth[0] / pull, smooth[1] / pull, smooth[2] / pull)
            return false
        }
        val cos = ((smooth[0] * lying[0] + smooth[1] * lying[1] + smooth[2] * lying[2]) / pull).coerceIn(-1f, 1f)
        if (Math.toDegrees(acos(cos).toDouble()) < TILT_DEGREES) { turnedSince = -1; return false }
        if (turnedSince < 0) turnedSince = atMs
        if (atMs - turnedSince < HOLD_MS) return false
        done = true
        return true
    }

    private fun norm(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)

    companion object {
        private const val SMOOTH = 0.15f
        private const val FLAT = 0.93f
        private const val STILL = 0.25f
        private const val REST_MS = 800L
        private const val TILT_DEGREES = 30.0
        private const val HOLD_MS = 300L
    }
}
