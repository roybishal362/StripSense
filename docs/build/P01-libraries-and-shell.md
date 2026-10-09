# P01. Libraries, manifest, colours, the empty screens, Help

**Goal:** the app opens with its real frame: top bar, bottom bar, five empty screens, the language switch and a working Help card. All libraries are in and the build is clean.

**Read:** `docs/spec/07-verified-apis.md` sections 1, 2 and 7, `docs/spec/06-screens-and-design.md` sections 1, 2 and the Help card. Look at the pictures in `docs/design/`.

## Build
1. **Gradle files.** Make `gradle/libs.versions.toml`, the root `build.gradle.kts` and `app/build.gradle.kts` match spec 07 section 1 exactly (versions, libraries, plugins, compile SDK 37, min SDK 31, target SDK 36, Java 17). Keep the wizard's other settings. Do not add anything that is not in spec 07.
2. **Manifest** as in spec 07 section 2: the three permissions, the two `tools:node="remove"` lines, the `<queries>` block, the two `uses-native-library` lines, portrait, `allowBackup="false"`.
3. **Theme** in `ui/theme/`: the colour tokens, type sizes and shapes of spec 06 section 1. Light theme only, no dynamic colour. `AppIcons.kt` with the app's own vector icons (microphone, camera, speaker, clock, torch, block, chart).
4. **`AppState.kt`:** one class held by `MainActivity` and passed down: `language` ("en" or "hi"), `screen` (an enum: TODAY, ASK, SCAN, NUMBERS, FAMILY, ANSWER, HELP, DEMO), and a `go(screen)` function. Plain `mutableStateOf`. No navigation library.
5. **The frame** (spec 06 section 2): top bar with the title, the `EN | हिं` switch and the red Help button; bottom bar with Today, Ask, the big round Scan button, Numbers, Family. Labels in both languages from a small `ui/Labels.kt` map (the keys of spec 03 section 5 can be added later; for now only the ones this stage shows).
6. **Five placeholder screens**, each showing its title and one line saying what will be here. These are the only placeholders allowed, and later stages replace them. The screens ANSWER and DEMO have nothing yet; `go()` to them shows Today.
7. **Help card** (spec 06): full screen, the `helpIntro` label, Call 112 opens the dialer with 112 filled in, Close returns to the screen before. The contact button is hidden for now. Nothing is spoken yet (speech comes in P04).
8. A `Log.i("StripSense", "started")` in `MainActivity.onCreate`.

There are no unit tests in this stage.

## Do not
- Do not add a navigation library, Hilt, Room, Retrofit or any library not in spec 07.
- Do not write camera, OCR or model code yet.

## Done when
- `gradlew.bat :app:assembleDebug` succeeds with no errors.
- The permission check in spec 07 section 2 lists CAMERA, RECORD_AUDIO, VIBRATE and no INTERNET.
- On the phone: the bottom bar switches screens; the language switch changes the labels to Hindi and back; Help opens the card; Call 112 opens the dialer showing 112 (do not press call).

## Report
The files you created, and the output of the permission check.
