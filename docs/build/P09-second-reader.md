# P09. The second reader: Gemma reads the strength again on every match

**Goal:** after the print rules name a strip, Gemma looks at the same picture and reads the strength independently. The screen shows whether the two readers agree.

**Read:** `docs/spec/04-voice-and-gemma.md` section 7 (and section 2 for the call rules), `docs/spec/06-screens-and-design.md` (Answer, item 4).

## Build
1. `core/SecondReader.kt` (pure, tested): `SecondReaderMode`, and `agrees(reply: String, med: Medicine): Boolean` using `StripParser.strengthsIn` (add it to the parser, spec 01 section 2) and `sameStrengths`. Tests: "500 mg" for a 500 mg medicine → true; " 500 MG\n" → true; "500 mg, 250 IU" for the vitamin → true; "650 mg" → false; "UNREADABLE" → false; "" → false; "The strength is 500 mg." → true (the token is found); "50 mg" → false.
2. In the scan flow, when the final verdict is `Match`: make the crop on the Scan screen while the frame is still open and keep it in `AppState.pendingCrop` (spec 04 section 7 says how). The Answer screen takes it and, right after it has called `speak`, calls `Gemma.ask(CallKind.SECOND_READER, ...)` in the background with the instruction and the question of spec 04 section 7.
3. The Answer screen shows "Second reader is checking…", then "Second reader agrees: 500 mg" with a green tick, or "Second reader could not confirm the strength" in amber. A timeout shows the amber line.
4. **Demo controls:** "second reader: off / on / strict", saved in the store. Strict mode exactly as spec 04 section 7 describes it.
5. Count each outcome in `GemmaStats` and show on the Numbers screen: second-reader calls, agreed, not agreed, unreadable, timeouts, median time, and the last reply that did not agree.
6. Skip the call unless `gemma.isFree()`. A new scan never cancels a call. It ignores the old result, and no new second-reader call starts while the old one is still running (spec 04 section 7).

## Do not
- The second reader never changes a refusal into a name. It only runs after `Match`.
- Its message never contains what ML Kit read.

## Done when
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` succeeds.
- On the phone: scan the taught strip five times. Each time the answer is spoken at once and the second-reader line appears within a few seconds. Write down how many of the five agreed and the times.
- Strict mode on: the question appears only after "agrees".
- A voice question right after a scan still works.

## Report
Agreed out of tries, the median time, and what Gemma replied when it did not agree.
