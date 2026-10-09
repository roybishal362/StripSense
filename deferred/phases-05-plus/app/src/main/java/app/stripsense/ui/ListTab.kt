package app.stripsense.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.core.Csv
import app.stripsense.core.MedStatus
import app.stripsense.core.MedType
import app.stripsense.core.Medicine
import app.stripsense.core.Strengths
import app.stripsense.core.Words
import app.stripsense.data.DemoData
import app.stripsense.data.FilesIO
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Family > List: the medicines, the demo list, and the list file in and out (spec 05 section 3). */
@Composable
fun ListTab(state: AppState) {
    val context = LocalContext.current
    val lang = state.language
    val data = state.data
    var preview by remember { mutableStateOf<List<Medicine>?>(null) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = FilesIO.readText(context, uri)
        when (val r = if (text == null) Csv.ReadResult.Bad(listOf("the file cannot be read")) else Csv.read(text, state.store.data.value.medicines)) {
            is Csv.ReadResult.Ok -> { preview = r.medicines; errors = emptyList() }
            is Csv.ReadResult.Bad -> { preview = null; errors = r.errors }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val pending = preview
        if (pending != null) {
            Text(Labels.get("previewList", lang), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold))
            pending.forEach { MedCard(it, lang, taught = false, photo = null) }
            WideButton(Labels.get("save", lang)) {
                state.store.setMedicines(pending, state.now())
                preview = null
            }
            SideButton(Labels.get("cancel", lang), Modifier.fillMaxWidth()) { preview = null }
            return@Column
        }
        if (errors.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.stopBg).padding(16.dp)) {
                Text(Labels.get("badFile", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.stopText, fontWeight = FontWeight.Bold))
                errors.take(20).forEach { Text(it, style = AppType.label.copy(color = AppColors.stopText)) }
            }
        }
        WideButton(Labels.get("loadDemo", lang)) {
            state.store.setMedicines(DemoData.medicines, state.now())
            errors = emptyList()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SideButton(Labels.get("importList", lang), Modifier.weight(1f)) {
                runCatching { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }
            }
            SideButton(Labels.get("saveList", lang), Modifier.weight(1f)) {
                val ok = FilesIO.saveText(context, "list.csv", "text/csv", Csv.write(state.store.data.value.medicines))
                Toast.makeText(context, Labels.get(if (ok) "savedInDownloads" else "saveFailed", lang), Toast.LENGTH_LONG).show()
            }
        }
        if (data.medicines.isEmpty()) {
            Text(Labels.get("emptyList", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink2))
        }
        data.medicines.forEach { med ->
            val looks = data.looks.filter { it.medId == med.id }
            MedCard(med, lang, taught = looks.isNotEmpty(), photo = looks.firstOrNull()?.let { File(state.store.looksDir, it.photoFile) })
        }
    }
}

@Composable
fun MedCard(med: Medicine, lang: String, taught: Boolean, photo: File?) {
    Row(
        Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.surface).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val thumb = photo?.let { rememberThumb(it) }
        if (thumb != null) {
            Image(thumb.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
        } else {
            MedIcon(med, 48)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nick(med, lang), style = AppType.forLanguage(AppType.body, lang).copy(fontWeight = FontWeight.Bold))
            Text(
                listOfNotNull(med.brand.ifEmpty { null }, med.release).joinToString(" ") + " · " + med.generics.joinToString(" + "),
                style = AppType.label.copy(color = AppColors.ink2),
            )
            if (med.strengths.isNotEmpty()) Text(Strengths.display(med.strengths), style = AppType.label)
            Text(
                med.times.mapNotNull { runCatching { Words.timeShown(LocalTime.parse(it), lang) }.getOrNull() }.joinToString(", "),
                style = AppType.forLanguage(AppType.caption, lang),
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (med.status == MedStatus.STOPPED) Chip(Labels.get("stopped", lang) + (med.stoppedOn?.let { " $it" } ?: ""), AppColors.stopText, AppColors.stopBg)
                when {
                    med.type == MedType.BUTTON -> Chip(Labels.get("eyeDropsNote", lang), AppColors.ink2, AppColors.greyBg)
                    taught -> Chip(Labels.get("taught", lang), AppColors.okText, AppColors.okBg)
                    else -> Chip(Labels.get("notTaughtYet", lang), AppColors.dueText, AppColors.dueBg)
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Text(text, style = AppType.caption.copy(color = fg, fontWeight = FontWeight.Bold),
        modifier = Modifier.clip(AppShapes.chip).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** A small thumbnail of a taught photo, decoded off the main thread at a reduced size. */
@Composable
fun rememberThumb(file: File): Bitmap? {
    val bmp by produceState<Bitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(file.path, opts)
            }.getOrNull()
        }
    }
    return bmp
}

