package app.stripsense.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Gemma's JSON for one question (spec 04 section 4). Gemma never writes the answer. */
@Serializable
data class VoiceRequest(
    val request: String,
    val medicine: Int = 0,
    val answer: String = "none",
    val transcript: String = "",
    val emergency: Boolean = false,
) {
    companion object {
        val REQUESTS = setOf(
            "status_of", "whats_left", "which_one", "which_is_x", "taken_now", "yes_no", "missed_dose",
            "extra_dose", "call_family", "repeat", "medical_question", "emergency", "self_harm", "other",
        )

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** The first "{" to the last "}". Any failure, an unknown request, or a medicine outside 0..count: null. */
        fun parse(text: String, medicineCount: Int): VoiceRequest? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val r = runCatching { json.decodeFromString(serializer(), text.substring(start, end + 1)) }.getOrNull() ?: return null
            if (r.request !in REQUESTS || r.medicine !in 0..medicineCount) return null
            return r
        }
    }
}
