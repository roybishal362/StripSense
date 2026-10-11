package app.stripsense.ml

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import app.stripsense.core.text.Roman
import app.stripsense.data.Logs
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The phone's own voice, in Hindi (hi-IN) and Indian English (en-IN). Spec 07 section 7.
 * The engine starts in the background; one sentence asked for before it is ready is kept and said then.
 *
 * Which voices work is found out by using them, not by the engine's "not installed" mark. On the finale phone
 * every Hindi voice carried that mark and every one of them spoke Hindi when asked. The app trusted the mark,
 * so Hindi mode was read out in English. Now a voice is used unless it has actually failed; a sentence whose
 * voice fails is said again at once another way, so nothing is ever silently dropped.
 */
class Speaker(val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var pending: Triple<String, Locale, (() -> Unit)?>? = null
    private val counter = AtomicInteger()
    @Volatile private var speakingId: String? = null

    // Called on the main thread when the utterance with this id ends (done or stopped).
    private var doneFor: Pair<String, () -> Unit>? = null

    /** What is being said, so that a failed voice can be answered by saying the same thing another way. */
    private class Saying(val id: String, val asked: String, val askedLocale: Locale, val usedLocale: Locale, val voice: String?, val onDone: (() -> Unit)?)
    @Volatile private var saying: Saying? = null

    /** Voices that failed when used, and languages with no working voice left. They are not tried again this run. */
    private val failedVoices = ConcurrentHashMap.newKeySet<String>()
    private val failedLanguages = ConcurrentHashMap.newKeySet<String>()

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
        override fun onError(utteranceId: String?) = failed(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = failed(utteranceId)
    }

    /** Nothing is said aloud: the person cannot hear the phone, and it speaks through the hand instead. */
    @Volatile var silent = false

    /** Says [text] at once, replacing anything being said. [onDone] runs on the main thread when it ends. */
    fun speak(text: String, hindi: Boolean, onDone: (() -> Unit)? = null) {
        if (text.isBlank() || silent) {
            onDone?.let { main.post(it) }
            return
        }
        val locale = if (hindi) HINDI else ENGLISH_INDIA
        if (ready) say(text, locale, onDone) else pending = Triple(text, locale, onDone)
    }

    fun stop() {
        pending = null
        doneFor = null
        speakingId = null
        saying = null
        if (ready) tts.stop()
    }

    /** True from the moment a sentence is handed to the voice until it has been said or stopped. */
    fun isSpeaking(): Boolean = speakingId != null

    /** Hindi can be spoken in a Hindi voice: the engine offers one and it has not failed when used. */
    fun hasHindiVoice(): Boolean = ready && canSpeak(HINDI)

    fun hasEnglishVoice(): Boolean = ready && canSpeak(ENGLISH_INDIA)

    fun isReady(): Boolean = ready

    fun voiceCount(locale: Locale): Int = if (ready) voices(locale).size else 0

    fun shutdown() {
        stop()
        runCatching { tts.shutdown() }
    }

    private fun canSpeak(locale: Locale): Boolean =
        locale.language !in failedLanguages && (voices(locale).isNotEmpty() || runCatching { tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false))

    private fun say(asked: String, askedLocale: Locale, onDone: (() -> Unit)?) {
        // Hindi with no working Hindi voice: the same Hindi sentence in Roman letters, read by the English voice.
        val roman = askedLocale == HINDI && !canSpeak(HINDI)
        val text = if (roman) Roman.fromDevanagari(asked) else asked
        val locale = if (roman) ENGLISH_INDIA else askedLocale
        // Indian English given up on (no data and no connection): whatever English voice the engine itself has.
        val lost = locale.language in failedLanguages
        val voice = if (lost) null else voices(locale).firstOrNull()
        val set = runCatching { if (voice != null) tts.setVoice(voice) else tts.setLanguage(if (lost) Locale.US else locale) }.getOrDefault(TextToSpeech.ERROR)
        // A touch slower than the engine's default: clearer for an older listener and less machine-like.
        tts.setSpeechRate(RATE)
        tts.setPitch(1.0f)
        val id = "answer-" + counter.incrementAndGet()
        doneFor = if (onDone != null) id to onDone else null
        speakingId = id
        saying = Saying(id, asked, askedLocale, locale, voice?.name, onDone)
        val result = if (set < 0 && voice == null) TextToSpeech.ERROR else tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        if (result != TextToSpeech.SUCCESS) failed(id)
    }

    private fun finished(id: String?) {
        if (speakingId == id) speakingId = null
        main.post {
            if (saying?.id == id) saying = null
            val d = doneFor ?: return@post
            if (d.first == id) {
                doneFor = null
                d.second()
            }
        }
    }

    /**
     * The voice could not say it (its data is missing and there is no connection, for example).
     * That voice is set aside and the same sentence is said again with the next one; when a language has
     * none left, Hindi goes to Roman letters and English to whatever voice the engine has.
     */
    private fun failed(id: String?) {
        main.post {
            val was = saying?.takeIf { it.id == id }
            if (was == null) { finished(id); return@post }
            saying = null
            val language = was.usedLocale.language
            val alreadyTried = failedVoices.count { it.startsWith(language, ignoreCase = true) }
            val again = when {
                // One more voice of the same language is worth a try; after two, the language itself is the problem.
                was.voice != null && failedVoices.add(was.voice) && alreadyTried < 1 && voices(was.usedLocale).isNotEmpty() -> true
                // The language is given up for this run. Said again, Hindi becomes Roman letters and English the engine's own voice.
                failedLanguages.add(language) -> true
                else -> false
            }
            Logs.w("speech failed in $language; ${if (again) "saying it another way" else "nothing left to try"}")
            if (again) say(was.asked, was.askedLocale, was.onDone) else finished(id)
        }
    }

    /**
     * The voices worth using for this language, best first: this country's, stored on the phone before
     * ones that need a connection, then by quality. Voices that failed when used are left out.
     */
    private fun voices(locale: Locale): List<Voice> {
        val now = android.os.SystemClock.elapsedRealtime()
        // Asked several times a second by the scan screen; the engine's list changes only when a voice is added.
        val cached = voiceCache[locale.language]
        val all = if (cached != null && now - cached.first < VOICE_CACHE_MS) cached.second else runCatching {
            (tts.voices ?: emptySet()).filter { it.locale.language == locale.language && it.locale.country == locale.country }
                .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality }.thenBy { it.name })
        }.getOrDefault(emptyList()).also { voiceCache[locale.language] = now to it }
        return all.filter { it.name !in failedVoices }
    }

    private val voiceCache = ConcurrentHashMap<String, Pair<Long, List<Voice>>>()

    companion object {
        const val RATE = 0.94f
        const val VOICE_CACHE_MS = 5000L
        val HINDI: Locale = Locale.forLanguageTag("hi-IN")
        val ENGLISH_INDIA: Locale = Locale.forLanguageTag("en-IN")
    }
}
