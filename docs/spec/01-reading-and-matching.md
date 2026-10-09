# Spec 01. Reading a strip and deciding

Everything in this file lives in `core/` (pure Kotlin) and has unit tests. The camera and ML Kit only produce `OcrLine`s; they hold no rules.

## 1. Data
```
enum StrengthUnit { MG, MCG, G, IU, ML, PERCENT }      // not named "Unit": that would hide kotlin.Unit
data class Strength(value: String, unit: StrengthUnit)  // value exactly as printed: "500", "2.5"
enum MedType { CAMERA, BUTTON }                        // BUTTON = checked with a Mark button (eye drops, syrup, insulin)
enum MedStatus { ACTIVE, STOPPED }

data class Medicine(
  id: String,
  nickEn: String, nickHi: String,       // "fever tablet", "बुख़ार की गोली"
  brand: String,                        // the brand word(s) as printed, uppercase: "DOLO"
  generics: List<String>,               // uppercase: ["PARACETAMOL"]
  strengths: List<Strength>,            // every strength printed for the generics, in order
  release: String?,                     // "SR", "ER", "XR", "CR", "MR", "OD", "XL", "PR", "DR" or null
  extraWords: List<String>,             // words that belong to this brand line: ["PLUS"], ["COLD"]; usually empty
  type: MedType, status: MedStatus, stoppedOn: String?,   // date "2026-10-01"
  times: List<String>,                  // "08:00","14:00","20:00", between 04:00 and 23:59, sorted
  noteEn: String, noteHi: String,       // the family's note, may be empty: "after food"
  colour: String                        // hex, for the icon
)

data class OcrLine(text: String, left: Int, top: Int, right: Int, bottom: Int, heightPx: Int, confidence: Float)

data class ReadKey(
  brands: Set<String>,                  // brands from the list that were seen
  generics: Set<String>,                // generic names from the list that were seen
  strengths: Set<Strength>,             // every number-with-unit read, kept in reading order (a LinkedHashSet)
  strengthSeen: Map<Strength, Int>,     // on how many lines each one was read
  brandNumbers: Set<String>,            // a bare number right after a brand word: "650" in "DOLO 650"
  release: Set<String>,
  extraWords: Set<String>,
  letters: Int                          // count of A-Z letters in usable lines
)

sealed interface Verdict (a `data object` for the ones with no values, a `data class` for the others, so tests can compare them with ==):
  Searching                              // nothing decided yet, keep looking
  Unreadable                             // C1: can't read, turn it over
  OneAtATime                             // RZ1
  Match(med)                             // print equals the list entry
  Different(med, printed: String, listPart: String)   // V1
  WrongMedicine(med)                     // D1: her brand name, another medicine inside
  NotShown(med)                          // N1: same medicine and strength, not a strip that was taught
  Stopped(med)                           // S1
  ButtonOnly(med)                        // L1
  NotTaught(med)                         // L2 (set outside core, when teaching is on and the medicine has no look)
  Unknown                                // K1
  NotSure                                // U1
```
The codes in the comments above (C1, V1, D1, S1, ...) are sentence IDs from spec 03. They are not the rule numbers D0 to D12 of section 4 and not the test vectors of section 7.

`Medicine`, `Strength` and the enums are `@Serializable` (they are saved later). `ReadKey`, `OcrLine` and `Verdict` are never saved and are not serializable.

**How a strength is written for people** (`Strengths.display`): the value, a space, the unit as `mg`, `mcg`, `g`, `IU`, `ml`, `%`. So `500 mg`, `250 IU`, `0.5 %`. Several are joined with ` + `.

## 2. Parser: `StripParser.parse(lines: List<OcrLine>, meds: List<Medicine>, tuning: Tuning = Tuning()): ReadKey`
All constants live in one `@Serializable data class Tuning` with these names and defaults, so they can be changed from the Demo controls:
`minConf = 0.40f`, `minHeightPx = 14`, `quickFrames = 2`, `strengthLines = 2`, `strengthFrames = 3`, `slowMs = 1200`, `timeoutMs = 6000`, `textLetters = 8`, `unknownLetters = 20`, `unknownLettersNoStrength = 40`, `closerMarginPx = 4`, `tLook = 0.55f`, `gemmaTimeoutMs = 8000`.

