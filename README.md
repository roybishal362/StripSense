# StripSense

StripSense reads the print on a medicine strip and checks it against the family's list. It refuses when the brand, generic names, strength or release word differs. It says whether this dose is marked and records a mark only after confirmation.

Build with `gradlew.bat :app:assembleDebug`. To install when ready, connect the phone and run `gradlew.bat :app:installDebug`, then `adb shell am start -n app.stripsense/.MainActivity`. This build needs Android 12 or later; the target demo phone is the iQOO 15. The family PIN starts as `1234`.

Copy `gemma-4-E2B-it.litertlm` to the phone with Office Kit or USB. In Family > Settings, choose the model file. Wait for Ready; GPU is tried first, then CPU. The expected size is 2,588,147,712 bytes. For development, `adb push gemma-4-E2B-it.litertlm /data/local/tmp/` is also supported. The model is separate from the APK. Install offline English and Hindi TextToSpeech voices in Android settings before the demo.

Teach each active camera medicine in Family > Teach. Show a strip until its whole print matches twice, then press Save strip. Save up to four views per medicine. The look check may refuse a print match; it never names a strip on its own.

Long-press Today, choose Seed yesterday, then Fresh day. Run these five checks on real strips:

1. The listed fever tablet: names 500 mg, says the morning dose is not marked, and asks whether to mark it. Yes shows the mark at 8:05 am with Undo.
2. The same strip again: says the morning dose is already marked; no question.
3. Another strength: says “Careful: this strip says…” and refuses.
4. Covered strength: says “I can't read this one. Could you turn it over?”
5. A strip outside the list: says “I don't recognise this one…”

Repeat in Hindi. Hindi speech falls back to English if an offline Hindi voice is missing. Family > Files saves list.csv and week.csv into Download/StripSense. Open `laptop/family.html` offline and drop the exported file onto it. Numbers records trial results without writing dose marks.

The phone uses bundled ML Kit Text Recognition, MediaPipe Image Embedder with MobileNet V3 small, Gemma 4 E2B through LiteRT-LM, and Android TextToSpeech. The APK has no internet permission. Check with `adb shell dumpsys package app.stripsense`; CAMERA, RECORD_AUDIO, VIBRATE and an AndroidX internal receiver permission are expected; INTERNET must be absent.

The app does not give medical advice, identify loose tablets or fake medicines, or support languages beyond English and Hindi. Android Jetpack, CameraX, MediaPipe, MobileNet V3, LiteRT-LM, Gemma 4 E2B, kotlinx.serialization and kotlinx.coroutines use Apache 2.0. ML Kit is a free Google library under the ML Kit terms; it is not open source.

Built at the iQOO Hackathon 2026 Grand Finale, 9 to 11 October 2026. The `docs/` folder holds design notes written before the event. All code was written during the event.

Build status and the deferred testing queue are in `TESTING-HANDOFF.md`.
