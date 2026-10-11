package app.stripsense.ui.family

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.core.domain.*
import app.stripsense.core.rx.*
import app.stripsense.core.say.Answers
import app.stripsense.core.text.Words
import app.stripsense.data.Logs
import app.stripsense.ml.*
import app.stripsense.ui.shell.Labels
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The family gives the phone a photo or PDF of the doctor's prescription. Each line is placed on one of the
 * saved strips, with the times the doctor wrote, and a strength that differs from the strip is shown in red.
 * Nothing is changed until the family presses the button on a line. The picture is read in memory and never saved.
 */
@Composable
fun PrescriptionScreen(state: AppState, onDone: () -> Unit) {
    val context = LocalContext.current
    val lang = state.language
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<RxLine>?>(null) }
    var usedModel by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val picked = remember { mutableStateMapOf<String, String>() }
    val applied = remember { mutableStateListOf<String>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        failed = false
        lines = null
        picked.clear()
        applied.clear()
        scope.launch {
            try {
                val pages = withContext(Dispatchers.IO) { pagesOf(context, uri) }
                if (pages.isEmpty()) { failed = true; return@launch }
                val read = ArrayList<String>()
                usedModel = false
                for (page in pages) {
                    // The fast reader takes printed and clearly written lines.
                    read += OcrPhotos.read(page).map { it.text }
                    // Gemma, on the phone, copies what it can of the handwriting. Its copy is only more lines for the same
                    // matching: a word it invents fits no saved strip and is dropped there.
                    if (state.gemma.state.value is GemmaState.Ready) {
                        val small = page.fitWithin(RX_PICTURE_PX)
                        val reply = state.gemma.ask(CallKind.ENROLLMENT, RxPrompt.SYSTEM, GemmaInputs.prescription(small), null, RX_TIMEOUT_MS)
                        if (small !== page) small.recycle()
                        if (reply is GemmaReply.Text) { usedModel = true; read += RxPrompt.lines(reply.text) }
                    }
                    page.recycle()
                }
                val data = state.data
                lines = withContext(Dispatchers.Default) { RxLines.tidy(RxReader.read(read, data.medicines, data.looks)) }
                Logs.i("prescription: ${read.size} lines read, ${lines?.size} about medicines, model used $usedModel")
            } catch (e: Exception) {
                Logs.w("prescription not read: ${e.javaClass.simpleName}")
                failed = true
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(Labels.get("rxTitle", lang), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold))
        Text(Labels.get("rxIntro", lang), style = AppType.label)
        Button(onClick = { runCatching { picker.launch(arrayOf("image/*", "application/pdf")) } }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(Labels.get("rxPick", lang)) }
        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(Labels.get("rxReading", lang), style = AppType.label) }
        if (failed) Text(Labels.get("rxFailed", lang), color = MaterialTheme.colorScheme.error)
        val found = lines
        if (found != null) {
            if (found.isEmpty()) Text(Labels.get("rxNothing", lang), style = AppType.body)
            else Text(Labels.get(if (usedModel) "rxCheckModel" else "rxCheck", lang), style = AppType.label.copy(color = AppColors.ink2))
            found.forEach { line ->
                val med = line.match ?: line.candidates.firstOrNull { it.id == picked[line.text] }
                Column(Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Labels.fill("rxWritten", lang, "t" to line.text.take(80)), style = AppType.label.copy(color = AppColors.ink2))
                    when {
                        med != null -> Text(Answers.nickname(med).get(lang), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold))
                        line.candidates.isNotEmpty() -> {
                            Text(Labels.get("rxWhich", lang), style = AppType.body)
                            line.candidates.forEach { option ->
                                OutlinedButton(onClick = { picked[line.text] = option.id }, modifier = Modifier.fillMaxWidth()) { Text(Answers.nickname(option).get(lang)) }
                            }
                        }
                        else -> Text(Labels.get("rxNotSaved", lang), style = AppType.body.copy(color = AppColors.dueText))
                    }
                    line.dose?.let { Text(doseWords(it, lang), style = AppType.body) }
                    // The chemist's mistake, caught at home: the prescription and the strip do not say the same strength.
                    line.conflict?.takeIf { line.match != null }?.let { (written, saved) ->
                        Text(Labels.fill("rxConflict", lang, "w" to written, "s" to saved), style = AppType.body.copy(color = AppColors.stopText, fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.stopBg).padding(10.dp))
                    }
                    val times = line.dose?.times().orEmpty()
                    if (med != null && times.isNotEmpty()) {
                        if (med.id in applied) Text(Labels.get("rxApplied", lang), style = AppType.label)
                        else Button(onClick = {
                            state.store.setMedicines(state.store.data.value.medicines.map { if (it.id == med.id) it.copy(times = times) else it }, state.now())
                            state.refreshReminders()
                            applied += med.id
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(Labels.fill("rxApply", lang, "t" to times.joinToString(", ") { Words.timeShown(LocalTime.parse(it), lang) }))
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onDone, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("close", lang)) }
    }
}

private fun doseWords(dose: RxDose, lang: String): String {
    val hi = lang == "hi"
    if (dose.asNeeded) return if (hi) "सिर्फ़ ज़रूरत पड़ने पर। कोई तय समय नहीं।" else "Only when needed. No fixed times."
    val parts = listOfNotNull((if (hi) "सुबह" else "morning").takeIf { dose.morning }, (if (hi) "दोपहर" else "afternoon").takeIf { dose.afternoon },
        (if (hi) "रात" else "night").takeIf { dose.night })
    val food = when (dose.food) {
        RxFood.BEFORE -> if (hi) ", खाने से पहले" else ", before food"
        RxFood.AFTER -> if (hi) ", खाने के बाद" else ", after food"
        RxFood.UNSAID -> ""
    }
    return (if (hi) "डॉक्टर ने लिखा: " else "The doctor wrote: ") + parts.joinToString(if (hi) ", " else ", ") + food
}

/** The picture, or the first pages of the PDF, as bitmaps no larger than the readers need. */
private fun pagesOf(context: android.content.Context, uri: Uri): List<Bitmap> {
    val resolver = context.contentResolver
    if (resolver.getType(uri) == "application/pdf") {
        return resolver.openFileDescriptor(uri, "r")?.use { file ->
            PdfRenderer(file).use { pdf ->
                (0 until minOf(pdf.pageCount, RX_PAGES)).map { n ->
                    pdf.openPage(n).use { page ->
                        val scale = RX_PAGE_PX.toFloat() / maxOf(page.width, page.height)
                        Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(AndroidColor.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }.orEmpty()
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= RX_PAGE_PX) sample *= 2
    val photo = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    return listOfNotNull(photo)
}

private const val RX_PAGES = 2
private const val RX_PAGE_PX = 2000
private const val RX_PICTURE_PX = 896
private const val RX_TIMEOUT_MS = 30_000L
