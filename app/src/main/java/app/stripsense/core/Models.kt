package app.stripsense.core

import kotlinx.serialization.Serializable

/** Named StrengthUnit, not Unit, so it never hides kotlin.Unit. */
@Serializable
enum class StrengthUnit { MG, MCG, G, IU, ML, PERCENT }

/** One strength as printed. [value] keeps the printed digits: "500", "2.5". */
@Serializable
data class Strength(val value: String, val unit: StrengthUnit)

/** BUTTON medicines are marked with a button, never by the camera (eye drops, syrup, insulin). */
@Serializable
enum class MedType { CAMERA, BUTTON }

@Serializable
enum class MedStatus { ACTIVE, STOPPED }

/** One row of the family's list. Brand, generics and extra words are uppercase. */
@Serializable
data class Medicine(
    val id: String,
    val nickEn: String,
    val nickHi: String,
    val brand: String,
    val generics: List<String>,
    val strengths: List<Strength>,
    val release: String? = null,
    val extraWords: List<String> = emptyList(),
    val type: MedType = MedType.CAMERA,
    val status: MedStatus = MedStatus.ACTIVE,
    val stoppedOn: String? = null,
    val times: List<String> = emptyList(),
    val noteEn: String = "",
    val noteHi: String = "",
    val colour: String = "",
)

/** One line of print as ML Kit read it. Box in the upright image, in pixels. Never saved. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val heightPx: Int,
    val confidence: Float,
)

/** What one frame said about the list. Sets keep reading order. Never saved. */
data class ReadKey(
    val brands: Set<String> = emptySet(),
    val generics: Set<String> = emptySet(),
    val strengths: Set<Strength> = emptySet(),
    val strengthSeen: Map<Strength, Int> = emptyMap(),
    val brandNumbers: Set<String> = emptySet(),
    val release: Set<String> = emptySet(),
    val extraWords: Set<String> = emptySet(),
    val letters: Int = 0,
) {
    /** Same key for the scan session: everything except the counts. */
    fun sameAs(other: ReadKey): Boolean =
        brands == other.brands &&
            generics == other.generics &&
            strengths == other.strengths &&
            brandNumbers == other.brandNumbers &&
            release == other.release &&
            extraWords == other.extraWords
}

/** The result of one decision. The comments name the sentence IDs of spec 03. */
sealed interface Verdict {
    /** Nothing decided yet, keep looking. */
    data object Searching : Verdict

    /** C1: can't read, turn it over. */
    data object Unreadable : Verdict

    /** RZ1: more than one strip in view. */
    data object OneAtATime : Verdict

    /** The print equals the list entry. */
    data class Match(val med: Medicine) : Verdict

    /** V1: the print differs from the list in one named part. */
    data class Different(val med: Medicine, val printed: String, val listPart: String) : Verdict

    /** D1: her brand name, another medicine inside. */
    data class WrongMedicine(val med: Medicine) : Verdict

    /** N1: same medicine and strength, not a strip that was taught. */
    data class NotShown(val med: Medicine) : Verdict

    /** S1 */
    data class Stopped(val med: Medicine) : Verdict

    /** L1 */
    data class ButtonOnly(val med: Medicine) : Verdict

    /** L2: set outside core, when teaching is on and this medicine has no look. */
    data class NotTaught(val med: Medicine) : Verdict

    /** K1 */
    data object Unknown : Verdict

    /** U1 */
    data object NotSure : Verdict
}
