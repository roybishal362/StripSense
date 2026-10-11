package app.stripsense.ml

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import app.stripsense.data.Logs

/** Plays one recorded message (16-bit mono WAV bytes held in memory). One at a time; nothing is written to disk. */
class NotePlayer {
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    @Volatile private var playing = false

    fun isPlaying(): Boolean = playing

    /** [onDone] runs on the main thread when the message has been played to its end or stopped. */
    fun play(wav: ByteArray, onDone: (() -> Unit)? = null) {
        stop()
        // The header is 44 bytes; the sample rate stands at byte 24, low byte first.
        if (wav.size <= HEADER + 2) { onDone?.let { main.post(it) }; return }
        val rate = (wav[24].toInt() and 0xff) or ((wav[25].toInt() and 0xff) shl 8) or ((wav[26].toInt() and 0xff) shl 16)
        val size = (wav.size - HEADER) and 1.inv()
        runCatching {
            val made = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate.coerceIn(8000, 48000))
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(size).build()
            made.write(wav, HEADER, size)
            track = made
            playing = true
            made.play()
            // A static track does not say when it has ended; the length of the recording does.
            val ms = size / 2 * 1000L / rate.coerceAtLeast(1)
            main.postDelayed({ if (track === made) { stop(); onDone?.invoke() } }, ms + 150)
        }.onFailure {
            Logs.w("voice note not played: ${it.javaClass.simpleName}")
            stop()
            onDone?.let { done -> main.post(done) }
        }
    }

    fun stop() {
        val old = track
        track = null
        playing = false
        if (old != null) runCatching { old.pause(); old.flush(); old.release() }
    }

    private companion object { const val HEADER = 44 }
}
