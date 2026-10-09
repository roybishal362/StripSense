package app.stripsense.ml

import app.stripsense.core.SecondReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class CallKind { VOICE, SECOND_READER }

/** What one model call gave back (spec 07 section 6). */
sealed interface GemmaReply {
    data class Text(val text: String) : GemmaReply
    data class Failed(val message: String) : GemmaReply // the call threw
    data object TimedOut : GemmaReply
    data object Busy : GemmaReply // not loaded, or a call that would not stop is still inside
}

/** Counters for the Numbers screen, in memory only. Never holds audio or transcripts. */
data class KindStats(val calls: Int = 0, val failures: Int = 0, val timeouts: Int = 0, val times: List<Long> = emptyList())

data class GemmaCounts(
    val voice: KindStats = KindStats(),
    val reader: KindStats = KindStats(),
    val agreed: Int = 0,
    val notAgreed: Int = 0,
    val unreadable: Int = 0,
    val lastNotAgreed: String = "",
)

class GemmaStats {
    private val _counts = MutableStateFlow(GemmaCounts())
    val counts: StateFlow<GemmaCounts> = _counts

    @Synchronized
    fun record(kind: CallKind, ms: Long, reply: GemmaReply) {
        if (reply is GemmaReply.Busy) return
        val c = _counts.value
        fun KindStats.add() = copy(
            calls = calls + 1,
            failures = failures + if (reply is GemmaReply.Failed) 1 else 0,
            timeouts = timeouts + if (reply is GemmaReply.TimedOut) 1 else 0,
            times = (times + ms).takeLast(500),
        )
        _counts.value = if (kind == CallKind.VOICE) c.copy(voice = c.voice.add()) else c.copy(reader = c.reader.add())
    }

    /** Second-reader outcome: agreed, not_agreed or unreadable. The reply kept is a strength, at most 60 characters. */
    @Synchronized
    fun readerOutcome(outcome: String, reply: String) {
        val c = _counts.value
        _counts.value = when (outcome) {
            SecondReader.AGREED -> c.copy(agreed = c.agreed + 1)
            SecondReader.NOT_AGREED -> c.copy(notAgreed = c.notAgreed + 1, lastNotAgreed = reply.trim().take(60))
            SecondReader.UNREADABLE -> c.copy(unreadable = c.unreadable + 1, lastNotAgreed = reply.trim().take(60))
            else -> c
        }
    }
}
