# Spec 02. This dose: slots, windows, marks

Everything here is `core/DoseEngine.kt`, with its small types in `core/DoseTypes.kt` (pure Kotlin, unit-tested). It takes the list, the event log and "now", and returns what to say and whether the question may be asked. It never reads the system clock itself: the time is passed in.

## 1. Time
- All times are local. `now` comes from `AppClock.now()` = system time + a demo offset (set in Demo controls).
- The **dosing day** starts at 04:00. `dosingDate(now)` = the calendar date of (now − 4 hours).
- A medicine's `times` are between 04:00 and 23:59 and sorted.

## 2. Slots and windows
- A **slot** = one time of one medicine on one dosing date. Its `slotKey` = `"2026-10-10T08:00"`.
- **Window opens** at slot time − 30 minutes.
- **Window closes** (the cut-off) at the earliest of: slot time + 120 minutes; slot time + half the gap to the same medicine's next slot that day. The last slot of the day has no next slot, so it closes at + 120 minutes. `now` is inside the window when it is at or after the opening and at or before the cut-off.
- **Half-interval** of a slot = half the gap from the same medicine's previous slot to this one. The previous slot of the first slot of the day is yesterday's last slot. Once a day gives 12 hours.

| Times | Windows | Half-intervals |
|---|---|---|
| 08:00 | 07:30 to 10:00 | 12 h |
| 08:00, 20:00 | 07:30 to 10:00, 19:30 to 22:00 | 6 h, 6 h |
| 08:00, 14:00, 20:00 | 07:30 to 10:00, 13:30 to 16:00, 19:30 to 22:00 | 6 h, 3 h, 3 h |
| 08:00, 12:00 | 07:30 to 10:00, 11:30 to 14:00 | 10 h, 2 h |

## 3. Events (append-only)
```
data class DoseEvent(id, medId, slotKey, atMillis, kind: TAKEN | NOT_SURE | UNDONE, source: PROMPT_TAP | PROMPT_VOICE | MARK_BUTTON | DEMO, undoes: String?)
```
- Events are never deleted or changed. UNDONE points at the event it cancels.
- A slot is **MARKED** if it has a TAKEN event that is not undone. It is **NOT_SURE** if it has a NOT_SURE event that is not undone and is not MARKED. Otherwise **UNMARKED**.
- **Last mark** of a medicine = its latest TAKEN event that is not undone.
- **Recent mark** for a slot = a last mark made less than the slot's half-interval ago, when that mark belongs to a different slot.

## 4. "This dose" of a medicine at `now`
Found in this order:
1. the slot of today whose window contains `now`
2. otherwise the latest slot of today whose time has passed, if it is not MARKED
3. otherwise the next slot of today
4. otherwise the latest slot of today (it is MARKED)

