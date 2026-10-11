# StripSense: the whole app, written down

This file describes everything the app does, how it is built, what has been checked, and what comes next.
`README.md` is the short version. `docs/` holds the design notes written before the event.

Built at the iQOO Hackathon 2026 Grand Finale, Bengaluru, 9 to 11 October 2026, Smart Living track, by Bishal Roy.

## 1. What it is

A phone app for an older person who cannot read the print on a medicine strip.

- It reads the strip in her hand and checks it against the list her family saved.
- It tells her which medicine it is and whether this dose is due, in Hindi or English.
- It can do a whole dose by touch alone, for someone who can neither read the strip nor hear the phone.
- It can hear the tablet pushed out of the strip, so a dose needs no button.
- Her family, far away, sees the week and how each dose is known.
- It counts the tablets down by itself, follows the doctor's number of days, and says before a medicine runs out.
- A character called Pattu grows with every dose. There are points, levels, a row of days, a gift each day, a little chit chat and two games, so there is a reason to come back tomorrow at any age.

It gives no medical advice. It never says a dose was taken. The word is "marked".

## 2. One dose, from the alarm to the family

1. **The alarm.** At the saved time the phone rings as a full screen, with every medicine of that time. It is also felt: one long buzz, then the rhythm of the medicine it is for.
2. **Picking the phone up** from the table stops the ringing and opens the camera. "Find my medicine" does the same.
3. **The camera** follows every strip in view. The phone says where the due strip is ("move the phone right"), draws an arrow, and leads the hand by touch.
4. **The strip is checked.** The name and the strength are read from the print over several frames and compared with what that family's own strip prints. Anything that differs is a refusal.
5. **The question.** "Are you taking it now?" It is answered by a press on Yes, by the sound of the tablet coming out, or in "Touch only" by two knocks on the phone or a volume button.
6. **Marked.** The phone says what it marked. Undo stays for one minute.
7. **The round.** If several medicines share that time, the phone goes on to the next strip by itself.
8. **The family.** With the family link on, their page shows the dose within about five seconds, with how the phone knows about it.

## 3. Everything it does

### Finding and checking a strip
- Reads print with ML Kit and gathers it over about 2.5 seconds of frames, because foil is shiny and one frame is never enough.
- Several strips in view at once, each with its own box.
- Spoken directions and an arrow to the strip that is due.
- Point a finger at a strip: "Yes, this is the one" or "Not this one, yours is to the right". The app has its own finger finder for a finger seen from above, and MediaPipe's hand model for a whole hand.
- Asks you to come closer when the print under the finger is too small to read.
- Says what a medicine is for ("your BP medicine"), only after the family has confirmed it.
- Warns when two strips have the same ingredient and one was already marked today.
- Reads the expiry date and says when a strip has expired.
- Several people on one phone: "This is Papa's medicine. It is not yours."
- A cut piece of a strip can be offered as a possible match. It is never an answer.
- Ask the camera out loud: "Is this my BP medicine?" Gemma understands the question; the answer comes from what the camera reads.

### By touch
The phone has a vibration motor at each end and each can be driven by itself (`ui/shell/Feel.kt`). The patterns are in `core/guide/FeelLanguage.kt`.

| Felt | Means |
| --- | --- |
| One buzz | Move the phone left |
| Two buzzes | Move the phone right |
| A tick, then a buzz. It runs up the phone. | Move the phone forward |
| A buzz, then a tick. It runs down the phone. | Move the phone back |
| Plain quick ticks, faster and faster | The strip is under the phone and nearing the middle |
| One long buzz that swells | Yes, this is the one |
| Two sharp knocks | No, not this one |
| A tap, a rest, a rising hum | "Are you taking it now?" |
| Three taps that grow | Marked |
| A heartbeat, twice | A BP or heart medicine |
| Three quick taps, twice | A sugar medicine |
| A slow wave, twice | A fever or pain medicine |
| Two long soft breaths | A breathing or allergy medicine |
| Two even beats | Any other medicine |

