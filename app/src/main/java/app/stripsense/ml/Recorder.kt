package app.stripsense.ml

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.stripsense.core.voice.Wav
import app.stripsense.data.Logs
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Records at most ten seconds in memory. Nothing is written to disk. */
class Recorder {
    @Volatile private var stopRequested = false
    @Volatile private var current: AudioRecord? = null

    fun stop() {
        stopRequested = true
        runCatching { current?.stop() }
    }

    @SuppressLint("MissingPermission")
    suspend fun record(): ByteArray? {
        // Reset before dispatch, so a quick release cannot be lost on the IO thread.
        stopRequested = false
        return withContext(Dispatchers.IO) {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0 || stopRequested) return@withContext null
            val rec = runCatching {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4)
            }.getOrNull() ?: return@withContext null
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@withContext null }
            current = rec
            val out = ByteArrayOutputStream(RATE * 2 * 4)
            try {
                if (!stopRequested) rec.startRecording()
                val buf = ByteArray(min)
                while (!stopRequested && coroutineContext.isActive && out.size() < MAX_BYTES) {
                    val n = rec.read(buf, 0, minOf(buf.size, MAX_BYTES - out.size()))
                    if (n > 0) out.write(buf, 0, n) else if (n < 0) break
                }
            } catch (e: Exception) {
                Logs.w("record failed: ${e.javaClass.simpleName}")
            } finally {
                current = null
                runCatching { rec.stop() }
                rec.release()
            }
            val pcm = out.toByteArray()
            if (pcm.size < MIN_BYTES) null else Wav.wav(pcm, RATE)
        }
    }

    companion object {
        const val RATE = 16000
        private const val MAX_BYTES = RATE * 2 * 10
        private const val MIN_BYTES = RATE * 2 * 400 / 1000
    }
}
