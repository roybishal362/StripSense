# Spec 07. Versions and API shapes that are known to work

Checked on 6 October 2026. Everything on this page was **compiled together into one APK** (Android Gradle Plugin 9.4.1, Kotlin 2.4.20, Gradle 9.8.0, JDK 21, compile SDK 37) and its unit tests ran. The APK's permission list was read back and has no INTERNET entry. Nothing here was run on an iQOO 15; behaviour on the phone is checked at the event.

**Use these versions and these call shapes. Do not use other method names from memory.**

## 1. Versions (`gradle/libs.versions.toml`)
Android Studio's new-project wizard writes this file. Set the numbers to these and add the missing lines.

```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.20"
coreKtx = "1.19.1"
lifecycle = "2.11.0"
activityCompose = "1.13.0"
composeBom = "2026.09.00"
materialIcons = "1.7.8"
camerax = "1.6.2"
mlkitText = "16.0.1"
mediapipe = "1.0.0"
litertlm = "0.17.1"
coroutines = "1.11.0"
serialization = "1.11.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core", version.ref = "materialIcons" }
androidx-camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkitText" }
mediapipe-tasks-vision = { group = "com.google.mediapipe", name = "tasks-vision", version.ref = "mediapipe" }
litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

Facts behind these numbers:
- **AGP 9 has Kotlin built in.** There is no `org.jetbrains.kotlin.android` plugin and no `kotlinOptions { }` block. Do not add them.
- **LiteRT-LM is pinned to 0.17.1.** A 0.18.0 file appeared on Google Maven on 6 Oct with no release notes; it has classes (`ModelInfo`, `Modality`) that 0.17.1 does not. Do not use `latest.release`.
- LiteRT-LM needs Kotlin 2.3 or newer. Compose BOM 2026.09.00 needs compile SDK 37 and AGP 9.1 or newer.
- `Icons.*` is **not** part of Material 3 any more. The `material-icons-core` line is needed, with its version written out.
- Do not turn on code shrinking (`isMinifyEnabled = false`); LiteRT-LM crashes in shrunk builds.

Root `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.stripsense"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.stripsense"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.litertlm.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
```
If the wizard wrote `compileSdk { version = release(36) }`, change the number to 37 in whichever form it used.

## 2. Manifest
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-feature android:name="android.hardware.camera" android:required="true" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <!-- The app must not be able to reach the internet, even if a library asks for it. -->
    <uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />

    <queries>
        <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>
    </queries>

    <application ... android:allowBackup="false">
        <!-- Needed for LiteRT-LM's GPU backend. Without them the model falls back to the CPU. -->
        <uses-native-library android:name="libOpenCL.so" android:required="false" />
        <uses-native-library android:name="libvndksupport.so" android:required="false" />
        <activity android:name=".MainActivity" android:exported="true" android:screenOrientation="portrait"> ... </activity>
    </application>
</manifest>
```
With these two `tools:node="remove"` lines, the built APK listed only CAMERA, RECORD_AUDIO and VIBRATE (plus one internal androidx permission). Check after every stage that adds a library:
`<sdk>\build-tools\36.0.0\aapt2.exe dump permissions app\build\outputs\apk\debug\app-debug.apk`

Opening the dialer (`ACTION_DIAL`), the SMS app (`ACTION_SENDTO`), the file picker and `MediaStore.Downloads` need no permission.

## 3. CameraX with Compose
```kotlin
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/** Live preview plus one analysis stream. The receiver of onFrame must close each frame. */
@Composable
fun CameraWithReader(onFrame: (ImageProxy) -> Unit, onCamera: (Camera) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }

    DisposableEffect(owner) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val selector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy -> onFrame(proxy) }
            p.unbindAll()
            onCamera(p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}
```
- Torch: keep the `Camera` from `onCamera`, then `camera.cameraControl.enableTorch(true)`.
- The analysis image is 4:3. The line boxes ML Kit returns are in the upright image (width and height swapped when the rotation is 90 or 270).
- `LocalLifecycleOwner` comes from `androidx.lifecycle.compose`, not from `androidx.compose.ui.platform`.
- A frame as an upright bitmap (for cropping, the embedder and Gemma):
```kotlin
fun ImageProxy.uprightBitmap(): Bitmap {
    val raw = toBitmap()
    val turn = imageInfo.rotationDegrees
    if (turn == 0) return raw
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(turn.toFloat()) }, true)
}
```
- Camera permission: `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`.

