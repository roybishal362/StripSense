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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The family transcribes the package label and prescribed schedule, then reviews and saves it. */
@Composable
fun MedicineEditor(state: AppState, existing: Medicine?, defaultOwner: String? = null, onDone: () -> Unit) {
    val lang = state.language
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(existing?.nickEn.orEmpty()) }
    var hindi by rememberSaveable { mutableStateOf(existing?.nickHi.orEmpty()) }
    var brand by rememberSaveable { mutableStateOf(existing?.brand.orEmpty()) }
    var generics by rememberSaveable { mutableStateOf(existing?.generics?.joinToString(";").orEmpty()) }
    var strengths by rememberSaveable { mutableStateOf(existing?.strengths?.joinToString(";") { Strengths.display(it) }.orEmpty()) }
    var release by rememberSaveable { mutableStateOf(existing?.release.orEmpty()) }
    var extras by rememberSaveable { mutableStateOf(existing?.extraWords?.joinToString(";").orEmpty()) }
    var times by rememberSaveable { mutableStateOf(existing?.times?.joinToString(";").orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.noteEn.orEmpty()) }
    var camera by rememberSaveable { mutableStateOf(existing?.type != MedType.BUTTON) }
    var active by rememberSaveable { mutableStateOf(existing?.status != MedStatus.STOPPED) }
    // What it is for: the family's own answer, or what the ingredients suggest until they answer.
    var purpose by rememberSaveable { mutableStateOf(existing?.purpose?.ifEmpty { existing.let(Purposes::suggest)?.name }.orEmpty()) }
    var purposeNote by rememberSaveable { mutableStateOf(if (existing != null && existing.purpose.isEmpty() && purpose.isNotEmpty()) "purposeFromTable" else "") }
    var purposeMenu by remember { mutableStateOf(false) }
    // What is printed on the strip is read by the camera. It is shown for typing only when someone asks, or when there is no strip to read.
    var details by rememberSaveable { mutableStateOf(existing == null) }
    // Whose medicine it is. A new one belongs to the person whose turn it is on the phone.
    var owner by rememberSaveable { mutableStateOf(existing?.personId ?: defaultOwner ?: People.active(state.data)) }
    var ownerMenu by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var remove by remember { mutableStateOf(false) }
    fun persist(list: List<Medicine>) {
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { state.store.setMedicines(list, state.now()); state.store.awaitSaved() }
            busy = false
            if (ok) onDone() else errors = listOf(Labels.get("saveMedicineFailed", lang))
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(Labels.get(if (existing == null) "addMedicine" else "editMedicine", lang))
        fun field(key: String, value: String, update: (String) -> Unit): @Composable () -> Unit = {
            OutlinedTextField(value, update, label = { Text(Labels.get(key, lang)) },
                enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        field("medicineName", name) { name = it }()
        field("medicineHindi", hindi) { hindi = it }()
        if (details) {
        Row { Switch(camera, { camera = it }, enabled = !busy); Text(Labels.get(if (camera) "cameraMedicine" else "buttonMedicine", lang), Modifier.padding(12.dp)) }
        field("medicineBrand", brand) { brand = it }()
        field("medicineGenerics", generics) { generics = it }()
        field("medicineStrengths", strengths) { strengths = it }()
        if (camera) {
            field("medicineRelease", release) { release = it }()
            field("medicineExtra", extras) { extras = it }()
        }
        }
        val people = People.all(state.data)
        if (people.size > 1) {
            Text(Labels.get("ownerField", lang))
            Box {
                OutlinedButton(onClick = { ownerMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text((people.firstOrNull { it.id == owner } ?: people.first()).call(lang).ifBlank { Labels.get("peopleFirst", lang) })
                }
                DropdownMenu(expanded = ownerMenu, onDismissRequest = { ownerMenu = false }) {
                    people.forEach { p -> DropdownMenuItem(text = { Text(p.call(lang).ifBlank { Labels.get("peopleFirst", lang) }) }, onClick = { owner = p.id; ownerMenu = false }) }
                }
            }
        }
        Text(Labels.get("purposeField", lang))
        Box {
            OutlinedButton(onClick = { purposeMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(Purposes.kind(purpose)?.let { if (lang == "hi") it.hi else it.en } ?: Labels.get("purposeNone", lang))
            }
            DropdownMenu(expanded = purposeMenu, onDismissRequest = { purposeMenu = false }) {
                DropdownMenuItem(text = { Text(Labels.get("purposeNone", lang)) }, onClick = { purpose = Purposes.NONE; purposeNote = ""; purposeMenu = false })
                PurposeKind.entries.forEach { kind ->
                    DropdownMenuItem(text = { Text(if (lang == "hi") kind.hi else kind.en) }, onClick = { purpose = kind.name; purposeNote = ""; purposeMenu = false })
                }
            }
        }
        if (purposeNote.isNotEmpty()) Text(Labels.get(purposeNote, lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
        // For an ingredient the built-in table does not know, the model on the phone may suggest one of the same fixed kinds.
        if (Purposes.kind(purpose) == null && generics.isNotBlank()) OutlinedButton(onClick = {
            asking = true
            scope.launch {
                try {
                    val asked = Medicine("ask", name, hindi, brand.trim(), generics.split(';').map(String::trim).filter(String::isNotEmpty), strengths.split(';').mapNotNull(Csv::strength))
                    val reply = state.gemma.askJson(app.stripsense.ml.CallKind.ENROLLMENT, PurposePrompt.SYSTEM, app.stripsense.ml.GemmaInputs.typed(PurposePrompt.question(asked)), PurposePrompt.SCHEMA, 12_000)
                    val kind = (reply as? app.stripsense.ml.GemmaReply.Text)?.let { PurposePrompt.parse(it.text) }
                    if (kind != null) { purpose = kind.name; purposeNote = "purposeAiNote" } else purposeNote = "purposeAiUnknown"
                } finally { asking = false }
            }
        }, enabled = !busy && !asking, modifier = Modifier.fillMaxWidth()) { Text(Labels.get("purposeAskAi", lang)) }
        if (asking) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (existing != null) VoiceNoteRow(state, existing.id, !busy)
        // When it is taken: three large choices. The exact clock times stay available under "More details".
        Text(Labels.get("editWhen", lang))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chosen = times.split(';').map(String::trim).filter(String::isNotEmpty)
            listOf("08:00" to "timeMorning", "14:00" to "timeAfternoon", "20:00" to "timeNight").forEach { (time, key) ->
                FilterChip(selected = time in chosen, enabled = !busy, onClick = { times = (if (time in chosen) chosen - time else chosen + time).sorted().joinToString(";") },
                    label = { Text(Labels.get(key, lang)) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp))
            }
        }
        if (details) field("medicineTimes", times) { times = it }()
        field("medicineNote", note) { note = it }()
        TextButton(onClick = { details = !details }, enabled = !busy) { Text(Labels.get(if (details) "editLess" else "editMore", lang)) }
        Row { Switch(active, { active = it }, enabled = !busy); Text(Labels.get("medicineActive", lang), Modifier.padding(12.dp)) }
        errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = {
            val candidate = Medicine(existing?.id ?: state.newId(), name.trim(), hindi.trim(), brand.trim(),
                generics.split(';').map(String::trim).filter(String::isNotEmpty),
                strengths.split(';').mapNotNull(Csv::strength), release.trim().ifEmpty { null },
                extras.split(';').map(String::trim).filter(String::isNotEmpty),
                if (camera) MedType.CAMERA else MedType.BUTTON,
                if (active) MedStatus.ACTIVE else MedStatus.STOPPED,
                if (active) null else existing?.stoppedOn ?: state.engine.local(state.now()).toLocalDate().toString(),
                times.split(';').map(String::trim).filter(String::isNotEmpty), note.trim(), existing?.noteHi.orEmpty(),
                existing?.colour?.ifEmpty { Csv.DEFAULT_COLOUR } ?: Csv.DEFAULT_COLOUR)
            val parsed = Csv.read(Csv.write(listOf(candidate)))
            val enteredStrengths = strengths.split(';').map(String::trim).filter(String::isNotEmpty)
            when {
                enteredStrengths.any { Csv.strength(it) == null } -> errors = listOf(Labels.get("medicineStrengths", lang))
                state.data.medicines.any { it.id != existing?.id && it.nickEn.equals(name.trim(), true) } -> errors = listOf(Labels.get("duplicateMedicineName", lang))
                parsed is Csv.ReadResult.Bad -> errors = parsed.errors
                parsed is Csv.ReadResult.Ok -> {
                    val saved = parsed.medicines.single().copy(id = candidate.id, purpose = purpose, personId = owner)
                    val list = if (existing == null) state.store.data.value.medicines + saved
                        else state.store.data.value.medicines.map { if (it.id == existing.id) saved else it }
                    persist(list)
                }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(Labels.get("save", lang)) }
        OutlinedButton(onClick = onDone, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(Labels.get("cancel", lang)) }
        if (existing != null) TextButton(onClick = { remove = true }, enabled = !busy) { Text(Labels.get("removeMedicine", lang)) }
    }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, text = { Text(Labels.get("removeMedicineNote", lang)) },
        confirmButton = { TextButton(onClick = { remove = false; existing?.let { state.notes.delete(it.id) }; persist(state.store.data.value.medicines.filterNot { it.id == existing?.id }) }) { Text(Labels.get("confirm", lang)) } },
        dismissButton = { TextButton(onClick = { remove = false }) { Text(Labels.get("cancel", lang)) } })
}

/** Record, hear and delete the family's own spoken message for one medicine. */
@Composable
private fun VoiceNoteRow(state: AppState, medId: String, enabled: Boolean) {
    val lang = state.language
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { app.stripsense.ml.Recorder() }
    var recording by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(state.notes.has(medId)) }
    var failed by remember { mutableStateOf(false) }
    fun start() {
        recording = true
        failed = false
        state.speaker.stop()
        state.notePlayer.stop()
        scope.launch {
            val wav = recorder.record()
            val ok = wav != null && withContext(Dispatchers.IO) { state.notes.save(medId, wav) }
            recording = false
            failed = !ok
            saved = state.notes.has(medId)
        }
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { if (it) start() }
    DisposableEffect(Unit) { onDispose { recorder.stop(); state.notePlayer.stop() } }
    Text(Labels.get("voiceNoteTitle", lang))
    Text(Labels.get(if (saved) "voiceNoteSaved" else "voiceNoteHint", lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (failed) Text(Labels.get("voiceNoteFailed", lang), color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            if (recording) recorder.stop()
            else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) start()
            else permission.launch(android.Manifest.permission.RECORD_AUDIO)
        }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(Labels.get(if (recording) "voiceNoteStop" else "voiceNoteRecord", lang)) }
        if (saved && !recording) {
            OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { state.notes.load(medId) }?.let { state.notePlayer.play(it) } } },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(Labels.get("voiceNotePlay", lang)) }
            OutlinedButton(onClick = { state.notePlayer.stop(); state.notes.delete(medId); saved = false },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(Labels.get("voiceNoteDelete", lang)) }
        }
    }
    if (recording) LinearProgressIndicator(Modifier.fillMaxWidth())
}