- The four ways to move differ in how many parts they have and how long the parts are, so a hand tells them apart without having to feel which end of the phone moved.
- A pattern is not begun again until it has ended and the hand has rested (`Homing.periodMs`), so two patterns never run together.
- "Voice off" on the scan screen silences the voice only. Touch goes on.
- **"Touch only"** (Family, Settings, "Feel the way"): the phone makes no sound and does the whole dose by feel.
- Two knocks on the phone answer yes (`core/guide/DoubleKnock.kt`, `ml/KnockListener.kt`). A knock is a jolt of at least 3.5 m/s² lasting at most 140 ms. A pair is two of them 150 to 650 ms apart with quiet before and after. The phone's own motors are ignored while they run.
- This phone holds vibration back while its camera is open. The app measured which kind of vibration it lets through and sends its patterns that way, so touch works during a scan.

### Hearing the tablet
- While the question is open the microphone is measured 200 times a second in the band from 8 to 16 kHz (`core/hear/Clicks.kt`, `core/hear/PopWatch.kt`, `ml/TabletListener.kt`).
- A tablet pushed through foil is a sharp crack with crackle around it. One clap is not.
- The movement sensor is watched too, so a knock on the phone is not taken for a tablet.
- The phone does not listen while it is speaking.
- Where sharp sounds keep coming or the room is loud, it says "It is noisy here. Press Yes." It would rather hear nothing than hear wrong.
- It listens only in the one minute after the camera has checked the right strip at the right hour. It says out loud what it marked, and Undo stays for a minute.
- When Yes is pressed, the loudest crack heard meanwhile teaches the phone what a tablet sounds like for this person.
- Nothing is recorded. Samples are measured and thrown away.
- The family can switch it off (Family, Settings, "Hear the tablet come out").

### Alarms and doses
- Exact alarms at the saved times, as a full ringing screen over the lock screen.
- It asks again and stops when the dose's time has passed.
- "Remind me later" inside the dose's time.
- A dose can be answered Yes, No or Not sure.
- Every mark stores how it was made: heard, strip seen then yes, or a button.

### For the family
- Family side behind a four-digit PIN.
- Teach a strip by showing it: the phone drafts the label, the family reviews it and sets the times. Up to eight views of a strip.
- Teach by showing the strip and saying when it is taken.
- A prescription photographed and read against the strips the family owns.
- A message in the family's own voice for each medicine.
- People: each person has their own medicines.
- The Health tab: the week as a ring, dots, "needs a look", and how each dose is known.
- Share: the family link, and the week as a file.
- An example week for showing the app on its first day. It is labelled as an example wherever it shows.

### Staying with it, day after day
An alarm and a check are not a habit. These three are what bring a person of any age back tomorrow.

- **Tablets left, and the doctor's course** (`core/dose/Supply.kt`, `core/say/SupplyText.kt`).
  - The family counts once ("30 tablets") and, if the doctor gave a number of days, enters it (Family, List, the medicine).
  - Every marked dose takes one tablet away. An undone mark gives it back. Nobody has to count again.
  - The phone says "9 tablets left, enough for 4 days" and "Day 3 of 5".
  - At three days or fewer it says "Only 2 days left: 5 tablets. Tell your family to buy more", on Today and inside the dose's own alarm.
  - If the tablets will not last the course it says so: "Tablets for 5 days, but 15 days of the course are left."
  - When the course is over: "The 5 days the doctor said are over. Ask the doctor before you go on." It never says to take more or less.
