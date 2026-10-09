package app.stripsense.ml

import android.content.Context
import app.stripsense.data.Logs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface GemmaState {
    data object NoFile : GemmaState
    data object Loading : GemmaState
    data class Ready(val backend: String, val loadMs: Long) : GemmaState
    data class Failed(val message: String) : GemmaState
}

/**
 * The one LiteRT-LM engine for the whole app life (spec 04 section 2), and the guarded call of spec 07 section 6.
 * The engine is never closed and recreated while the app runs.
 */
class Gemma(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile private var engine: Engine? = null // set once, after initialize() succeeded

    @Volatile private var formatOff = false // the fixed JSON shape made a call throw
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy // a call that would not stop is still inside the engine

    private val _state = MutableStateFlow<GemmaState>(GemmaState.NoFile)
    val state: StateFlow<GemmaState> = _state

    val stats = GemmaStats()

    @Volatile private var loadStarted = false

    val modelFile: File get() = File(File(context.filesDir, "models"), MODEL_NAME)

    /** The file to load: the developer shortcut in /data/local/tmp, or the copied file. */
    fun findModel(): File? {
        val dev = File("/data/local/tmp", MODEL_NAME)
        if (runCatching { dev.isFile && dev.canRead() }.getOrDefault(false)) return dev
        return modelFile.takeIf { it.isFile && it.length() > 0 }
    }

    fun hasModel(): Boolean = findModel() != null

    /** Starts loading once, in the background, if the model file exists. Safe to call more than once. */
    @Synchronized
    fun start() {
        if (loadStarted) return
        val file = findModel()
        if (file == null) {
            _state.value = GemmaState.NoFile
            return
        }
        loadStarted = true
        _state.value = GemmaState.Loading
        scope.launch { load(file) }
    }

    private fun load(file: File) {
        val started = System.currentTimeMillis()
        for ((name, main, vision) in listOf(Triple("GPU", Backend.GPU(), Backend.GPU()), Triple("CPU", Backend.CPU(), Backend.CPU()))) {
            val e = runCatching {
                Engine(
                    EngineConfig(
                        modelPath = file.absolutePath,
                        backend = main,
                        visionBackend = vision, // must be set, or image input is not loaded
                        audioBackend = Backend.CPU(), // must be set, or audio input is not loaded
                        maxNumTokens = 2048,
                        cacheDir = context.cacheDir.absolutePath,
                    ),
                ).also { it.initialize() }
            }.onFailure { Logs.w("gemma $name failed: ${it.javaClass.simpleName}") }.getOrNull()
            if (e != null) {
                engine = e
                val ms = System.currentTimeMillis() - started
                Logs.i("gemma ready on $name in $ms ms")
                _state.value = GemmaState.Ready(name, ms)
                return
            }
        }
        _state.value = GemmaState.Failed("could not start")
    }

    /** True when a new call would start at once. The second reader runs only then. */
    fun isFree(): Boolean = engine != null && !_busy.value && !mutex.isLocked

    /** One call. It may be called from the main thread: the work moves to a background thread. */
    suspend fun ask(kind: CallKind, system: String, contents: Contents, schema: String?, timeoutMs: Long): GemmaReply {
        val engine = engine ?: return GemmaReply.Busy
        if (_busy.value) return GemmaReply.Busy
        return mutex.withLock {
            withContext(Dispatchers.Default + NonCancellable) { // a caller that goes away must not skip the clean-up
                val started = System.currentTimeMillis()
                val conversation = runCatching {
                    engine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(system),
                            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0),
                            enableResponseFormat = schema != null,
                        ),
                    )
                }.getOrElse { return@withContext GemmaReply.Failed(it.message ?: "could not start") }
                val call = scope.async { // the blocking call, on its own thread
                    runCatching {
                        val reply: Message =
                            if (schema != null) conversation.sendMessage(contents, responseFormat = ResponseFormat.json(schema))
                            else conversation.sendMessage(Message.user(contents))
                        reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.trim()
                    }
                }
                val result = withTimeoutOrNull(timeoutMs) { call.await() } // the caller waits, with the limit
                if (result == null) {
                    runCatching { conversation.cancelProcess() } // ask the model to stop
                    withTimeoutOrNull(3_000) { call.join() } // give it time to return
                }
                if (call.isCompleted) {
                    runCatching { conversation.close() }
                } else {
                    _busy.value = true // never close while the call runs
                    call.invokeOnCompletion {
                        runCatching { conversation.close() }
                        _busy.value = false
                    }
                }
                val reply = when {
                    result == null -> GemmaReply.TimedOut
                    result.isSuccess -> GemmaReply.Text(result.getOrThrow())
                    else -> GemmaReply.Failed(result.exceptionOrNull()?.message ?: "error")
                }
                record(kind, System.currentTimeMillis() - started, reply)
                reply
            }
        }
    }

    /** A call with a fixed JSON shape. If the shape makes the call throw, it is switched off for the rest of the run. */
    suspend fun askJson(kind: CallKind, system: String, contents: Contents, schema: String, timeoutMs: Long): GemmaReply {
        if (!formatOff) {
            val first = ask(kind, system, contents, schema, timeoutMs)
            if (first !is GemmaReply.Failed) return first
            formatOff = true
            Logs.i("gemma json shape off, plain replies from now on")
        }
        return ask(kind, system, contents, null, timeoutMs)
    }

    private fun record(kind: CallKind, ms: Long, reply: GemmaReply) {
        stats.record(kind, ms, reply)
        Logs.i("gemma ${kind.name.lowercase()} ${reply.javaClass.simpleName} in $ms ms")
    }

    companion object {
        const val MODEL_NAME = "gemma-4-E2B-it.litertlm"
        const val MODEL_BYTES = 2_588_147_712L
    }
}