## 4. ML Kit Text Recognition v2 (Latin model inside the APK, works offline)
```kotlin
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

// onLines runs on the main thread while the frame is still open; the frame is closed right after it returns.
@OptIn(ExperimentalGetImage::class)          // androidx.annotation.OptIn + androidx.camera.core.ExperimentalGetImage
fun read(proxy: ImageProxy, onLines: (List<OcrLine>) -> Unit) {
    val media = proxy.image
    if (media == null) { proxy.close(); return }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    recognizer.process(image)
        .addOnSuccessListener { text -> try { onLines(toLines(text)) } finally { proxy.close() } }
        .addOnFailureListener { try { onLines(emptyList()) } finally { proxy.close() } }
}
// used from the camera composable:  CameraWithReader(onFrame = { proxy -> reader.read(proxy) { lines -> ... } })
// Inside that last block the frame is still open. To keep the picture of the frame that gave the final
// verdict (for teaching, the look check, the second reader), call proxy.uprightBitmap() there, and only for that frame. (The Teach screen needs it for each frame that matches.) Put the function in ml/UprightBitmap.kt.

fun toLines(text: Text): List<OcrLine> = text.textBlocks.flatMap { block ->
    block.lines.map { line ->
        val box = line.boundingBox
        OcrLine(line.text, box?.left ?: 0, box?.top ?: 0, box?.right ?: 0, box?.bottom ?: 0, box?.height() ?: 0, line.confidence)
    }
}
// also available: line.angle (Float), line.cornerPoints, line.elements, element.confidence, element.symbols, symbol.confidence
// from a bitmap: InputImage.fromBitmap(bitmap, 0)
```
The line boxes are in the coordinates of the upright image (after `rotationDegrees` is applied).

## 5. MediaPipe Image Embedder (`tasks-vision` 1.0.0)
Model file: `mobilenet_v3_small.tflite` (4.1 MB) in `app/src/main/assets/`. Download: `https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite`
```kotlin
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder

val base = BaseOptions.builder().setModelAssetPath("mobilenet_v3_small.tflite").setDelegate(Delegate.CPU).build()
val options = ImageEmbedder.ImageEmbedderOptions.builder()
    .setBaseOptions(base).setRunningMode(RunningMode.IMAGE).setL2Normalize(true).setQuantize(false).build()
val embedder = ImageEmbedder.createFromOptions(context, options)

val vector: FloatArray = embedder.embed(BitmapImageBuilder(bitmap).build()).embeddingResult().embeddings()[0].floatEmbedding()
// keep vectors as FloatArray / List<Float>; cosine = dot / (|a| * |b|) in plain Kotlin
embedder.close()
```

## 6. LiteRT-LM 0.17.1 (Gemma 4 E2B)
```kotlin
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig

// load once, off the main thread; initialize() can take 10 seconds or more
val engine = Engine(
    EngineConfig(
        modelPath = modelFile.absolutePath,
        backend = Backend.GPU(),            // fall back to Backend.CPU() if initialize() throws
        visionBackend = Backend.GPU(),      // must be set, or image input is not loaded
        audioBackend = Backend.CPU(),       // must be set, or audio input is not loaded
        maxNumTokens = 2048,
        cacheDir = context.cacheDir.absolutePath,
    )
)
engine.initialize()

// one conversation per call
val conversation: Conversation = engine.createConversation(
    ConversationConfig(
        systemInstruction = Contents.of(systemText),
        samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0),
        enableResponseFormat = true,        // only when a responseFormat will be passed
    )
)
try {
    // text
    val a: Message = conversation.sendMessage("Did I take my fever tablet?")
    // audio (a whole WAV file: 16 kHz, mono, 16-bit) and a fixed JSON shape
    val b: Message = conversation.sendMessage(
        Contents.of(Content.AudioBytes(wavBytes), Content.Text("Reply with the JSON for this spoken request.")),
        responseFormat = ResponseFormat.json(schemaString),
    )
    // image (PNG or JPEG bytes)
    val c: Message = conversation.sendMessage(Message.user(Contents.of(Content.ImageBytes(pngBytes), Content.Text(question))))
    val replyText: String = c.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.trim()
} finally {
    conversation.close()
}
// stop a call that runs too long: conversation.cancelProcess()
// streaming, if wanted: conversation.sendMessageAsync(Contents.of(...)) returns Flow<Message>
// engine.close() only when the app is being destroyed
```
Rules learned from the library's issue list:
- **Keep one Engine for the whole run.** Deleting and recreating a GPU engine can leave about 2 GB of graphics memory held.
- **Never close a Conversation while a call is running** (crash). Cancel, wait for the call to return, then close.
- Put a time limit on every call. A streaming call's `onDone` has been reported not to fire.
- `sendMessage` blocks its thread. A `withTimeoutOrNull` wrapped straight around it does nothing: in a test, a 200 ms limit around a 1.5-second blocking call returned after 1.5 seconds. So every call goes through the guarded shape below, with a `Mutex` so two calls never overlap.

