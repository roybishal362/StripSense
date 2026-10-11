package app.stripsense.core.dose

import app.stripsense.core.domain.EventSource

/**
 * How the phone knows about a marked dose, weakest first.
 * PRESSED: somebody pressed a button. SEEN: the camera had checked the strip, then the person said yes.
 * HEARD: the camera had checked the strip, and the phone heard a tablet pushed out of it.
 * None of them says the tablet was swallowed. They say how much the phone itself was there for.
 */
enum class Proof { PRESSED, SEEN, HEARD }

object Proofs {
    fun of(source: EventSource): Proof? = when (source) {
        EventSource.HEARD -> Proof.HEARD
        EventSource.PROMPT_TAP, EventSource.PROMPT_VOICE -> Proof.SEEN
        EventSource.MARK_BUTTON, EventSource.REMINDER_TAP -> Proof.PRESSED
        EventSource.DEMO -> null
    }
}
