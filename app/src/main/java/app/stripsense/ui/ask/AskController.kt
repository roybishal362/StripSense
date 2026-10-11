package app.stripsense.ui.ask

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

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.stripsense.AppState
import app.stripsense.Bubble
import app.stripsense.Screen
import app.stripsense.ml.*
import kotlinx.coroutines.*

/** Owns one visit to Ask. Model replies from a closed screen are ignored. */
class AskController(private val state: AppState, private val scope: CoroutineScope) {
    var working by mutableStateOf(false)
        private set
    private var open = true
    private var foreground = true
    private var visit = 0L

    fun pause() { foreground = false; visit++ }
    fun resume() { foreground = true }

    fun close() { open = false }

    fun quick(medicine: Medicine? = null) {
        if (working || !foreground || !open) return
        val d = state.data
        val parts = if (medicine == null) state.engine.whatsLeft(People.medicines(d), state.now(), d.events)
            else state.engine.statusAnswer(medicine, state.now(), d.events)
        act(VoiceAction.Say(parts), null)
    }

    fun typed(text: String) {
        if (working || !foreground || !open || text.isBlank()) return
        add(Bubble(true, listOf(Rendered(text, text))))
        VoiceRouter.beforeModel(text)?.let { act(it, null); return }
        // A question about a time of day needs no model: the words say it.
        if (Keywords.check(text) == Keywords.Hit.NONE) DueQuestion.parse(text)?.let { asked ->
            val d = state.data
            act(VoiceAction.ShowDue(DueQuestion.answer(asked, People.medicines(d), state.now(), d.events, state.engine)), null)
            return
        }
        call(text, null)
    }

    fun audio(wav: ByteArray) {
        if (!working && foreground && open) call(null, wav)
    }

    private fun call(text: String?, wav: ByteArray?) {
        if (state.gemma.state.value !is GemmaState.Ready) {
            act(VoiceAction.Say(listOf(Part("V4"))), null)
            return
        }
        working = true
        val currentVisit = visit
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                val d = state.data
                val contents = if (wav != null) GemmaInputs.audio(wav) else GemmaInputs.typed(text.orEmpty())
                val reply = state.gemma.askJson(CallKind.VOICE, VoicePrompt.system(People.medicines(d)), contents, VoicePrompt.SCHEMA, d.tuning.gemmaTimeoutMs)
                if (!open || !foreground || visit != currentVisit || state.screen != Screen.ASK) return@launch
                val request = (reply as? GemmaReply.Text)?.let { VoiceRequest.parse(it.text, People.medicines(d).size) }
                if (wav != null && request != null && request.transcript.isNotBlank()) {
                    add(Bubble(true, listOf(Rendered(request.transcript, request.transcript))))
                }
                val action = when {
                    request != null -> VoiceRouter.route(request, People.medicines(d), state.now(), state.data.events, state.engine, d.person.contactPhone.isNotBlank())
                    reply is GemmaReply.Text -> VoiceRouter.unparsed(reply.text)
                    else -> VoiceAction.Say(listOf(Part("V0")))
                }
                act(action, SystemClock.elapsedRealtime() - started)
            } finally { working = false }
        }
    }

    private fun add(bubble: Bubble) {
        state.bubbles.add(bubble)
        while (state.bubbles.size > 100) state.bubbles.removeAt(0)
    }

    private fun act(action: VoiceAction, ms: Long?) {
        if (!open || !foreground) return
        fun say(parts: List<Part>, call: Boolean = false, photo: String? = null) {
            add(Bubble(false, state.render(parts), parts, ms, photo, call))
            state.say(parts)
        }
        when (action) {
            is VoiceAction.Say -> say(action.parts, action.callButton)
            VoiceAction.Repeat -> say(state.lastAnswer.ifEmpty { listOf(Part("RP0")) })
            is VoiceAction.ShowDue -> {
                // The words, then the family's own photo of each strip of that time, so she sees which ones are meant.
                val lang = state.language
                val shown = Sentences.dueAt(action.due, lang)
                val photos = action.due.items.mapNotNull { item ->
                    val looks = state.data.looks.filter { it.medId == item.med.id }
                    (looks.firstOrNull { it.verifiedPrint } ?: looks.firstOrNull())?.let { Answers.nickname(item.med).get(lang) to it.photoFile }
                }
                add(Bubble(false, listOf(Rendered(shown, Sentences.dueAt(action.due, state.speechLanguage()))), ms = ms, photos = photos,
                    findMed = action.due.items.firstOrNull { !it.marked && it.med.type == MedType.CAMERA }?.med?.id,
                    round = action.due.time?.let { time ->
                        val today = state.engine.local(state.now()).toLocalDate()
                        action.due.items.filter { !it.marked && it.med.type == MedType.CAMERA }
                            .mapNotNull { item -> state.engine.slotsFor(item.med, today).firstOrNull { it.time == time }?.let { item.med.id to it.key } }
                    }.orEmpty()))
                if (state.speakAnswers) state.speaker.speak(Sentences.dueAt(action.due, state.speechLanguage()), state.speechLanguage() == "hi") else state.speaker.stop()
            }
            is VoiceAction.ShowPhoto -> {
                val photo = state.data.looks.firstOrNull { it.medId == action.med.id }?.photoFile
                say(listOf(Part("V6")), photo = photo)
            }
            is VoiceAction.Help -> {
                state.openHelp(action.selfHarm)
                state.say(listOf(Part(if (action.selfHarm) "SH1" else "HLP1")))
            }
            is VoiceAction.Find -> {
                val currentVisit = visit
                val parts = listOf(Part("V7"))
                add(Bubble(false, state.render(parts), parts, ms))
                var moved = false
                fun find() {
                    if (!moved && open && foreground && visit == currentVisit && state.screen == Screen.ASK) {
                        moved = true
                        state.findOnTable(action.med.id)
                    }
                }
                state.say(parts, ::find)
                scope.launch { delay(4000); find() }
            }
            VoiceAction.OpenScan -> {
                val currentVisit = visit
                val parts = listOf(Part("V5"))
                add(Bubble(false, state.render(parts), parts, ms))
                var moved = false
                fun scan() {
                    if (!moved && open && foreground && visit == currentVisit && state.screen == Screen.ASK) {
                        moved = true
                        state.go(Screen.SCAN)
                    }
                }
                state.say(parts, ::scan)
                scope.launch { delay(3500); scan() }
            }
        }
    }
}
