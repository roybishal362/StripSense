package app.stripsense.core

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