- **Pattu and the journey** (`core/play/Journey.kt`, `ui/play/`). Pattu is the app's character: a round little sprout. "Patta" means both a leaf and a strip of tablets.
  - **Points** come from doses, by how the phone knows about each one: a button press 10, a strip the camera checked 20, a tablet the phone heard 30, and 20 more for a day with every dose marked. Points cannot be had by pressing a button over and over.
  - **Levels**: Seed, Sprout, Bud, Bloom, Sparkle, Garden, Forest. Pattu is drawn as its level: it grows from a seed to a flower.
  - **The row of full days**, with a flame, and the last seven days as dots. An early morning before the first dose neither adds to the row nor breaks it.
  - **Today's goals** (mark every dose, let the phone check one strip, open today's gift) and **badges** to collect.
  - Pattu breathes, blinks and hops. Its face is its mood: it looks at you when a dose is due, beams when the day is done, droops after a missed dose, sleeps at night. Tapped, it says a line out loud. It is cheerful and never scolds.
  - **Five Pattus to open**: Sprout, Puppy, Kitten, Fairy and the Little devil doctor, one at each level from 1 to 5. All are the app's own drawings.
  - When a dose is marked, confetti falls for a moment and the points are shown. It takes no touch, so Undo under it works as before.
- **Today's gift** (`core/play/Gifts.kt`). When the day's medicine is on track, the person chooses a gift: a good thought, a movie, a book, an anime or a K-drama. The list is inside the app. The phone keeps what it gave and does not give the same thing twice until it has given them all.
- **Today's chit chat** (`core/play/Chat.kt`, `core/play/PattuTalk.kt`). On a later day Pattu asks how the gift was, once. What was loved is offered first next time. On a day with nothing to ask about it asks how the day is going.
  - The person can also type to Pattu. That is answered by the model on the phone from a book of knowledge: what Pattu gave, what the person said about it, what else it may suggest and how the journey stands.
  - It is the one place where a model's own words are shown, and it is small talk only. A message about medicine or health is never sent to the model: Pattu says to use the Ask tab or ask the doctor. A reply is shown only when it is short and has nothing about medicine in it. Otherwise a fixed sentence is used.
- **Games.**
  - **Tic-tac-toe with Pattu** (`core/play/TicTacToe.kt`). Pattu plays better as the level goes up and cannot be beaten from level 5. The first win of a day earns points.
  - **Today's code** (`core/play/DailyCode.kt`). One code for each day, the same for everyone, made from the date. Pattu plays it into the hand while its pads light up, and the person presses it back; it grows by one each time, with three hearts. With "eyes closed" the pads do not light up and it is only felt, for double points. Each weekday has its own kind of code.
- **For whoever shows the app:** an example family (a wife and a baby with example medicines) can be added beside the example week, and taken out with one press.

### Always
- Help is on every screen.
- English and Hindi, for the screen and the voice.

## 4. How a strip is recognised

- Each saved medicine is known by its reviewed label and by the words and numbers read from its own saved photos (`core/scan/PrintIndex.kt`).
- Each frame adds votes. The answer comes from the last 2.5 seconds together (`core/scan/PrintEvidence.kt`). Old readings expire.
- **Checked** needs the saved name read in two frames, the telling strength read in two frames, and nothing that differs.
- **A difference is a refusal:** a number the saved strip never showed, an extra word such as PLUS or SR, another saved medicine's ingredient.
- **Misreads are outvoted, differences are not.** A near-miss like 225 for 325 is ignored only when the right number is read at least twice as often. 650 is never treated as a misread of 500.
- **Name only:** when the strength is not read, the phone says so and does not lead to that strip.
- A strip is never named from how it looks.

## 5. Rules the code keeps

1. A strip is named only from print that was read.
2. A different strength, or anything else that differs, is a refusal.
3. A dose is marked only by an answer to the open question. The question opens only after the camera has checked the strip for a dose that is due.
4. The phone says what it marked, and Undo stays for a minute.
5. No sentence says "you took it". No medical advice.
6. Every answer about a medicine is a fixed sentence from `core/say/`. No model writes one. The only words of a model that are ever shown are Pattu's small talk, and a question about medicine or health is never sent there.
7. Gemma never decides a match and never marks a dose.
8. The Help button is on every screen.