**The guarded call** (compiled; `ml/Gemma.kt` holds it and every call goes through it):
```kotlin
enum class CallKind { VOICE, SECOND_READER }

/** What one model call gave back. */
sealed interface GemmaReply {
    data class Text(val text: String) : GemmaReply
    data class Failed(val message: String) : GemmaReply      // the call threw
    data object TimedOut : GemmaReply
    data object Busy : GemmaReply                            // not loaded, or a call that would not stop is still inside
}

// inside class Gemma:
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val mutex = Mutex()
@Volatile private var engine: Engine? = null             // set once, after initialize() succeeded
@Volatile private var formatOff = false                  // the fixed JSON shape made a call throw
private val _busy = MutableStateFlow(false)
val busy: StateFlow<Boolean> = _busy                     // a call that would not stop is still inside the engine

/** True when a new call would start at once. The second reader runs only then. */
fun isFree(): Boolean = engine != null && !_busy.value && !mutex.isLocked

/** One call. It may be called from the main thread: the work moves to a background thread. */
suspend fun ask(kind: CallKind, system: String, contents: Contents, schema: String?, timeoutMs: Long): GemmaReply {
    val engine = engine ?: return GemmaReply.Busy
    if (_busy.value) return GemmaReply.Busy
    return mutex.withLock {
        withContext(Dispatchers.Default + NonCancellable) {        // a caller that goes away must not skip the clean-up
            val started = System.currentTimeMillis()
            val conversation = runCatching {
                engine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(system),
                        samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0),
                        enableResponseFormat = schema != null,
                    )
                )
            }.getOrElse { return@withContext GemmaReply.Failed(it.message ?: "could not start") }
            val call = scope.async {                               // the blocking call, on its own thread
                runCatching {
                    val reply: Message =
                        if (schema != null) conversation.sendMessage(contents, responseFormat = ResponseFormat.json(schema))
                        else conversation.sendMessage(Message.user(contents))
                    reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.trim()
                }
            }
            val result = withTimeoutOrNull(timeoutMs) { call.await() }   // the caller waits, with the limit
            if (result == null) {
                runCatching { conversation.cancelProcess() }       // ask the model to stop
                withTimeoutOrNull(3_000) { call.join() }           // give it time to return
            }
            if (call.isCompleted) {
                runCatching { conversation.close() }
            } else {
                _busy.value = true                                 // never close while the call runs
                call.invokeOnCompletion { runCatching { conversation.close() }; _busy.value = false }
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
    }
    return ask(kind, system, contents, null, timeoutMs)
}

private fun record(kind: CallKind, ms: Long, reply: GemmaReply) {
    // GemmaStats: count the call, its time and how it ended, by kind
}
```
Rules for it:
- Copy it whole. Do not simplify it. `engine` is set once, after `initialize()` succeeded; the first line of `ask` takes a local copy.
- Imports besides the LiteRT-LM ones above: `kotlinx.coroutines.CoroutineScope`, `Dispatchers`, `NonCancellable`, `SupervisorJob`, `async`, `withContext`, `withTimeoutOrNull`, `kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.flow.StateFlow`, `kotlinx.coroutines.sync.Mutex`, `kotlinx.coroutines.sync.withLock`.
- `scope.async` is on the class's own scope, as written. Started as a child of the waiting coroutine it would make the wait last as long as the call.
- `NonCancellable` is needed: without it, a screen that closes while a call is running would skip the clean-up and leave the call inside the engine with the lock open. (Tested: with it, a cancelled caller still closes the conversation and frees the lock, and the time limit still works.)
- Never cancel a running call from outside, and never call `cancelProcess()` anywhere else. A caller that no longer wants the answer just ignores it.
- `record(...)` writes into `GemmaStats` (spec 04 section 2).
- The voice call uses `askJson`. The second reader uses `ask` with `schema = null`.

