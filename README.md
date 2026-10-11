# StripSense

An older person has several medicine strips on the table and cannot read the print. An alarm rings. StripSense finds the strip that is due, by sound and touch, and asks before it marks the dose. If the print on a strip differs from the family's saved strip, it refuses.

Reading, speaking and deciding all run on the phone. The network is used for one thing only: the optional family link, which is off until the family turns it on. It sends one sealed message (battery, charging and the week of doses) that only the family's own code can open. Scanning, voice, alarms and storage work with no network.

`DOCUMENTATION.md` describes the whole app: every feature, the language of touch, the rules the code keeps, privacy, how it is built and what has not been checked yet.

## What it does

1. **The family shows each strip once.** The phone reads the label, the family reviews it and sets the prescribed times. The photos and the text read from them are saved.
2. **At the saved time the phone rings.** It asks again every two minutes and stops thirty minutes after the saved time. The reminder offers: Find my medicine, I've taken it, I'm not sure, Remind me later.
3. **Find my medicine opens the camera.** Every strip in view is followed on its own. The phone speaks where the due strip is. It also ticks, and the ticking gets faster as the due strip comes to the centre of the view. When the strip is centred there is one firm pulse. Held there, its answer opens by itself.
4. **The phone asks "Are you taking it now?"** A dose is marked only by an answer to that question: a press on Yes, the sound of the tablet pushed out of its strip while the question is open, or in "Touch only" two knocks on the phone or a volume button. The phone says what it marked and Undo stays for a minute. Scanning, waiting and closing never mark anything.
5. **By touch.** The phone has a vibration motor at each end. Each way to move the phone has its own pattern: one buzz for left, two for right, a tick and then a buzz for forward, a buzz and then a tick for back. Quick ticks mean the strip is under the phone. One long swelling buzz is yes, two sharp knocks is no, and each kind of medicine has its own rhythm. With "Touch only" on, the phone is silent and a whole dose is done by feel (`core/guide/FeelLanguage.kt`, `ui/shell/Feel.kt`).
6. **The family far away.** With the family link on, a son or daughter opens one page and sees the week, and for each marked dose how the phone knows: seen and heard, strip seen, or button pressed. The relay that carries the message cannot open it (the `backend` folder).

## How a strip is recognised

- **Print signature.** Each saved medicine is known by its reviewed label and by every word and number the phone read from its own saved photos (`core/PrintIndex.kt`). A strip is compared with what that strip really prints.
- **Evidence over frames.** Foil is shiny and one camera frame rarely shows everything. Each frame adds votes for what it could read. The answer comes from the last 2.5 seconds together (`core/PrintEvidence.kt`). Old readings expire, so a strip swapped in at the same place inherits nothing.
- **Checked** needs the saved name read in two frames, the telling strength read in two frames, and nothing that differs.
- **A difference is a refusal.** A number the saved strip never showed, an extra word such as PLUS or SR, or another saved medicine's ingredient. 650 is never treated as a misread of 500.
- **Misreads are outvoted.** The phone sometimes reads one "325" in three as 225. A number that is nearly the saved number is ignored only when the saved number is read at least twice as often on the same strip.
- **A torn piece** with no name on it can be offered as a possible match from the words left on it. It is never an answer and never opens a dose question.
- **Name only.** If the name is read but the strength is not, the phone says so and does not lead the hand to that strip.

The models all run on the phone: ML Kit reads the print, a MediaPipe hand model finds a pointing finger, Gemma (LiteRT-LM, on the GPU) drafts a label when a strip is taught, reads the strength a second time on the answer screen and answers spoken questions, and Android's offline voice speaks. Every spoken answer is a fixed sentence from `core/Sentences.kt`, so no model can invent a dose.

## What has been checked, and what has not

- 561 unit tests in 64 classes pass, and 15 tests of the relay. `core/` is pure Kotlin. `PrintEvidenceTest` uses the text the phone really read from three strips, with its misreads. `HearTabletTest` uses real recordings of a tablet and of busy rooms.
- On the phone, the read-only check (`scanOnly`) passes its 3 fixtures. The four saved strip photos were each read again as one still picture and shown to the engine with that photo left out of what it learned from: the strict rule named 1 as itself and none wrongly. An earlier run on six photos named 6 of 6, but that was another set of photos and two rules had been added after a first run of 4 of 6. Neither is a live accuracy figure. The live camera gathers print over many frames and does better than one still; that has not been measured.
- The touch language, the two knocks and hearing the tablet were checked from the phone's own records and with recorded sound. Nobody blind or deaf has tried them, and there is no figure for how often the phone hears a tablet rightly.
- Not measured: live accuracy on moving strips, a real wrong-strength strip, glare, and alarm delivery with the screen off or in battery saver.

It gives no medical advice. It never says a dose was taken, only that it was marked.

## Build

The project is this folder. From here:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-local.ps1
```

This runs the unit tests and builds `app/build/outputs/apk/debug/app-debug.apk` offline. It is a debug build, not a signed release.

The read-only check on the phone's saved photos:

```powershell
adb shell am instrument -w -e scanOnly true app.stripsense.test/app.stripsense.device.CoreDeviceChecks
```

The relay and the family page, from the `backend` folder (Python, standard library only):

```powershell
python -m stripsense_backend relay
```

Then open `http://127.0.0.1:8765`.

The model file is `gemma-4-E2B-it.litertlm`. Choose it in first-run setup or in Family > Settings. The app works without it: scanning, reminders and marking do not need it.

## Set up your own medicines

1. On a new phone the setup screen asks for the person's name, the family contact, a family PIN and the permissions.
2. Family > List > Scan a new medicine. Capture the name and the composition. Review the draft, choose the prescribed times, confirm and save.
3. Family > Teach adds more views of a strip, up to eight. A photo that shows another strength or another saved name is refused.
