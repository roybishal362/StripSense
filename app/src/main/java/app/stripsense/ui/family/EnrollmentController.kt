package app.stripsense.ui.family

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import app.stripsense.AppState
import app.stripsense.data.ReferenceInput
import app.stripsense.data.ReferenceSaver
import app.stripsense.data.Logs
import app.stripsense.ml.*
import kotlinx.coroutines.*
import java.io.File

/** Draft photos stay in memory until the family reviews the extracted label. */
class EnrollmentController(private val state: AppState, val previous: Medicine?, private val scope: CoroutineScope) {
    var views by mutableStateOf<List<TeachingSnapshot>>(emptyList()); private set
    var capturing by mutableStateOf(true)
    var reading by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var restoring by mutableStateOf(false); private set
    var confirmed by mutableStateOf(false)
    var brand by mutableStateOf("")
    var generics by mutableStateOf("")
    var strengths by mutableStateOf("")
    var release by mutableStateOf("")
    var variants by mutableStateOf("")
    var name by mutableStateOf(previous?.nickEn.orEmpty())
    var times by mutableStateOf(previous?.times?.joinToString(";").orEmpty())
    var status by mutableStateOf("readLabelGuide"); private set
    var errors by mutableStateOf<List<String>>(emptyList()); private set
    private var closed = false
    private val newId = state.newId()

    fun readSavedPhoto() {
        val saved = previous?.let { med -> state.data.looks.lastOrNull { it.medId == med.id } } ?: return
        if (closed || reading || restoring || views.isNotEmpty()) return
        restoring = true
        scope.launch {
            withContext(NonCancellable) {
            val snapshot = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(state.store.looksDir, saved.photoFile)
                    val photo = app.stripsense.data.LookPhotos.decode(file, state.store.cipher) ?: return@runCatching null
                    try {
                        val lines = OcrPhotos.read(photo)
                        TeachingSnapshot(photo, lines, StripParser.parse(lines, state.data.medicines, state.data.tuning))
                    } catch (e: Exception) { photo.recycle(); throw e }
                }.getOrNull()
            }
            restoring = false
            if (closed) snapshot?.photo?.recycle()
            else if (snapshot != null) capture(snapshot)
            else errors = listOf(Labels.get("savedPhotoUnreadable", state.language))
            }
        }
    }

    fun capture(view: TeachingSnapshot) {
        if (closed) { view.photo.recycle(); return }
        views = views + view
        capturing = false
        confirmed = false
        val lines = views.flatMap { it.lines }
        fill(LabelDrafts.ocr(lines))
        reading = true
        status = "readingLabel"
        val copies = views.map { checkNotNull(it.photo.copy(Bitmap.Config.ARGB_8888, false)) }
        scope.launch {
            try {
                val reply = if (state.gemma.isFree()) {
                    val inputs = withContext(Dispatchers.Default) { GemmaInputs.labels(copies, lines.joinToString("\n") { it.text }) }
                    state.gemma.askJson(CallKind.ENROLLMENT, LabelPrompt.SYSTEM, inputs, LabelPrompt.SCHEMA, 20_000)
                } else GemmaReply.Busy
                if (!closed) {
                    val read = (reply as? GemmaReply.Text)?.let { LabelDrafts.reply(it.text) }
                    if (reply is GemmaReply.Text && read == null) Logs.w("label reply rejected: ${LabelDrafts.replyIssue(reply.text)}")
                    if (read != null) {
                        fill(LabelDrafts.merge(read, lines))
                    }
                    status = if (draft().complete()) "reviewLabel" else "labelNeedsSide"
                }
            } finally {
                copies.forEach { it.recycle() }
                if (!closed) reading = false
            }
        }
    }

    private fun fill(draft: LabelDraft) {
        brand = draft.brand
        generics = draft.generics.joinToString(";")
        strengths = draft.strengths.joinToString(";")
        release = draft.release
        variants = draft.variants.joinToString(";")
        if (previous == null) name = listOf(brand, strengths).filter(String::isNotBlank).joinToString(" ")
    }
    fun draft() = LabelDraft(brand.trim(), parts(generics), parts(strengths), release.trim(), parts(variants))
    private fun parts(text: String) = text.split(';').map(String::trim).filter(String::isNotEmpty)

    fun save(onSaved: () -> Unit) {
        if (reading || saving || !confirmed || views.isEmpty()) return
        val draft = draft()
        if (!draft.complete()) { errors = listOf(Labels.get("labelNeedsSide", state.language)); return }
        val identity = draft.medicine(newId)
        val matching = previous ?: state.data.medicines.firstOrNull { old ->
            TextNorm.normalise(old.brand) == TextNorm.normalise(identity.brand) &&
                old.generics.map(TextNorm::normalise).toSet() == identity.generics.map(TextNorm::normalise).toSet() &&
                Strengths.sameStrengths(old.strengths, identity.strengths) && old.release == identity.release && old.extraWords.toSet() == identity.extraWords.toSet()
        }
        val med = draft.medicine(matching?.id ?: newId, matching).copy(nickEn = name.trim().ifEmpty { brand.trim() }, times = parts(times), purpose = matching?.purpose.orEmpty(), personId = matching?.personId ?: People.active(state.data))
        val validation = Csv.read(Csv.write(listOf(med)))
        if (validation is Csv.ReadResult.Bad) { errors = validation.errors; return }
        val reviewed = (validation as Csv.ReadResult.Ok).medicines.single().copy(id = med.id, purpose = med.purpose, personId = med.personId)
        val old = state.store.data.value.medicines.firstOrNull { it.id == med.id }
        val same = old != null && old.brand == reviewed.brand && old.generics == reviewed.generics && old.strengths == reviewed.strengths && old.release == reviewed.release && old.extraWords == reviewed.extraWords
        val kept = if (same) state.store.data.value.looks.count { it.medId == med.id } else 0
        if (kept + views.size > 8) { errors = listOf(Labels.get("referenceFull", state.language)); return }
        val inputs = mutableListOf<ReferenceInput>()
        try { views.forEach { inputs += ReferenceInput(checkNotNull(it.photo.copy(Bitmap.Config.ARGB_8888, false)), it.lines) } }
        catch (_: Exception) { inputs.forEach { it.photo.recycle() }; errors = listOf(Labels.get("teachFailed", state.language)); return }
        saving = true
        errors = emptyList()
        scope.launch {
            val ok = withContext(Dispatchers.IO + NonCancellable) {
                ReferenceSaver(state.store, state.embedder).save(reviewed, inputs, state.now(), true)
            }
            if (!closed) {
                saving = false
                if (ok) { state.noteTeachingSave(); onSaved() }
                else errors = listOf(Labels.get("saveMedicineFailed", state.language))
            }
        }
    }
    fun close() { closed = true; views.forEach { it.photo.recycle() } }
}
