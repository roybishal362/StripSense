# P06. This dose on screen: the question, Yes, Undo, Today, the demo clock, Hindi speech

**Goal:** all five tests of the demo work with buttons, in English and in Hindi.

**Read:** `docs/spec/02-dose-rules.md` sections 5 to 9, `docs/spec/06-screens-and-design.md` (Today, Answer, Demo controls), `docs/spec/03-sentences.md` section 4, `docs/spec/04-voice-and-gemma.md` section 8. Look at `docs/design/answer-due.png`, `answer-marked.png`, `today.png`.

## Build
1. **Answer screen, Match:** call `DoseEngine.stripAnswer` and show and speak its parts. The banner follows the row: "Due now · not marked yet" (amber), "Already marked" (green), or the slot line.
2. **The question panel** when `askNow` is true: "Are you taking it now?", Yes, No, Not sure, the 60-second bar. It appears when the speech of the answer ends (the `onDone` of spec 07 section 7) or after 4 seconds, whichever is first, and at once when "speak answers" is off.
   - Yes → `answerPrompt` → the event is saved → R1 is shown and spoken → the green "Marked at …" row with **Undo** for 60 seconds and **Done** (spec 06, Answer, item 6).
   - Undo → `undo` → the UNDONE event → U2.
   - No → NO1. Not sure → the NOT_SURE event → NS3. No tap in 60 seconds → the panel closes and NM1 is shown.
   - Leaving the screen while the question is open closes it with nothing saved.
3. **Today screen** as in spec 06: the greeting with the clock's time, the "Next up" card, the four sections with every slot of today and its state chip, a **Mark** button on BUTTON medicines (spec 02 section 7, with a small dialog for MB1), a tap on any row speaks `statusAnswer`. The screen refreshes when an event is added and once a minute.
4. **Demo controls** (long-press on the Today title), spec 06 and spec 02 section 9: the four clock buttons, Fresh day, Seed yesterday, the switches that exist so far ("speak answers", "show read text"), the tuning numbers, "Load the demo list", "Reset everything" (asks twice, then puts back the first data of `DemoData.fresh`).
5. **Hindi:** the language switch now changes the spoken language too. `Speaker` picks an offline `hi-IN` voice; if none exists (`hasHindiVoice()` is false) the answer is spoken in English while the screen stays in Hindi (spec 04 section 8). The label `noHindiVoice` is shown later, in Family > Settings (P07). Q1 and SH1 use the woman's form when `person.isWoman`.
6. The Help card's second button, "Call Rahul", appears when a contact number is set in the store.

## Do not
- No voice input, no teaching, no files yet.
- A dose is written only from a Yes tap or a Mark confirmation.

## Done when
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` succeeds.
- On the phone, after Demo controls > Seed yesterday > Fresh day (time 8:05 am):
  1. her fever tablet strip → "Maa, this is your fever tablet, 500 mg, the morning one. Nothing's marked for this morning yet. Last one was marked yesterday at … Rahul's note: after food. Not sure? Ask Rahul before taking another. Are you taking it now?" → Yes → "Okay, I've marked your fever tablet at 8:05 am." with Undo
  2. the same strip again → "That's your fever tablet, 500 mg. It's already marked for this morning, at 8:05 am, 1 minute ago." and no question
  3. the other strength → "Careful: …"
  4. strength covered → "I can't read this one. Could you turn it over?"
  5. a strip not on the list → "I don't recognise this one…"
- Today shows the fever tablet's morning slot as "Marked 8:05 am". The eye drops' Mark button marks them.
- Switch to Hindi and repeat 1 and 3: Hindi text on screen, Hindi speech if the phone has the voice.
- Fresh day again → test 1 asks the question again.

## Report
What each of the five tests said, in both languages, and whether a Hindi voice was found.