- There is no `message.text` property. Read the text as shown above, or use `message.toString()`.
- `ResponseFormat.json` has no official Kotlin example. If the first call with it throws, switch it off (create conversations with `enableResponseFormat = false`) and parse the first `{...}` in the reply.
- No Gemma 4 NPU file exists for this phone's chip (SM8850). GPU is the fast path; CPU is the fallback.
- Published speed on a phone with the same chip family: about 0.3 s to the first token and about 50 tokens a second on GPU.

WAV bytes from raw PCM:
```kotlin
fun wav(pcm: ByteArray, rate: Int = 16000): ByteArray {
    val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    h.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
    h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
    h.put("data".toByteArray()).putInt(pcm.size)
    return h.array() + pcm
}
```
Recording: `AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)`, with `@SuppressLint("MissingPermission")` after the permission was granted.

## 7. Speech out, files, small things
```kotlin
val HINDI: Locale = Locale.forLanguageTag("hi-IN")          // Locale("hi","IN") is deprecated
val ENGLISH_INDIA: Locale = Locale.forLanguageTag("en-IN")

// The engine starts in the background. One sentence may be asked for before it is ready, so keep it and say it then.
private var ready = false
private var pending: Pair<String, Locale>? = null
private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
    ready = status == TextToSpeech.SUCCESS
    if (ready) pending?.let { (text, locale) -> say(text, locale) }
    pending = null
}
fun speak(text: String, hindi: Boolean) {
    val locale = Locale.forLanguageTag(if (hindi) "hi-IN" else "en-IN")
    if (ready) say(text, locale) else pending = text to locale
}
fun stop() { pending = null; tts.stop() }
fun hasHindiVoice(): Boolean = ready && offlineVoices(HINDI).isNotEmpty()

fun offlineVoices(locale: Locale): List<Voice> = (tts.voices ?: emptySet()).filter {
    it.locale.language == locale.language && !it.isNetworkConnectionRequired &&
        !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
}
// inside say():
offlineVoices(locale).firstOrNull()?.let { tts.voice = it } ?: tts.setLanguage(locale)
tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
    override fun onStart(utteranceId: String?) {}
    override fun onDone(utteranceId: String?) { /* speech finished */ }
    @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) {}
})
tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "answer")
```
```kotlin
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A sentence with one part drawn inside a rounded box that stays on one line. */
@Composable
fun BoxedSentence(sentence: String, boxed: String, textColor: Color, boxText: Color, boxFill: Color, fontSize: TextUnit = 20.sp) {
    val joined = boxed.replace(' ', Char(0x00A0))            // a no-break space, so the box stays on one line
    val shown = sentence.replace(boxed, joined)
    val start = shown.indexOf(joined)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val text = buildAnnotatedString {
        if (start < 0) { append(shown); return@buildAnnotatedString }
        append(shown.substring(0, start))
        withStyle(SpanStyle(color = boxText, fontWeight = FontWeight.Bold)) { append(joined) }
        append(shown.substring(start + joined.length))
    }
    Text(
        text = text,
        color = textColor,
        fontSize = fontSize,
        lineHeight = fontSize * 1.5f,
        onTextLayout = { layout = it },
        modifier = Modifier.padding(horizontal = 4.dp).drawBehind {
            val l = layout ?: return@drawBehind
            if (start < 0 || joined.isEmpty()) return@drawBehind
            val first = l.getBoundingBox(start)
            val last = l.getBoundingBox(start + joined.length - 1)
            val pad = 4.dp.toPx()
            val topLeft = Offset(first.left - pad, first.top + 2.dp.toPx())
            val size = Size(last.right - first.left + 2 * pad, first.height - 4.dp.toPx())
            val corner = CornerRadius(8.dp.toPx())
            drawRoundRect(boxFill, topLeft, size, corner)
            drawRoundRect(boxText, topLeft, size, corner, style = Stroke(width = 1.5.dp.toPx()))
        },
    )
}
```
A screen reads the store with `val data by store.data.collectAsState()` (`androidx.compose.runtime.collectAsState`, compiled). It redraws when the store changes.

