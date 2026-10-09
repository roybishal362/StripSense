# P11. Trial mode and the Numbers screen

**Goal:** the app can count its own right, wrong and not-sure answers on real strips, and show the totals to a judge.

**Read:** `docs/spec/05-files-and-family.md` section 6, `docs/spec/06-screens-and-design.md` (Numbers).

## Build
1. `core/Trials.kt` (pure, tested): `score(truth, verdict): TrialScore` exactly as the table in spec 05 section 6, and `summary(trials)` giving the counts, the two separate lines (her taught strips, not-hers), median and 90th-percentile time, and the "by look alone" count. Tests: one per row of the scoring table; a summary over a small fixed list; the percentile of 1, 2 and 10 values.
2. **Trial mode** on the Numbers screen: a switch. When on, every way into the camera first opens the screen "What is in your hand?" (spec 05 section 6 says how trial mode behaves) with one button per list medicine, "Not hers / not taught", and "Hers, but print covered or cut". After the pick the camera opens. On the final verdict:
   - the Answer screen shows the verdict banner and only the first part of the answer, spoken; no dose question, no marking
   - a `Trial` is saved with the truth, the verdict, the score, the time from the first frame with text to the verdict, the look score, what the look alone would have named, and the second reader's result if it ran
   - a large button "Next try" goes straight back to the "What is in your hand?" sheet, with the last pick remembered
   - every try shows one line with its score (`trialLogged`), a wrong one in red, so the tester sees that it was logged
3. **Numbers screen** as in spec 05 section 6: the totals with "1 in N", the two lines, the times, the by-look-alone sentence, the Gemma block from P08 and P09, whether the look check and the second reader were on, the last look score, "Save trials file" (`Download/StripSense/trials.csv`), "Clear trials" behind the PIN, and at the bottom "This app has no internet permission" with the permission list read from `PackageManager` at run time.
4. The Numbers screen is readable from two metres: the four main counts in 44 sp.

## Do not
- Trial mode never writes dose events.
- The score comes from the table. The tester cannot edit it.

## Done when
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` succeeds.
- On the phone: 12 tries in trial mode (her strip, the other strength, a covered one, an unknown one, three times each). The counts add up to 12, the time line shows two numbers, and the trials file opens in the Files app with 12 rows.
- With trial mode off, scanning behaves as before and the question is asked again.

## Report
The 12-try totals and the median time.
