# Spec 04. Gemma on the phone: voice questions and the second reader

Gemma 4 E2B runs on the phone through LiteRT-LM. It has two jobs:
1. **Hear a spoken question** and turn it into one request from a fixed list. It never writes the answer. The answer is a fixed sentence from spec 03.
2. **Read the strength on a strip a second time**, from the picture, after the print rules have decided.

API shapes are in spec 07. Use them exactly.

## 1. The model file
- File: `gemma-4-E2B-it.litertlm` (2,588,147,712 bytes). It holds the text, vision and audio parts. Do not use the `-gpu` file (text only).
- It cannot be inside the APK. The app looks for it at `<filesDir>/models/gemma-4-E2B-it.litertlm`.
- **Family > Settings > "Choose the model file"** opens the system file picker (`ACTION_OPEN_DOCUMENT`), copies the chosen file into `<filesDir>/models/` with a progress bar, then loads it. If the chosen file is not exactly 2,588,147,712 bytes, show the label `wrongModelSize` and go on. If an engine is already loaded, the new file is used from the next start of the app (the engine is never recreated while the app runs). The file gets onto the phone by Office Kit file transfer or `adb push` into Downloads.
- A developer shortcut: if `/data/local/tmp/gemma-4-E2B-it.litertlm` exists and is readable, use it directly.

## 2. The engine
- **One `Engine` for the whole app life.** Create it once in `ml/Gemma.kt`, on a background thread, when the app starts and the model file exists. Never close and recreate it while the app runs.
- Try the GPU first: `backend = GPU`, `visionBackend = GPU`, `audioBackend = CPU`, `cacheDir = context.cacheDir.absolutePath`, `maxNumTokens = 2048`. If `initialize()` throws, try again with `backend = CPU`, `visionBackend = CPU`. Record which one loaded ("GPU" or "CPU") and how long it took.
- State for the UI, as `Gemma.state: StateFlow<GemmaState>`: a `sealed interface GemmaState` with `NoFile`, `Loading`, `Ready(backend: String, loadMs: Long)` and `Failed(message: String)`. A small chip on the Ask screen shows it. While not READY, the Ask screen shows V4 and the question buttons work without Gemma.
- **One call at a time.** A `Mutex` guards the engine. A voice question waits for a running second-reader call to finish, and the other way round. Never close a conversation while a call is running.
- **Every call goes through the guarded call of spec 07 section 6,** copied exactly: `Gemma.ask(kind, system, contents, schema, timeoutMs)`, or `Gemma.askJson(...)` for the voice call. Both return a `GemmaReply` (`Text`, `Failed`, `TimedOut`, `Busy`), so the caller can tell a timeout from a thrown call. They may be called from the main thread.
- **Files:** `ml/Gemma.kt` (the engine, its state, the guarded call), `ml/GemmaStats.kt`, `ml/ModelCopy.kt` (the copy with its progress), `ml/GemmaInputs.kt` (the three `Contents.of(...)` shapes for audio, typed text and a picture, so that no screen imports LiteRT-LM). `AppState` holds the one `Gemma`, the one `Speaker` and the `Store`: `AppState(store, speaker, gemma)`.
- **Every call makes a new `Conversation`** with its own system instruction, sends one message and reads the reply. The guarded call closes the conversation once the call has returned.
- Sampler for every call: `topK = 1, topP = 1.0, temperature = 0.0`.
- **Watchdog:** the model call blocks its thread, so the guarded call runs it on its own thread and waits `tuning.gemmaTimeoutMs` for it (default 8,000; set from measured times). After that it calls `cancelProcess()`, waits up to 3 seconds for the call to return, and closes the conversation only once the call has returned. While a call that would not stop is still inside the engine (`busy`, a `StateFlow<Boolean>` the chip can watch), new calls return `Busy` at once: a voice question gets V0 and the question buttons, and the second reader shows its amber line. The chip on the Ask screen shows "Voice is getting ready" during that time.
- **Count every call** in `GemmaStats` (kept in memory, in `ml/Gemma.kt`): for each kind (`enum class CallKind { VOICE, SECOND_READER }`) the calls, failures, timeouts and the list of call times in milliseconds, so a median can be shown. For the second reader also: agreed, not agreed, unreadable. The Numbers screen shows them.

