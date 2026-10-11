package app.stripsense.core.dose

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
/** Scan another keeps this run; a different result or a main tab ends it. */
class AnswerHistory {
    var unreadableCount: Int = 0
        private set

    fun accept(verdict: Verdict): Int {
        unreadableCount = if (verdict == Verdict.Unreadable) (unreadableCount + 1).coerceAtMost(3) else 0
        return unreadableCount
    }

    fun reset() {
        unreadableCount = 0
    }
}