1. **Usable lines:** confidence ≥ minConf and heightPx ≥ minHeightPx.
2. **Normalise each line**, in this order: replace `µ` (U+00B5) and `μ` (U+03BC) with `MC` when the next letter is `g` or `G` (so µg → MCg); then uppercase with `Locale.ROOT`; replace each of `- _ / , : ; ( ) [ ] *` with a space; keep `.` only between two digits (any other `.` becomes a space); put spaces around `+` and `%` so they are their own tokens; collapse spaces.
3. **Known words first, on the tokens as they are.** Build the word lists from `meds`: every brand, every generic.
   - A token equals a known word if it is identical, or identical after the letter fixes `0→O`, `1→I`, `5→S`, `8→B` applied to the token (`FEV0RA` is FEVORA). The letter fixes are tried only on tokens that contain at least one letter.
   - A known word of several words (`VITAMIN D3`) must appear as consecutive tokens on one line, each token matching its word. A known word that contains digits (`D3`, `B12`) can only be found here, before step 4 splits letters from digits.
   - For each single word of a **generic** name that has 8 letters or more, a token at edit distance 1 from it also matches (try the token as read, and after the letter fixes). Shorter words, and every brand word, must be exact.
   - Try brands before generics, and longer known words before shorter ones.
   - Tokens used by a known word are **consumed**: they are not split, not used for strengths, and not matched again.
4. **Then split the remaining joined tokens** between a letter and a digit, and between a digit and a letter: `DOLO650` → `DOLO 650`, `500MG` → `500 MG`. Then run the known-word matching of step 3 once more over the tokens that are not consumed (so `FEVORA500` gives the brand and a number).
5. **Strength tokens:** a NUMBER token (`\d{1,4}` optionally `.` and 1 to 3 digits) followed by a UNIT token: `MG`, `MCG`, `UG` (→ MCG), `G`, `GM` (→ G), `IU`, `ML`, `%` (→ PERCENT). Two common misreads of "mg" are also taken as MG: `RNG` and `MQ`. Only tokens that are not consumed are looked at. Count each strength once per line it appears on (`strengthSeen`). Keep the strengths in reading order (lines by their top, then tokens left to right).
   - **Numbers are never corrected.** No letter-to-digit fixes inside a number. `5OO` is not a number.
6. **After a brand on the same line**, look at the next three tokens, stopping at the first one that is none of these (a consumed token also stops it):
   - one of `SR ER XR CR MR OD XL PR DR` → add to `release`
   - one of `PLUS FORTE COLD EXTRA DUO TRIO AM H D M LS CV DS XT MAX` → add to `extraWords`
   - a NUMBER not followed by a UNIT → add to `brandNumbers` (`650` in `DOLO 650`, `500` in `GLYMET SR 500`)
7. **Release words anywhere on a line that has a strength or a brand:** the short codes above as whole tokens, and the phrases `SUSTAINED RELEASE` → SR, `EXTENDED RELEASE` → ER, `PROLONGED RELEASE` → PR, `MODIFIED RELEASE` → MR.
8. `letters` = number of A-Z characters in the usable lines, counted after normalising.

`StripParser.strengthsIn(text: String): Set<Strength>` runs only the strength steps (normalise, split, number followed by a unit) on a plain piece of text, with no confidence or height test. The second reader (spec 04 section 7) uses it.

## 3. Comparing strengths
`sameStrengths(a, b)`: both sets equal after normalising each strength to (number as a decimal, unit) with `G` turned into `MG` × 1000. `0.5 G` equals `500 MG`. `500 MG` does not equal `650 MG`. `2.5` does not equal `25`.