## 6. Privacy

- Everything is stored on the phone in one file, sealed with AES-256-GCM. The key is held by the phone's keystore (`data/KeystoreCipher.kt`, `data/Store.kt`). Taught photos and voice messages are sealed the same way.
- A saved file that cannot be opened is never written over. The family is asked what to do.
- No backup of the app's data leaves the phone.
- The app does not log sound, pictures or the text it read.
- **The network is used for one thing: the family link.** It is off until the family turns it on.
  - The phone sends one message: battery, charging, and for each person the week of doses.
  - Never a photo, a recording, a prescription or the camera picture.
  - The message is sealed on the phone with a key made from the family code (PBKDF2-HMAC-SHA256, 100,000 rounds, then AES-256-GCM).
  - The relay keeps one sealed message for each family and has no key. It cannot open one.
  - The family page opens the message in the browser. The code sits after the `#` in the link, which a browser never sends to a server.
- Scanning, voice, alarms and storage work with no network.

## 7. How it is built

Kotlin and Jetpack Compose, one activity, package `app.stripsense`. No dependency-injection, navigation or database library.

```
app/src/main/java/app/stripsense/
  MainActivity.kt   the only activity
  AppState.kt       the app-wide holder: store, clock, language, screen
  core/             pure Kotlin, no Android imports, unit-tested
    domain/         the data: medicines, people, events, settings
    text/           reading words and strengths out of print
    scan/           the print engine and the scene of several strips
    guide/          what is said and felt: directions, the hand, touch, knocks, pick-up
    teach/          drafting and checking a label when a strip is taught
    dose/           dose times, the question, reminders, the example week
    hear/           finding a tablet in the microphone's samples
    play/           the journey: points, levels, gifts, chit chat, the games, and which Pattu it is
    say/            every sentence, in English and Hindi
    voice/          understanding a spoken request
    report/         the week, as a view and as a file
    rx/             reading a prescription against the saved strips
  data/             the sealed store, photos, voice notes, the family link
  ml/               the camera reader, Gemma, the hand finder, the listeners, the voice
  reminders/        alarms and notifications
  ui/               Compose screens: shell, scan, ask, today, family, setup, theme
app/src/test/       unit tests
app/src/androidTest/  checks that run on the phone
backend/            the relay and the family page (Python, standard library only)
laptop/             the older one-file family page for an exported week
docs/               design notes from before the event
```

### What runs on the phone
- **ML Kit Text Recognition** reads print.
- **Gemma** (`gemma-4-E2B-it.litertlm`, LiteRT-LM, on the GPU) understands a spoken question, drafts a label when a strip is taught, reads print the fast reader cannot settle, and reads the strength a second time.
- **MediaPipe Hand Landmarker** finds a fingertip when a whole hand is in view.
- **Android text to speech**, with offline Hindi and Indian English voices.
- The app's own code for the finger seen from above, the tablet's sound, the two knocks and the pick-up.

### What it uses of the phone
Camera, microphone, movement sensor, two vibration motors, the GPU, exact alarms, the lock screen.

### Permissions
| Permission | Why |
| --- | --- |
| CAMERA | Reading strips |
| RECORD_AUDIO | Spoken questions, hearing the tablet, the family's voice messages |
| VIBRATE | The language of touch |
| POST_NOTIFICATIONS, USE_FULL_SCREEN_INTENT, SCHEDULE_EXACT_ALARM | Alarms at the saved time, as a full screen |
| RECEIVE_BOOT_COMPLETED | Alarms set again after the phone restarts |
| INTERNET | The family link only |

## 8. The relay and the family page

In `backend/`. Python, standard library only.

```powershell
cd backend
python -m stripsense_backend relay
```

Then open `http://127.0.0.1:8765`. With the phone on a cable, `adb reverse tcp:8765 tcp:8765` lets the phone reach the laptop at the same address.

