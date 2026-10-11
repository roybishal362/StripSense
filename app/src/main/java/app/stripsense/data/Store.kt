package app.stripsense.data

import app.stripsense.core.domain.*

import app.stripsense.core.domain.DoseEvent
import app.stripsense.core.domain.Look
import app.stripsense.core.domain.Medicine
import app.stripsense.core.domain.Person
import app.stripsense.core.domain.SecondReaderMode
import app.stripsense.core.domain.StoreData
import app.stripsense.core.domain.Trial
import app.stripsense.core.domain.Tuning
import app.stripsense.core.domain.UserDataMigration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
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
class Store(private val dir: File, val cipher: StoreCipher?, private val fresh: () -> StoreData) {

    /** A store that keeps plain files: the unit tests, and the isolated folders of the device checks. */
    constructor(dir: File, fresh: () -> StoreData) : this(dir, null, fresh)

    private val file = File(dir, "store.json")
    private val tmp = File(dir, "store.json.tmp")
    private val mutationLock = Any()
    private val _writeFailed = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeFailed
    private val _unreadable = MutableStateFlow(false)

    /** The saved file exists but could not be read. It is never written over until the family decides. */
    val unreadable: StateFlow<Boolean> = _unreadable
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
    fun saveReminderConfirmation(medId: String, slot: String, now: Long, id: String,
        kind: app.stripsense.core.domain.EventKind = app.stripsense.core.domain.EventKind.TAKEN): Boolean = runCatching {
        writer.submit<Boolean> {
            synchronized(mutationLock) {
                val before = _data.value
                val engine = app.stripsense.core.dose.DoseEngine()
                if (engine.markOf(medId, slot, before.events) != null) return@synchronized true
                // A second "not sure" for the same dose is the same answer, not a failure.
                if (kind == app.stripsense.core.domain.EventKind.NOT_SURE &&
                    engine.slotState(medId, slot, before.events) == app.stripsense.core.dose.SlotState.NOT_SURE) return@synchronized true
                val event = app.stripsense.core.dose.ReminderPlan.confirmation(before, medId, slot, now, id, engine, kind) ?: return@synchronized false
                val saved = before.copy(events = before.events + event)
                write(saved)
                if (_writeFailed.value) false else {
                    _data.value = saved
                    runCatching { onChange?.invoke(saved) }
                    true
                }
            }
        }.get()
    }.getOrDefault(false)
    fun addEvents(events: List<DoseEvent>) {
        if (events.isNotEmpty()) change { it.copy(events = it.events + events) }
    }
    /** The example week is put in fresh each time, so filling twice never doubles it. */
    fun fillExampleWeek(made: List<DoseEvent>) = change { it.copy(events = it.events.filterNot { e -> e.example } + made) }
    fun removeExampleWeek() = change { it.copy(events = it.events.filterNot { e -> e.example }) }
    fun setLanguage(language: String) = change { it.copy(language = language) }
    fun setPerson(person: Person) = change { it.copy(person = person) }
    fun setTuning(tuning: Tuning) = change { it.copy(tuning = tuning) }
    fun setClockOffset(offsetMs: Long) = change { it.copy(clockOffsetMs = offsetMs) }
    fun addPerson(profile: Profile) = change { it.copy(people = it.people.filterNot { p -> p.id == profile.id } + profile) }
    fun setActivePerson(id: String) = change { it.copy(activePerson = id) }
    fun setFamilyLink(url: String, code: String, on: Boolean) = change { it.copy(linkUrl = url.trim(), linkCode = code.trim(), linkOn = on) }
    fun setLookCheck(on: Boolean) = change { it.copy(lookCheckOn = on) }
    fun setTopMotor(id: Int) = change { it.copy(topMotor = id) }
    fun setTouchOnly(on: Boolean) = change { it.copy(touchOnly = on) }
    fun setFeelBest(row: Int) = change { if (row > it.feelBest) it.copy(feelBest = row) else it }
    /** Switching it off also forgets how loud a tablet sounded, so switching it on again starts from the beginning. */
    fun setHearTablet(on: Boolean) = change { it.copy(hearTablet = on, tabletSound = if (on) it.tabletSound else 0f) }

    /** One more tablet was heard coming out of its strip: what that sounds like on this phone is followed. */
    fun learnTabletSound(level: Float) = change { it.copy(tabletSound = app.stripsense.core.hear.PopWatch.learn(it.tabletSound, level)) }
    fun setSecondReader(mode: SecondReaderMode) = change { it.copy(secondReader = mode) }
    fun setCheckSeen() = change { it.copy(checkSeen = true) }

    /** First-run setup is done only once its answers are on disk. Call off the UI thread. */
    fun completeSetup(person: Person): Boolean = runCatching {
        writer.submit<Boolean> {
            synchronized(mutationLock) {
                val saved = _data.value.copy(person = person, checkSeen = true)
                write(saved)
                if (_writeFailed.value) false else {
                    _data.value = saved
                    runCatching { onChange?.invoke(saved) }
                    true
                }
            }
        }.get()
    }.getOrDefault(false)

    /** Reads the saved file again, for a failure that may have passed. Call off the UI thread. */
    fun retryLoad(): Boolean = runCatching {
        writer.submit<Boolean> {
            synchronized(mutationLock) {
                if (!_unreadable.value) return@synchronized true
                val loaded = load()
                if (_unreadable.value) false else {
                    _writeFailed.value = false
                    _data.value = loaded
                    runCatching { onChange?.invoke(loaded) }
                    true
                }
            }
        }.get()
    }.getOrDefault(false)

