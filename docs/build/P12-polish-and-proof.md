# P12. Polish and proof

**Goal:** the app is ready for a judge who has never seen it: nothing rough, nothing confusing, and the claims are visible on screen.

**Read:** `docs/spec/06-screens-and-design.md` (all), `docs/spec/04-voice-and-gemma.md` section 1, `docs/spec/07-verified-apis.md` section 2, and the five tests in the "Done when" list of `docs/build/P06-dose-screens.md`. Build only what this list names; nothing beyond it.

## Build
1. **A first-run check screen** (shown once, and from Family > Settings > "Check this phone"): a list with a tick or a cross and one plain line each: camera permission; microphone permission; an English voice; a Hindi voice; the model file; Gemma loaded and on which backend; the list has medicines; how many are taught; "no internet permission". The screen only reports; where the camera or the microphone is missing it shows the button "Open settings", and "Check again" reads everything anew. The taught line gets a tick when every active camera medicine has a look. `StoreData.checkSeen` remembers that it was shown.
2. **App icon:** a white tick on the brand green, as an adaptive icon: a vector foreground and a colour background in `res/mipmap-anydpi/ic_launcher.xml` (the project has no `res` folder yet; create it), named in the manifest as `icon` and `roundIcon`. App name "StripSense".
3. **Every screen at the largest system font size and at display size "large":** nothing cut off, every screen scrolls. Read each screen file for a fixed height on anything that holds text (use `heightIn(min = ...)` instead) and for a column that cannot scroll, and fix what you find. The developer then checks on the phone with Settings > Display > font size and display size at their largest: Today, Scan, Answer with the question open, Ask, Family (all tabs), Numbers, Help, Demo controls.
4. **Empty states:** every list that can be empty says what to do next in one sentence: Today, Family > List, Teach, Files, the trial pick screen, the trials on Numbers, the conversation on Ask.
5. **Errors:** no screen ever shows an exception text. A failed step says what happened in plain words and what to press.
6. **The Answer screen's timing:** the vibration, the banner and the first spoken word arrive together. No blank frame between Scan and Answer.
7. **The phone's own Back key:** from Scan, Answer and Demo controls it goes to Today; from Ask, Numbers and Family it goes to Today; on the Help card it does nothing (Help closes only with Close); from Today it leaves the app. With the question panel open, Back closes the question with nothing saved.
8. **README.md** in the repository's top folder, in plain words, about one screen long:
   - what the app does, in three sentences
   - how to build and install it (`gradlew.bat :app:installDebug`), and how to put the model file on the phone
   - the five tests and what the phone should say
   - what runs on the phone: ML Kit Text Recognition, MediaPipe Image Embedder, Gemma 4 E2B on LiteRT-LM, Android TextToSpeech, and that the APK has no internet permission (with the check command)
   - what it does not do (medical advice, loose tablets, fake medicines, languages beyond Hindi and English)
   - the open-source parts and their licences, as in the credits list of P07 (ML Kit is named as a free Google library that is not open source)
   - "Built at the iQOO Hackathon 2026 Grand Finale, 9 to 11 October 2026. The `docs/` folder holds design notes written before the event. All code was written during the event."
9. Remove dead code, unused files and leftover placeholder screens. Run a search for `TODO` and `FIXME`: none may remain.

## Do not
- No new features in this stage. No refactoring of working logic.

## Done when
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` succeeds with no warnings about unused code that you added.
- A fresh install (uninstall first; it already holds the demo list) goes: check screen → Family > import the list file if you changed it → teach two strips → the five tests → a voice question → save the week file → Numbers. No crash, no dead end.
- The permission check command still shows CAMERA, RECORD_AUDIO, VIBRATE and no INTERNET. (One more line, an AndroidX permission ending in `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, is expected. "No INTERNET" is the test.)
- A search for `Labels.get("` keys that are not in `ui/Labels.kt` finds none (a missing key would show on screen as the bare key).

## Report
What you changed, screen by screen, and anything you found that still feels rough.