```kotlin
// a text file into Download/StripSense (no permission needed). A second save of the same name replaces the first.
// A plain insert would keep the old file and make "week (1).csv", so look for our own file first.
fun saveText(context: Context, name: String, mime: String, text: String): Boolean {
    val folder = Environment.DIRECTORY_DOWNLOADS + "/StripSense"
    val resolver = context.contentResolver
    val bytes = text.toByteArray()
    val old = runCatching {
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?", arrayOf(name, "$folder/"), null,
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)) else null }
    }.getOrNull()
    if (old != null && runCatching { resolver.openOutputStream(old, "wt")?.use { it.write(bytes) } }.isSuccess) return true
    return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, folder)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        resolver.openOutputStream(uri!!)?.use { it.write(bytes) }
    }.isSuccess
}
// (A file of that name left by an earlier install of the app is not ours. Then a "(1)" copy appears. Uninstalling is rare; accept it.)

// pick a file and copy it into the app's own folder
val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri != null) context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it, 1 shl 20) } }
}
picker.launch(arrayOf("*/*"))        // "text/*" for the list file

// dialer, with the number filled in
context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))

// the SMS app, with the text filled in (the person presses send; the app sends nothing)
context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone))).putExtra("sms_body", text))

// JSON store
@Serializable data class StoreData(...)
val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
json.encodeToString(StoreData.serializer(), data); json.decodeFromString(StoreData.serializer(), text)

// copy text to the clipboard (Office Kit then shares it with the laptop)
(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("StripSense", text))

// one short vibration
(context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    .vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))

// the app's own permission list, read at run time (compiled in the practice build)
val permissions: List<String> = context.packageManager
    .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
    .requestedPermissions?.toList() ?: emptyList()

// SIM present?
(context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).simState == TelephonyManager.SIM_STATE_READY

// icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
Icon(Icons.Filled.Check, contentDescription = null)
```
`material-icons-core` 1.7.8 holds only these icons (read from the library file itself). In `Icons.Filled`: AccountBox, AccountCircle, Add, AddCircle, ArrowDropDown, Build, Call, Check, CheckCircle, Clear, Close, Create, DateRange, Delete, Done, Edit, Email, Face, Favorite, FavoriteBorder, Home, Info, KeyboardArrowDown, KeyboardArrowUp, LocationOn, Lock, MailOutline, Menu, MoreVert, Notifications, Person, Phone, Place, PlayArrow, Refresh, Search, Settings, Share, ShoppingCart, Star, ThumbUp, Warning. In `Icons.AutoMirrored.Filled`: ArrowBack, ArrowForward, ExitToApp, KeyboardArrowLeft, KeyboardArrowRight, List, Send.

**There is no microphone, camera, speaker, clock, torch, block or chart icon in it.** Draw those as small `ImageVector`s in `ui/theme/AppIcons.kt` (a 24 x 24 viewport, simple paths), and use them everywhere. Do not add `material-icons-extended` (it is large and slows every build).

## 8. Commands
```
gradlew.bat :app:testDebugUnitTest          run the unit tests
gradlew.bat :app:assembleDebug              build the APK
gradlew.bat :app:installDebug               build and install on the connected phone
adb devices                                 the phone must be listed as "device"
adb shell am start -n app.stripsense/.MainActivity
adb logcat -d -s StripSense:* AndroidRuntime:E
adb push gemma-4-E2B-it.litertlm /sdcard/Download/
```
