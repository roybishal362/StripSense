# Stretch stages (only after P00 to P12 work on the phone)

Pick at most one. Each is a separate run. If it is not solid in 60 minutes, `git reset --hard` to the last good commit and stop.

---

## S1. "Which one now?": several strips on the table
**Goal:** the person lays her strips on the table and the camera view puts a green box on the one that is due now, a red box on a strip that is not on her list, and a grey "Not now" label on her other strips. The deck calls this the many-strip view.

**Read:** `docs/spec/01-reading-and-matching.md`, `docs/spec/02-dose-rules.md` section 4.

**Build**
1. `core/StripGroups.kt` (pure, tested): group one frame's `OcrLine`s into strips. Two lines belong to the same strip when their boxes, grown by one line height on every side, touch or overlap; groups are the connected sets. Each group has a bounding box. Tests: two blocks far apart → two groups; four lines of one strip → one group; a third strip in the middle → three.
2. For each group run `StripParser.parse` and `Decider.decide` on that group's lines only.
3. A new screen "Which one now?" (a button on the Today card): the camera with an overlay drawn in the preview's coordinates. For each group whose verdict stayed the same for 3 frames:
   - `Match(med)` and `DoseEngine.thisDose` is inside its window and UNMARKED → a thick green box and the label "Due now · {nickname} {strength}"
   - `Match(med)` otherwise → a thin white box and "Not now"
   - `Different`, `WrongMedicine`, `Unknown` → a red box and "Not this one · it says {printed}" (or "Not on your list")
   - `Stopped` → a red box and "Stopped, leave it"
   - anything else → no box
4. One spoken line when the first green box appears: the P1 sentence for that strip (spec 03). Tapping a green box opens the normal Answer screen for that strip.
5. Boxes are mapped from analysis coordinates to the `PreviewView` with `FIT_CENTER` in mind (scale by the smaller ratio and centre).

**Done when:** her due strip and the other strength lie side by side and get one green and one red box, steady, for 10 seconds. Moving a strip moves its box.

---

## S2. A chime at dose time
**Goal:** at each slot time the phone shows a full-screen card "8:00 am · Morning medicines" with a short chime and the slot's medicines, and a "Scan a strip" button.

**Build:** only while the app is in the foreground: a coroutine checks the clock every 20 seconds; when a slot time of any ACTIVE medicine has just passed and that slot is UNMARKED and the card was not shown for it yet, show the card, play a three-note chime with `ToneGenerator` or a generated `AudioTrack`, vibrate once. Never over an open question panel. A "Remind me in 10 minutes" button. No alarms, no notifications, no background work.

**Done when:** setting the demo clock to 7:59:50 shows the card ten seconds later, once.

---

## S3. Voice commands for the app, and more Hindi
**Goal:** she can run the app by voice, and more everyday Hindi is understood.

**Build**
1. Three more request values in the instruction and the schema of spec 04 section 4, with two examples each in English and Hindi:
   - "open_screen" with a new field "screen": "today", "scan", "ask", "family", "numbers" ("open today's list", "आज की सूची दिखाओ") → go to that screen and show the label of the screen as the answer
   - "switch_language" with a field "language": "en" or "hi" ("speak in Hindi", "English mein bolo") → switch, then say LNG1 in the new language (add LNG1 from the table below to `Sentences`)
   - "bigger_text" / "normal_text" → a text-size setting in the store (scale 1.0 or 1.2 applied to the theme's type sizes)
   LNG1: English "Okay, I'll speak English now." Hindi "ठीक है, अब हिंदी में बात करेंगे।"
2. Ten more example phrases in the instruction (two per request, Devanagari and Roman letters), taken from phrases that failed in your own tests.
3. A "Voice check" list under Demo controls: it runs a fixed set of typed phrases through Gemma and shows right or wrong for each, with the total.

**Done when:** "switch to Hindi", "open today's list" and "which one is this?" each do the right thing by voice; the phrases that failed before now pass; the eight phrases of P08 still pass.