## 5. The answer for a strip (only after verdict Match)
`stripAnswer(med: Medicine, now: Long, events: List<DoseEvent>): StripAnswer`, with `data class StripAnswer(parts: List<Part>, askNow: Boolean, slotKey: String)`. `now` is in milliseconds. A `Part` is a sentence ID plus its values (spec 03 section 0). `DoseEngine` is a class that takes the time zone (`ZoneId`, the system's by default) so that tests can fix it.

| Row | This dose is | State | Parts, in order | Ask "Are you taking it now?" |
|---|---|---|---|---|
| DS1 | inside its window | UNMARKED, no recent mark | P1, P2 (P2n when the medicine has never been marked), NOTE (only if the note is not empty), CHK, Q1 | **yes** |
| DS2 | inside its window | MARKED | A1, CHK | no |
| DS3 | inside its window, or passed | NOT_SURE | NAME, NS1 | no |
| DS5 | inside its window | UNMARKED, but a recent mark | P3, P2, CHK | no |
| DS6 | the next slot, window not open yet | any | W1, P2 (P2n when never marked), CHK | no |
| DS7 | a passed slot, after its cut-off | UNMARKED | P3, P2 (P2n when never marked), NX1 (if a later slot exists today), CHK | no |
| DS8 | the latest slot today | MARKED | A1, CHK | no |

Values: `{slot}` and `{now_part}` come from the slot's time. A1's `{time}` and `{ago}` are the mark's. P2 describes the last mark. NX1's time is the next slot's.

**The question may be asked only if all hold:** the verdict was Match in this scan; `now` is inside this dose's window; the slot is UNMARKED; there is no recent mark; the medicine is ACTIVE.

## 6. Answering the question
The prompt remembers the `slotKey` it asked about.
```kotlin
enum class PromptAnswer { YES_TAP, YES_VOICE, NO, NOT_SURE, NOT_SURE_VOICE, TIMEOUT }
data class PromptResult(val event: DoseEvent?, val parts: List<Part>, val showUndo: Boolean = false)

fun answerPrompt(med: Medicine, slotKey: String, answer: PromptAnswer, now: Long, events: List<DoseEvent>, newId: String): PromptResult
fun undo(taken: DoseEvent, now: Long, events: List<DoseEvent>, newId: String): PromptResult
```
The engine never saves anything. It returns the event to append (or null) and the parts to say; the screen appends the event to the store. `newId` is a fresh UUID string from the caller, so tests can pass a fixed one. R1's values are the nickname and the time of `now`. These small types (`DoseSlot`, `StripAnswer`, `PromptAnswer`, `PromptResult`, `MarkAnswer`) go in `core/DoseTypes.kt`, so that `DoseEngine.kt` stays short.

"Still askable" in the table below means: a fresh `stripAnswer(med, now, events)` has `askNow == true` and the same `slotKey`. A TAKEN or NOT_SURE event has source PROMPT_TAP for a tap and PROMPT_VOICE for a spoken answer (`YES_VOICE`, `NOT_SURE_VOICE`).

`undo`: when `taken` is not undone and is at most 60 seconds old, it returns an UNDONE event (with the same source as the mark it cancels) and U2. When `taken` is already undone: no event, NM1. When it is older than 60 seconds: no event, AM1 with the mark's time and ago.
| Answer | Check again, then | Event | Say |
|---|---|---|---|
| Yes (tap) | the same slot must still be askable | TAKEN, PROMPT_TAP | R1. Show **Undo** for 60 seconds |
| Yes (voice, while the question is open) | the same | TAKEN, PROMPT_VOICE | R1. Undo for 60 seconds |
| No | | none | NO1 |
| Not sure | | NOT_SURE | NS3 |
| No answer for 60 seconds | | none | close the question; show NM1 (not spoken) |
| Undo within 60 seconds | | UNDONE pointing at the TAKEN event | U2 |

If the check fails at the tap (for example the window closed), nothing is written: `answerPrompt` returns no event and the parts of a fresh `stripAnswer`, and the screen shows those like a new answer.

## 7. Mark buttons (BUTTON medicines, on the Today screen)
A Mark button never reads a strip, so it never says "That's your…". The button stays on the row after the dose is marked (pressing it then says AM1). MB1 is asked in a small dialog: the nickname as its title, MB1 as its text, Yes and No.
```kotlin
data class MarkAnswer(val parts: List<Part>, val ask: Boolean, val slotKey: String)    // ask is true only with MB1
fun markButton(med: Medicine, now: Long, events: List<DoseEvent>): MarkAnswer
fun confirmMark(med: Medicine, slotKey: String, now: Long, events: List<DoseEvent>, newId: String): PromptResult   // Yes on MB1
```
| This dose is | Do |
|---|---|
| the medicine is STOPPED | VS8, S0 (the Today screen shows no Mark button for a stopped medicine) |
| inside its window, UNMARKED, no recent mark | ask MB1 with Yes / No. Yes → TAKEN, MARK_BUTTON → R1 + Undo 60 s. No → NO1 |
| inside its window, UNMARKED, but a recent mark | P2, CHK. No question |
| MARKED | AM1 |
| NOT_SURE | VS3, CHK |
| not inside a window | MB0 |

## 8. Answers with no strip (voice, and taps on the Today list)
`statusAnswer(med, now, events): List<Part>`; never uses NAME, P1, P3, A1 or W1.
| First match wins | Parts |
|---|---|
| the medicine is STOPPED | VS8, S0 |
| this dose is MARKED | VS2, CHK |
| this dose is NOT_SURE | VS3, CHK |
| this dose is inside its window, UNMARKED | VS1, CHK |
| this dose is a passed slot after its cut-off, UNMARKED | VS4, CHK |
| this dose is the next slot, window not open | VS6, CHK |

For an ACTIVE CAMERA medicine add V2 at the end ("Hold up the strip and I'll check it for you."). A STOPPED medicine never gets V2.

`whatsLeft(meds, now, events): List<Part>`: look at every slot of today of every ACTIVE medicine.
- MARKED → leave out.
- NOT_SURE → goes in the WL3 list.
- slot time later than now → WL1 list.
- slot time passed and its window still open → WL2 list.
- past its cut-off → WL2x list.

Say the non-empty lists in the order WL2, WL2x, WL3, WL1, then CHK. Everything marked → WL0 alone. No slots today → WL0b alone. Inside each list, sort by time; slots at the same time keep the order of the medicine list.

## 9. Demo clock and fresh day (Demo controls screen)
- **Set the time:** buttons for 8:05 am, 2:05 pm, 8:05 pm today, and "real time". They only change the clock offset. "Today" is the real calendar date.
- **Fresh day:** writes an UNDONE event (source DEMO) for every TAKEN and NOT_SURE event of today's dosing date that is not undone yet. Nothing is deleted. Then sets the time to 8:05 am.
- **Seed yesterday:** writes TAKEN events (source DEMO) for yesterday's slots of every ACTIVE medicine, 10 minutes after each slot time, so that "Last one was marked yesterday at 8:10 pm" has something to say. A slot that already has a mark is skipped, so pressing it twice changes nothing.
- Signatures: `freshDay(now: Long, events: List<DoseEvent>, newId: () -> String): List<DoseEvent>` and `seedYesterday(meds: List<Medicine>, now: Long, events: List<DoseEvent>, newId: () -> String): List<DoseEvent>`. Both return the events to append. Every event gets its own id from `newId`.

## 10. Test vectors (each becomes a unit test)
In the calls below, `newId` is any fixed string and `events` is the list the row describes. The engine always passes `ago` in minutes; turning 1,440 or more into nothing is the job of `Sentences` (spec 03).

List: the five medicines of spec 01 section 7. M1 note "after food". Person: call name "Maa", contact and family "Rahul". Today is 2026-10-10. Seeded: M1's 20:00 slot of 2026-10-09 TAKEN at 20:10.

| # | Now and events | Call | Expected |
|---|---|---|---|
| T1 | 08:05, nothing today | stripAnswer(M1) | parts P1, P2, NOTE, CHK, Q1; askNow true; slotKey 2026-10-10T08:00. P2 values: when = yesterday, time = 8:10 pm, ago = 715 minutes (shown as ", 11 hours ago") |
| T2 | TAKEN for 08:00 at 08:05; now 08:06 | stripAnswer(M1) | A1 (time 8:05 am, ago ", 1 minute ago"), CHK; askNow false |
| T3 | 07:10, nothing today | stripAnswer(M1) | W1 (slot morning, time 8:00 am), P2, CHK; askNow false |
| T4 | 11:00, nothing today | stripAnswer(M1) | P3, P2, NX1 (2:00 pm), CHK; askNow false |
| T5 | TAKEN for 08:00 at 08:05; now 13:40 | stripAnswer(M1) | P1 (slot afternoon), P2 (today, 8:05 am, ago = 335 minutes, shown as ", 5 hours ago"), NOTE, CHK, Q1; askNow true; slotKey ...T14:00 |
| T6 | NOT_SURE for 08:00 at 08:02; now 08:05 | stripAnswer(M1) | NAME, NS1 (time 8:02 am); askNow false |
| T7 | TAKEN at 08:05, UNDONE of it at 08:06; now 08:07 | stripAnswer(M1) | same as T1 (askNow true; P2 is yesterday's mark) |
| T8 | a medicine with times 08:00, 12:00; TAKEN for 08:00 at 09:55; now 11:35 | stripAnswer | P3 (slot afternoon: this dose is the 12:00 slot), P2, CHK; askNow false (recent mark: 1 h 40 min is less than the 2 h half-interval) |
| T9 | the same; now 12:00 | stripAnswer | P1 …, Q1; askNow true |
| T10 | 08:10, nothing today | markButton(M5), then confirmMark | markButton gives MB1 with ask true. confirmMark gives a TAKEN event with source MARK_BUTTON, R1, showUndo true |
| T10b | T1, then answerPrompt(M1, slotKey, YES_TAP) at 08:05 | answerPrompt | a TAKEN event with source PROMPT_TAP for 2026-10-10T08:00, R1 (time 8:05 am), showUndo true |
| T10c | T1, then NOT_SURE, NO and TIMEOUT in turn | answerPrompt | NOT_SURE: a NOT_SURE event and NS3. NO: no event, NO1. TIMEOUT: no event, NM1 |
| T10d | T2 (already marked), then answerPrompt(M1, the 08:00 slotKey, YES_TAP) at 08:06 | answerPrompt | no event; parts A1, CHK |
| T10e | the TAKEN event of T10b | undo | Undo at 08:05:30: an UNDONE event pointing at it, U2. Undo again after that: no event, NM1 (it is already undone). In a separate case with no undo in the log, undo at 08:07 (over 60 seconds): no event, AM1 |
| T11 | after T10; now 08:13 | markButton(M5) | AM1 (time 8:10 am, ago ", 3 minutes ago") |
| T12 | 11:00, nothing today | markButton(M5) | MB0 |
| T13 | TAKEN for M1 08:00 at 08:05; now 08:06 | statusAnswer(M1) | VS2 (8:05 am, ", 1 minute ago"), CHK, V2 |
| T14 | 08:05 | statusAnswer(M3) | VS8, S0 |
| T15 | TAKEN for M1 08:00 at 08:05; now 08:06; nothing else marked | whatsLeft | WL2: sugar tablet 8:00 am, eye drops 8:00 am. WL1: fever tablet 2:00 pm, vitamin tablet 2:00 pm, fever tablet 8:00 pm, sugar tablet 8:00 pm, eye drops 8:00 pm. Then CHK |
| T16 | every slot of today of every ACTIVE medicine MARKED | whatsLeft | WL0 only |
| T17 | 02:30 on 2026-10-11 (still the dosing day of 10 Oct), M1 20:00 slot unmarked | stripAnswer(M1) | P3 for the night slot of 2026-10-10 (after its cut-off at 22:00), P2 (when = yesterday; ago is over 1,440 minutes, so nothing is shown for it), CHK; askNow false |
| T18 | Fresh day after T2 | stripAnswer(M1) at 08:05 | same parts as T1; the old TAKEN event is still in the log, undone |
