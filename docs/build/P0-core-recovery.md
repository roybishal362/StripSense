# Core recovery

The family owns the medicine identities and schedules. Capture the label first, let OCR and local vision extract a draft, then review it and choose prescribed times. Manual entry and CSV remain optional fallbacks. See SCAN-FIRST-PLAN.md for the Android bitmap fix and enrollment transaction.

## P0: teaching and recognition

1. Capture a reference on an explicit tap. Freeze the picture and its read text for family review. Do not throw away Save because a later camera frame changes.
2. Show the saved medicine identity beside the photograph. Reject visible conflicting brand, generic, strength, release or variant. Require the family to confirm the photographed medicine. A full print match is a verified reference; a back or used view is a supporting reference.
3. Save the photo and visual features off the main thread. A teaching success requires the reference to be written to the JSON store. A missing embedding must not discard the photograph. Report failures and keep the review available for retry.
4. Keep up to eight reference views per medicine, including the printed side, back, different lighting and a used strip. Never erase existing views when adding another.
5. Group separate OCR regions. Track the same evidence across frames. Draw each box in the actual PreviewView coordinates. Tap a verified box to recheck the current frame and pass that region's photograph to the existing answer and second reader.
6. For incomplete print, retrieve compatible saved views, using print fragments plus visual features and a margin over competing medicines. Show a possible match and its saved photo. An appearance-only similarity does not authorize a dose confirmation. A visible conflicting strength always blocks the candidate.
7. A green due highlight comes only from a full print match, a passed enabled saved-reference check and a saved active schedule whose slot is unmarked. The second reader runs after selection. The box is a schedule reminder, not a new prescription.

Physically indistinguishable or unprinted pieces cannot be identified reliably from a camera alone. Ask for the printed side or family confirmation rather than turning the highest similarity into a certain identity. Phone trials determine useful retrieval thresholds; no accuracy percentage is claimed before measured real-strip trials.

## P1: real data

Remove demo list loading, synthetic event actions and demo clock controls from navigation. New stores are empty. Migrate only unchanged, untaught sample rows and their synthetic demo events; preserve user medicines, taught photos, non-demo marks, family edits and the Gemma model. Reset any leftover simulated clock to real time.

## P2: existing defects

Apply the independently reviewed import-ID, malformed-CSV, numeric-token, exact-generic, model busy, camera executor, bounded OCR, answer lifecycle and laptop evening-summary fixes. Merge them into the latest source instead of replacing Kiro's valid multi-strip additions. Build and run meaningful core regressions, then install without clearing device data.

## Phone acceptance

Add a real medicine, teach two reference views, restart and confirm both remain. Compare a full strip, a used strip, an unrelated foil and a wrong strength. Show two or three separated strips and tap a recognized box. Verify the answer crop is from that box, dose marking requires confirmation, and a candidate does not open the dose question. Test typed Gemma requests and inspect filtered logs. Physical real-strip trials require the strips in front of the phone.
