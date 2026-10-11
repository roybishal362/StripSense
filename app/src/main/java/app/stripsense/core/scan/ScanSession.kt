package app.stripsense.core.scan

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
/** The hint shown under the guide frame while looking. Words come from ui labels. */
enum class Hint { HOLD_INSIDE, CLOSER, READING }

sealed interface ScanResult {
    data class Looking(val hint: Hint) : ScanResult
    data class Final(val verdict: Verdict) : ScanResult
}

/**
 * Decides when a verdict is steady enough to be spoken. Spec 01 section 5.
 * One session per scan, fed one key per analysed frame. It has no clock of its own.
 */
class ScanSession(private val meds: List<Medicine>, private val tuning: Tuning) {

    private var finalResult: ScanResult.Final? = null

    private var lastKey: ReadKey? = null
    private var sameKeyFrames = 0

    private var slowVerdict: Verdict? = null
    private var slowSinceMs = 0L

    private var textSinceMs: Long? = null

    /** The last verdict the decider gave, for the debug panel. */
    var lastVerdict: Verdict = Verdict.Searching
        private set

    fun feed(key: ReadKey, nowMs: Long, smallestKeyLinePx: Int?, tooSmallLines: Int = 0): ScanResult {
        finalResult?.let { return it }

        val verdict = Decider.decide(key, meds, tuning)
        lastVerdict = verdict

        val prev = lastKey
        sameKeyFrames = if (prev != null && prev.sameAs(key)) sameKeyFrames + 1 else 1
        lastKey = key

        if (isQuick(verdict)) {
            slowVerdict = null
            if (quickIsSteady(key)) return finish(verdict)
        } else if (isSlow(verdict)) {
            if (verdict != slowVerdict) {
                slowVerdict = verdict
                slowSinceMs = nowMs
            }
            if (nowMs - slowSinceMs >= tuning.slowMs) return finish(verdict)
        } else {
            slowVerdict = null
        }

        if (key.letters >= tuning.textLetters) {
            val since = textSinceMs ?: nowMs.also { textSinceMs = it }
            if (nowMs - since >= tuning.timeoutMs) return finish(Verdict.Unreadable)
        } else {
            textSinceMs = null
        }

        return ScanResult.Looking(hint(key, smallestKeyLinePx, tooSmallLines))
    }

    val isFinished: Boolean get() = finalResult != null

    // Same key in quickFrames frames and each strength on strengthLines lines, or the key held strengthFrames frames.
    private fun quickIsSteady(key: ReadKey): Boolean {
        if (key.strengths.isEmpty()) return sameKeyFrames >= tuning.quickFrames
        if (sameKeyFrames >= tuning.strengthFrames) return true
        return sameKeyFrames >= tuning.quickFrames &&
            key.strengths.all { (key.strengthSeen[it] ?: 0) >= tuning.strengthLines }
    }

    private fun hint(key: ReadKey, smallestKeyLinePx: Int?, tooSmallLines: Int): Hint = when {
        key.letters == 0 && tooSmallLines > 0 -> Hint.CLOSER
        key.letters == 0 -> Hint.HOLD_INSIDE
        smallestKeyLinePx != null && smallestKeyLinePx < tuning.minHeightPx + tuning.closerMarginPx -> Hint.CLOSER
        else -> Hint.READING
    }

    private fun finish(verdict: Verdict): ScanResult.Final = ScanResult.Final(verdict).also { finalResult = it }

    private fun isQuick(v: Verdict): Boolean = when (v) {
        is Verdict.Match, is Verdict.Stopped, is Verdict.ButtonOnly,
        is Verdict.Different, is Verdict.WrongMedicine, Verdict.OneAtATime -> true
        else -> false
    }

    private fun isSlow(v: Verdict): Boolean = when (v) {
        Verdict.Unreadable, is Verdict.NotShown, Verdict.Unknown, Verdict.NotSure -> true
        else -> false
    }
}
