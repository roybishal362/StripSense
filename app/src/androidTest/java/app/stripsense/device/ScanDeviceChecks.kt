package app.stripsense.device

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import app.stripsense.ml.OcrPhotos
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.measureTimeMillis

/** Native OCR and read-only reference audit. Synthetic fixtures are never user medicines. */
object ScanDeviceChecks {
    fun run(context: Context): String = runBlocking {
        val med = Medicine("scan-fixture", "Fixture", "", "FEVORA", listOf("PARACETAMOL"), listOf(Strength("500", StrengthUnit.MG)))
        val other = med.copy(id = "other-fixture", brand = "CALVORA")
        val meds = listOf(med, other)
        var checks = 0
        val report = StringBuilder()
        fun fixture(vararg names: Pair<String, Float>): Bitmap {
            val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
            val canvas = Canvas(bitmap)
            names.forEach { (text, x) -> canvas.drawText(text, x, 240f, paint) }
            return bitmap
        }
        suspend fun names(photo: Bitmap): List<Medicine> = try {
            val lines = OcrPhotos.read(photo)
            StripGroups.group(lines, meds).mapNotNull { NameRecognition.find(StripParser.parse(it.lines, meds), meds) }
        } finally { photo.recycle() }
        check(names(fixture("FEVORA" to 30f)).single().id == med.id) { "name-only native OCR failed" }; checks++
        check(names(fixture("FEVORA" to 30f, "CALVORA" to 650f)).map { it.id }.toSet() == meds.map { it.id }.toSet()) { "multi-name native OCR failed" }; checks++
        check(names(fixture("FEVORA 650 mg" to 30f)).isEmpty()) { "wrong strength was named" }; checks++
        report.append("PASS $checks/$checks native scan fixtures: name only; two strips; wrong-strength refusal.\n")

        report.append(sealCheck(context))
        report.append(voiceCheck(context))
        val cipher = app.stripsense.data.KeystoreCipher()
        val file = File(context.filesDir, "store.json")
        if (file.exists()) {
            val original = file.readBytes()
            report.append("Saved records on disk are ${if (app.stripsense.data.Sealed.isSealed(original)) "sealed" else "PLAIN"}.\n")
            val data = app.stripsense.data.Store.read(file, cipher)
            var readable = 0
            var matched = 0
            var conflicts = 0
            var elapsed = 0L
            for (look in data.looks.take(12)) {
                if (File(look.photoFile).name != look.photoFile) continue
                val photo = app.stripsense.data.LookPhotos.decode(File(File(context.filesDir, "looks"), look.photoFile), cipher) ?: continue
                try {
                    val ms = measureTimeMillis {
                        val lines = OcrPhotos.read(photo)
                        val key = StripParser.parse(lines, data.medicines, data.tuning)
                        if (key.brands.isNotEmpty()) readable++
                        if (NameRecognition.find(key, data.medicines)?.id == look.medId) matched++
                        val expected = data.medicines.firstOrNull { it.id == look.medId }
                        if (expected != null && EnrollmentEvidence.conflict(key, expected)) conflicts++
                    }
                    elapsed += ms
                } finally { photo.recycle() }
            }
            // What is saved, as pack print only: how each name is stored and whether the phone finds that name in the pack's own photo.
            for (med in data.medicines) {
                val lines = data.looks.filter { it.medId == med.id }.flatMap { it.readLines }
                val strict = lines.count { line -> KnownWords.canon(med.brand) in StripParser.parse(listOf(OcrLine(line, 0, 0, 0, 0, 100, 1f)), data.medicines).brands }
                val words = KnownWords.canon(med.brand).split(' ')
                val perWord = words.joinToString(",") { word -> "$word=" + lines.count { line -> word in TextNorm.tokens(line).flatMap(TextNorm::splitLetterDigit) } }
                report.append("Saved: nick=[${med.nickEn}] name=[${med.brand}] ingredients=${med.generics.size} strengths=[${Strengths.display(med.strengths)}] release=[${med.release}] extra=${med.extraWords} times=${med.times} photos=${data.looks.count { it.medId == med.id }} lines=${lines.size} nameAsWholeOnLines=$strict wordsOnLines($perWord)\n")
            }
            report.append(printAudit(context, data))
            check(file.readBytes().contentEquals(original)) { "user store changed during read-only audit" }
            report.append("Saved-reference audit: photos=${data.looks.take(12).size}, readableSavedNames=$readable, matchingSavedIdentities=$matched, conflictingSavedLabels=$conflicts, totalOcrMs=$elapsed.\n")
            report.append("Reference audit is not a live-camera accuracy benchmark; normal user records unchanged.\n")
        }
        report.toString()
    }

