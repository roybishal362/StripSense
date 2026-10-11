package app.stripsense.data

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
import app.stripsense.ml.LookEmbedder
import app.stripsense.ml.ReferenceFingerprint
import java.io.File
import java.util.UUID

data class ReferenceInput(val photo: Bitmap, val lines: List<OcrLine>)

/** Owns private photo copies supplied by the caller and releases them after the disk transaction. */
class ReferenceSaver(private val store: Store, private val embedder: LookEmbedder) {
    fun save(med: Medicine, inputs: List<ReferenceInput>, now: Long, enrollment: Boolean): Boolean {
        val files = mutableListOf<String>()
        var phase = "features"
        val ok = try {
            val views = inputs.map { input ->
                val id = UUID.randomUUID().toString()
                val fingerprint = ReferenceFingerprint.extract(input.photo)
                val vector = embedder.embed(input.photo).orEmpty()
                check(!input.photo.isRecycled) { "embedding destroyed caller pixels" }
                phase = "photo"
                val file = checkNotNull(FilesIO.savePhoto(store.looksDir, "$id.jpg", input.photo, store.cipher))
                files += file
                val key = StripParser.parse(input.lines, listOf(med), store.data.value.tuning)
                Look(id, med.id, vector, file, input.lines.map { it.text }, now,
                    EnrollmentEvidence.verified(key, med, store.data.value.tuning), fingerprint)
            }
            phase = "store"
            if (enrollment) store.saveEnrollment(med, views) else views.size == 1 && store.saveLook(views.single())
        } catch (e: Exception) {
            Logs.w("teaching $phase failed: ${e.javaClass.simpleName}")
            false
        } finally { inputs.forEach { if (!it.photo.isRecycled) it.photo.recycle() } }
        if (!ok) files.forEach { File(store.looksDir, it).delete() }
        Logs.i("teaching saved=$ok views=${inputs.size}")
        return ok
    }
}
