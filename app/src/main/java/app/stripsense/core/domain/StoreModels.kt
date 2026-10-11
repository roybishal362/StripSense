package app.stripsense.core.domain

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
import kotlinx.serialization.Serializable

@Serializable
enum class EventKind { TAKEN, NOT_SURE, UNDONE }

/**
 * How a dose came to be marked. HEARD: the camera had just checked the strip, the question was open, and the
 * phone heard a tablet pushed out of it. The others are a press or a spoken yes.
 */
@Serializable
enum class EventSource { PROMPT_TAP, PROMPT_VOICE, MARK_BUTTON, DEMO, REMINDER_TAP, HEARD }

/** One entry of the append-only dose log. Spec 02 section 3. UNDONE points at the event it cancels. */
@Serializable
data class DoseEvent(
    val id: String,
    val medId: String,
    val slotKey: String,
    val atMillis: Long,
    val kind: EventKind,
    val source: EventSource,
    val undoes: String? = null,
    /** Part of an example week filled in for showing the app (ExampleWeek). Never something the person did. */
    val example: Boolean = false,
)

/** A taught strip: the picture's vector and where its photo is. Spec 05 section 1. */
@Serializable
data class Look(
    val id: String,
    val medId: String,
    val vector: List<Float>,
    val photoFile: String,
    val readLines: List<String> = emptyList(),
    val taughtAtMillis: Long = 0,
    val verifiedPrint: Boolean = true,
    val fingerprint: List<Float> = emptyList(),
)

@Serializable
enum class TrialScore { RIGHT, WRONG, NOT_SURE, WRONG_REFUSAL }

/** One scored try of trial mode. Spec 05 section 6. */
@Serializable
data class Trial(
    val id: String,
    val atMillis: Long,
    val truth: String,
    val verdict: String,
    val verdictMedId: String? = null,
    val score: TrialScore,
    val msToVerdict: Long = -1,
    val lookScore: Float? = null,
    val lookOnlyWouldName: String? = null,
    val secondReader: String? = null,
)

/** Spec 04 section 7. */
@Serializable
enum class SecondReaderMode { OFF, ON, STRICT }

/** Everything the phone remembers, in one JSON file. Spec 05 section 1. */
@Serializable
data class StoreData(
    val person: Person = Person(),
    val language: String = "en",
    val medicines: List<Medicine> = emptyList(),
    val events: List<DoseEvent> = emptyList(),
    val looks: List<Look> = emptyList(),
    val trials: List<Trial> = emptyList(),
    val tuning: Tuning = Tuning(),
    val clockOffsetMs: Long = 0,
    /** The optional link to the family's own computer: its address, the pairing code typed once, and whether it is on. */
    val linkUrl: String = "",
    val linkCode: String = "",
    val linkOn: Boolean = false,
    /** The other people whose medicines are on this phone, and whose turn it is now (empty: the first person). */
    val people: List<Profile> = emptyList(),
    val activePerson: String = "",
    /** Listening for the tablet coming out of its strip while the question is open, and how loud that has sounded on this phone (0: not heard yet). */
    val hearTablet: Boolean = true,
    val tabletSound: Float = 0f,
    /** Which of the phone's two vibration motors is at its top end (see ui/shell/Feel.kt). The family can swap it. */
    val topMotor: Int = 1,
    /** For someone who cannot hear the phone: it stays silent, says everything through the hand, and is answered by two knocks. */
    val touchOnly: Boolean = false,
    /** The longest row of right answers in "Feel and tell" (core/play/FeelQuiz.kt). */
    val feelBest: Int = 0,
    /** The daily code (core/play/DailyCode.kt): the last day a try was finished, whether it was cracked, the row of days, and the points the games earned. */
    val codeDay: String = "",
    val codeWon: Boolean = false,
    val codeRow: Int = 0,
    val codeBestRow: Int = 0,
    val playPoints: Int = 0,
    /** Tic-tac-toe with Pattu: games won, and the last day a win was counted for points. */
    val tttWins: Int = 0,
    val tttDay: String = "",
    /** The day's gifts as "day|giftId", what the person said about them as "giftId|LOVED", and the last day each chit chat was answered. */
    val gifts: List<String> = emptyList(),
    val giftTalk: List<String> = emptyList(),
    val chatGiftDay: String = "",
    val chatDay: String = "",
    val chatFeel: String = "",
    val lookCheckOn: Boolean = true,
    val secondReader: SecondReaderMode = SecondReaderMode.ON,
    val listSavedAtMillis: Long = 0,
    val checkSeen: Boolean = false,
    val setupVersion: Int = 0,
)
