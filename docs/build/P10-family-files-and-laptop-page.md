# P10. The week file, the evening text, the family's laptop page

**Goal:** the phone writes the week as a file, and a one-file web page on the family's laptop shows it. The file travels by Office Kit.

**Read:** `docs/spec/05-files-and-family.md` section 5, `docs/spec/02-dose-rules.md` section 10 (vector T15), `docs/spec/01-reading-and-matching.md` section 7 (the list M1 to M5), `docs/spec/06-screens-and-design.md` section 1 (colours).

## Build
1. `core/WeekFile.kt` (pure, tested): builds the `week.csv` text of spec 05 section 5 from the medicines, the events and now. Test: after the events of spec 02 vector T15 the file has the header lines, a `marked` row for the fever tablet at 08:00 with `marked_at` 08:05 and source `prompt`, `not marked` rows for the sugar tablet and eye drops at 08:00, and `later` rows for the afternoon and night.
2. `core/EveningText.kt` (pure, tested): the message of spec 05 section 5. Test with the example in the spec.
3. **Family > Files** (the third tab, between Teach and Settings): "Save this week's file" (to `Download/StripSense/week.csv`, replacing an older one with `saveText` of spec 07 section 7), "Save the list file", "Evening text" (shown, Copy, and "Send as SMS" only when a SIM is ready; it opens the SMS app for the contact's number, or with no recipient when none is set). After each save, one line: "Saved to Download/StripSense. Send it to the laptop with Office Kit."
4. `laptop/family.html` in the repository's top folder, as in spec 05 section 5: one static file, plain HTML, CSS and JavaScript, no libraries, no network. Drag and drop or a file button for `week.csv` and `list.csv`. The week table, the legend, the latest day's evening text, "last updated …". The app's colours. It must open from a `file://` path.
5. Put a sample `laptop/sample-week.csv` next to it, written by the same code path as the app (spec 05 section 5 says how a test keeps it true), so the page can be checked on the laptop without the phone.

## Do not
- The app sends nothing by itself. SMS only opens the phone's SMS app with the text filled in.
- The page has no server and loads nothing from the internet.

## Done when
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` succeeds.
- On the laptop: open `laptop/family.html` in a browser, drop `sample-week.csv` on it, and the table appears.
- On the phone: mark a dose, save the week file, send it to the laptop with Office Kit file transfer, drop it on the page: the new mark and its time are in the table, and "last updated" shows the export time.

## Report
A one-line description of how the page looks, and anything in the CSV that was awkward to parse.
