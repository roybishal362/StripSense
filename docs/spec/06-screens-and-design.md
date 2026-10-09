# Spec 06. Screens and design

The app must look like a finished consumer app. Large type, calm colours, one clear thing per screen. Pictures of the design are in `docs/design/` (screens of the earlier web mock-up, as a visual reference only; no code from it is used). Match their layout, colours and spacing.

The pictures differ from this spec in a few places. **This spec wins.** In the pictures: the bottom bar says "Health" where this app has **Numbers**; the Today picture has a "Running low" card that this app does not have; the Ask picture has a "Turning speech into text" card that this app replaces with the Gemma state chip; the answer in `answer-different.png` is from a menu pick, while here it comes from the camera; the top bar in the pictures has a microphone button, which this app does not have (Ask is in the bottom bar); `laptop-page.png` shows a full name, a line "list last checked against the prescription" and a caption under the eye drops that the week file does not carry (the page shows the time the list was saved instead); the answer pictures end with "Scan again" and "Done", where this app has "Scan another" (and, after a Yes, Undo and Done).

| Picture | Shows |
|---|---|
| `today.png`, `today-hindi.png` | the Today screen in English and Hindi |
| `answer-due.png` | Match, due now, the question panel open |
| `answer-marked-undo.png` | just after Yes: the "Marked" row with Undo |
| `answer-marked.png` | Match, already marked |
| `answer-different.png` | Different: the red banner and the boxed strength |
| `answer-cant-read.png` | Unreadable |
| `answer-unknown.png` | Unknown |
| `ask.png` | the Ask screen with bubbles and question buttons |
| `help.png` | the Help card |
| `family-list.png`, `family-teach.png` | the Family tabs |
| `laptop-page.png` | the family's laptop page |

## 1. Design tokens (`ui/theme/`)
Material 3, light theme only, dynamic colour off.

| Token | Value | Use |
|---|---|---|
| canvas | `#F3F5F4` | screen background |
| surface | `#FFFFFF` | cards |
| ink | `#0D1714` | main text |
| ink2 | `#46524E` | second-level text |
| ink3 | `#66726E` | captions |
| brand700 | `#0A6A4F` | main buttons, the Scan button |
| brand500 | `#13A07A` | accents, the tick badge |
| brand100 | `#D9F2E8` | soft green fills |
| okText / okBg | `#0A7350` / `#E3F4EC` | "Already marked" |
| dueText / dueBg | `#9A5605` / `#FFF1D9` | "Due now · not marked yet" |
| unsureText / unsureBg | `#5638C0` / `#EEEAFD` | "Not sure" |
| stopText / stopBg | `#BB2129` / `#FCEBEA` | "Not on your list" |
| greyBg | `#E7EBE9` | "Can't read", "Not recognised" |
| sos | `#D72C20` | the Help button |

- Corner radius: cards 26 dp, buttons 20 dp, chips 999 dp.
- Type (sp): screen title 28 bold · medicine name 28 bold · strength 44 extra-bold · answer text 20, line height 30 · body 17 · label 15 · caption 13. Hindi uses the system Devanagari font; add 2 sp to Hindi body text.
- Touch targets at least 56 dp high. Main buttons 64 dp high, full width.
- One vibration (30 ms) when an answer appears.
- Icons: from the core icon set where it has one (Check, Close, Warning, Refresh, Home, Person, Call, Info, Settings, List), and the app's own small vector icons in `ui/theme/AppIcons.kt` for the rest: microphone, camera, speaker, clock, torch, block, chart. Spec 07 section 7 lists what the core set really contains.

## 2. The frame around every screen
- **Top bar:** on the left a Back or Close button when needed, then the screen title. On the right: the language switch (`EN | हिं`, a two-part pill; it changes `language` at once and everything on screen re-renders) and the red **Help** button (always visible, every screen).
- **Bottom bar** (5 places): Today · Ask · **Scan** (a larger round green button in the middle) · Numbers · Family. It is shown on Today, Ask, Numbers and Family. Scan, Answer, Help and Demo controls have no bottom bar; their top bar has a Back button that goes to Today (Help has its own Close). Back while the question panel is open closes the question with nothing saved, then goes to Today.
- **Long-press on the screen title** of the Today screen opens Demo controls.

## 3. Screens
### Today (home)
- Greeting: "Good morning, Maa" (spec 03 section 5 says which greeting at which hour) and the date and time line (from `AppClock`).
- A large green card: "NEXT UP", the nickname of the next dose, its slot and time, a wide white button "Scan a strip" (`scanStrip`). The next dose is the earliest slot of today that is due now (inside its window and not marked); if there is none, the earliest slot still to come; if there is none, the earliest passed slot that is not marked. When nothing is left the card shows the WL0 sentence, or WL0b for an empty day.
- Sections Morning, Afternoon, Evening, Night. Each row: a coloured round icon, the nickname, strength and time, and at the right the state chip: "Marked 8:05 am" (green), "Due now" (amber), "Later" (grey), "Not marked" (amber outline), "Not sure" (purple), "Stopped" (red, for stopped medicines listed last).
- A BUTTON medicine's row has a **Mark** button; its caption is "Checked by its button, not by camera". The button stays after the dose is marked. A stopped medicine has no Mark button.
- Tapping a row speaks its `statusAnswer`.
- From stage P08 the "NEXT UP" card also has a small microphone button. It opens Ask and starts listening.

