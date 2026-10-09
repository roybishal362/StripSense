package app.stripsense.ui

import app.stripsense.AppState
import app.stripsense.Bubble
import app.stripsense.Screen
import app.stripsense.core.Part
import app.stripsense.core.Rendered
import app.stripsense.core.VoiceAction
import app.stripsense.core.VoicePrompt
import app.stripsense.core.VoiceRequest
import app.stripsense.core.VoiceRouter
import app.stripsense.ml.CallKind
import app.stripsense.ml.GemmaInputs
import app.stripsense.ml.GemmaReply
import app.stripsense.ml.Recorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Orchestrates recording, calling Gemma, and routing the answer (spec 04). */
class AskController(val state: AppState) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: Recorder? = null
    var listening = false
        private set

    fun startListening() {
        if (listening) return
        val context = state.store.looksDir.parentFile!!.parentFile!!
        if (!hasMic(state.speaker.context)) {
            openAppSettings(state.speaker.context)
            return
        }
        listening = true
        state.speaker.stop()
        scope.launch {
            val r = Recorder()
            recorder = r
            val wav = r.record()
            recorder = null
            listening = false
            if (wav != null) {
                state.bubbles.add(Bubble(mine = true, text = listOf(Rendered("Voice", "Voice"))))
                process(wav, null)
            }
        }
    }

    fun stopListening() {
        recorder?.stop()
    }

    fun askTyped(text: String) {
        state.speaker.stop()
        state.bubbles.add(Bubble(mine = true, text = listOf(Rendered(text, text))))
        val before = VoiceRouter.beforeModel(text)
        if (before != null) {
            action(before, null)
        } else {
            if (!state.gemma.hasModel() && !state.gemma.isFree()) {
                action(VoiceAction.Say(listOf(Part("V4"))), null)
                return
            }
            process(null, text)
        }
    }

    private fun process(wav: ByteArray?, text: String?) {
        val thinkIdx = state.bubbles.size
        state.bubbles.add(Bubble(mine = false, text = listOf(Rendered("...", ""))))
        scope.launch {
            val d = state.data
            val system = VoicePrompt.system(d.medicines)
            val contents = if (wav != null) withContext(Dispatchers.Default) { GemmaInputs.audio(wav) } else GemmaInputs.typed(text!!)
            val started = System.currentTimeMillis()
            val reply = state.gemma.askJson(CallKind.VOICE, system, contents, VoicePrompt.SCHEMA, d.tuning.gemmaTimeoutMs)
            val ms = System.currentTimeMillis() - started
            val req = when (reply) {
                is GemmaReply.Text -> VoiceRequest.parse(reply.text, d.medicines.size)
                else -> null
            }
            if (reply is GemmaReply.Text && req != null) {
                // Replace the dummy "Voice" bubble with the real transcript.
                if (wav != null) {
                    val transcript = req.transcript.ifEmpty { "[audio]" }
                    state.bubbles[thinkIdx - 1] = Bubble(mine = true, text = listOf(Rendered(transcript, transcript)))
                }
            }
            val act = if (reply is GemmaReply.Text && req == null) VoiceRouter.unparsed(reply.text)
                else VoiceRouter.route(req, d.medicines, state.now(), d.events, state.engine, d.person.contactPhone.isNotBlank())
            state.bubbles.removeAt(thinkIdx)
            action(act, ms)
        }
    }

    private fun action(act: VoiceAction, ms: Long?) {
        when (act) {
            is VoiceAction.Say -> {
                state.bubbles.add(Bubble(mine = false, text = state.render(act.parts), parts = act.parts, ms = ms))
                state.say(act.parts)
                if (act.callButton) dial(state.speaker.context, state.data.person.contactPhone)
            }
            VoiceAction.OpenScan -> {
                val p = listOf(Part("V5"))
                state.bubbles.add(Bubble(mine = false, text = state.render(p), parts = p, ms = ms))
                state.say(p) { state.go(Screen.SCAN) }
            }
            is VoiceAction.ShowPhoto -> {
                val p = listOf(Part("V6"))
                val photo = state.data.looks.firstOrNull { it.medId == act.med.id }?.photoFile
                state.bubbles.add(Bubble(mine = false, text = state.render(p), parts = p, ms = ms, photo = photo))
                state.say(p)
            }
            is VoiceAction.Help -> {
                val p = listOf(Part(if (act.selfHarm) "SH1" else "HLP1"))
                state.bubbles.add(Bubble(mine = false, text = state.render(p), parts = p, ms = ms))
                state.say(p)
                state.openHelp(act.selfHarm)
            }
            VoiceAction.Repeat -> {
                if (state.lastAnswer.isNotEmpty()) {
                    state.bubbles.add(Bubble(mine = false, text = state.render(state.lastAnswer), parts = state.lastAnswer, ms = ms))
                    state.say(state.lastAnswer)
                } else {
                    val p = listOf(Part("RP0"))
                    state.bubbles.add(Bubble(mine = false, text = state.render(p), parts = p, ms = ms))
                    state.say(p)
                }
            }
        }
    }

    private fun hasMic(context: android.content.Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
