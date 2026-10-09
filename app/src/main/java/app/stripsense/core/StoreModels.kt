package app.stripsense.core

import kotlinx.serialization.Serializable

@Serializable
enum class EventKind { TAKEN, NOT_SURE, UNDONE }

@Serializable
enum class EventSource { PROMPT_TAP, PROMPT_VOICE, MARK_BUTTON, DEMO }

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
    val lookCheckOn: Boolean = true,
    val secondReader: SecondReaderMode = SecondReaderMode.ON,
    val listSavedAtMillis: Long = 0,
    val checkSeen: Boolean = false,
)
