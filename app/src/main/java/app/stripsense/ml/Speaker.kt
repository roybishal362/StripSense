package app.stripsense.ml

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import app.stripsense.data.Logs
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android's own voice, offline, English (en-IN) and Hindi (hi-IN). Spec 07 section 7.
 * The engine starts in the background; one sentence asked for before it is ready is kept and said then.
 */
class Speaker(val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var pending: Triple<String, Locale, (() -> Unit)?>? = null
    private val counter = AtomicInteger()

    // Called on the main thread when the utterance with this id ends (done, error or stopped).
    private var doneFor: Pair<String, () -> Unit>? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            Logs.i("speech ready=$ready")
            if (ready) {
                tts.setOnUtteranceProgressListener(listener)
                pending?.let { (text, locale, done) -> say(text, locale, done) }
            }
            pending = null
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) = finished(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)
    }

    /** Says [text] at once, replacing anything being said. [onDone] runs on the main thread when it ends. */
    fun speak(text: String, hindi: Boolean, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.let { main.post(it) }
            return
        }
        val locale = if (hindi) HINDI else ENGLISH_INDIA
        if (ready) say(text, locale, onDone) else pending = Triple(text, locale, onDone)
    }

    fun stop() {
        pending = null
        doneFor = null
        if (ready) tts.stop()
    }

    fun hasHindiVoice(): Boolean = ready && offlineVoices(HINDI).isNotEmpty()

    fun hasEnglishVoice(): Boolean = ready && offlineVoices(ENGLISH_INDIA).isNotEmpty()

    fun isReady(): Boolean = ready

    fun voiceCount(locale: Locale): Int = if (ready) offlineVoices(locale).size else 0

    fun shutdown() {
        stop()
        runCatching { tts.shutdown() }
    }

    private fun say(text: String, locale: Locale, onDone: (() -> Unit)?) {
        val voice = offlineVoices(locale).firstOrNull()
        if (voice != null) tts.voice = voice else tts.setLanguage(locale)
        val id = "answer-" + counter.incrementAndGet()
        doneFor = if (onDone != null) id to onDone else null
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        if (result != TextToSpeech.SUCCESS) finished(id)
    }

    private fun finished(id: String?) {
        main.post {
            val d = doneFor ?: return@post
            if (d.first == id) {
                doneFor = null
                d.second()
            }
        }
    }

    private fun offlineVoices(locale: Locale): List<Voice> = runCatching {
        (tts.voices ?: emptySet()).filter {
            it.locale.language == locale.language && !it.isNetworkConnectionRequired &&
                !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }.sortedByDescending { it.locale.country == locale.country }
    }.getOrDefault(emptyList())

    companion object {
        val HINDI: Locale = Locale.forLanguageTag("hi-IN")
        val ENGLISH_INDIA: Locale = Locale.forLanguageTag("en-IN")
    }
}
