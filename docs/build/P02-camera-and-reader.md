# P02. The camera and the text reader, with a debug list

**Goal:** the Scan screen shows the live camera, reads the print on every frame with ML Kit, and can list exactly what it read. No rules yet. This screen is how the real strips get measured on Friday night.

**Read:** `docs/spec/07-verified-apis.md` sections 3 and 4, `docs/spec/06-screens-and-design.md` (Scan), `docs/spec/01-reading-and-matching.md` section 1 (only the `OcrLine` type).

## Build
1. `core/Models.kt`: only `OcrLine` for now (spec 01 section 1).
2. `ml/OcrReader.kt`: wraps the ML Kit recogniser exactly as in spec 07 section 4. One function takes an `ImageProxy`, gives `List<OcrLine>` to a callback, and closes the frame right after the callback returns. Never run two frames through ML Kit at once: if one is in flight, close the new frame at once.
3. `ui/ScanScreen.kt`:
   - asks for the camera permission the first time; if refused, one line of explanation and a button to the app's settings page
   - the camera composable of spec 07 section 3, copied as written (analysis size 1920 x 1440, keep-only-latest, the `onCamera` callback for the torch)
   - dark background, the white rounded guide frame, the hint line under it, a torch button
   - a "Show read text" toggle. When on, a panel over the lower half lists every line of the latest frame: `text · height in px · confidence with two decimals`, smallest height last, plus one summary line: `lines N · frames per second F · image WxH` (F = frames ML Kit finished in the last second; W and H = the upright image)
   - a "Copy" button on that panel puts the listed lines on the clipboard (so they can be pasted to the laptop over Office Kit)
4. Keep the screen on while this screen is open.
5. Log one line per second with the tag `StripSense`: frames per second and the number of lines. Never log the text itself.

There are no unit tests in this stage. The labels it needs are in spec 03 section 5 (`guideHint`, `reading`, `cameraNeeded`, `openSettings`, `torch`, `showText`, `copy`).

## Do not
- No parsing, no matching, no speech in this stage.
- Do not save frames.

## Done when
- `gradlew.bat :app:assembleDebug` succeeds.
- On the phone: Scan shows the camera at once. With "Show read text" on, holding a medicine strip or any printed page in the frame lists its lines within a second, and the list follows as the page moves.
- The torch button works.
- Leaving the Scan screen and coming back works three times in a row with no black preview.

## Report
The analysis image size the phone really gave (from the summary line), and the frames per second you saw in the log.