    /** Which speech engine the phone has and what it offers for Hindi and for English. */
    private fun voiceCheck(context: Context): String {
        val ready = java.util.concurrent.CompletableFuture<Int>()
        val tts = android.speech.tts.TextToSpeech(context) { ready.complete(it) }
        return try {
            val status = ready.get(15, java.util.concurrent.TimeUnit.SECONDS)
            if (status != android.speech.tts.TextToSpeech.SUCCESS) return "Speech engine did not start (status $status).\n"
            val out = StringBuilder("Speech engine: ${tts.defaultEngine}; engines=${tts.engines.map { it.name }}\n")
            for (tag in listOf("hi-IN", "en-IN")) {
                val locale = java.util.Locale.forLanguageTag(tag)
                val voices = (tts.voices ?: emptySet()).filter { it.locale.language == locale.language }
                out.append("  $tag: available=${tts.isLanguageAvailable(locale)} voices=${voices.size}\n")
                voices.sortedBy { it.name }.take(14).forEach {
                    out.append("    ${it.name} ${it.locale} quality=${it.quality} needsNetwork=${it.isNetworkConnectionRequired} notInstalled=${it.features.contains(android.speech.tts.TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)}\n")
                }
            }
            // Can the engine really make Hindi sound right now? Written to a file and measured, since nobody is listening.
            val done = java.util.concurrent.ConcurrentHashMap<String, String>()
            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { done[id.orEmpty()] = "done" }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { done[id.orEmpty()] = "error" }
                override fun onError(id: String?, code: Int) { done[id.orEmpty()] = "error $code" }
            })
            fun probe(label: String, text: String, set: () -> Int) {
                val id = "probe-$label"
                val file = File(context.cacheDir, "$id.wav").apply { delete() }
                val setResult = runCatching(set).getOrDefault(-99)
                val started = android.os.SystemClock.elapsedRealtime()
                val queued = tts.synthesizeToFile(text, android.os.Bundle(), file, id)
                while (done[id] == null && android.os.SystemClock.elapsedRealtime() - started < 12_000) Thread.sleep(100)
                out.append("  probe $label: set=$setResult queued=$queued result=${done[id] ?: "no answer"} bytes=${file.length()} ms=${android.os.SystemClock.elapsedRealtime() - started} voiceNow=${tts.voice?.name}\n")
                file.delete()
            }
            val hindi = "यह आपकी दवा है। अगली खुराक शाम आठ बजे है।"
            probe("hi-setLanguage", hindi) { tts.setLanguage(java.util.Locale.forLanguageTag("hi-IN")) }
            for (name in listOf("hi-in-x-hia-local", "hi-in-x-hia-network", "hi-in-x-hic-local")) {
                val voice = (tts.voices ?: emptySet()).firstOrNull { it.name == name } ?: continue
                probe(name, hindi) { tts.setVoice(voice) }
            }
            probe("en-IN-setLanguage", "This is your medicine. The next dose is at eight in the evening.") { tts.setLanguage(java.util.Locale.forLanguageTag("en-IN")) }
            (tts.voices ?: emptySet()).firstOrNull { it.name == "en-in-x-ena-local" }?.let { v -> probe(v.name, "This is your medicine.") { tts.setVoice(v) } }
            out.append("  installed now: " + (tts.voices ?: emptySet()).filter { !it.features.contains(android.speech.tts.TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }.map { it.name }.sorted() + "\n")
            out.toString()
        } catch (e: Exception) { "Speech engine check failed: ${e.javaClass.simpleName}\n" } finally { runCatching { tts.shutdown() } }
    }

    /**
     * The phone's keystore really seals and opens: records and a photo, in a throwaway folder with its own key.
     * Nothing of the family's is touched.
     */
    private fun sealCheck(context: Context): String {
        val cipher = app.stripsense.data.KeystoreCipher("stripsense-device-check")
        val folder = File(context.cacheDir, "seal-check-${System.nanoTime()}")
        try {
            val secret = "family pin 4821 and a medicine name".toByteArray()
            val sealed = app.stripsense.data.Sealed.seal(cipher, secret)
            check(app.stripsense.data.Sealed.isSealed(sealed) && !String(sealed, Charsets.ISO_8859_1).contains("4821")) { "sealed bytes still show the text" }
            check(app.stripsense.data.Sealed.open(cipher, sealed).contentEquals(secret)) { "sealed bytes did not open" }
            check(!sealed.contentEquals(app.stripsense.data.Sealed.seal(cipher, secret))) { "two writes gave the same bytes" }
            val changed = sealed.copyOf().also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }
            check(runCatching { app.stripsense.data.Sealed.open(cipher, changed) }.isFailure) { "changed bytes were accepted" }

            val med = Medicine("seal", "Seal check", "", "CHECK", listOf("INGREDIENT"), listOf(Strength("500", StrengthUnit.MG)))
            val store = app.stripsense.data.Store(folder, cipher) { StoreData(medicines = listOf(med), setupVersion = 1) }
            store.setLanguage("hi")
            check(store.awaitSaved()) { "sealed store did not save" }
            store.close()
            val onDisk = File(folder, "store.json").readBytes()
            check(app.stripsense.data.Sealed.isSealed(onDisk) && !String(onDisk, Charsets.ISO_8859_1).contains("Seal check")) { "store file is readable" }
            val again = app.stripsense.data.Store(folder, cipher) { StoreData() }
            check(!again.unreadable.value && again.data.value.language == "hi" && again.data.value.medicines.single().id == "seal") { "sealed store did not reopen" }
            again.close()

            val picture = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(40, 120, 200)) }
            val name = checkNotNull(app.stripsense.data.FilesIO.savePhoto(File(folder, "looks"), "p.jpg", picture, cipher))
            picture.recycle()
            val photoFile = File(File(folder, "looks"), name)
            check(BitmapFactory.decodeFile(photoFile.path) == null) { "sealed photo opens as a plain picture" }
            val opened = checkNotNull(app.stripsense.data.LookPhotos.decode(photoFile, cipher)) { "sealed photo did not open" }
            check(opened.width == 320 && opened.height == 240)
            opened.recycle()
            return "PASS keystore sealing: records and a photo sealed, reopened, unreadable as plain files, changed bytes refused.\n"
        } finally { folder.deleteRecursively() }
    }

    /**
     * Each saved photo is read again by the phone and shown to the print engine as a strip it has not learned from:
     * the index is built without that photo. Regions are grouped as the live screen groups them.
     * Reports result kinds and nicknames only, never the text that was read.
     */
    private suspend fun printAudit(context: Context, data: StoreData): String {
        val cipher = app.stripsense.data.KeystoreCipher()
        val out = StringBuilder("Print engine, each photo held out:\n")
        var right = 0
        var wrong = 0
        var oldFull = 0
        val looks = data.looks.take(12)
        PrintIndex.build(data.medicines, data.looks).prints.forEach { p ->
            out.append("  saved ${p.brand}: needed=${p.needed} strengths=${p.strengths} numbers=${p.numbers} generics=${p.med.generics} extra=${p.med.extraWords} release=${p.med.release} onPack=${p.releaseOnPack} weak=${p.weakName} parts=${p.nameParts} marks=${p.marks.take(8)}\n")
        }
        for ((n, look) in looks.withIndex()) {
            if (File(look.photoFile).name != look.photoFile) continue
            val photo = app.stripsense.data.LookPhotos.decode(File(File(context.filesDir, "looks"), look.photoFile), cipher) ?: continue
            val lines = try { OcrPhotos.read(photo) } finally { photo.recycle() }
            val index = PrintIndex.build(data.medicines, data.looks - look)
            val traces = ArrayList<String>()
            val results = StripGroups.group(lines.filter { it.confidence >= 0.2f && it.heightPx >= 8 }, data.medicines, data.tuning).map { group ->
                val evidence = PrintEvidence()
                repeat(3) { evidence.add(group.lines, index, data.tuning, it * 100L) }
                evidence.decide(index, 200L).also { result ->
                    if (result !is PrintResult.Checked) {
                        traces += evidence.trace(200L)
                        // Pack print that produced a number, so a false difference can be traced to its line.
                        group.lines.filter { line -> StripParser.parse(listOf(line), data.medicines, data.tuning).let { it.strengths.isNotEmpty() || it.brandNumbers.isNotEmpty() || it.extraWords.isNotEmpty() } }
                            .forEach { traces += "line h=${it.heightPx} conf=${"%.2f".format(it.confidence)}: ${it.text}" }
                    }
                }
            }
            // The same photo as the live screen takes it: the whole index, strips assembled from their names.
            val full = PrintIndex.build(data.medicines, data.looks)
            val scene = Scene()
            var live: List<SeenStrip> = emptyList()
            repeat(4) { live = scene.read(lines.filter { it.confidence >= 0.2f && it.heightPx >= 8 }, full, data.tuning, 1000L + it * 150L) }
            traces += "LIVE " + Scene.oneBoxEach(live).joinToString { r -> when (val x = r.result) {
                is PrintResult.Checked -> "CHECKED:" + x.med.nickEn; is PrintResult.NameOnly -> "NAME:" + x.med.nickEn
                is PrintResult.Differs -> "DIFFERS:" + x.med.nickEn + "[" + x.printed + "]"; is PrintResult.Piece -> "PIECE:" + x.med.nickEn; else -> x.toString() } }
            scene.traces(1450L).lines().forEach { traces += "LIVE votes " + it }
            val expected = data.medicines.firstOrNull { it.id == look.medId }
            fun name(r: PrintResult) = when (r) {
                is PrintResult.Checked -> "CHECKED:" + r.med.nickEn
                is PrintResult.NameOnly -> "NAME:" + r.med.nickEn
                is PrintResult.Piece -> "PIECE:" + r.med.nickEn
                is PrintResult.Differs -> "DIFFERS:" + r.med.nickEn + "[" + r.printed + "]"
                else -> r.toString()
            }
            val named = results.mapNotNull { when (it) { is PrintResult.Checked -> it.med; is PrintResult.NameOnly -> it.med; is PrintResult.Piece -> it.med; else -> null } }
            if (named.any { it.id != look.medId } || results.any { it is PrintResult.Differs }) wrong++
            else if (results.any { it is PrintResult.Checked && it.med.id == look.medId }) right++
            if (Decider.decide(StripParser.parse(lines, data.medicines, data.tuning), data.medicines, data.tuning) is Verdict.Match) oldFull++
            out.append("  photo ${n + 1} (${expected?.nickEn?.take(12)}): regions=${results.size} ")
                .append(results.filter { it != PrintResult.Reading }.groupingBy(::name).eachCount()).append('\n')
            traces.forEach { out.append("    votes over 3 frames: ").append(it).append('\n') }
        }
        out.append("Print engine: checkedAsItself=$right of ${looks.size}, wrongNameOrFalseDifference=$wrong. Earlier full-match rule on the same photos: $oldFull of ${looks.size}.\n")
        return out.toString()
    }
}
