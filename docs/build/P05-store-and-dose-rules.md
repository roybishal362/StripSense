# P05. The store and the dose rules (pure Kotlin, with tests)

**Goal:** the app remembers its data in one file, and `DoseEngine` answers "what is the state of this dose?" for any medicine and time, proven by unit tests. No new screens in this stage.

**Read:** `docs/spec/02-dose-rules.md` (all), `docs/spec/05-files-and-family.md` section 1.

## Build
1. `core/StoreModels.kt` (a new file): `DoseEvent`, `Look`, `Trial`, `StoreData` (spec 02 section 3, spec 05 sections 1 and 6). All `@Serializable`. `Person` already exists from P04.
2. `core/AppClock.kt`: `class AppClock(var offsetMs: Long, private val system: () -> Long)` with `now()`. Everything that needs the time takes it as a parameter or from this clock. Tests pass a fixed time.
3. `core/DoseEngine.kt`, a class with pure functions (signatures as written in spec 02; the small result types go in `core/DoseTypes.kt`), each with the spec section in a comment:
   - `dosingDate`, `slotsFor(med, date)`, `window(slot)`, `halfInterval(slot)` (section 2)
   - `slotState`, `lastMark`, `recentMark` (section 3)
   - `thisDose(med, now, events)` (section 4)
   - `stripAnswer(med, now, events)` → parts, `askNow`, `slotKey` (section 5)
   - `answerPrompt(...)` and `undo(...)` → the new event (if any) and the parts to say (section 6)
   - `markButton(...)` and `confirmMark(...)` (section 7)
   - `statusAnswer(...)` and `whatsLeft(...)` (section 8)
   - `freshDay(...)` and `seedYesterday(...)` → the events to append (section 9)
4. `data/Store.kt`: spec 05 section 1. Loads `store.json` at start (or starts with the demo list and person), exposes `StateFlow<StoreData>`, and has small update functions (`addEvent`, `setLanguage`, `setMedicines`, `setTuning`, `setClockOffset`, ...) that each write the file through a temp file. `MainActivity` creates the `Store` with `filesDir` and gives it to `AppState`. `AppState` now reads medicines, language, tuning and the clock offset from the store, and the language switch calls `setLanguage`.
5. **Tests** `DoseEngineTest`: every vector T1 to T18 of spec 02 section 10 (T10b to T10e too), one test each, named with its number. The tests build the engine with `ZoneId.of("Asia/Kolkata")`. Check the values of the parts (time, ago, slot), not only their IDs. Also the window table of section 2 (four rows) as a test. `StoreTest`: `StoreData` survives encode and decode; a `Store` that is closed and opened again on the same folder still has the language, the events and the clock offset; a broken file falls back to `fresh`.

## Do not
- No UI changes beyond reading from the store.
- Events are never removed or edited. Undo and Fresh day only append.

## Done when
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` succeeds. At least 20 new tests.
- On the phone: change the language, close the app from recents, open it again. The language is still the one you chose.

## Report
The test count, and any vector where the spec was unclear.
