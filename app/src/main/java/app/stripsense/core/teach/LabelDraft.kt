package app.stripsense.core.teach

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
import kotlinx.serialization.json.Json

@Serializable
data class LabelDraft(
    val brand: String = "",
    val generics: List<String> = emptyList(),
    val strengths: List<String> = emptyList(),
    val release: String = "",
    val variants: List<String> = emptyList(),
) {
    fun complete() = brand.isNotBlank() && generics.isNotEmpty() && strengths.isNotEmpty() && strengths.all { Csv.strength(it) != null }
    fun medicine(id: String, previous: Medicine? = null): Medicine = Medicine(
        id, previous?.nickEn ?: listOf(brand, strengths.joinToString(" + ")).filter(String::isNotBlank).joinToString(" "),
        previous?.nickHi.orEmpty(), LabelDrafts.brandKey(brand), generics.map { it.trim().uppercase() },
        strengths.mapNotNull(Csv::strength), release.trim().uppercase().ifEmpty { null }, variants.map { it.trim().uppercase() },
        MedType.CAMERA, previous?.status ?: MedStatus.ACTIVE, previous?.stoppedOn, previous?.times.orEmpty(),
        previous?.noteEn.orEmpty(), previous?.noteHi.orEmpty(), previous?.colour ?: Csv.DEFAULT_COLOUR,
    )
}

