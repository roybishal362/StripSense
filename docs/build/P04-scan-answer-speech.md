# P04. Scan to answer, spoken in English

**Goal:** hold up a strip and the phone decides, shows the answer screen and speaks. After this stage the app passes tests 3, 4 and 5, and names the strip in test 1.

**Read:** `docs/spec/03-sentences.md` (all of it; the table goes in in both languages, the voice is English in this stage), `docs/spec/01-reading-and-matching.md` section 5, `docs/spec/06-screens-and-design.md` (Scan, Answer), `docs/spec/05-files-and-family.md` section 2, `docs/spec/07-verified-apis.md` section 7 (speech). Look at `docs/design/answer-*.png`.

## Build
1. `data/DemoData.kt`: the demo medicines, **copied row by row from `docs/sample/list.csv`** (that file holds the real strips on the table; its columns are explained in spec 05 section 3), and the person (`Person()` with its defaults: Maa, Rahul), as plain Kotlin values. `AppState` holds the medicine list in memory for now (the store comes in P05). The unit tests keep using their own made-up list M1 to M5 from spec 01.
2. `core/Person.kt`, `core/Sentences.kt` and `core/Words.kt`: the types of spec 03 section 0 exactly as written there (later stages use them), the sentence table for **every ID in spec 03 sections 2 and 3, in both languages** (type the Hindi exactly as written in the spec), and `render(parts, lang, person)` returning shown and spoken text for each part. Numbers, times, `{ago}`, `{when}` and units follow spec 03 section 1. Unit tests: the eight checks of spec 03 section 6.
3. `core/Answers.kt`: `forVerdict(verdict, unreadableCount): List<Part>` using the table in spec 03 section 4. For `Match` return only `NAME` in this stage (the dose answer comes in P06); its banner is the grey-green one with the nickname and no caption. The count of Unreadable verdicts follows spec 03 section 4.
4. **Scan screen:** feed each frame's lines to `StripParser.parse` and then `ScanSession` (with `StripParser.smallestKeyLinePx`, which this stage adds to the parser with one test). Show the session's hint. On `Final(verdict)`: one short vibration, stop analysing, go to the Answer screen with the verdict.
5. **Answer screen** as in spec 06: the verdict banner, the medicine card where the table says so, the "StripSense says" card with the rendered lines (the `{printed}` part of V1 in its red box: `BoxedSentence` from spec 07 section 7), the speaker button, and "Scan another" at the bottom. A second and third Unreadable in a row use C2 and C3.
6. `ml/Speaker.kt` as in spec 07 section 7 (`speak(text, hindi)`; it keeps one sentence while the engine is still starting): speaks the joined "speak" text when the Answer screen opens, in English (`en-IN`). The speaker button repeats it. Leaving the screen stops it. The Help card's Call 112 button now says EM1.
7. Labels for this stage from spec 03 section 5, both languages (the screen titles and captions switch with the language switch; the answer lines switch too, since the Hindi table is already in).

## Do not
- No dose status, no question, no marking, no store file yet.
- No answer text outside `Sentences`.

## Done when
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` succeeds.
- On the phone, with the real strips that are in `docs/sample/list.csv`:
  - a strip on the list → its banner, its medicine card, and "That's your {nickname}, {strength}." spoken
  - the other strength of the same brand → the red "Not on your list" banner and "Careful: this strip says … but yours is …"
  - a strip with the strength covered by a thumb everywhere it is printed, or a cut piece without it → "I can't read this one. Could you turn it over?" after about a second
  - a strip that is not on the list → "I don't recognise this one. It isn't on anyone's list here."
- Scan another → the camera is live again every time.

## Report
For each of the four cases: what the phone said and roughly how long it took.
