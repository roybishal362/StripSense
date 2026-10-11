# StripSense first evaluation

## What to do before entering

1. Keep the phone charged. Open StripSense. Do not clear app data or delete the local model. In Family > Settings wait for the model to be Ready. English voice is installed; offline Hindi voice is not installed on the checked phone.
2. Review each real medicine against its strip. Teach > Read label and update details can reread a saved reference photograph. Check product name, ingredients, strength and DT/SR/other variant. Confirm and save only after review. Two P-500-related entries have different times and mixed APEX/P-500 identities; do not present them as verified prescriptions until corrected by the caregiver. Do not change real prescribed times just to make a demonstration green.
3. Open Family > Settings > Offline medicine alarms. Allow notifications and precise alarms. Check sound. Press Test an alarm in 15 seconds, turn the screen off and listen. It is a labelled delivery test, with no medicine or intake mark. Actual screen-off arrival and sound still need confirmation on this phone.
4. Place two or three strips separately on a plain surface, print up, in steady light. Turn over a reflective strip instead of forcing a guess from unreadable foil. Open Which strip to take? and keep the phone steady. A full printed match plus the enabled reference check is required before a schedule color is assigned.
5. Prepare Evaluation rehearsal as the fallback: Family > Settings > Evaluation rehearsal, below the alarm controls in this debug build. It is a separate simulated example. It does not use the live camera or save real medicine/history. Rehearse its confirmation and reset button before presenting.

## Sixty-second pitch

"When children move to another city, they can call their parents, but they cannot always help at the moment a parent is confused between similar medicine strips. We are building StripSense so parents can keep their independence and children can help with the complicated setup.

The parent shows the strips. The phone checks the printed medicine details against a reviewed family list and highlights the saved schedule: green for the next medicine, grey for a marked dose, and red when the time has passed without confirmation. It refuses unclear or conflicting labels rather than guessing.

Teaching, recognition, schedules, alarms and local history run on the phone without internet. Our prototype combines the camera, OCR, local AI and a separate schedule engine. The next stage connects family devices so children can manage schedules remotely and receive unresolved-confirmation alerts, while parents keep their local reminders during outages."

## Explain the technology

- Kotlin and Jetpack Compose build the Android app.
- CameraX captures frames. ML Kit OCR reads medicine print. The app groups separate printed regions and tracks them across frames.
- Gemma 4 E2B through LiteRT-LM helps draft medicine details from label photographs locally. The caregiver reviews the draft before saving. Gemma does not prescribe a dose or invent its timing.
- MediaPipe image embeddings and a local visual fingerprint compare taught reference views. Similarity can suggest a comparison; it does not prove the identity of an unprinted fragment.
- A pure Kotlin dose engine combines reviewed schedules with append-only confirmation events. The schedule layer supplies the now/next/later/marked/unconfirmed state independently of recognition.
- Private local JSON and saved reference photographs persist the family's data. Teaching and notification confirmation wait for durable saves.
- Android AlarmManager, a notification channel and a persistent retry ledger implement local reminders. Alarms depend on phone state, permissions and sound settings. They do not load the AI model.
- Android TextToSpeech speaks fixed English/Hindi answer sentences. The system records confirmation, not verified swallowing.

## Three-minute live demonstration

1. Show the real medicine list and explain that the caregiver checks labels and prescribed schedules. Open a saved reference or reread its label. Do not save an unreviewed extraction.
2. Open Which strip to take? and show two or three separated strips. Explain each visible status. Green means scheduled now or next at the displayed time; a future green box does not mean take it early. Blue means a later medicine. Grey with a check means that scheduled dose was marked. Red means the time passed without confirmation, not proof it was missed and not advice to take it late.
3. Tap a verified currently scheduled strip. Confirm only for an actual intake you intend to record. For the presentation's artificial transition use the isolated rehearsal instead. Show grey after confirmation and the next green example. No dose is recorded simply because the camera saw a strip.
4. Show the labelled 15-second reminder test and local history. To prove lack of internet dependency, disable Wi-Fi/mobile data yourself and repeat local navigation/recognition/reminder checks; do not claim a check you did not observe.

If the live camera does not reliably match, say: "Live recognition still needs calibration. This labelled rehearsal demonstrates the intended interaction." Open Evaluation rehearsal and press Simulate confirmation for Example A. Example A turns grey with a check, Example B turns green with its future time, and Example C remains red. This transition was inspected on the connected phone. The screen explicitly identifies itself as simulated; do not present it as live AI accuracy.

## Genuine iQOO contribution

The connected iQOO 15 processed two actual saved strip photos locally using GPU extraction in 4,384 and 4,724 milliseconds. This is limited latency evidence, not a recognition accuracy benchmark. Its camera supplies label photos and local compute avoids sending private medicine images to cloud AI.

Official India specifications list a 7,000 mAh battery and OriginOS 6 based on Android 16. Capacity can help keep the reminder device available between charges. The app uses standard Android alarms and permission controls, not a claimed private iQOO AI API. Battery and last-seen monitoring for distant caregivers are planned sync features. [Official iQOO specifications](https://www.iqoo.com/in/products/param/iqoo15).

## What is complete and what remains

The local implementation is built: scan-first reviewed teaching and references, print checks, local model extraction, schedule/history screens, spoken fixed answers, alarm scheduling, bounded retries/snooze and explicit durable reminder confirmation. The current APK has no INTERNET permission. Four added next-selection checks bring the JVM total to 280; six earlier native teaching checks passed.

Live multi-strip accuracy, partially used-strip performance, physical screen-off sound delivery, reboot/power-saving delivery and broad end-to-end acceptance are not certified. Remote authentication, separate patient profiles across devices, automatic family sync, distant caregiver escalation and fresh remote battery status are planned. Describe them as the next stage, not already working.

Do not claim 100 percent recognition, verified swallowing, guaranteed alarm delivery or fully deployed remote family management. A clear working local prototype with honest boundaries is stronger than an unreliable claim.