## 3. Recording a question
- Permission RECORD_AUDIO, asked the first time the microphone button is pressed.
- `AudioRecord`, source `VOICE_RECOGNITION`, 16,000 Hz, mono, 16-bit PCM.
- **Hold to talk:** recording starts when the big microphone button is pressed and stops when it is released, or at 10 seconds. Under 400 ms of audio is ignored, and nothing is shown for it.
- The press that opens the permission dialog records nothing. The person presses again after allowing.
- The small microphone on the Today card (from P08) opens Ask and starts recording at once (after the permission, if it had to be asked). That recording stops when the big microphone is tapped, or at 10 seconds.
- The PCM gets a 44-byte WAV header and goes to Gemma as `Content.AudioBytes`. The audio is not saved anywhere.

## 4. The voice call
**System instruction** (one string; `{medicines}` is the numbered nickname list of the person's medicines, ACTIVE and STOPPED, in English and Hindi, for example `1. fever tablet / बुख़ार की गोली; 2. vitamin tablet / विटामिन की गोली`):

```
You turn one short spoken request from an older person in India into JSON. The speech may be English, Hindi or a mix. You never answer the request yourself and you never give medical advice.
Choose exactly one "request":
- "status_of": asks whether a medicine was taken or marked, or whether it is due. Example: "Did I take my fever tablet?", "क्या मैंने बुख़ार की गोली ले ली?"
- "whats_left": asks what is still left or due today. Example: "What's left today?", "aur kya bacha hai?"
- "which_one": asks which strip this is or wants a strip checked. Example: "Which one is this?", "kaunsi hai?"
- "which_is_x": asks which strip is a named medicine. Example: "Which one is my BP tablet?"
- "taken_now": says they are taking or have just taken a medicine. Example: "I'm taking it now", "le rahi hoon"
- "yes_no": only a yes, a no, or not sure. Example: "haan", "nahin", "pata nahin"
- "missed_dose": asks what to do about a missed or late dose.
- "extra_dose": says they took an extra tablet or too many.
- "call_family": wants to call or talk to family.
- "repeat": wants the last answer again.
- "medical_question": any question about health, side effects, food, or whether to take something.
- "emergency": chest pain, cannot breathe, a fall, fainting, bleeding, or any urgent health problem.
- "self_harm": wants to hurt or kill themselves, or to take all their tablets to end it.
- "other": a greeting, a thank you, or anything that is none of the above.
The person's medicines are: {medicines}
"medicine": the number of the medicine the speaker names, or 0 if none is clearly named.
"answer": for yes_no only: "yes", "no" or "not_sure". Otherwise "none".
"transcript": the words that were said. Write Hindi words in Devanagari.
"emergency": true if the speech mentions any urgent health problem, even inside another request.
Output only the JSON object.
```

**User message:** `Contents.of(Content.AudioBytes(wav), Content.Text("Reply with the JSON for this spoken request."))`. For a typed question: `Contents.of(Content.Text(typed))`, with the same instruction and schema.

**The order for any question, spoken or typed:** (1) for typed text, the keyword check and the greeting and thank-you word lists run first, with no model; (2) if nothing matched and Gemma is not Ready, say V4; (3) otherwise call `askJson`; (4) parse the reply. When parsing fails, still run the keyword check on the raw reply text before saying V0, so that an emergency word is never lost.

**Response format** (`ConversationConfig(enableResponseFormat = true)`, then `responseFormat = ResponseFormat.json(schema)`):
```json
{
  "type": "object",
  "properties": {
    "request": {"type": "string", "enum": ["status_of", "whats_left", "which_one", "which_is_x", "taken_now", "yes_no", "missed_dose", "extra_dose", "call_family", "repeat", "medical_question", "emergency", "self_harm", "other"]},
    "medicine": {"type": "integer", "minimum": 0, "maximum": 20},
    "answer": {"type": "string", "enum": ["yes", "no", "not_sure", "none"]},
    "transcript": {"type": "string"},
    "emergency": {"type": "boolean"}
  },
  "required": ["request", "medicine", "answer", "transcript", "emergency"]
}
```
If constrained decoding fails to start on the phone, catch the error once, turn it off for the session (plain `sendMessage`), and parse the first `{...}` in the reply.

**Parsing** (`core/VoiceRequest.kt`, pure, unit-tested): `@Serializable data class VoiceRequest(val request: String, val medicine: Int = 0, val answer: String = "none", val transcript: String = "", val emergency: Boolean = false)` with `VoiceRequest.parse(text: String, medicineCount: Int): VoiceRequest?` in its companion. Find the first `{` and the last `}`, parse with kotlinx.serialization (ignore unknown keys). Any failure, an unknown request, or a medicine number outside 0..count → `null` (the caller says V0). There are 13 real requests; "other" is the model's way to say "none of them", so that a greeting is not forced into a wrong request. Tests: a clean object; an object with text around it; a broken object; `medicine` 7 with 5 medicines; an unknown request.

## 5. The keyword check (`core/Keywords.kt`, pure, unit-tested)
Runs on the transcript of every call, and on typed questions. It does not need Gemma to be right about `request`.
- **Normalise** both the transcript and the keywords: lowercase, trim, collapse spaces; for Devanagari remove the nukta (`़`) and map chandrabindu (`ँ`) to anusvara (`ं`).
- **Emergency, English:** chest pain, can't breathe, cannot breathe, breathless, fell down, i fell, fainted, bleeding, heart attack, stroke, unconscious.
- **Emergency, Hindi:** सीने में दर्द, छाती में दर्द, सांस नहीं, गिर गया, गिर गई, चक्कर, बेहोश, खून, बचाओ.
- **Emergency, romanised:** seene mein dard, chhati mein dard, saans nahi, sans nahi, gir gaya, gir gayi, chakkar, behosh, khoon, bachao.
- **Self-harm:** kill myself, end my life, want to die, don't want to live, marna chahta, marna chahti, jeena nahi chahta, jeena nahi chahti, मरना चाहता, मरना चाहती, जीना नहीं चाहता, जीना नहीं चाहती.
- **Must not trigger:** "did I take all my tablets", "what's left today", "सुबह की सारी गोलियाँ ले लीं?".
- A keyword hit, or `emergency: true`, or request `emergency` → the Help card opens and HLP1 is spoken. A self-harm hit or request → the Help card opens with SH1 and the 14416 button first.

## 6. From request to answer (`ui/AskScreen` calls `core`)
| Request | Answer |
|---|---|
| status_of with a medicine | `DoseEngine.statusAnswer` (spec 02 section 8) |
| status_of with no medicine | `DoseEngine.whatsLeft` |
| whats_left | `DoseEngine.whatsLeft` |
| which_one | V5, then open the Scan screen when the sentence has been spoken (or after 3.5 seconds) |
| which_is_x with a medicine | CAMERA: show its taught photo and say V6. BUTTON: V9 |
| taken_now, yes_no | With no question open (always the case on the Ask screen): `statusAnswer` for the named medicine, or V0 if none. Nothing is marked. The other case, a spoken yes, no or not sure while "Are you taking it now?" is open, is reached only by the optional listen of section 8: yes marks it (spec 02 section 6), no is NO1, not sure is NS3 |
| missed_dose | MD1 |
| extra_dose | MD1, then CL1 with a Call button |
| call_family | CL1 with a Call button (opens the dialer). When no contact phone is set, CL1 is not used: the Help card opens and HLP1 is said |
| repeat | speak the last answer again; RP0 if there is none |
| medical_question | MQ1 |
| emergency, self_harm | section 5 |
| other | a greeting ("hello", "namaste") → HELLO; a thank you → THX; anything else → V0. The check is a small word list in `core/Keywords.kt` run on the transcript |
| no parse, a timeout | V0. The question buttons stay on screen |

The Ask screen always shows **question buttons** that work without Gemma: "What's left today?", and one "Did I take my {nickname}?" per medicine. It also has a text box for a typed question (for demos in a noisy hall): the typed text goes to Gemma as `Content.Text` with the same instruction and schema.

The screen shows: the transcript under "You asked", the answer lines, a speaker button, and the time the call took in small grey text (the whole wait, including any time spent waiting for another call to finish).

The table above is a pure function, `core/VoiceRouter.kt`, with one test for each row. The instruction text and the schema live in `core/VoicePrompt.kt`. The WAV header is `core/Wav.kt`. `core/Stats.kt` has the median and the 90th percentile.

## 7. The second reader (after verdict Match only)
- Runs in the background after the answer is on screen and speech has started. It never delays the answer.
- **Picture:** the analysed frame, turned upright and cropped with the same box as the look check (`LookCheck.cropBox`: around all read lines plus 12% on each side; the whole frame if the box is under 8 px), scaled down so that the longest side is at most 896 px (never scaled up), PNG bytes → `Content.ImageBytes`.
- **Where the picture is made:** on the Scan screen, at the moment the verdict becomes final, while the frame is still open. One `uprightBitmap()` for that frame serves both the look check and this crop. The crop is kept in `AppState` (`pendingCrop`) and taken by the Answer screen, which starts the call right after it has called `speak`.
- **The call:** `Gemma.ask(CallKind.SECOND_READER, system, contents, schema = null, timeoutMs)`. Plain text, no JSON shape.
- **System instruction:** `You read printed text on medicine strips. You answer with what is printed and nothing else.`
- **User message:** the image, then `Content.Text("Read the strength printed on this strip: the number and its unit, for example 250 mg. If there are several, give them all separated by commas. If any digit is unclear, reply UNREADABLE.")`. The message never contains what ML Kit read.
- **Compare** (`core/SecondReader.agrees(reply, med)`, pure, unit-tested): `StripParser.strengthsIn(reply)` (spec 01 section 2), and require `sameStrengths` with the medicine's strengths. The word `UNREADABLE` anywhere in the reply, no strength, or a different set → not agreed.
- **The outcome** of each run is one of `agreed`, `not_agreed`, `unreadable`, `timeout`, `failed`, `skipped`. It goes into `GemmaStats` and into the `Trial` of trial mode.
- **Shown on the Answer screen** under the answer card: "Second reader is checking…", then "Second reader agrees: 500 mg" (`okText` on `okBg`, the Check icon) or "Second reader could not confirm the strength" (`dueText` on `dueBg`, the Warning icon). When the call was skipped and strict mode is off, nothing is shown.
- **The mode** is `enum class SecondReaderMode { OFF, ON, STRICT }` in `core/SecondReader.kt`, saved in the store, `ON` by default, changed in Demo controls.
- **Strict mode:** the question "Are you taking it now?" appears only after the second reader agrees. In strict mode Q1 is left out of the first spoken answer. When the spoken answer has ended (or after 6 seconds) and the outcome is `agreed`, Q1 is said and the question panel opens. Any other outcome, `skipped` included: the question is not asked, and C3 is added to the card and said.
- Every result is counted for the Numbers screen: agreed, not agreed, unreadable, timeout, milliseconds. The first 60 characters of the last reply that did not agree are kept in memory and shown there (`lastNotAgreed`). It is a strength, never audio or a transcript.
- Skipped unless `gemma.isFree()` is true: Gemma is Ready and no call is running or waiting (a voice question always goes first). A new scan does not cancel an old call: its result is ignored, and the call ends by itself or at its time limit.

## 8. Speaking (`ml/Speaker.kt`)
- Android `TextToSpeech`. Pick an offline voice for the language (spec 07). English uses `en-IN`, Hindi uses `hi-IN`. If no Hindi voice is installed, speak English text and show the Hindi text; the Family > Settings screen says which voices were found.
- The spoken text is the "speak" form from `Sentences.render` (numbers and times in words), all parts joined with a space, as one utterance.
- `Speaker.hasHindiVoice()` is true when `offlineVoices` finds a Hindi voice. The caller renders the answer in the language to be spoken: Hindi when the app is in Hindi and that is true, otherwise English, and calls `speak(text, hindi)`.
- A new answer stops the previous one. Leaving the Answer or Ask screen stops speech, with one exception: when the answer itself opens the Scan screen (V5) or the Help card (HLP1, SH1), the sentence is spoken to its end.
- When speech of a strip answer that ends in Q1 finishes (`onDone`), the Answer screen may start a 5-second listen for a spoken yes or no. This is optional (build it last); the buttons always work.
