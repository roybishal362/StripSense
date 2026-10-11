package app.stripsense.ui.family

import androidx.compose.foundation.horizontalScroll

import androidx.compose.ui.unit.sp
import app.stripsense.ui.theme.AppIcons

import app.stripsense.core.domain.*

import app.stripsense.core.teach.Purposes
import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
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
import app.stripsense.core.text.Csv
import app.stripsense.core.domain.MedStatus
import app.stripsense.core.domain.MedType
import app.stripsense.core.domain.Medicine
import app.stripsense.core.text.Strengths
import app.stripsense.core.text.Words
import app.stripsense.data.FilesIO
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Family > List: the family's medicines and optional list-file import and export. */
@Composable
fun ListTab(state: AppState) {
    val context = LocalContext.current
    val lang = state.language
    val data = state.data
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var prescription by remember { mutableStateOf(false) }
    var shownPerson by remember { mutableStateOf(People.active(state.data)) }
    if (prescription) { PrescriptionScreen(state) { prescription = false }; return }
    if (scanning) { EnrollmentScreen(state) { scanning = false }; return }
    if (adding || editing != null) {
        key(editing?.id ?: "new") { MedicineEditor(state, editing, shownPerson) { adding = false; editing = null } }
        return
    }
    var preview by remember { mutableStateOf<List<Medicine>?>(null) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val text = FilesIO.readText(context, uri)
                    if (text == null) Csv.ReadResult.Bad(listOf(Labels.get("fileReadFailed", lang)))
                    else state.store.data.value.let { saved -> Csv.read(text, saved.medicines, saved.events.mapTo(HashSet()) { it.medId }) }
                }
                when (result) {
                    is Csv.ReadResult.Ok -> { preview = result.medicines; errors = emptyList() }
                    is Csv.ReadResult.Bad -> { preview = null; errors = result.errors }
                }
            } finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (busy) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(Labels.get("fileWorking", lang), style = AppType.body)
            return@Column
        }
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
        // One person's medicines at a time. Papa's strips are not mixed in with Maa's.
        val everyone = People.all(data)
        val person = everyone.firstOrNull { it.id == shownPerson } ?: everyone.first()
        if (everyone.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            everyone.forEach { p -> PersonChip(p.call(lang).ifBlank { Labels.get("peopleFirst", lang) }, p.id == person.id) { shownPerson = p.id } }
        }
        val theirs = People.medicines(data, person.id)
        Text(Labels.get("famListIntro", lang), fontSize = 15.sp, color = AppColors.ink3, lineHeight = 21.sp)
        // The phone suggests what each medicine is for from its ingredients. Nothing is called by a purpose until the family says yes here.
        data.medicines.filter { it.status == MedStatus.ACTIVE && Purposes.unasked(it) }.forEach { med ->
            val kind = Purposes.suggest(med) ?: return@forEach
            fun answer(value: String) { state.store.setMedicines(state.store.data.value.medicines.map { if (it.id == med.id) it.copy(purpose = value) else it }, state.now()) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.dueBg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Labels.fill("purposeAsk", lang, "n" to (if (lang == "hi") med.nickHi.ifEmpty { med.nickEn } else med.nickEn),
                    "g" to med.generics.joinToString(" + ").lowercase(), "p" to (if (lang == "hi") kind.hi else kind.en)), fontSize = 15.5.sp, color = AppColors.ink, lineHeight = 22.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuietButton(Labels.get("purposeYes", lang), Modifier.weight(1f)) { answer(kind.name) }
                    QuietButton(Labels.get("purposeNo", lang), Modifier.weight(1f)) { answer(Purposes.NONE) }
                }
            }
        }
        if (theirs.isEmpty()) Text(Labels.get("emptyRealList", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink2))
        // One card for each medicine, drawn as the prototype drew it: who it is, then what the family saved about it.
        theirs.forEach { med ->
            val looks = data.looks.filter { it.medId == med.id }
            MedCard(med, lang, taught = looks.isNotEmpty(), photo = looks.firstOrNull()?.let { File(state.store.looksDir, it.photoFile) }, onEdit = { editing = med })
        }
        // Adding a medicine: one place, three ways, each said in a line.
        SectionTitle(Labels.get("addMedicine", lang))
        FamilyCard {
            ChoiceRow(Labels.get("addByCamera", lang), Labels.get("addByCameraSub", lang), AppIcons.Camera, first = true) { scanning = true }
            if (data.medicines.isNotEmpty()) ChoiceRow(Labels.get("rxOpen", lang), Labels.get("addByRxSub", lang), AppIcons.Chart) { prescription = true }
            ChoiceRow(Labels.get("manualMedicine", lang), Labels.get("addByTypingSub", lang), AppIcons.Clock) { adding = true }
        }
    }
}

/**
 * One medicine as the family sees it: its photo or tile, the name it is called by, whether the camera knows its
 * strip, and what was saved about it in rows of label and value. [owner]: whose it is, where the phone has several people.
 */
@Composable
fun MedCard(med: Medicine, lang: String, taught: Boolean, photo: File?, owner: String? = null, onEdit: (() -> Unit)? = null) {
    FamilyCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val thumb = photo?.let { rememberThumb(it) }
            if (thumb != null) Image(thumb.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
            else MedTile(med, 56)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(nick(med, lang), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink, lineHeight = 24.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (med.status == MedStatus.STOPPED) Chip(Labels.get("stopped", lang) + (med.stoppedOn?.let { " $it" } ?: ""), AppColors.stopText, AppColors.stopBg)
                    when {
                        med.type == MedType.BUTTON -> Chip(Labels.get("eyeDropsNote", lang), AppColors.ink2, AppColors.greyBg)
                        taught -> Chip(Labels.get("taught", lang), AppColors.okText, AppColors.okBg)
                        else -> Chip(Labels.get("notTaughtYet", lang), AppColors.dueText, AppColors.dueBg)
                    }
                }
            }
            if (onEdit != null) QuietButton(Labels.get("editShort", lang)) { onEdit() }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (owner != null) KeyValue(Labels.get("kvWhose", lang), owner)
            KeyValue(Labels.get("kvBrand", lang), listOfNotNull(med.brand.ifEmpty { null }, med.release).joinToString(" "))
            KeyValue(Labels.get("kvMedicine", lang), med.generics.joinToString(" + "))
            KeyValue(Labels.get("kvStrength", lang), Strengths.display(med.strengths))
            KeyValue(Labels.get("kvWhen", lang), med.times.mapNotNull { runCatching { Words.timeShown(LocalTime.parse(it), lang) }.getOrNull() }.joinToString(", "))
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
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val bmp by produceState<Bitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) {
            // Saved photos are sealed on disk; they are opened into memory only.
            app.stripsense.data.LookPhotos.decode(file, app.stripsense.data.AppStore.get(context).cipher, BitmapFactory.Options().apply { inSampleSize = 4 })
        }
    }
    return bmp
}

