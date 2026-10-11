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
import android.graphics.Color
import android.os.SystemClock
import app.stripsense.ml.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The hand model and Gemma as a live reader, on this phone. Read-only: the saved photos are read, nothing is written.
 * Reports times and what the print engine made of each reading, never the text itself.
 */
object LiveDeviceChecks {
    fun run(context: Context): String = runBlocking {
        val out = StringBuilder()
        val hands = HandFinder(context)
        val blank = Bitmap.createBitmap(384, 288, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        val started = SystemClock.elapsedRealtime()
        val none = hands.find(blank)
        val first = SystemClock.elapsedRealtime() - started
        var total = 0L
        repeat(10) {
            val again = Bitmap.createBitmap(384, 288, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
            val t = SystemClock.elapsedRealtime()
            hands.find(again)
            total += SystemClock.elapsedRealtime() - t
        }
        hands.close()
        out.append("Hand model: loaded and ran. Blank picture gave ${if (none == null) "no hand" else "A HAND (wrong)"}. First call $first ms, then ${total / 10} ms each.\n")

        val file = File(context.filesDir, "store.json")
        if (!file.exists()) return@runBlocking out.append("No saved medicines; Gemma reader not timed.\n").toString()
        val cipher = app.stripsense.data.KeystoreCipher()
        val data = app.stripsense.data.Store.read(file, cipher)
        val gemma = Gemma(context)
        gemma.start()
        val loadStart = SystemClock.elapsedRealtime()
        while (gemma.state.value is GemmaState.Loading && SystemClock.elapsedRealtime() - loadStart < 420_000) delay(250)
        val ready = gemma.state.value as? GemmaState.Ready ?: return@runBlocking out.append("Gemma did not start: ${gemma.state.value}\n").toString()
        out.append("Gemma ready on ${ready.backend} in ${ready.loadMs} ms.\n")
        for (side in listOf(LiveReader.PICTURE_PX, 320)) {
            val times = ArrayList<Long>()
            for ((n, look) in data.looks.take(6).withIndex()) {
                if (File(look.photoFile).name != look.photoFile) continue
                val photo = app.stripsense.data.LookPhotos.decode(File(File(context.filesDir, "looks"), look.photoFile), cipher) ?: continue
                val small = photo.fitWithin(side)
                if (small !== photo) photo.recycle()
                val t = SystemClock.elapsedRealtime()
                val reply = gemma.ask(CallKind.LIVE_READER, LiveReader.SYSTEM, GemmaInputs.reading(small), null, 20_000)
                val ms = SystemClock.elapsedRealtime() - t
                small.recycle()
                times += ms
                val lines = (reply as? GemmaReply.Text)?.let { LiveReader.lines(it.text) }.orEmpty()
                // The index never contains this photo, as in the print audit.
                val index = PrintIndex.build(data.medicines, data.looks - look)
                val evidence = PrintEvidence()
                evidence.addReading(lines, index, data.tuning, 0)
                evidence.addReading(lines, index, data.tuning, 100)
                val expected = data.medicines.firstOrNull { it.id == look.medId }?.nickEn?.take(10)
                out.append("  ${side}px photo ${n + 1} ($expected): ${reply.javaClass.simpleName} $ms ms, ${lines.size} lines, votes ${evidence.trace(100)} -> ${evidence.decide(index, 100).javaClass.simpleName}\n")
            }
            if (times.isNotEmpty()) out.append("Gemma reader at ${side}px: median ${times.sorted()[times.size / 2]} ms, slowest ${times.max()} ms over ${times.size} photos.\n")
        }
        out.toString()
    }
}