`conflict(key, med)`: true when the key has a strength that is not in the medicine, or a brand number that is not the number of any of the medicine's strengths.

## 4. Decider: `Decider.decide(key: ReadKey, meds: List<Medicine>): Verdict`
The first rule that fits wins.

| # | Condition | Verdict |
|---|---|---|
| D0 | The brands in the key belong to two or more different medicines of the list, or the key has two different brand numbers | OneAtATime |
| D1 | **Candidates** = medicines whose brand is in `key.brands`, or that share a generic with `key.generics`. None found, and `key.letters ≥ unknownLetters` and (`key.strengths` not empty or `key.letters ≥ unknownLettersNoStrength`) | Unknown |
| D2 | No candidates, less text than that | Searching |
| D3 | **Full match.** A candidate is a full match when: its brand is in `key.brands`; all its generics are in `key.generics`; `sameStrengths(key.strengths, med.strengths)`; every brand number seen is the number of one of its strengths; `key.release` equals its release (both empty, or the key holds exactly that one code); `key.extraWords` ⊆ its `extraWords`. If **exactly one** candidate is a full match, then by that medicine: STOPPED → Stopped; BUTTON → ButtonOnly; otherwise | Match |
| D4 | More than one full match | NotSure |
| | **No full match.** Take the best candidate `c`: a brand match first, then the most generics matched. | |
| D5 | `key.strengths` is empty: if a brand number was seen and it is not the number of any strength of `c` → Different(c, printed = that number, a space, and the unit of c's first strength, e.g. `650 mg`; listPart = c's strengths). Otherwise | Unreadable |
| D6 | `c`'s brand was seen, `key.generics` is not empty, and none of them is a generic of `c` | WrongMedicine(c) |
| D7 | `conflict(key, c)` is true | Different(c, printed = the key's strengths joined with " + ", listPart = c's strengths joined with " + ") |
| D8 | No conflict, but the key has fewer strengths than `c` (a strict subset) | Unreadable |
| D9 | Strengths are the same, an extra word was seen that `c` does not have | Different(c, printed = `c.brand` + " " + the extra word, listPart = `c.brand` alone) |
| D10 | Strengths are the same, the release word differs | Different(c, printed = strengths + " " + the release word seen, or "plain " + strengths when none was seen; listPart = c's strengths + its release word, or "plain " + strengths) |
| D11 | Generics and strengths are the same, `c`'s brand was not seen | NotShown(c) |
| D12 | Anything else | NotSure |

`printed` and `listPart` are display strings such as `650 mg`, `325 mg + 400 mg`, `plain 500 mg`. Several release words seen are joined with a space.

Signature: `Decider.decide(key: ReadKey, meds: List<Medicine>, tuning: Tuning = Tuning()): Verdict`.

## 5. ScanSession: when a verdict may be spoken
```
class ScanSession(meds: List<Medicine>, tuning: Tuning)
fun feed(key: ReadKey, nowMs: Long, smallestKeyLinePx: Int?, tooSmallLines: Int = 0): ScanResult
sealed ScanResult { Looking(hint: Hint), Final(verdict: Verdict) }
enum Hint { HOLD_INSIDE, CLOSER, READING }
```
One `ScanSession` per scan. It is fed one `ReadKey` per analysed frame with the frame's time. It calls `Decider.decide` itself. It has no clock of its own. `smallestKeyLinePx` is the height of the smallest line that gave a brand, a generic or a strength in this frame (null if none). The parser gives it: `StripParser.smallestKeyLinePx(lines, meds, tuning): Int?` parses each usable line on its own and, of the lines whose own key has a brand, a generic or a strength, returns the smallest `heightPx`.

- **Same key:** two keys are the same when brands, generics, strengths, brandNumbers, release and extraWords are all equal.
- **Quick verdicts** (Match, Stopped, ButtonOnly, Different, WrongMedicine, OneAtATime) are final when the same key has come in `quickFrames` (2) frames in a row **and** every strength in the key was read on `strengthLines` (2) or more lines in this frame, or the key has stayed the same for `strengthFrames` (3) frames in a row. A key with no strengths (a Different from a brand number alone) needs only the 2 frames.
- **Slow verdicts** (Unreadable, NotShown, Unknown, NotSure) are final when the same verdict has come on every frame for `slowMs` (1,200 ms), counted from the first frame of that run. A frame with any other verdict (Searching, a quick verdict, or a different slow verdict) starts the count again. A quick verdict that becomes final in that time wins. "For 1,200 ms" and "after 6,000 ms" both mean: final on the first frame whose time is at or past that mark.
- **Timeout:** a run of frames with `letters ≥ textLetters` (8) starts a timer at its first frame. If no verdict is final `timeoutMs` (6,000 ms) after that first frame, the result is Final(Unreadable). A frame with fewer letters ends the run and clears the timer.
- **Hints** while looking, first match wins: `CLOSER` when `letters == 0` and `tooSmallLines > 0` (print is in view but too small to use; `StripParser.tooSmallLines(lines, tuning)` counts the lines that pass `minConf` and fail only `minHeightPx`); `HOLD_INSIDE` when `letters == 0`; `CLOSER` when `smallestKeyLinePx` is not null and is under `minHeightPx + closerMarginPx`; otherwise `READING`. The words for the hints are screen labels (spec 03 section 5: `guideHint`, `bringCloser`, `reading`), not answer sentences.
- After a final result the session is finished; feeding it again returns the same result. A new scan makes a new session.
- Which sentence an Unreadable gets (C1, then C2, then C3 for the second and third in a row) is decided by the Answer screen in a later stage, not here.

## 6. The look check (its rules are pure functions in `core/LookCheck.kt`; the vectors come from `ml/LookEmbedder`; the scan screen joins them)
Only after `Match(med)`:
- Teaching not in use yet (no medicine has any saved look): keep Match. (So the app works from the typed list alone. Once the family teaches one strip, every other camera medicine answers "not taught yet" until it is taught too. That is on purpose: a half-taught table must not look fully checked.)
- `med` has no saved look: NotTaught(med).
- Otherwise compare the current picture's vector with each saved vector of `med` (cosine). Best score ≥ `tLook` (default 0.55, changed in Demo controls) → Match. Lower → NotShown(med).
- The look never names anything. It can only turn a Match into NotShown.
- A switch in Demo controls turns the look check off. The Numbers screen says whether it was on.

## 7. Test vectors (each one becomes a unit test)
The list for these tests:
```
M1 fever tablet   brand FEVORA   generics PARACETAMOL                 strengths 500 MG          times 08:00,14:00,20:00  CAMERA ACTIVE
M2 vitamin tablet brand CALVORA  generics CALCIUM, VITAMIN D3         strengths 500 MG, 250 IU  times 14:00              CAMERA ACTIVE  extraWords [D]
M3 BP tablet      brand AMLOVA   generics AMLODIPINE                  strengths 5 MG            times 08:00              CAMERA STOPPED (2026-10-01)
M4 sugar tablet   brand GLYMET   generics METFORMIN                   strengths 500 MG          release SR  times 08:00,20:00  CAMERA ACTIVE
M5 eye drops      brand TEARVA   generics CARBOXYMETHYLCELLULOSE      strengths 0.5 PERCENT     times 08:00,20:00        BUTTON ACTIVE
```

"×2" after the lines means the whole group of lines appears twice in the frame (the print repeats along a strip). Lines are separated by " / ".

| # | Lines read (each line is one OcrLine, all usable) | Expected |
|---|---|---|
| V1 | `FEVORA 500` / `Paracetamol Tablets IP 500 mg` / `FEVORA 500` / `Paracetamol Tablets IP 500 mg` | Match(M1) |
| V2 | `FEVORA 650` / `Paracetamol Tablets IP 650 mg` / `FEVORA 650` / `Paracetamol Tablets IP 650 mg` | Different(M1, "650 mg", "500 mg") |
| V3 | `FEVORA` / `Paracetamol Tablets IP` | Unreadable (D5) |
| V4 | `FEVORA 650` / `Paracetamol Tablets IP` | Different(M1, "650 mg", "500 mg") (brand number, D5) |
| V5 | `FEVORA 500` / `Paracetamol Tablets IP` | Unreadable (D5: brand number agrees, but no strength with a unit) |
| V6 | `ZYNTRA 10` / `Cetirizine Hydrochloride Tablets IP 10 mg` / `ZYNTRA 10` | Unknown |
| V7 | `FEV0RA 500` / `PARACETAM0L TABLETS IP 500MG` ×2 | Match(M1) (letter fixes, joined token) |
| V8 | `FEVORA 500` / `Paracetamol Tablets IP 500 mg` / `FEVORA 650` / `Paracetamol 650 mg` | OneAtATime |
| V9 | `CALVORA-D` / `Calcium 500 mg + Vitamin D3 250 IU` ×2 | Match(M2) |
| V10 | `CALVORA-D` / `Calcium 500 mg` (the D3 line not read) | Unreadable (D8) |
| V11 | `AMLOVA 5` / `Amlodipine Tablets IP 5 mg` ×2 | Stopped(M3) |
| V12 | `GLYMET SR 500` / `Metformin Hydrochloride Sustained Release Tablets 500 mg` ×2 | Match(M4) |
| V13 | `GLYMET 500` / `Metformin Hydrochloride Tablets IP 500 mg` ×2 | Different(M4, "plain 500 mg", "500 mg SR") |
| V14 | `PYRAMOL 500` / `Paracetamol Tablets IP 500 mg` ×2 | NotShown(M1) |
| V15 | `FEVORA COLD` / `Paracetamol 325 mg + Phenylephrine 5 mg` ×2 | Different(M1, "325 mg + 5 mg", "500 mg") |
| V16 | `FEVORA 500` / `Ibuprofen Tablets IP 400 mg` ×2, with a sixth list entry M6 (brand IBUVA, generic IBUPROFEN, 400 MG) so that IBUPROFEN is a known word | WrongMedicine(M1). The best candidate is the brand match M1, and none of the generics read is M1's (D6). |
| V16b | The same lines, without M6 on the list | Different(M1, "400 mg", "500 mg") |
| V17 | `TEARVA` / `Carboxymethylcellulose Eye Drops 0.5 %` ×2 | ButtonOnly(M5) |
| V18 | `Mfg Lic 123` / `Store 25 C` | Searching (D2: little text, nothing on the list) |
| V19 | `FEVORA 500` / `Paracetamol Tablets IP 5OO mg` (letters O in the number) | not Match. `5OO` is not a number, so the strength is unread → Unreadable |
| V20 | `fevora 500` / `paracetamol tablets ip 0.5 g` ×2 | Match(M1) (0.5 G equals 500 MG) |

ScanSession tests:
| # | Frames | Expected |
|---|---|---|
| S1 | V1 key, V1 key | final Match after the 2nd frame (each strength is on 2 lines) |
| S2 | a key with `500 MG` read on one line only, three times | final Match after the 3rd frame |
| S3 | V1 key, V2 key, V2 key | final Different after the 3rd frame (the key changed, so the count restarts) |
| S4 | V3 key every 100 ms | Unreadable only at or after 1,200 ms |
| S5 | a key with `letters = 12` and no candidates, for 6,100 ms | Unreadable at the timeout |
| S6 | V14 key every 100 ms | NotShown only at or after 1,200 ms |
| S7 | V14 key for 500 ms, then V1 key twice | final Match (the brand line was read late; the quick verdict wins) |

**Real strips (added at the event, Friday night, from the read-text screen).** These rows use the list in `docs/sample/list.csv`, not M1 to M5. For each real strip: the exact lines the phone read, separated by " / ", and the verdict you expect (Match with the nickname, Different with the two parts, Unknown, and so on).

| # | Lines read | Expected |
|---|---|---|

