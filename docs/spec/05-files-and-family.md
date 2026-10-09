# Spec 05. Storage, the family's files, teaching, trials

## 1. What is stored on the phone (`data/Store.kt`)
One JSON file, `<filesDir>/store.json`, read once at start and kept in memory as a `StateFlow<StoreData>`. Every change writes the whole file to `store.json.tmp` and renames it over the old one. kotlinx.serialization, `ignoreUnknownKeys = true`.

```
StoreData(
  person: Person,                     // spec 03 section 0; it exists since stage P04
  language: "en" | "hi",
  medicines: List<Medicine>,          // spec 01
  events: List<DoseEvent>,            // spec 02, append-only
  looks: List<Look>,                  // taught strips
  trials: List<Trial>,                // section 6
  tuning: Tuning,                     // every threshold, changeable in Demo controls
  clockOffsetMs: Long,
  lookCheckOn: Boolean = true,        // Demo controls
  secondReader: SecondReaderMode = ON, // OFF, ON or STRICT (spec 04 section 7; Demo controls)
  listSavedAtMillis: Long = 0,        // set when the list is imported, loaded or changed
  checkSeen: Boolean = false          // the first-run check screen was shown (stage P12)
)
Look(id, medId, vector: List<Float>, photoFile: String, readLines: List<String>, taughtAtMillis)
```
`class Store(dir: File, fresh: () -> StoreData)`: `dir` is the app's files folder, and `fresh` gives the first data when there is no file yet or it cannot be read (the demo list and the default person). `MainActivity` creates the one `Store` as `Store(filesDir, DemoData::fresh)` and gives it to `AppState` (`AppState(store, speaker)` from P04, `AppState(store, speaker, gemma)` from P08). Screens read it with `store.data.collectAsState()` (spec 07 section 7), so they redraw when the store changes. `setMedicines(list, savedAtMillis)` also sets `listSavedAtMillis` (the caller passes the app clock's time). It has `val data: StateFlow<StoreData>` and one small function for each kind of change (`addEvent`, `addEvents`, `setLanguage`, `setMedicines`, `setPerson`, `setTuning`, `setClockOffset`, and later `addLook`, `addTrial`).

Taught photos are JPEG files in `<filesDir>/looks/`. Nothing else is written. Camera frames and audio are never saved.

## 2. The demo list (`data/DemoData.kt`)
"Load the demo list" (Family screen and Demo controls) replaces the medicines with the rows of `docs/sample/list.csv`, which `DemoData.kt` holds as Kotlin values: fever tablet / बुख़ार की गोली · vitamin tablet / विटामिन की गोली · BP tablet / बीपी की गोली (stopped) · sugar tablet / शुगर की गोली · eye drops / आँख की दवा (button). Note on the fever tablet: "after food" / "खाने के बाद".

Before the event that file holds made-up brands. At the event its brand, generic and strength columns are changed to the real strips on the table (see `docs/sample/README.md`).

## 3. The list file (`list.csv`) and its import
UTF-8, comma-separated, one header row, one row per medicine. Inside a cell, `;` separates several values. Quotes follow the usual CSV rule. `core/Csv.kt` reads and writes it (pure, unit-tested: a round trip, a quoted cell with a comma, Hindi text, a missing column).

| Column | Example |
|---|---|
| nickname_en | fever tablet |
| nickname_hi | बुख़ार की गोली |
| type | CAMERA or BUTTON |
| brand | DOLO |
| generics | PARACETAMOL (several: `CALCIUM;VITAMIN D3`) |
| strengths | `500 mg` (several: `500 mg;250 IU`) |
| release | empty, or SR |
| extra_words | empty, or `D` |
| times | `08:00;14:00;20:00` |
| note_en | after food |
| note_hi | खाने के बाद |
| status | ACTIVE or STOPPED |
| stopped_on | empty, or 2026-10-01 |
| colour | #C0562D |

**Import (Family screen, behind the PIN):** the system file picker (`ACTION_OPEN_DOCUMENT`, `text/*`). The file is checked first: the header must have every column; each row needs a nickname, a type, and for CAMERA a brand, at least one generic and at least one strength that parses; times must be `HH:mm`. Any bad row → nothing is imported and the screen lists the bad rows with the reason (a short English line such as "row 3: no strength"; the same in both languages).
- A strength parses when it is 1 to 4 digits, an optional decimal part with a point (`0.5`, never `0,5`), an optional space, and one of `mg`, `mcg`, `g`, `IU`, `ml`, `%` in any letter case.
- `release` is empty or one of the codes of spec 01 section 1. `type` and `status` are read in any letter case. An empty `colour` becomes `#2F6F9F`.
- **The id of an imported medicine:** a row keeps the id of the current medicine that has the same `nickname_en`. A row with a new nickname gets an id made from it (lower case, every run of other characters turned into `-`). So renaming a nickname makes a new medicine, and its old marks stay in the log under the old id. A good file → the screen shows the list it is about to save, and **Save** replaces the medicines. A medicine (the same id) keeps its taught looks when its brand, generics, strengths and release are unchanged; otherwise its looks are dropped and it must be taught again. "Load the demo list" follows the same rule.

