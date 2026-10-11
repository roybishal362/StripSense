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
/**
 * A hand on a strip, noticed without finding a hand. The hand model needs to see a palm, and a person
 * reaching into a close camera view shows only fingers. So each strip's own patch of the picture is watched
 * instead: foil is grey, a hand is skin, and skin appearing over a patch that was foil is a hand on that strip.
 * Each strip is compared with itself, so a strip with skin-coloured print, or a wooden table, is not a hand.
 */
object Touch {
    /** Skin has to cover this much more of the patch than it did when the strip lay open. */
    const val COVERED = 0.22f

    /** How fast a strip's own colour is learned while no hand is on it. */
    const val LEARN = 0.2f

    /** A wide rule for skin of any tone under indoor light, in the colour space cameras use. */
    fun isSkin(argb: Int): Boolean {
        val r = (argb shr 16) and 0xff
        val g = (argb shr 8) and 0xff
        val b = argb and 0xff
        val y = 0.299 * r + 0.587 * g + 0.114 * b
        val cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
        val cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
        return y > 50 && cb in 77.0..127.0 && cr in 133.0..173.0
    }

    fun fraction(pixels: IntArray): Float = if (pixels.isEmpty()) 0f else pixels.count(::isSkin).toFloat() / pixels.size

    /**
     * Every strip with a hand over it. Asked before [pick], which learns from the same numbers.
     * This is what lets a strip be remembered while it cannot be read: a hand is the reason it cannot.
     */
    fun covered(now: Map<String, Float>, usual: Map<String, Float>): Set<String> =
        now.filterTo(HashMap()) { (key, fraction) -> usual[key]?.let { fraction - it >= COVERED } == true }.keys

    /**
     * The strip a hand is on: the one whose patch gained the most skin over its own usual amount.
     * [usual] is updated here for every strip that is not covered, so it follows the light.
     */
    fun pick(now: Map<String, Float>, usual: MutableMap<String, Float>): String? {
        usual.keys.retainAll(now.keys)
        var best: String? = null
        var most = COVERED
        for ((key, fraction) in now) {
            val base = usual[key]
            if (base == null) { usual[key] = fraction; continue }
            val gained = fraction - base
            if (gained >= most) { most = gained; best = key }
            else if (gained < COVERED / 2) usual[key] = base + (fraction - base) * LEARN
        }
        return best
    }
}