    /** The family chose to begin again. The unreadable file is moved aside, never deleted. Call off the UI thread. */
    fun startEmpty(now: Long): Boolean = runCatching {
        writer.submit<Boolean> {
            synchronized(mutationLock) {
                if (!_unreadable.value) return@synchronized true
                val kept = File(dir, "store-unreadable-$now.json")
                if (runCatching { Files.move(file.toPath(), kept.toPath(), StandardCopyOption.ATOMIC_MOVE) }.isFailure) return@synchronized false
                _unreadable.value = false
                val empty = fresh()
                write(empty)
                _data.value = empty
                runCatching { onChange?.invoke(empty) }
                !_writeFailed.value
            }
        }.get()
    }.getOrDefault(false)
    fun prepareRealUse() = change { UserDataMigration.clean(it, DemoData.medicines) }

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

    fun addLook(look: Look) = change { d ->
        if (d.looks.any { it.id == look.id } || d.medicines.none { it.id == look.medId } || d.looks.count { it.medId == look.medId } >= 8) d
        else d.copy(looks = d.looks + look)
    }

    /** Only report teaching success after its reference reached the store file. Call off the UI thread. */
    fun saveLook(look: Look): Boolean {
        return runCatching {
            writer.submit<Boolean> {
                synchronized(mutationLock) {
                    val before = _data.value
                    if (before.medicines.none { it.id == look.medId } || before.looks.any { it.id == look.id } ||
                        before.looks.count { it.medId == look.medId } >= 8) return@synchronized false
                    val saved = before.copy(looks = before.looks + look)
                    write(saved)
                    if (_writeFailed.value) false else {
                        _data.value = saved
                        onChange?.invoke(saved)
                        true
                    }
                }
            }.get()
        }.getOrDefault(false)
    }

    fun awaitSaved(): Boolean = runCatching {
        writer.submit<Boolean> { write(_data.value); !_writeFailed.value }.get()
    }.getOrDefault(false)

    /** One durable enrollment: the reviewed label and all captured views appear together. */
    fun saveEnrollment(med: Medicine, views: List<Look>): Boolean = runCatching {
        writer.submit<Boolean> {
            synchronized(mutationLock) {
                val before = _data.value
                val old = before.medicines.firstOrNull { it.id == med.id }
                val keep = before.looks.filter { it.medId != med.id || (old != null && sameIdentity(old, med)) }
                val drop = before.looks - keep.toSet()
                if (views.isEmpty() || views.any { it.medId != med.id || it.id in before.looks.map { l -> l.id } } ||
                    views.map { it.id }.distinct().size != views.size || keep.count { it.medId == med.id } + views.size > 8)
                    return@synchronized false
                val medicines = if (old == null) before.medicines + med else before.medicines.map { if (it.id == med.id) med else it }
                val saved = before.copy(medicines = medicines, looks = keep + views, listSavedAtMillis = views.maxOf { it.taughtAtMillis })
                write(saved)
                if (_writeFailed.value) false else {
                    _data.value = saved
                    runCatching { onChange?.invoke(saved) }
                    deletePhotos(drop)
                    true
                }
            }
        }.get()
    }.getOrDefault(false)

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
        synchronized(mutationLock) {
            _data.value = f(_data.value)
            onChange?.invoke(_data.value)
            if (queued.compareAndSet(false, true)) {
                writer.execute {
                    queued.set(false)
                    write(_data.value)
                }
            }
        }
    }

    private fun write(d: StoreData) {
        // Writing now would replace the family's real records with an empty list.
        if (_unreadable.value) { _writeFailed.value = true; return }
        runCatching {
            dir.mkdirs()
            val plain = JSON.encodeToString(StoreData.serializer(), d).toByteArray(Charsets.UTF_8)
            // Sealed and opened again in memory first: the old file is replaced only by bytes known to open.
            val bytes = if (cipher != null) Sealed.seal(cipher, plain) else plain
            FileOutputStream(tmp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onSuccess { _writeFailed.value = false }
            .onFailure { _writeFailed.value = true; Logs.w("store write failed: ${it.javaClass.simpleName}") }
    }

    private fun load(): StoreData {
        if (!file.exists()) { _unreadable.value = false; return fresh() }
        // A sealed file that will not open (another phone's key, or changed bytes) is unreadable, and so is kept.
        // A plain file from an older build is read once; the next write seals it.
        return runCatching { read(file, cipher) }
            .onSuccess { _unreadable.value = false }
            .getOrElse {
                Logs.w("store unreadable, kept on disk: ${it.javaClass.simpleName}")
                _unreadable.value = true
                fresh()
            }
    }

    private fun deletePhotos(looks: List<Look>) {
        if (looks.isEmpty()) return
        writer.execute {
            write(_data.value)
            if (!_writeFailed.value) for (l in looks) {
                if (l.photoFile.isNotEmpty() && _data.value.looks.none { it.photoFile == l.photoFile })
                    runCatching { File(looksDir, File(l.photoFile).name).delete() }
            }
        }
    }

    private fun sameIdentity(a: Medicine, b: Medicine) =
        a.brand == b.brand && a.generics == b.generics && a.strengths == b.strengths && a.release == b.release && a.extraWords == b.extraWords

    companion object {
        val JSON = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }

        /** The saved records of [file], sealed or plain. Throws when they cannot be opened or read. */
        fun read(file: File, cipher: StoreCipher?): StoreData {
            val bytes = file.readBytes()
            val plain = if (Sealed.isSealed(bytes)) Sealed.open(checkNotNull(cipher) { "sealed records, no key" }, bytes) else bytes
            return JSON.decodeFromString(StoreData.serializer(), plain.toString(Charsets.UTF_8))
        }
    }
}