**Export:** "Save the list file" writes `list.csv` to `Download/StripSense/` through `MediaStore.Downloads` (spec 07), and a toast names the folder. Office Kit and the Files app can see that folder.

## 4. Teaching a strip (Family > Teach, behind the PIN)
1. Pick a CAMERA medicine from the list.
2. The camera opens with the same reader as the Scan screen. The screen shows the typed row and what is being read, side by side.
3. The **Save this strip** button is enabled only while the last two frames gave the same `ReadKey` and `Decider.decide` gives `Match` for **this** medicine. (`ScanSession` is not used here: it stops at its first result, and this screen keeps reading.)
4. If the verdict is Different for this medicine, the screen shows: "This strip says {printed}, but the list has {listpart}, so it can't be taught for this medicine." Nothing is saved.
5. On Save: the frame is cropped to the box around all read lines plus 12%, turned upright, saved as a JPEG; its vector comes from `LookEmbedder`; a `Look` is stored. The medicine now shows "Taught" with its first photo. A medicine can have up to 4 looks (both sides, two lights). "Remove looks" clears them.
6. The Teach screen shows a stopwatch from the first Save to the last, for the pitch number "8 strips in N minutes". It is kept in memory. A long press on it sets it back to zero.

## 5. The week file (`week.csv`), the evening text, the laptop page
**Export "Save this week's file":** `week.csv` to `Download/StripSense/`.
- Line 1: `exported_at,2026-10-10T21:30,person,Maa,list_saved_at,2026-10-10T11:20,woman,true` (8 cells, like every other line). Both times come from the app clock, so with the demo clock set they show the demo time.
- Line 2: the header `date,time,nickname,brand,strength,state,marked_at,source`
- Then one row per slot of every ACTIVE medicine for the last 7 dosing days, oldest first. `state` is `marked`, `not marked`, `not sure`, or `later` (slot time not reached). `source` is `prompt`, `voice`, `button` or empty (also empty for marks written by Demo controls).
- Inside a day the rows go medicine by medicine in list order, each medicine's times in order. `marked_at` is `HH:mm` for a marked row and empty for the others. A dose marked before its slot time is `marked`: a mark wins over `later`.
- `core/WeekFile.kt`: `WeekFile.build(meds, events, now, person, listSavedAtMillis, engine): String`, where `engine` is the `DoseEngine` (for the time zone and the slot rules).

**The evening text** (Family > Evening text): one message built by `core/EveningText.kt` (pure, tested), shown on screen with a Copy button and, only if the phone has a SIM (`TelephonyManager.simState == SIM_STATE_READY`), a "Send as SMS" button that opens the SMS app with the text filled in (`ACTION_SENDTO`, `smsto:`). The app itself never sends.
```
Maa, 10 October. Morning: fever tablet marked 8:05 am (on the prompt); sugar tablet not marked; eye drops marked 8:12 am (with its button). Afternoon: fever tablet marked 2:06 pm (by voice); vitamin tablet not marked. Night: fever tablet not marked; sugar tablet not marked; eye drops not marked. "Not marked" doesn't always mean missed. Please check with Maa before asking her to take another.
```
The rules behind the example (the list is M1 to M5 of spec 01 section 7, the time is 9:30 pm, and the only events are the three marks named in it):
- One part for each of Morning, Afternoon, Evening, Night that has at least one slot whose time has come. Slots whose time has not come are left out. Stopped medicines are left out.
- Inside a part, the medicines in list order, joined with "; ". Each is "{nickname} marked {time} ({how})", "{nickname} not marked" or "{nickname} not sure".
- `{how}` is "on the prompt" for PROMPT_TAP, "by voice" for PROMPT_VOICE, "with its button" for MARK_BUTTON, and left out with its brackets for DEMO.
- The first words are the call name and the date. The last two sentences are fixed.
- The evening text is English only. The date is the dosing date. For a man the last sentence says "him" (`person.isWoman` is false).
- Before the first slot of the day there is no part: the text is then the name and date and the two fixed sentences.

**The laptop page** (`laptop/family.html`, one static file, no server, no libraries): the family opens it in a browser on their laptop and drops `week.csv` on it (drag and drop, or a file button).
- Heading "{person}'s medicines, this week" and "last updated {exported_at}, from her phone over Office Kit".
- A table: one row per medicine and time, one column per day, each cell a small pill: the mark time on green, "not marked" on amber, "not sure" on purple, a grey dot for "later" or no slot.
- Under it, the same evening text for the latest day. The page builds it from the rows with the rules above (and the `woman` cell of line 1), with a Copy button.
- It also accepts `list.csv` and then shows the list as a second table: nickname (the Hindi one under it), brand and generics, strengths, times in 12-hour form, status (with the stopped date), note (and "marked with its button" for a BUTTON medicine). The content of a dropped file decides which of the two it is, not its name.
- This one file may be longer than 250 lines.
- `laptop/sample-week.csv` is a made-up full week whose last day is the evening-text example. A unit test builds it with `WeekFile.build` and fails if the file and the code ever differ.
- Plain HTML, CSS and JavaScript. The same emerald colours as the app. It must work offline from a `file://` path.