### Scan
- Dark screen. The live camera fills the middle. A rounded white guide frame (85% of the width, 45% of the height) with the hint line under it ("Hold the whole strip inside the frame", "Bring it a little closer", "Reading the strip…").
- A torch button. A small text button "Show read text" toggles the debug list of read lines (off by default).
- The reader runs on every frame (spec 01 section 5). On a final verdict: vibrate, stop the camera, go to Answer.

### Answer
The top bar title is `resultTitle`. Top to bottom, as in `docs/design/answer-due.png` and the others:
1. **Verdict banner** (a rounded tinted card with an icon tile): title and one caption line.
   - Match, due and askable: amber, clock icon, "Due now · not marked yet", caption "Morning dose · 8:00 am".
   - Match, marked: green, tick icon, "Already marked", caption "Morning dose · 8:00 am".
   - Match, other rows: `greyBg` background, `brand700` text, tick icon, the nickname as title, the slot line as caption. (Before stage P06 there is no dose answer: every Match uses this style with no caption.)
   - Different, WrongMedicine: red, block icon, "Not on your list", caption "StripSense won't name it as your medicine".
   - Unreadable, NotSure: grey, refresh icon, "Can't read the print", caption "Turn it over, or lay it flat in good light".
   - Unknown: grey, the Info icon, "Not recognised", caption "It isn't on any list here".
   - NotShown: amber, "Not a strip I was shown". Stopped: red, "Stopped on your list". OneAtATime: grey, "One strip at a time".
2. **Medicine card** (only for Match, NotShown, Stopped, ButtonOnly, NotTaught): the coloured icon tile, the nickname (28), the strength (44), and a small grey chip with the printed generic and brand ("PARACETAMOL · DOLO 500").
3. **"StripSense says" card:** a small green tick badge and the label, a speaker button at the right (says it again), then the answer lines, each sentence its own paragraph. The CHK line is drawn as a quote with a green bar at its left. In a V1 answer the `{printed}` part is drawn in a red rounded box that never breaks across lines (the `BoxedSentence` shape in spec 07 section 7, with `textColor` = ink, `boxText` = stopText, `boxFill` = stopBg, and the font size 2 sp larger in Hindi).
4. **Second reader line** (when Gemma is ready): "Second reader is checking…", then the result.
5. **The question panel**, pinned to the bottom when the question may be asked: "Are you taking it now?" in 24 bold, a wide green **Yes** button, and **No** and **Not sure** side by side under it, a thin progress bar running down over 60 seconds, the caption "Buttons stay for 60 seconds".
6. After Yes: the banner turns green with the title `marked`, the answer card shows R1, and the panel becomes a green row "Marked at 8:05 am" (`markedAt`) with **Undo** (for 60 seconds) and **Done** (goes to Today). After Undo: the card shows U2, the banner goes back to its amber form, and "Scan another" is shown. The question is not asked again on this visit.
7. When no question: a wide button "Scan another" at the bottom.

### Ask
- Dark green screen. A chip with Gemma's state. The conversation as bubbles: the person's words on the right in green, the answer on the left in a white card with a speaker button.
- A row of question buttons that scroll sideways.
- At the bottom: the big round **microphone** button (hold to talk; it pulses while recording), a text box "Type a question…", and a send button.

### Family (behind the 4-digit PIN, asked once per app start)
Tabs: **List** · **Teach** · **Files** · **Settings**.
- List: each medicine as a card (nickname, brand, generics, strengths, times, taught or not, status). Buttons "Load the demo list", "Import a list file", "Save the list file".
- Teach: spec 05 section 4.
- Files (added in stage P10): "Save this week's file", "Save the list file" again, "Evening text" (shows it, Copy, and SMS if a SIM is present).
- Settings: the person's call name and whether to use the woman's forms, the contact's name and phone, the PIN (always 4 digits; the field saves only when it holds exactly 4), the voices found for English and Hindi, "Choose the model file" with Gemma's state, a link to Demo controls, and "Open-source credits".

### Numbers
Spec 05 section 6.

### Help card (full screen, red top)
- Title "Help", and under it the label `helpIntro`. No other body text. A very large button **Call 112** (opens the dialer with 112 filled in; says EM1). A large button **Call Rahul** (opens the dialer with the contact's number; hidden when no number is set).
- When opened for self-harm words (built in stage P08): SH1 as text at the top and a button **Call 14416** first. HLP1 is only spoken; the card shows no text for it.
- It closes only with its own **Close** button.

### Demo controls (from a long-press, no PIN)
- Clock: 8:05 am · 2:05 pm · 8:05 pm · real time. Fresh day. Seed yesterday.
- Switches: look check on/off · second reader off / on / strict · speak answers on/off · show read text on the Scan screen. "Speak answers" and "show read text" are kept in memory only; the others are saved in the store.
- Tuning numbers with plus and minus buttons: `minConf`, `minHeightPx`, `quickFrames`, `tLook`, `gemmaTimeoutMs`.
- "Load the demo list". "Reset everything" (asks twice).

## 4. Behaviour rules
- The screen stays on while Scan, Teach or the question panel is showing (`FLAG_KEEP_SCREEN_ON`).
- Portrait only.
- Every screen scrolls if the text is large. Nothing is cut off at the largest system font size.
- No screen shows an empty state without a sentence saying what to do next ("No list yet. Load the demo list or import a list file in Family.").
- The app asks for the camera permission when Scan is first opened and the microphone permission when the microphone button is first pressed. If refused, the screen explains in one line and shows a button that opens the app's settings page.
