package app.stripsense.ml

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.stripsense.core.Wav
import app.stripsense.data.Logs
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Hold to talk: 16 kHz mono 16-bit PCM, at most 10 seconds, as WAV bytes. Nothing is saved (spec 04 section 3). */
class Recorder {

    @Volatile private var stopRequested = false

    fun stop() {
        stopRequested = true
    }

    /** Records until [stop] or 10 seconds. Null when it could not record or the audio is under 400 ms. Call after the permission was granted. */
    @SuppressLint("MissingPermission")
    suspend fun record(): ByteArray? = withContext(Dispatchers.IO) {
        stopRequested = false
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return@withContext null
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4)
        }.getOrNull() ?: return@withContext null
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return@withContext null
        }
        val out = ByteArrayOutputStream(RATE * 2 * 4)
        try {
            rec.startRecording()
            val buf = ByteArray(min)
            while (!stopRequested && coroutineContext.isActive && out.size() < MAX_BYTES) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) out.write(buf, 0, n) else if (n < 0) break
            }
        } catch (e: Exception) {
            Logs.w("record failed: ${e.javaClass.simpleName}")
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        val pcm = out.toByteArray()
        if (pcm.size < MIN_BYTES) null else Wav.wav(pcm, RATE)
    }

    companion object {
        const val RATE = 16000
        private const val MAX_BYTES = RATE * 2 * 10 // 10 seconds
        private const val MIN_BYTES = RATE * 2 * 400 / 1000 // 400 ms
    }
}
