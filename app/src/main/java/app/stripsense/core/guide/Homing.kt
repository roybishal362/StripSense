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
import kotlin.math.sqrt

/**
 * Finding the due strip by sound and touch. The phone ticks only for the medicine that is due,
 * faster as that strip comes to the centre of the camera view, and gives one firm pulse when it is centred.
 * A person who cannot read the screen sweeps the phone over the table and follows the ticking.
 */
object Homing {
    const val FAR_MS = 900L
    const val NEAR_MS = 130L

    /** At or above this the strip counts as centred. */
    const val LOCK = 0.80f

    /**
     * Held centred this long, the due strip opens its answer by itself, so the whole way from alarm to
     * "Are you taking it now?" needs no button to be found. The answer still marks nothing until the person confirms.
     */
    const val HOLD_MS = 1500L

    /** True once the strip has stayed centred since [lockedSince]. */
    fun held(lockedSince: Long?, now: Long): Boolean = lockedSince != null && now - lockedSince >= HOLD_MS

    /** 1 when the strip's centre is the centre of the view, falling to 0 at the edge. */
    fun closeness(box: CropBox, imageW: Int, imageH: Int): Float {
        if (imageW <= 0 || imageH <= 0) return 0f
        val dx = ((box.left + box.right) / 2f - imageW / 2f) / (imageW / 2f)
        val dy = ((box.top + box.bottom) / 2f - imageH / 2f) / (imageH / 2f)
        return (1f - sqrt(dx * dx + dy * dy)).coerceIn(0f, 1f)
    }

    fun intervalMs(closeness: Float): Long = (FAR_MS - (FAR_MS - NEAR_MS) * closeness.coerceIn(0f, 1f)).toLong()

    fun locked(closeness: Float): Boolean = closeness >= LOCK

    /**
     * Only a checked strip of the dose that is due now, or of the dose the reminder asked for, is homed on.
     * A name whose strength was not read, a later dose and a marked dose stay silent: no ticking means "not this one".
     */
    fun wanted(target: ScanTarget, requestedMedId: String?): Boolean =
        !target.nameOnly && (target.timing.kind == TimingKind.NOW || (target.med.id == requestedMedId && target.timing.kind == TimingKind.UNCONFIRMED))
}
