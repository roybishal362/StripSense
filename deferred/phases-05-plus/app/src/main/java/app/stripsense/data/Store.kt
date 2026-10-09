package app.stripsense.data

import app.stripsense.core.DoseEvent
import app.stripsense.core.Look
import app.stripsense.core.Medicine
import app.stripsense.core.Person
import app.stripsense.core.SecondReaderMode
import app.stripsense.core.StoreData
import app.stripsense.core.Trial
import app.stripsense.core.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Everything the phone remembers, in `<dir>/store.json` (spec 05 section 1).
 * Changes update the flow at once; the file is written on one background thread through a temp file,
 * so a burst of changes costs one write and the screen never waits for the disk.
 */
class Store(private val dir: File, private val fresh: () -> StoreData) {

    private val file = File(dir, "store.json")
    private val tmp = File(dir, "store.json.tmp")
    private val _data = MutableStateFlow(load())
    val data: StateFlow<StoreData> = _data

    /** Called after every change, on the thread that made it. AppState mirrors the data for Compose. */
    @Volatile
    var onChange: ((StoreData) -> Unit)? = null

    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "StripSense-store").apply { isDaemon = true } }
    private val queued = AtomicBoolean(false)

    val looksDir: File get() = File(dir, "looks").apply { mkdirs() }

    // ---- changes ----

    fun addEvent(event: DoseEvent) = change { it.copy(events = it.events + event) }
    fun addEvents(events: List<DoseEvent>) {
        if (events.isNotEmpty()) change { it.copy(events = it.events + events) }
    }
    fun setLanguage(language: String) = change { it.copy(language = language) }
    fun setPerson(person: Person) = change { it.copy(person = person) }
    fun setTuning(tuning: Tuning) = change { it.copy(tuning = tuning) }
    fun setClockOffset(offsetMs: Long) = change { it.copy(clockOffsetMs = offsetMs) }
    fun setLookCheck(on: Boolean) = change { it.copy(lookCheckOn = on) }
    fun setSecondReader(mode: SecondReaderMode) = change { it.copy(secondReader = mode) }
    fun setCheckSeen() = change { it.copy(checkSeen = true) }

    /**
     * Replaces the list. A medicine with the same id keeps its looks while its brand, generics,
     * strengths and release are unchanged; otherwise its looks (and their photos) are dropped.
     */
    fun setMedicines(list: List<Medicine>, savedAtMillis: Long) {
        val byId = list.associateBy { it.id }
        var dropped: List<Look> = emptyList()
        change { d ->
            val oldById = d.medicines.associateBy { it.id }
            val (keep, drop) = d.looks.partition { look ->
                val new = byId[look.medId]
                val old = oldById[look.medId]
                new != null && old != null && sameIdentity(old, new)
            }
            dropped = drop
            d.copy(medicines = list, looks = keep, listSavedAtMillis = savedAtMillis)
        }
        deletePhotos(dropped)
    }

    fun addLook(look: Look) = change { it.copy(looks = it.looks + look) }

    fun removeLooks(medId: String) {
        val gone = _data.value.looks.filter { it.medId == medId }
        change { d -> d.copy(looks = d.looks.filterNot { it.medId == medId }) }
        deletePhotos(gone)
    }

    fun addTrial(trial: Trial) = change { it.copy(trials = it.trials + trial) }

    fun setTrialSecondReader(id: String, outcome: String) = change { d ->
        d.copy(trials = d.trials.map { if (it.id == id) it.copy(secondReader = outcome) else it })
    }

    fun clearTrials() = change { it.copy(trials = emptyList()) }

    /** "Reset everything": the first data again, and no taught photos. */
    fun reset() {
        val gone = _data.value.looks
        change { fresh() }
        deletePhotos(gone)
    }

    // ---- file ----

    /** Waits until every change so far is on disk. */
    fun flush() {
        runCatching { writer.submit { }.get(5, TimeUnit.SECONDS) }
    }

    fun close() {
        flush()
        writer.shutdown()
    }

    private fun change(f: (StoreData) -> StoreData) {
        _data.update(f)
        onChange?.invoke(_data.value)
        if (queued.compareAndSet(false, true)) {
            writer.execute {
                queued.set(false)
                write(_data.value)
            }
        }
    }

    private fun write(d: StoreData) {
        runCatching {
            dir.mkdirs()
            tmp.writeText(JSON.encodeToString(StoreData.serializer(), d))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { Logs.w("store write failed: ${it.javaClass.simpleName}") }
    }

    private fun load(): StoreData {
        if (!file.exists()) return fresh()
        return runCatching { JSON.decodeFromString(StoreData.serializer(), file.readText()) }
            .getOrElse {
                Logs.w("store unreadable, starting fresh: ${it.javaClass.simpleName}")
                fresh()
            }
    }

    private fun deletePhotos(looks: List<Look>) {
        for (l in looks) if (l.photoFile.isNotEmpty()) runCatching { File(looksDir, File(l.photoFile).name).delete() }
    }

    private fun sameIdentity(a: Medicine, b: Medicine) =
        a.brand == b.brand && a.generics == b.generics && a.strengths == b.strengths && a.release == b.release

    companion object {
        val JSON = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
