# P07. The family's side: the list file, teaching strips, the look check

**Goal:** the family can import their list, teach each strip, and the app refuses a strip that reads right but was never shown to it.

**Read:** `docs/spec/05-files-and-family.md` sections 1 to 4, `docs/spec/01-reading-and-matching.md` section 6, `docs/spec/06-screens-and-design.md` (Family), `docs/spec/07-verified-apis.md` sections 5 and 7.

## Build
1. **PIN gate** for the Family screen: a 4-digit keypad, asked once per app start, default 1234 from the store. The PIN is always 4 digits.
2. `core/Csv.kt` (pure, tested): read and write `list.csv` as in spec 05 section 3, with the checks listed there. Tests: a round trip of the demo list; a quoted cell with a comma; Hindi text; a missing column; a bad time; a CAMERA row with no strength.
3. **Family > List:** the medicines as cards; "Load the demo list"; "Import a list file" (file picker → check → preview → Save, with the keep-or-drop rule for looks); "Save the list file" to `Download/StripSense/list.csv`.
4. `ml/LookEmbedder.kt` exactly as in spec 07 section 5. The model file `mobilenet_v3_small.tflite` is already in `app/src/main/assets/` (if it is missing, stop and say so; do not download anything).
5. **Family > Teach** (spec 05 section 4): pick a medicine → camera with the same reader → the typed row and the live read side by side → Save enabled only on a steady `Match` for this medicine (spec 05 section 4, step 3) → on Save store the photo, the vector and the read lines as a `Look`. The "can't be taught" line for a Different verdict. Up to 4 looks. "Remove looks". The stopwatch.
6. **Look check in scanning** (spec 01 section 6): after `Match(med)`, apply the three cases (teaching not in use → Match; no look for this medicine → NotTaught; otherwise cosine against the saved vectors with `tLook`). The crop is the box around all read lines plus 12%, from `ImageProxy.uprightBitmap()` (spec 07 section 3; put it in `ml/UprightBitmap.kt`). The cosine, the three cases and the crop box are pure functions in `core/LookCheck.kt`, with tests. Keep the last score in memory for the Numbers screen later.
7. **Demo controls:** add the "look check on/off" switch and `tLook`.
8. **Family > Settings:** the person's call name (English and Hindi), "use the woman's forms", the contact's name and phone, the PIN, the voices found, "Open-source credits": a plain list, one line each. Under the Apache 2.0 licence: Android Jetpack and CameraX; MediaPipe Image Embedder with the MobileNet V3 model; LiteRT-LM; the Gemma 4 E2B model; kotlinx.serialization and kotlinx.coroutines. Then one more line: ML Kit Text Recognition is a free Google library under the ML Kit terms; it is not open source. This tab has three sub-tabs for now (List, Teach, Settings); Files comes in P10.

## Do not
- The look never names a strip. It can only turn Match into NotShown.
- Importing never changes events.

## Done when
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` succeeds.
- On the phone:
  - Save the list file, open `Download/StripSense/list.csv` in the Files app: it lists the five medicines.
  - Teach the fever tablet with its real strip: Save turns on only when the print matches, and the card then shows "Taught" with the photo.
  - Scan that strip → the normal answer. Scan a strip of the same medicine and strength from another brand → "This looks like your fever tablet, 500 mg, but it's a strip I haven't seen before…"
  - Scan a listed medicine that was not taught → "This one hasn't been taught yet…"
  - Try to teach the 650 strip as the fever tablet → the "can't be taught" line, nothing saved.
  - Turn the look check off in Demo controls → the taught strip still gets its normal answer.

## Report
The look scores you saw for a taught strip and for a different strip (two or three numbers each), so the threshold can be set.
