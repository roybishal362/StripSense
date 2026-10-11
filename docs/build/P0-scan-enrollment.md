# Scan-first medicine enrollment

The confirmed save defect is Android bitmap ownership: MediaPipe MPImage.close calls Bitmap.recycle. LookEmbedder passed the caller's photo directly, so teaching, reference retrieval and second-reader crops could lose their pixels after embedding. Give MediaPipe a private bounded bitmap. Check the actual Android capture/save path, not just pure store tests.

Manual-first enrollment also caused a real label to be entered with manufacturer APEX as brand and P-500 as the generic. The package says P-500 and Paracetamol. Reverse the workflow: capture the package first, extract the printed identity, review/correct the few extracted fields, choose prescribed times, then save the identity and captured references together.

Use bundled OCR for immediate candidates and the local Gemma vision model for structured label extraction. Permit a second side when the composition is missing. Keep raw print separate from identity evidence: dosage instructions, manufacturing numbers and dates are not ingredient strengths. Never invent a strength from a brand number or a schedule from the package dosage. Clearly mark missing fields for recapture/correction. Manual entry stays available as a fallback.

The family reviews the picture and extracted brand, ingredients, strengths and release. Store the medicine and reference photographs in one acknowledged transaction. Keep existing IDs and prescribed times when updating an existing medicine; retain earlier references only when the identity is unchanged. A supporting view is saved, but is not described as a verified label.

Existing entries can be reread from their most recent saved reference photo. Run fresh OCR on owned pixels, reconcile model product/ingredient/manufacturer confusion using printed evidence, then present the same review card. Support a separately printed DT variant and normalize joined brand numbers for matching. Do not overwrite the saved row without the family's review.

Keep the full-print and visible-conflict gates in the normal scan. Do not turn the nearest visual candidate into a dose instruction. To reduce OCR clutter, separate composition evidence from instructions before the match; show unresolved evidence clearly. Verify the real P-500 capture on this phone, save it, restart, and inspect its photo and vector. Do not create demo medicine data or dose marks. Do not commit or push.
