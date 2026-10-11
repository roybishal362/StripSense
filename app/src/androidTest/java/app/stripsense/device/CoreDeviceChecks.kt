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

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import app.stripsense.data.*
import app.stripsense.ml.LookEmbedder
import app.stripsense.ml.ReferenceFingerprint
import app.stripsense.ml.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

/** Native-library checks run in an isolated cache store, never the user's medicine list. */
class CoreDeviceChecks : Instrumentation() {
    private var checkArgs = Bundle()
    override fun onCreate(arguments: Bundle?) { checkArgs = arguments ?: Bundle(); super.onCreate(arguments); start() }
    override fun onStart() {
        if (checkArgs.getString("liveOnly") == "true") {
            try { finish(Activity.RESULT_OK, Bundle().apply { putString("stream", LiveDeviceChecks.run(targetContext)) }) }
            catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL live: ${e.javaClass.simpleName}: ${e.message}\n") }) }
            return
        }
        // For looking at the family's side while it is being drawn: opens it on the tab named, past the PIN, and holds it
        // open for a while. Only this test harness can do that; the app itself has no way round the PIN.
        checkArgs.getString("showFamily")?.let { tab ->
            try {
                startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                runOnMainSync {
                    val state = app.stripsense.AppGraph.state(targetContext.applicationContext)
                    state.familyUnlocked = true
                    state.familyTab = app.stripsense.FamilyTab.valueOf(tab)
                    state.go(app.stripsense.Screen.FAMILY)
                }
                Thread.sleep((checkArgs.getString("seconds")?.toLongOrNull() ?: 20L) * 1000)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "shown " + tab + "\n") })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL show: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // Which microphone setting hears the highest sounds on this phone: the loudness in ten bands, for each setting.
        // Numbers only.
        if (checkArgs.getString("hearBands") == "true") {
            try {
                startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(1500)
                val rate = 48000
                val edges = intArrayOf(200, 1000, 2000, 4000, 6000, 8000, 10000, 12000, 16000, 20000, 24000)
                val out = StringBuilder("bands Hz: " + edges.joinToString(" ") + "\n")
                val names = listOf(6 to "VOICE_RECOGNITION", 9 to "UNPROCESSED", 1 to "MIC", 5 to "CAMCORDER")
                for ((source, name) in names) {
                    val min = android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
                    val rec = runCatching { android.media.AudioRecord(source, rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, rate / 5 * 2)) }.getOrNull()
                    if (rec == null || rec.state != android.media.AudioRecord.STATE_INITIALIZED) { out.append(name).append(": not offered\n"); rec?.release(); continue }
                    rec.startRecording()
                    val n = 1024
                    val frame = ShortArray(n)
                    val sums = DoubleArray(edges.size - 1)
                    var frames = 0
                    var peak = 0
                    // The first half second is thrown away: the microphone is still settling.
                    val skip = ShortArray(rate / 2); var skipped = 0
                    while (skipped < skip.size) { val r = rec.read(skip, 0, skip.size - skipped); if (r <= 0) break; skipped += r }
                    val re = DoubleArray(n); val im = DoubleArray(n)
                    while (frames < 140) {
                        var got = 0
                        while (got < n) { val r = rec.read(frame, got, n - got); if (r <= 0) break; got += r }
                        if (got < n) break
                        for (i in 0 until n) { peak = maxOf(peak, kotlin.math.abs(frame[i].toInt())); re[i] = frame[i] * (0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * i / (n - 1))); im[i] = 0.0 }
                        // A plain transform, slow and simple: this runs only in this check.
                        var j = 0
                        for (i in 1 until n) { var bit = n shr 1; while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }; j = j xor bit
                            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t } }
                        var len = 2
                        while (len <= n) {
                            val ang = -2 * Math.PI / len
                            var i = 0
                            while (i < n) {
                                for (k in 0 until len / 2) {
                                    val c = kotlin.math.cos(ang * k); val sn = kotlin.math.sin(ang * k)
                                    val a = i + k; val b = a + len / 2
                                    val tr = re[b] * c - im[b] * sn; val ti = re[b] * sn + im[b] * c
                                    re[b] = re[a] - tr; im[b] = im[a] - ti; re[a] += tr; im[a] += ti
                                }
                                i += len
                            }
                            len = len shl 1
                        }
                        for (band in 0 until edges.size - 1) {
                            val from = edges[band] * n / rate; val to = minOf(n / 2, edges[band + 1] * n / rate)
                            var sum = 0.0
                            for (bin in from until to) sum += re[bin] * re[bin] + im[bin] * im[bin]
                            sums[band] += sum
                        }
                        frames++
                    }
                    rec.stop(); rec.release()
                    out.append(name).append(": peak ").append(peak).append(", bands dB ").append(sums.joinToString(" ") { (10 * kotlin.math.log10(it / maxOf(1, frames) + 1.0)).toInt().toString() }).append("\n")
                }
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", out.toString()) })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL bands: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // Do the phone's own motors sound like a tablet to its microphone? Each message is played while the listener
        // runs with nothing held back, and the clicks and "tablets" it would have counted are reported. Numbers only.
        if (checkArgs.getString("micAndMotors") == "true") {
            try {
                startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(1500)
                val rate = app.stripsense.core.hear.ClickFinder.RATE
                val min = android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
                val rec = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, rate / 5 * 2))
                val language = app.stripsense.core.guide.FeelLanguage
                val plays = listOf("nothing" to null, "question" to language.question(), "marked" to language.done(), "yes" to language.yes(), "no" to language.no(),
                    "heartbeat" to language.signature(app.stripsense.core.teach.PurposeKind.BP), "tick" to language.nearer(1f))
                val out = StringBuilder()
                rec.startRecording()
                val buffer = ShortArray(rate / 100)
                for ((name, felt) in plays) {
                    val finder = app.stripsense.core.hear.ClickFinder(rate)
                    val open = app.stripsense.core.hear.PopWatch(0f)
                    val careful = app.stripsense.core.hear.PopWatch(0f)
                    var samples = 0L; var clicks = 0; var tablets = 0; var kept = 0; var strongest = 0f
                    val end = android.os.SystemClock.elapsedRealtime() + 4200
                    var played = 0
                    while (android.os.SystemClock.elapsedRealtime() < end) {
                        // Three times in the four seconds, after a second of quiet for the room to be learned.
                        if (felt != null && played < 3 && android.os.SystemClock.elapsedRealtime() > end - 3200 + played * 1000L) { app.stripsense.ui.shell.Feel.play(targetContext, felt); played++ }
                        val n = rec.read(buffer, 0, buffer.size)
                        if (n <= 0) continue
                        val at = samples * 1000 / rate
                        samples += n
                        if (app.stripsense.ui.shell.Feel.moving()) careful.deaf(at - 150, at + 350)
                        for (click in finder.feed(buffer, n) { from, until -> open.deaf(from, until); careful.deaf(from, until) }) { open.click(click); careful.click(click); clicks++; strongest = maxOf(strongest, click.above) }
                        if (open.poll(samples * 1000 / rate) != null) tablets++
                        if (careful.poll(samples * 1000 / rate) != null) kept++
                    }
                    out.append(name).append(": clicks ").append(clicks).append(", strongest ").append(strongest.toInt()).append(" over the room ").append(finder.floorDb.toInt())
                        .append(", counted as a tablet ").append(tablets).append(" times, with the motors left out ").append(kept).append("\n")
                }
                rec.stop(); rec.release()
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", out.toString()) })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL mic and motors: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // A real alarm, for watching it open by itself on a locked, sleeping phone. "arm" gives one medicine one more
        // dose time a minute or two ahead, as the Alarms tab's Add does, and ends, so the app is not running when the
        // alarm comes. "disarm" takes that time out again, takes off any mark made for it, and puts "Touch only" back.
        checkArgs.getString("alarmRehearsal")?.let { what ->
            try {
                val store = AppStore.get(targetContext)
                val note = File(targetContext.cacheDir, "alarm-rehearsal.txt")
                if (what == "arm") {
                    val data = store.data.value
                    val med = data.medicines.first { it.id == checkArgs.getString("med") }
                    val at = java.time.LocalDateTime.now().plusMinutes(checkArgs.getString("minutes")?.toLongOrNull() ?: 2).withSecond(0).withNano(0)
                    val time = at.toLocalTime().toString().take(5)
                    note.writeText(med.id + "|" + time + "|" + at.toLocalDate() + "|" + data.touchOnly + "|" + med.times.joinToString(","))
                    if (checkArgs.getString("touchOnly") == "true") store.setTouchOnly(true)
                    store.setMedicines(store.data.value.medicines.map { if (it.id == med.id) it.copy(times = (it.times + time).distinct().sorted()) else it }, data.listSavedAtMillis)
                    store.awaitSaved()
                    app.stripsense.reminders.ReminderAlarms.reschedule(targetContext)
                    finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "armed for " + time + ", touch only " + store.data.value.touchOnly + "\n") })
                } else {
                    val parts = note.readText().split("|")
                    val data = store.data.value
                    val key = parts[2] + "T" + parts[1]
                    val made = data.events.filter { it.medId == parts[0] && it.slotKey == key }
                    val engine = DoseEngine()
                    for (event in made) if (event.kind == EventKind.TAKEN && !engine.isUndone(event, store.data.value.events))
                        store.addEvent(DoseEvent(java.util.UUID.randomUUID().toString(), event.medId, event.slotKey, System.currentTimeMillis(), EventKind.UNDONE, event.source, undoes = event.id))
                    store.setMedicines(store.data.value.medicines.map { if (it.id == parts[0]) it.copy(times = parts[4].split(",").filter { t -> t.isNotBlank() }) else it }, data.listSavedAtMillis)
                    if (store.data.value.touchOnly != (parts[3] == "true")) store.setTouchOnly(parts[3] == "true")
                    store.awaitSaved()
                    app.stripsense.reminders.ReminderNotifications.cancel(targetContext, parts[0], key)
                    app.stripsense.reminders.ReminderAlarms.reschedule(targetContext)
                    note.delete()
                    finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "disarmed " + parts[1] + ": records made for it " + made.joinToString { it.kind.toString() + " by " + it.source } + "; times back to " + parts[4] + "; touch only " + store.data.value.touchOnly + "\n") })
                }
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL alarm rehearsal: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // The whole dose as it will be shown, in "Touch only", with a real alarm and with the hands' parts pretended:
        // the alarm comes by itself, the round starts as if the phone had been picked up, the strip is matched as if the
        // camera had read it, the question is felt, and it is answered as if by two knocks. Each step is reported.
        // Everything it changes is put back at the end.
        checkArgs.getString("demoRehearsal")?.let { medId ->
            val out = StringBuilder()
            try {
                startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(1500)
                val state = app.stripsense.AppGraph.state(targetContext.applicationContext)
                val store = state.store
                val before = store.data.value
                val med = before.medicines.first { it.id == medId }
                val at = java.time.LocalDateTime.now().plusMinutes(1).withSecond(0).withNano(0).let { if (java.time.Duration.between(java.time.LocalDateTime.now(), it).seconds < 25) it.plusMinutes(1) else it }
                val time = at.toLocalTime().toString().take(5)
                val key = at.toLocalDate().toString() + "T" + time
                store.setTouchOnly(checkArgs.getString("sound") != "true")
                store.setMedicines(store.data.value.medicines.map { if (it.id == med.id) it.copy(times = (it.times + time).distinct().sorted()) else it }, before.listSavedAtMillis)
                store.awaitSaved()
                app.stripsense.reminders.ReminderAlarms.reschedule(targetContext)
                out.append("1 armed for ").append(time).append(", touch only ").append(store.data.value.touchOnly).append("\n")
                // The app goes to the back, as it would be when an alarm comes.
                runOnMainSync { state.go(app.stripsense.Screen.TODAY) }
                val wait = android.os.SystemClock.elapsedRealtime() + 150_000
                while (state.reminderRequest == null && android.os.SystemClock.elapsedRealtime() < wait) Thread.sleep(250)
                val came = state.reminderRequest
                out.append("2 alarm came by itself: ").append(came != null).append(came?.let { ", for the armed dose " + (it.first == med.id && it.second == key) } ?: "")
                    .append(", at ").append(java.time.LocalTime.now().withNano(0)).append("\n")
                if (came != null) {
                    Thread.sleep(7000)
                    // As if the phone had been picked up from the table.
                    runOnMainSync { state.startRound(listOf(came)) }
                    Thread.sleep(2500)
                    out.append("3 round started, camera open: ").append(state.screen == app.stripsense.Screen.SCAN || state.screen == app.stripsense.Screen.MULTI_STRIP).append(", looking for the dose ").append(state.scanDoseRequest == came).append("\n")
                    // As if the camera had read and checked the strip.
                    runOnMainSync { state.finishScan(Verdict.Match(store.data.value.medicines.first { it.id == med.id }), null, emptyList(), -1L, state.scanId, printChecked = true) }
                    Thread.sleep(10500)
                    var asking = false
                    runOnMainSync { asking = state.outcome?.let { state.answerFlowFor(it).phase == app.stripsense.ui.scan.AnswerPhase.ASKING } == true }
                    out.append("4 answer open and asking: ").append(state.screen == app.stripsense.Screen.ANSWER && asking).append("\n")
                    // As if the phone had been knocked twice.
                    var answered = false
                    runOnMainSync { answered = state.answerYesByHand() }
                    Thread.sleep(1200)
                    val mark = store.data.value.events.lastOrNull { it.medId == med.id && it.slotKey == key }
                    out.append("5 answered by hand: ").append(answered).append(", record ").append(mark?.let { it.kind.toString() + " by " + it.source } ?: "none").append("\n")
                    Thread.sleep(6500)
                    out.append("6 round over, back on Today: ").append(state.round == null && state.screen == app.stripsense.Screen.TODAY).append("\n")
                    val done = java.util.concurrent.CountDownLatch(1)
                    var sent = false
                    FamilyLink.maybeSend(targetContext, store.data.value, force = true) { sent = it; done.countDown() }
                    done.await(15, TimeUnit.SECONDS)
                    out.append("7 sent to the family: ").append(sent).append(" (").append(FamilyLink.last).append(")\n")
                    Thread.sleep((checkArgs.getString("hold")?.toLongOrNull() ?: 4L) * 1000)
                }
                // Everything back as it was.
                for (event in store.data.value.events.filter { it.medId == med.id && it.slotKey == key })
                    if (event.kind == EventKind.TAKEN && !state.engine.isUndone(event, store.data.value.events))
                        store.addEvent(DoseEvent(state.newId(), event.medId, event.slotKey, System.currentTimeMillis(), EventKind.UNDONE, event.source, undoes = event.id))
                store.setMedicines(store.data.value.medicines.map { if (it.id == med.id) it.copy(times = before.medicines.first { m -> m.id == med.id }.times) else it }, before.listSavedAtMillis)
                store.setTouchOnly(before.touchOnly)
                store.awaitSaved()
                app.stripsense.reminders.ReminderNotifications.cancel(targetContext, med.id, key)
                app.stripsense.reminders.ReminderAlarms.reschedule(targetContext)
                runOnMainSync { state.reminderRequest = null; state.go(app.stripsense.Screen.TODAY) }
                out.append("8 put back: times ").append(store.data.value.medicines.first { it.id == med.id }.times.joinToString(",")).append(", touch only ").append(store.data.value.touchOnly).append("\n")
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", out.toString()) })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", out.toString() + "FAIL demo rehearsal: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // The dose as it will be shown on stage, by the presenter's own way in: the app's clock is moved so a dose is due,
        // its alarm is rung as the presenter's button rings it, and the hands' parts are pretended as in demoRehearsal.
        // "answer knock" (the default) answers as two knocks do; "answer sound" leaves the question open for a tablet to
        // be heard; "answer none" lets it run out. "sound true" keeps the voice on. Everything is put back at the end.
        checkArgs.getString("stageRehearsal")?.let { medId ->
            val out = StringBuilder()
            try {
                startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(1500)
                val state = app.stripsense.AppGraph.state(targetContext.applicationContext)
                val store = state.store
                val before = store.data.value
                val med = before.medicines.first { it.id == medId }
                val engine = state.engine
                // A dose of the real day that nobody has marked, if there is one; otherwise tomorrow's first.
                val real = System.currentTimeMillis()
                val today = engine.slotsFor(med, engine.dosingDate(real)).firstOrNull { engine.slotState(med.id, it.key, before.events) == SlotState.UNMARKED }
                val slot = today ?: engine.slotsFor(med, engine.dosingDate(real).plusDays(1)).first()
                store.setClockOffset(engine.slotMillis(slot) + 60_000 - real)
                store.setTouchOnly(checkArgs.getString("sound") != "true")
                Thread.sleep(1200)
                out.append("1 dose due by the app's clock: ").append(slot.key.takeLast(5)).append(if (today != null) " of the real day" else " of tomorrow").append(", touch only ").append(store.data.value.touchOnly).append("\n")
                runOnMainSync { state.go(app.stripsense.Screen.TODAY); state.reminderRequest = med.id to slot.key }
                Thread.sleep(7000)
                out.append("2 alarm screen up: ").append(state.reminderRequest != null).append("\n")
                runOnMainSync { state.startRound(listOf(med.id to slot.key)) }
                Thread.sleep(2500)
                out.append("3 round started, camera open: ").append(state.screen == app.stripsense.Screen.SCAN || state.screen == app.stripsense.Screen.MULTI_STRIP).append(", alarm screen gone ").append(state.reminderRequest == null).append("\n")
                runOnMainSync { state.finishScan(Verdict.Match(store.data.value.medicines.first { it.id == med.id }), null, emptyList(), -1L, state.scanId, printChecked = true) }
                val speaks = checkArgs.getString("sound") == "true"
                Thread.sleep(if (speaks) 19000 else 10500)
                var asking = false
                runOnMainSync { asking = state.outcome?.let { state.answerFlowFor(it).phase == app.stripsense.ui.scan.AnswerPhase.ASKING } == true }
                out.append("4 answer open and asking: ").append(state.screen == app.stripsense.Screen.ANSWER && asking).append("\n")
                val way = checkArgs.getString("answer") ?: "knock"
                if (way == "knock") {
                    var answered = false
                    runOnMainSync { answered = state.answerYesByHand() }
                    out.append("5 answered as two knocks do: ").append(answered).append("\n")
                    Thread.sleep(1200)
                } else {
                    // The question stays open; a tablet may be heard meanwhile.
                    Thread.sleep((checkArgs.getString("seconds")?.toLongOrNull() ?: 25L) * 1000)
                    out.append("5 left open for ").append(way).append("\n")
                }
                val mark = store.data.value.events.lastOrNull { it.medId == med.id && it.slotKey == slot.key && it.id !in before.events.map { e -> e.id } }
                out.append("6 record: ").append(mark?.let { it.kind.toString() + " by " + it.source } ?: "none").append("\n")
                Thread.sleep(if (speaks) 9000 else 6500)
                out.append("7 round over, back on Today: ").append(state.round == null && state.screen == app.stripsense.Screen.TODAY).append(" (screen ").append(state.screen).append(")\n")
                val done = java.util.concurrent.CountDownLatch(1)
                var sent = false
                FamilyLink.maybeSend(targetContext, store.data.value, force = true) { sent = it; done.countDown() }
                done.await(15, TimeUnit.SECONDS)
                out.append("8 sent to the family: ").append(sent).append(" (").append(FamilyLink.last).append(")\n")
                Thread.sleep((checkArgs.getString("hold")?.toLongOrNull() ?: 5L) * 1000)
                val known = before.events.mapTo(HashSet()) { it.id }
                for (event in store.data.value.events.filter { it.id !in known })
                    if (event.kind == EventKind.TAKEN && !engine.isUndone(event, store.data.value.events))
                        store.addEvent(DoseEvent(state.newId(), event.medId, event.slotKey, state.now(), EventKind.UNDONE, event.source, undoes = event.id))
                if (before.tabletSound == 0f && store.data.value.tabletSound != 0f) { store.setHearTablet(false); store.setHearTablet(true) }
                store.setClockOffset(before.clockOffsetMs)
                store.setTouchOnly(before.touchOnly)
                store.awaitSaved()
                runOnMainSync { state.reminderRequest = null; state.go(app.stripsense.Screen.TODAY) }
                val after = store.data.value
                out.append("9 put back: clock moved by ").append(after.clockOffsetMs / 60000).append(" min, touch only ").append(after.touchOnly).append(", example records ").append(after.events.count { it.example }).append(" (were ").append(before.events.count { it.example }).append(")\n")
                done.await(1, TimeUnit.SECONDS)
                FamilyLink.maybeSend(targetContext, after, force = true)
                Thread.sleep(1500)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", out.toString()) })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", out.toString() + "FAIL stage rehearsal: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // How the app stands on this phone, for a last look before it is shown: switches and counts only.
        // No name, no medicine name, no code and nothing a person said.
        if (checkArgs.getString("stateSummary") == "true") {
            try {
                startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(2500)
                val state = app.stripsense.AppGraph.state(targetContext.applicationContext)
                val data = state.store.data.value
                val people = People.all(data)
                val out = StringBuilder()
                out.append("people ").append(people.size).append(", medicines ").append(data.medicines.size).append(" (active ").append(data.medicines.count { it.status == MedStatus.ACTIVE }).append(")\n")
                out.append("medicines with a confirmed purpose ").append(data.medicines.count { app.stripsense.core.teach.Purposes.of(it) != null }).append(", with dose times ").append(data.medicines.count { it.times.isNotEmpty() }).append(", taught photos ").append(data.looks.size).append("\n")
                out.append("events: real ").append(data.events.count { !it.example }).append(", example ").append(data.events.count { it.example }).append("\n")
                out.append("language ").append(data.language).append(", app clock moved by ").append(data.clockOffsetMs / 60000).append(" min\n")
                out.append("hear the tablet ").append(data.hearTablet).append(" (microphone allowed ").append(state.canHearTablet()).append("), learned loudness ").append(data.tabletSound).append("\n")
                out.append("touch only ").append(data.touchOnly).append(", motors ").append(app.stripsense.ui.shell.Feel.motors(targetContext)).append(", top motor ").append(data.topMotor).append("\n")
                out.append("family link on ").append(data.linkOn).append(", relay address set ").append(data.linkUrl.isNotBlank()).append(", code set ").append(data.linkCode.isNotBlank()).append("\n")
                out.append("helper model ").append(state.gemma.state.value::class.simpleName).append(", Hindi voice ").append(state.speaker.hasHindiVoice()).append(", English voice ").append(state.speaker.hasEnglishVoice()).append("\n")
                out.append("camera allowed ").append(targetContext.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    .append(", notifications allowed ").append(targetContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    .append(", exact alarms ").append(app.stripsense.reminders.ReminderAlarms.exact(targetContext)).append("\n")
                val week = app.stripsense.core.report.WeekViews.build(People.medicines(data), data.events, state.now(), state.engine, app.stripsense.core.report.WeekViews.knownSince(data))
                out.append("first person's week: marked ").append(week.marked).append(", not marked ").append(week.notMarked).append(", not sure ").append(week.notSure).append(", share ").append(week.markedPercent).append("\n")
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", out.toString()) })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL state: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // The example week, put in or taken out as the presenter's button does it, and sent to the family's page at once.
        checkArgs.getString("exampleWeek")?.let { what ->
            try {
                startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(1500)
                val state = app.stripsense.AppGraph.state(targetContext.applicationContext)
                runOnMainSync { if (what == "fill") state.fillExampleWeek() else state.store.removeExampleWeek() }
                Thread.sleep(1500)
                val done = java.util.concurrent.CountDownLatch(1)
                FamilyLink.maybeSend(targetContext, state.store.data.value, force = true) { done.countDown() }
                done.await(15, TimeUnit.SECONDS)
                runOnMainSync { state.go(app.stripsense.Screen.HEALTH) }
                Thread.sleep((checkArgs.getString("seconds")?.toLongOrNull() ?: 6L) * 1000)
                val events = state.store.data.value.events
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "example events " + events.count { it.example } + ", real events " + events.count { !it.example } + ", link " + FamilyLink.last + "\n") })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL example week: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // For setting the tablet listener on this phone: the app is opened so the microphone is allowed, then the
        // listener runs for some seconds and reports numbers only. No sound is kept or printed.
        checkArgs.getString("hearSeconds")?.toLongOrNull()?.let { seconds ->
            try {
                startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(1500)
                val rate = app.stripsense.core.hear.ClickFinder.RATE
                val min = android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
                val rec = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, rate / 5 * 2))
                val finder = app.stripsense.core.hear.ClickFinder(rate)
                val watch = app.stripsense.core.hear.PopWatch(0f)
                val out = StringBuilder()
                // The same sound through other settings at the same time: click, rise and strong, in decibels.
                val others = listOf(Triple(18.0, 8.0, 24f), Triple(12.0, 6.0, 16f), Triple(14.0, 8.0, 16f)).map { (click, rise, strong) ->
                    Triple(app.stripsense.core.hear.ClickFinder(rate, click, rise), app.stripsense.core.hear.PopWatch(0f, strong), intArrayOf(0, 0)) to "$click/$rise/$strong"
                }
                val buffer = ShortArray(rate / 100)
                var samples = 0L
                var peak = 0
                var clicks = 0
                var dull = 0
                val floors = ArrayList<Int>()
                rec.startRecording()
                val end = android.os.SystemClock.elapsedRealtime() + seconds * 1000
                while (android.os.SystemClock.elapsedRealtime() < end) {
                    val n = rec.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    for (i in 0 until n) peak = maxOf(peak, kotlin.math.abs(buffer[i].toInt()))
                    val second = samples / rate
                    samples += n
                    for (click in finder.feed(buffer, n) { from, until -> watch.deaf(from, until); dull++ }) {
                        watch.click(click); clicks++
                        if (clicks <= 60) out.append("click ").append(click.atMs).append(" ms above ").append(click.above.toInt()).append(" level ").append(click.level.toInt()).append(" bright ").append(click.bright.toInt()).append("\n")
                    }
                    for ((set, _) in others) {
                        for (click in set.first.feed(buffer, n) { from, until -> set.second.deaf(from, until) }) { set.second.click(click); set.third[0]++ }
                        if (set.second.poll(samples * 1000 / rate) != null) set.third[1]++
                    }
                    watch.poll(samples * 1000 / rate)?.let { out.append("TABLET at ").append(it.atMs).append(" ms, clicks ").append(it.clicks).append(", above ").append(it.above.toInt()).append(", level ").append(it.level.toInt()).append("\n") }
                    if (samples / rate != second) floors += finder.floorDb.toInt()
                }
                rec.stop(); rec.release()
                for ((set, name) in others) out.append("with ").append(name).append(": clicks ").append(set.third[0]).append(", tablets ").append(set.third[1]).append("NL".replace("NL", "\n"))
                out.append("heard ").append(seconds).append(" s, loudest sample ").append(peak).append(", clicks ").append(clicks).append(", dull reports ").append(dull).append(", room each second ").append(floors.joinToString(" ")).append("\n")
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", out.toString()) })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL hear: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // Which kinds of vibration this phone lets through while the camera is open. With the camera on it was found
        // to throw away the app's longer vibrations. Each kind is played once on the scan screen; the phone's own
        // record (dumpsys vibrator_manager) then says which were played. A different length marks each kind.
        if (checkArgs.getString("feelWithCamera") == "true") {
            try {
                startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(1500)
                val state = app.stripsense.AppGraph.state(targetContext.applicationContext)
                runOnMainSync { state.go(app.stripsense.Screen.SCAN) }
                Thread.sleep(3500)
                val manager = targetContext.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                val ids = manager.vibratorIds
                val kinds = listOf(android.os.VibrationAttributes.USAGE_UNKNOWN to "UNKNOWN", android.os.VibrationAttributes.USAGE_TOUCH to "TOUCH",
                    android.os.VibrationAttributes.USAGE_ACCESSIBILITY to "ACCESSIBILITY", android.os.VibrationAttributes.USAGE_ALARM to "ALARM",
                    android.os.VibrationAttributes.USAGE_NOTIFICATION to "NOTIFICATION", android.os.VibrationAttributes.USAGE_HARDWARE_FEEDBACK to "HARDWARE_FEEDBACK",
                    android.os.VibrationAttributes.USAGE_COMMUNICATION_REQUEST to "COMMUNICATION_REQUEST", android.os.VibrationAttributes.USAGE_MEDIA to "MEDIA",
                    android.os.VibrationAttributes.USAGE_PHYSICAL_EMULATION to "PHYSICAL_EMULATION", android.os.VibrationAttributes.USAGE_RINGTONE to "RINGTONE")
                val out = StringBuilder("motors " + ids.joinToString() + "\n")
                kinds.forEachIndexed { i, (usage, name) ->
                    val ms = 300L + 10 * i
                    val effect = android.os.VibrationEffect.createWaveform(longArrayOf(ms), intArrayOf(255), -1)
                    val both = android.os.CombinedVibration.startParallel()
                    for (id in ids) both.addVibrator(id, effect)
                    manager.vibrate(both.combine(), android.os.VibrationAttributes.createForUsage(usage))
                    out.append(name).append(" asked for ").append(ms).append(" ms\n")
                    Thread.sleep(1100)
                }
                // Then the app's own messages, as the scan sends them, still with the camera open.
                val language = app.stripsense.core.guide.FeelLanguage
                for (felt in listOf(language.yes(), language.no(), language.signature(app.stripsense.core.teach.PurposeKind.BP),
                    language.pull(app.stripsense.core.guide.Toward.AWAY, 0.8f), language.pull(app.stripsense.core.guide.Toward.CLOSER, 0.8f), language.pull(app.stripsense.core.guide.Toward.RIGHT, 0.8f))) {
                    app.stripsense.ui.shell.Feel.play(targetContext, felt)
                    Thread.sleep(1500)
                }
                out.append("then yes, no, heartbeat, top end, bottom end, right\n")
                runOnMainSync { state.go(app.stripsense.Screen.TODAY) }
                Thread.sleep(500)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", out.toString()) })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL feel with camera: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // What the movement sensor feels: how often it reports, how still a resting phone is, and how hard the phone's
        // own motors shake it. For setting the two-knock answer. Numbers only.
        if (checkArgs.getString("knockNumbers") == "true") {
            try {
                startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(1500)
                val sensors = targetContext.getSystemService(android.hardware.SensorManager::class.java)
                val sensor = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
                val mean = FloatArray(3)
                var have = false
                val jolts = ArrayList<Pair<Long, Float>>()
                val knock = app.stripsense.core.guide.DoubleKnock()
                var pairs = 0
                val listener = object : android.hardware.SensorEventListener {
                    override fun onSensorChanged(event: android.hardware.SensorEvent) {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (!have) { mean[0] = event.values[0]; mean[1] = event.values[1]; mean[2] = event.values[2]; have = true }
                        val dx = event.values[0] - mean[0]; val dy = event.values[1] - mean[1]; val dz = event.values[2] - mean[2]
                        synchronized(jolts) { jolts += now to kotlin.math.sqrt(dx * dx + dy * dy + dz * dz) }
                        for (i in 0..2) mean[i] += (event.values[i] - mean[i]) * 0.06f
                        // Counted with the motors' own time left in, to see what would happen without that care.
                        if (knock.reading(event.values[0], event.values[1], event.values[2], now, own = false)) pairs++
                    }
                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
                }
                val thread = android.os.HandlerThread("knock-check").apply { start() }
                sensors.registerListener(listener, sensor, app.stripsense.ml.KnockListener.READING_MICROSECONDS, android.os.Handler(thread.looper))
                val t0 = android.os.SystemClock.elapsedRealtime()
                Thread.sleep(3000)
                val language = app.stripsense.core.guide.FeelLanguage
                val marks = ArrayList<Pair<String, Long>>()
                for ((name, felt) in listOf("question" to language.question(), "heartbeat" to language.signature(app.stripsense.core.teach.PurposeKind.BP), "yes" to language.yes(), "tick" to language.nearer(1f))) {
                    marks += name to android.os.SystemClock.elapsedRealtime()
                    app.stripsense.ui.shell.Feel.play(targetContext, felt)
                    Thread.sleep(2000)
                }
                sensors.unregisterListener(listener); thread.quitSafely()
                val all = synchronized(jolts) { jolts.toList() }
                fun part(from: Long, until: Long) = all.filter { it.first in from until until }.map { it.second }.sorted()
                val out = StringBuilder()
                val still = part(t0 + 500, t0 + 3000)
                out.append("readings a second ").append(all.size * 1000L / maxOf(1, all.last().first - all.first().first)).append("\n")
                out.append("lying still: most ").append(still.lastOrNull()).append(", 99 in 100 under ").append(still.getOrNull(still.size * 99 / 100)).append("\n")
                for ((name, at) in marks) { val p = part(at, at + 1800); out.append(name).append(": most ").append(p.lastOrNull()).append(", half under ").append(p.getOrNull(p.size / 2)).append("\n") }
                out.append("pairs of knocks counted with the motors left in: ").append(pairs).append("\n")
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", out.toString()) })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL knock numbers: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // The whole heard dose on the phone, without a camera: the app's own clock is moved so this medicine is due,
        // its answer is opened as if its strip had just been checked, and the question is left open for some seconds.
        // Afterwards any mark made is taken off again and the clock and the learned loudness are put back.
        checkArgs.getString("hearDose")?.let { medId ->
            try {
                startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Thread.sleep(1500)
                val state = app.stripsense.AppGraph.state(targetContext.applicationContext)
                val store = state.store
                val before = store.data.value
                val med = before.medicines.first { it.id == medId }
                // "touchOnly true": the same dose with the phone silent, asked and answered through the hand.
                if (checkArgs.getString("touchOnly") == "true") store.setTouchOnly(true)
                val real = java.time.LocalDateTime.now()
                val due = real.toLocalDate().plusDays(1).atTime(state.engine.times(med).first())
                store.setClockOffset(java.time.Duration.between(real, due.plusMinutes(1)).toMillis())
                Thread.sleep(1000)
                runOnMainSync { state.go(app.stripsense.Screen.SCAN) }
                Thread.sleep(1500)
                runOnMainSync { state.finishScan(Verdict.Match(med), null, emptyList(), -1L, state.scanId, printChecked = true) }
                val wait = (checkArgs.getString("seconds")?.toLongOrNull() ?: 40L) * 1000
                // "pretendHeard": after this many seconds the screen is told a tablet was heard, as the listener would tell it.
                // For checking everything that follows the sound in a room too loud to make the sound in.
                val pretend = checkArgs.getString("pretendHeard")?.toLongOrNull()?.times(1000)
                val knockAt = checkArgs.getString("pretendKnock")?.toLongOrNull()?.times(1000)
                if (knockAt != null && knockAt < wait) {
                    // As if two knocks had been felt: the same call the knock listener and the volume button make.
                    Thread.sleep(knockAt)
                    var taken = false
                    runOnMainSync { taken = state.answerYesByHand() }
                    android.util.Log.i("StripSense", "check: answered by hand " + taken)
                    Thread.sleep(wait - knockAt)
                } else if (pretend != null && pretend < wait) {
                    Thread.sleep(pretend)
                    runOnMainSync { state.outcome?.let { state.answerFlowFor(it).heard(app.stripsense.core.hear.Pop(0, 3, 30f, 0f)) } }
                    Thread.sleep(wait - pretend)
                } else Thread.sleep(wait)
                val after = store.data.value
                val known = before.events.mapTo(HashSet()) { it.id }
                val made = after.events.filter { it.id !in known }
                val report = "screen " + state.screen + ", new events " + made.joinToString { it.kind.toString() + " by " + it.source } + ", learned loudness " + after.tabletSound
                if (checkArgs.getString("keep") != "true") {
                    for (event in made) if (event.kind == EventKind.TAKEN && !state.engine.isUndone(event, store.data.value.events))
                        store.addEvent(DoseEvent(state.newId(), event.medId, event.slotKey, state.now(), EventKind.UNDONE, event.source, undoes = event.id))
                    if (before.tabletSound == 0f && after.tabletSound != 0f) { store.setHearTablet(false); store.setHearTablet(true) }
                    store.setClockOffset(before.clockOffsetMs)
                    if (store.data.value.touchOnly != before.touchOnly) store.setTouchOnly(before.touchOnly)
                    runOnMainSync { state.go(app.stripsense.Screen.TODAY) }
                }
                Thread.sleep(800)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report + "\n") })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL hear dose: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        // For trying the family link end to end: points the app at a relay, turns sharing on with the family code the app
        // makes, sends once, and reports what happened. The same thing the Share page does when its button is pressed.
        checkArgs.getString("linkRelay")?.let { relay ->
            try {
                val store = AppStore.get(targetContext)
                val code = store.data.value.linkCode.ifBlank { LinkSeal.newCode() }
                store.setFamilyLink(relay, code, true)
                Thread.sleep(1500)
                val done = java.util.concurrent.CountDownLatch(1)
                FamilyLink.maybeSend(targetContext, store.data.value, force = true) { done.countDown() }
                done.await(20, TimeUnit.SECONDS)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "link " + FamilyLink.last + " code " + code + " people " + People.all(store.data.value).size + "\n") })
            } catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL link: " + e.javaClass.simpleName + ": " + e.message + "\n") }) }
            return
        }
        if (checkArgs.getString("handOnly") == "true") {
            try { finish(Activity.RESULT_OK, Bundle().apply { putString("stream", HandDeviceChecks.run(targetContext)) }) }
            catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL hand: ${e.javaClass.simpleName}: ${e.message}\n") }) }
            return
        }
        if (checkArgs.getString("scanOnly") == "true") {
            try { finish(Activity.RESULT_OK, Bundle().apply { putString("stream", ScanDeviceChecks.run(targetContext)) }) }
            catch (e: Throwable) { finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL scan: ${e.javaClass.simpleName}: ${e.message}\n") }) }
            return
        }
        val folder = File(targetContext.cacheDir, "reference-check-${System.nanoTime()}")
        val embedder = LookEmbedder(targetContext)
        var store: Store? = null
        try {
            val activity = startActivitySync(android.content.Intent(targetContext, app.stripsense.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            runOnMainSync { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val photo = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY); setPixel(20, 20, Color.RED) }
            check(!embedder.embed(photo).isNullOrEmpty()) { "native embedding failed" }
            check(!photo.isRecycled && photo.getPixel(20, 20) == Color.RED) { "embedder consumed caller bitmap" }
            check(ReferenceFingerprint.extract(photo).size == 112)
            val med = Medicine("isolated-check", "Isolated check", "", "CHECK", listOf("INGREDIENT"), listOf(Strength("500", StrengthUnit.MG)))
            store = Store(folder) { StoreData(setupVersion = 1) }
            check(ReferenceSaver(store, embedder).save(med, listOf(ReferenceInput(photo, emptyList())), 1000L, true)) { "photo and label transaction failed" }
            check(photo.isRecycled) { "save did not release its owned pixels" }
            store.close(); store = null
            val reopened = Store(folder) { StoreData(setupVersion = 1) }
            store = reopened
            check(reopened.data.value.medicines.single().id == med.id)
            val look = reopened.data.value.looks.single()
            check(look.vector.isNotEmpty() && look.fingerprint.size == 112)
            val saved = checkNotNull(BitmapFactory.decodeFile(File(reopened.looksDir, look.photoFile).path))
            check(saved.width == 640 && saved.height == 480)
            saved.recycle()
            val fixture = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            val canvas = Canvas(fixture)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
            listOf("CHECK-500", "Paracetamol Tablets I.P.", "Each tablet contains:", "Paracetamol I.P. 500 mg", "Dosage: 10-15 mg/kg per dose", "Made by TEST LABS").forEachIndexed { i, text -> canvas.drawText(text, 40f, 70f + i * 95, paint) }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = CompletableFuture<List<OcrLine>>()
            recognizer.process(InputImage.fromBitmap(fixture, 0)).addOnSuccessListener { text ->
                result.complete(text.textBlocks.flatMap { it.lines }.map { l ->
                    val b = checkNotNull(l.boundingBox); OcrLine(l.text, b.left, b.top, b.right, b.bottom, b.height(), l.confidence)
                })
            }.addOnFailureListener { result.completeExceptionally(it) }
            val lines = try { result.get(15, TimeUnit.SECONDS) } finally { recognizer.close() }
            val ocr = LabelDrafts.ocr(lines)
            check(ocr.complete() && ocr.strengths == listOf("500 mg")) { "OCR draft included dosage or missed composition" }
            runBlocking {
                val model = app.stripsense.AppGraph.state(activity.applicationContext).gemma
                model.start()
                withTimeout(30_000) { while (model.state.value !is GemmaState.Ready) { check(model.state.value !is GemmaState.Failed); delay(100) } }
                Logs.i("device check label inputs")
                val inputs = GemmaInputs.labels(listOf(fixture), lines.joinToString("\n") { it.text })
                Logs.i("device check label request")
                val reply = model.askJson(CallKind.ENROLLMENT, LabelPrompt.SYSTEM, inputs, LabelPrompt.SCHEMA, 20_000)
                Logs.i("device check label returned ${reply.javaClass.simpleName}")
                val draft = checkNotNull((reply as? GemmaReply.Text)?.let { LabelDrafts.reply(it.text) }) { "vision extraction failed: ${(reply as? GemmaReply.Text)?.let { LabelDrafts.replyIssue(it.text) } ?: reply.javaClass.simpleName}" }
                check(draft.brand.uppercase().replace('-', ' ').replace(Regex("\\s+"), " ") == "CHECK 500") { "vision brand mismatch" }
                check(draft.generics.map { it.uppercase() } == listOf("PARACETAMOL")) { "vision ingredient mismatch" }
                check(Strengths.sameStrengths(draft.strengths.mapNotNull(Csv::strength), listOf(Strength("500", StrengthUnit.MG)))) { "vision included dosage as strength" }
                for ((key, expected) in listOf("actualP500" to LabelDraft("P-500", listOf("Paracetamol"), listOf("500 mg")), "actualParawel" to LabelDraft("Parawel-325", listOf("Paracetamol"), listOf("325 mg"), variants = listOf("DT")))) {
                    val filename = checkArgs.getString(key) ?: continue
                    check(filename == File(filename).name && filename.endsWith(".jpg"))
                    val actual = checkNotNull(LookPhotos.decode(File(File(targetContext.filesDir, "looks"), filename), KeystoreCipher()))
                    try {
                        val actualLines = readLines(actual)
                        val response = model.askJson(CallKind.ENROLLMENT, LabelPrompt.SYSTEM, GemmaInputs.labels(listOf(actual), actualLines.joinToString("\n") { it.text }), LabelPrompt.SCHEMA, 20_000)
                        val parsed = checkNotNull((response as? GemmaReply.Text)?.let { LabelDrafts.reply(it.text) }) { "$key extraction failed: ${(response as? GemmaReply.Text)?.let { LabelDrafts.replyIssue(it.text) } ?: response.javaClass.simpleName}" }
                        val resolved = LabelDrafts.merge(parsed, actualLines)
                        fun compact(text: String) = text.uppercase().replace(Regex("[^A-Z0-9]"), "")
                        val brand = compact(resolved.brand).let { if (resolved.variants.contains("DT")) it.removeSuffix("DT") else it }
                        check(brand == compact(expected.brand)) { "$key product mismatch: model=${parsed.brand.take(80)}; OCR=${LabelDrafts.ocr(actualLines).brand.take(80)}" }
                        check(resolved.generics.map { it.uppercase() } == expected.generics.map { it.uppercase() }) { "$key ingredient mismatch" }
                        check(Strengths.sameStrengths(resolved.strengths.mapNotNull(Csv::strength), expected.strengths.mapNotNull(Csv::strength))) { "$key strength mismatch" }
                        check(resolved.variants.toSet() == expected.variants.toSet() && resolved.release.isBlank()) { "$key form mismatch" }
                        Logs.i("device check $key label passed")
                    } finally { actual.recycle() }
                }
            }
            fixture.recycle()
            val extra = listOf("actualP500", "actualParawel").count { checkArgs.containsKey(it) }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS ${4 + extra}/${4 + extra}: embedding ownership; atomic enrollment/photo; persisted reload; OCR + local vision label extraction (isolated fixture); $extra read-only actual strip photo checks\n") })
        } catch (e: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL ${e.javaClass.simpleName}: ${e.message}\n") })
        } finally { store?.close(); embedder.close(); folder.deleteRecursively() }
    }
    private fun readLines(photo: Bitmap): List<OcrLine> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = CompletableFuture<List<OcrLine>>()
        recognizer.process(InputImage.fromBitmap(photo, 0)).addOnSuccessListener { text ->
            result.complete(text.textBlocks.flatMap { it.lines }.map { l ->
                val b = checkNotNull(l.boundingBox); OcrLine(l.text, b.left, b.top, b.right, b.bottom, b.height(), l.confidence)
            })
        }.addOnFailureListener { result.completeExceptionally(it) }
        return try { result.get(15, TimeUnit.SECONDS) } finally { recognizer.close() }
    }
}