- `POST /api/box/<id>` keeps a family's latest sealed message. `GET /api/box/<id>` gives it back.
- `/` is the family page. It asks for the family code once and opens the message in the browser.
- The relay's data is one SQLite file in `backend/data/`, which is not in the repository.
- `serve`, `ingest` and `pull` are the older way, by exported week files. The app's Share tab can still save a week file.

Tests: `python -m unittest discover -s tests` from `backend/`.

## 9. Build, test, install

```powershell
powershell -ExecutionPolicy Bypass -File .\build-local.ps1
```

This runs the unit tests and builds `app/build/outputs/apk/debug/app-debug.apk` offline. It is a debug build.

The model file `gemma-4-E2B-it.litertlm` is not in the repository. It is chosen once on the phone. Scanning, alarms and marking work without it.

Checks that run on the phone itself (`app/src/androidTest`, numbers and switches only, never names or pictures):

```powershell
adb shell am instrument -w -e scanOnly true app.stripsense.test/app.stripsense.device.CoreDeviceChecks
```

Other switches: `stateSummary`, `feelWithCamera`, `knockNumbers`, `hearSeconds`, `hearDose`, `stageRehearsal`, `exampleWeek`, `liveOnly`, `handOnly`.

## 10. What has been checked

- **606 unit tests in 72 classes pass, and 15 tests of the relay.** They cover everything in `core/`.
- **The four ways to move agree everywhere.** `SteerAgreesTest` pins the place of the strip in the picture to what is said in English and Hindi and to what is felt, for left, right, forward and back. The arrow on the screen is drawn from the same value. `PrintEvidenceTest` uses text the phone really read from three strips, with its misreads. `HearTabletTest` uses real recordings of a tablet and of busy rooms.
- **Used with real strips through the whole event:** reading, refusing, several strips in view, spoken directions, a pointing finger, Hindi.
- **No strip named wrongly in the checks on saved photos.** Each photo was read again as one still picture and left out of what the engine learned from. It named the right strip or declined: 6 of 6 on one set of photos, 1 of 4 on a stricter one, none wrong on either. One still is the hardest case, because the live camera gathers print over many frames.
- **A whole dose run on the phone, from the alarm to the family page.** Alarm, camera, question, answer, marked, sent to the family. Once answered as two knocks do, twice by the sound of a tablet played from a recording.
- **The motors.** The phone's own record shows each motor running alone, and vibration getting through with the camera open.
- **Storage.** Checked on the phone: sealed, opened again, unreadable as plain files, and changed bytes are refused.

## 11. What comes next

- Put it in the hands of people who cannot see or hear well. It was built this weekend, and they are the ones to judge the language of touch.
- Count accuracy over many live tries, and how often the phone hears a tablet rightly. Until that is counted, no percentage is claimed.
- Measure glare, a real wrong-strength strip, and alarms in battery saver.
- A public address for the relay, so the family page opens from anywhere without a cable.
- More languages than Hindi and English.
- Renaming or removing a person, and Undo on the alarm screen itself.

What the app keeps to on purpose: it says "marked", never "taken"; it gives no medical advice; and print too small or worn to read stays "name only" and is never guessed. Loose tablets, syrups, insulin and eye drops get a button, not the camera.

## 12. For whoever shows the app

Family, long-press the "Medicines of ..." line, then Settings. Two tools appear that a family never sees:
- **Demo time** moves the app's own clock so the next dose is due now, and can ring its alarm at once. It goes back to the real time when the app restarts.
- **Example week** fills the six days before today so the Health tab and the family page are not empty. Today stays real.

## 13. Credits

- Test recordings in `app/src/test/resources/sounds` are from freesound.org. Their authors and licences are in the README in that folder.
- ML Kit, MediaPipe, LiteRT-LM and Gemma are Google's. CameraX and Compose are AndroidX.
- All app code was written during the event. The design notes in `docs/` were written before it.
