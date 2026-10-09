# P08. Gemma on the phone: load the model, ask by voice

**Goal:** Gemma 4 E2B loads once, and a spoken or typed question gets a fixed-sentence answer from the dose log.

Do this stage in two halves. Finish and test the first half on the phone before starting the second.

**Read:** `docs/spec/04-voice-and-gemma.md` sections 1 to 6 and 8, `docs/spec/07-verified-apis.md` section 6, `docs/spec/06-screens-and-design.md` (Ask).

## First half: the engine and a typed question
1. **Family > Settings > "Choose the model file":** the file picker, a copy into `<filesDir>/models/gemma-4-E2B-it.litertlm` with a progress bar (the file is 2.6 GB; copy in 1 MB blocks off the main thread), then load. Also the `/data/local/tmp` shortcut of spec 04 section 1.
2. `ml/Gemma.kt`: the single engine, the state (`GemmaState`, exactly as in spec 04 section 2), GPU first then CPU, and the guarded call of spec 07 section 6 copied whole: `CallKind`, `GemmaReply`, `ask`, `askJson`, `isFree`, `busy` (do not simplify it: a plain timeout around `sendMessage` does not work). `ml/GemmaStats.kt` for the counters, `ml/ModelCopy.kt` for the copy, `ml/GemmaInputs.kt` for the three message shapes. Start loading when the app starts if the file exists.
3. `core/VoiceRequest.kt`, `core/Keywords.kt`, `core/VoicePrompt.kt` (the instruction and the schema, copied exactly), `core/VoiceRouter.kt` (the table of spec 04 section 6 as a pure function, one test for each row), `core/Stats.kt` (median, 90th percentile), with their tests (spec 04 sections 4 to 6).
4. **Ask screen** (spec 06): the state chip, the question buttons (they call `DoseEngine` directly, no Gemma), the text box. A typed question goes to Gemma as text with the system instruction and schema of spec 04 section 4; the reply is parsed and answered by the table in section 6. The transcript, the answer lines, the speaker button and the call time are shown as bubbles.
5. Voice calls use `askJson`, which already falls back when the JSON shape makes a call throw. Log which mode is in use.
5b. The Help card's self-harm mode (spec 06, Help card): SH1 as text at the top, the Call 14416 button first.

**Test the first half on the phone before going on:**
- Family > Settings > "Choose the model file": pick the file in Download and wait for the copy.
- The chip goes from "Voice is getting ready" to "Gemma ready (GPU)" (or CPU). Note the load time from the log.
- Type "Did I take my fever tablet?" → the status sentence. Type "What's left today?" → the list. Type "Can I take it with milk?" → "That's one for your doctor or chemist. I don't want to guess." Type "I have chest pain" → the Help card.
- The question buttons work with the model file removed.

## Second half: the microphone
6. `ml/Recorder.kt` (spec 04 section 3, spec 07 section 6): hold to talk, WAV bytes (the header is the pure function in `core/Wav.kt`).
7. The big microphone button on the Ask screen: asks for the permission the first time, pulses while held, then sends the audio to Gemma (audio first, then the short text) and shows "You asked: {transcript}" and the answer.
8. A small microphone button on the Today screen's "Next up" card opens Ask and starts listening (spec 04 section 3 says when that recording stops).
9. `repeat`, `call_family`, `which_one` and the rest of the request table.
10. **Numbers screen (first version):** just the voice part of the Gemma block of spec 05 section 6: backend, load time, voice calls, failures, timeouts, median call time.

## Do not
- Gemma never writes an answer sentence. Its only output is the JSON.
- Never create a second Engine. Never close a conversation while its call runs.
- No audio is saved.

## Done when
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` succeeds. The permission check still shows no INTERNET.
- On the phone, in a quiet spot: five spoken questions in English and three in Hindi ("क्या मैंने बुख़ार की गोली ले ली?", "aur kya bacha hai?", "kaunsi hai?") each show a sensible transcript and the right answer.
- A spoken "haan" with nothing open gets "Sorry, I didn't catch that…" or a status answer, and marks nothing.
- With airplane mode on, everything above still works.

## Report
Backend, load time, the call time for each of the eight questions, and every question that got a wrong request (with its transcript).