## 6. Trial mode and the Numbers screen
**Trial mode** (Numbers screen, a switch): for measuring on real strips.
1. Before each try, the tester taps what is in their hand from a list: each list medicine by nickname ("her fever tablet"), plus **"Not hers / not taught"** and **"Hers, but print covered or cut"**.
2. Then they scan. The verdict is logged. Nothing is spoken longer than the first sentence, the dose question is not asked, and no dose is marked.
3. The app scores the try itself:

| In the hand | Verdict | Score |
|---|---|---|
| her medicine X | Match(X) | right |
| her medicine X | Match(another medicine) | **wrong** |
| her medicine X | Stopped, ButtonOnly, NotShown or NotTaught naming **another** medicine | **wrong** |
| her medicine X | Stopped(X) or ButtonOnly(X) when X is that kind | right |
| her medicine X | Unreadable, NotSure, OneAtATime, NotShown(X), NotTaught(X) | not sure |
| her medicine X | Different, WrongMedicine, Unknown | wrong refusal |
| not hers / not taught | Match(anything), ButtonOnly | **wrong** |
| not hers / not taught | Different, WrongMedicine, Unknown, NotShown, NotTaught, Stopped | right (the app refused, and said why) |
| not hers / not taught | Unreadable, NotSure, OneAtATime | not sure |
| print covered or cut | Match(anything) | **wrong** |
| print covered or cut | anything else | right |

```
Trial(id, atMillis, truth: String, verdict: String, verdictMedId: String?, score: RIGHT | WRONG | NOT_SURE | WRONG_REFUSAL, msToVerdict: Long, lookScore: Float?, lookOnlyWouldName: String?, secondReader: String?)
```
`truth` is the medicine's id, or `NOT_HERS`, or `COVERED`. `secondReader` is the outcome of spec 04 section 7 (`agreed`, `not_agreed`, `unreadable`, `timeout`, `failed`, `skipped`) or null. `verdict` is the verdict's name (`Match`, `Different`, ...), and `verdictMedId` the medicine it carries, if any. `msToVerdict` = from the first frame with `letters ≥ 8` to the final verdict, or -1 when no frame had that much text (such tries are left out of the time numbers). `id` is a new UUID. The score type is `TrialScore`.

How trial mode behaves:
- The switch is kept in memory only, so after a restart the app is back in normal use.
- "What is in your hand?" is a full screen (Back goes to Today). The last pick is shown in green; the tester still taps once.
- Trial mode changes only the scan path. On the Answer screen: the banner, the first part of the answer (spoken), one line with the score ("Right: logged", and a wrong one in red), and the button "Next try". No dose question, no marking.
- The `Trial` is saved at the verdict with `secondReader = null`. The second reader runs unless its mode is Off (never strict in trial mode), and when it ends the same trial gets its outcome (`Store.setTrialSecondReader(id, outcome)`).
- `lookScore` is the best cosine between this frame and every saved look, worked out for every try (also when the verdict is not Match). `lookOnlyWouldName` is that look's medicine when the score is at or above `tLook`.
- "Save trials file" writes `trials.csv`: a header and one row per try with `id, at, truth, verdict, verdict_medicine, score, ms_to_verdict, look_score, look_only_would_name, second_reader`.
- "Clear trials" asks for the PIN every time. `lookOnlyWouldName` = the medicine whose saved look scores highest for this picture, if that score is at or above `tLook` (what a look-only app would have said).

**The Numbers screen** shows, from the trials:
- Tries as a title. Then four counts in 44 sp: Right, **Wrong**, Not sure, Wrong refusals, each with "1 in N" (N rounded to a whole number; nothing for a count of 0).
- Three lines that add up to the total, by what was in the hand: her strips (a medicine was picked), strips that are not hers, her strips with the print covered or cut.
- Time to verdict: median (for an even count, the mean of the two middle values) and 90th percentile (nearest rank), in seconds.
- "By look alone, a strip that is not hers would have been named as hers N times. The print check named K." N counts the not-hers tries (truth `NOT_HERS` only) where `lookOnlyWouldName` is set. K is the count of those not-hers tries scored wrong. Both are counted from the trials; neither number is typed into the code.
- Gemma: which backend loaded and its load time; voice calls, failures, timeouts and the median call time; second-reader calls, agreed, not agreed, unreadable, and their median time; the last reply that did not agree.
- The last look score (a number from 0 to 1), so that `tLook` can be set from what you see. Whether the look check and the second reader are on now.
- At the bottom: "This app has no internet permission" and the permission list read at run time (spec 07 section 7). One extra line from AndroidX (`...DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) is expected. If INTERNET were ever in the list, the line turns red and says so.
- Buttons: "Save trials file" (`trials.csv` to `Download/StripSense/`), "Clear trials" (behind the PIN).
- A line at the bottom: "This app has no internet permission", with the list of permissions read from `PackageManager` at run time.