/** Extract drafts, never prescriptions. Missing fields remain missing for capture/review. */
object LabelDrafts {
    internal fun brandKey(text: String) = TextNorm.tokens(text).flatMap(TextNorm::splitLetterDigit).joinToString(" ")
    private val json = Json { ignoreUnknownKeys = true }
    private fun ingredient(text: String) = text.replace(Regex("(?i)\\b(?:I\\.?P\\.?|B\\.?P\\.?|USP|dispersible|tablets?|capsules?)\\b"), " ").replace(Regex("\\s+"), " ").trim(' ', '.')
    fun reply(text: String): LabelDraft? = runCatching { decode(text) }.getOrNull()
    fun replyIssue(text: String): String? = runCatching { decode(text) }.exceptionOrNull()?.let {
        if (it is kotlinx.serialization.SerializationException) "JSON field shape" else it.message ?: "invalid label"
    }
    private fun decode(text: String): LabelDraft {
        require(text.length <= 8000) { "reply too long" }
        require(text.indexOf('{') >= 0 && text.lastIndexOf('}') > text.indexOf('{')) { "no JSON object" }
        val body = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)
        val raw = json.decodeFromString(LabelDraft.serializer(), body)
        val draft = raw.copy(brand = raw.brand.trim(), generics = raw.generics.map(String::trim).filter(String::isNotEmpty),
            strengths = raw.strengths.map(String::trim).filter(String::isNotEmpty), release = raw.release.trim().uppercase(),
            variants = raw.variants.map { it.trim().uppercase() }.filter(String::isNotEmpty))
        require(draft.brand.length <= 80 && draft.generics.size <= 12 && draft.strengths.size <= 12) { "label size" }
        require(draft.generics.all { it.isNotBlank() && it.length <= 100 }) { "ingredient field" }
        require(draft.strengths.all { Csv.strength(it) != null }) { "strength field" }
        require(draft.release.isBlank() || draft.release.uppercase() in KnownWords.RELEASE_CODES) { "release field" }
        require(draft.variants.all { it.uppercase() in KnownWords.EXTRA_WORDS }) { "variant field" }
        return draft.copy(brand = draft.brand.trim(), generics = draft.generics.map(::ingredient).filter(String::isNotEmpty).distinct(), strengths = draft.strengths.distinct())
    }

    fun merge(read: LabelDraft, lines: List<OcrLine>): LabelDraft {
        val fallback = ocr(lines)
        val manufacturer = lines.any { line ->
            Regex("(?i)laborator|pharmaceutical|private|limited").containsMatchIn(line.text) &&
                TextNorm.normalise(line.text.trim().substringBefore(' ')) == TextNorm.normalise(read.brand)
        }
        val fullerProduct = fallback.brand.isNotBlank() && brandKey(fallback.brand).startsWith(brandKey(read.brand) + " ")
        val ingredientAsBrand = (read.generics + fallback.generics).any {
            TextNorm.normalise(ingredient(read.brand)) == TextNorm.normalise(ingredient(it))
        }
        return read.copy(brand = if (manufacturer || ingredientAsBrand || read.brand.isBlank() || fullerProduct) fallback.brand else read.brand,
            generics = read.generics.ifEmpty { fallback.generics }, strengths = read.strengths.ifEmpty { fallback.strengths },
            release = read.release.ifBlank { fallback.release }, variants = (read.variants + fallback.variants).distinct())
    }

    fun ocr(lines: List<OcrLine>): LabelDraft {
        val usable = LabelEvidence.lines(lines).map { it.text.trim() }.filter(String::isNotBlank)
        val manufacturer = lines.mapNotNull { line ->
            if (Regex("(?i)laborator|pharmaceutical|private|limited").containsMatchIn(line.text)) line.text.trim().substringBefore(' ').uppercase() else null
        }.toSet()
        val generic = usable.mapNotNull { text ->
            val form = Regex("(?i)\\b(tablets?|capsules?)\\b").find(text) ?: return@mapNotNull null
            ingredient(text.substring(0, form.range.first))
                .takeIf { it.length >= 4 && !it.any(Char::isDigit) && !Regex("(?i)each|coated|contains").containsMatchIn(it) }
        }.firstOrNull()
        val branded = usable.flatMap { text ->
            Regex("(?i)\\b[A-Z]{1,20}\\s*(?:-\\s*)?\\d{2,4}(?:\\.\\d+)?\\b").findAll(text).mapNotNull { m ->
                val prefix = m.value.takeWhile(Char::isLetter).uppercase()
                val after = text.substring(m.range.last + 1)
                if (prefix in setOf("IP", "BP", "USP", "BATCH") ||
                    Regex("(?i)^\\s*(?:mg|mcg|ug|g|gm|iu|ml)\\b").containsMatchIn(after)) null else m.value.trim()
            }.toList()
        }
        val mostSeen = branded.groupingBy { TextNorm.normalise(it) }.eachCount().maxByOrNull { it.value }?.key
        val brand = branded.firstOrNull { TextNorm.normalise(it) == mostSeen }
            ?: usable.firstOrNull { text ->
                text.length in 3..30 && text.split(' ').size <= 3 && text.uppercase() !in manufacturer &&
                    !Regex("(?i)tablet|capsule|contains|uncoated|coated|composition|^I\\.?P\\.?$").containsMatchIn(text) &&
                    StripParser.strengthsIn(text).isEmpty()
            }.orEmpty()
        val strengths = usable.flatMap { StripParser.strengthsIn(it) }.distinct().map(Strengths::display)
        // A release word counts only on a line that also carries the name or a strength. Taken from anywhere on the
        // pack, a stray "SR" or the "Dr" of a maker's name was saved as the release of a plain tablet.
        val release = usable.filter { line -> StripParser.strengthsIn(line).isNotEmpty() || (brand.isNotBlank() && brandKey(line).contains(brandKey(brand))) }
            .flatMap { TextNorm.tokens(it) }.firstOrNull { it in KnownWords.RELEASE_CODES }.orEmpty()
        val variants = usable.filter { brand.isNotBlank() && (brandKey(it).contains(brandKey(brand)) || TextNorm.normalise(it) == "DT") }
            .flatMap(TextNorm::tokens).filter { it in KnownWords.EXTRA_WORDS }.distinct()
        return LabelDraft(brand, generic?.let { listOf(it) }.orEmpty(), strengths, release, variants)
    }
}
