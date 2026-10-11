# Core recovery handoff

Latest scan speed follow-up: SCAN-LATENCY-FIX.md. The current total is 285 passing JVM checks. Fingerprint-first lazy embedding, immediate per-region results and a shorter batch interval remove avoidable waiting without changing identity thresholds. Earlier native checks remain teaching evidence, not live camera timing measurements.

The latest follow-up is SCAN-FIRST-HANDOFF.md (source: docs/build/P0-scan-handoff.md). It supersedes the earlier manual-first setup and documents the Android bitmap ownership failure that the first JVM-only teaching checks missed. Use Scan a new medicine or Read label and update details for enrollment. Six native checks now pass, including read-only extraction from the user's actual P-500 and Parawel-325 DT photos.

The current total is 280 passing JVM checks. Four new checks verify next selection, marked-state preservation and no early intake prompt. REMINDER-HANDOFF.md documents offline background alarms, bounded retry/snooze, durable reminder confirmation and explicit scan timing. The latest scan colors are green for now/next, grey checked for marked, red for overdue/unconfirmed, blue for later and amber for uncertain. EVALUATION-GUIDE.md describes the debug-only simulated rehearsal, whose confirmation transition was inspected on the phone. OFFLINE-FAMILY-PLAN.md is the plan for optional remote family exchange; online accounts, multi-patient device linking and remote escalation are not implemented. Screen-off delivery still needs its labelled test because the connected phone was being used by another app during verification. Notification and precise-alarm access were observed enabled.

The working source is `C:\dev\StripSense`. Preserve the existing P01-P12 work and the current uncommitted fixes. Do not commit or push without the developer's instruction. Read AGENTS.md and the relevant phase/spec documents first. The user explicitly expanded scope to repair the core, remove demo data and fix related defects.

## What changed

- Teaching uses explicit capture, frozen review, identity confirmation and durable save. Moving OCR no longer consumes the Save request. Atomic JPEG writes and store acknowledgement prevent false success. Missing embeddings still allow a supporting photograph to be saved. Up to eight views are kept.
- Enrollment refuses visible conflicting brands, ingredients, strengths, release codes and variant words. A full print match is a verified reference. Used/back views are supporting references.
- Separate OCR regions are tracked across frames. Boxes use preview size and screen density. Selecting a recognized region captures that region and reaches the existing answer, reference check and second reader. The former MULTI_STRIP state guard failure is fixed.
- Green due boxes wait for the enabled reference check and use the saved schedule. They do not record a dose. Partial retrieval needs distinguishing print, visual similarity and a margin over competing medicines; it opens a photo comparison only.
- Family can add and edit real medicines on the phone. New stores are empty. Demo navigation and loading are removed. Startup migration removes unchanged untaught legacy samples and synthetic DEMO marks, preserves edited/taught medicines and real history, and resets simulated time.
- Other fixes cover historical import IDs, malformed CSV, comma-number parsing, extra generic rejection, Gemma queued-call busy state, bounded OCR history, camera executor lifecycle, answer/prompt lifecycle and future-dose exclusion in the laptop summary. Damaged Hindi label encoding was repaired.

## Verification completed

- 280 Android JVM tests pass, with zero failures or errors. Twenty additional checks cover local reminder timing, next selection, attempts, snooze, expiry and confirmation persistence. Label checks cover manufacturer/ingredient confusion, spaced and joined product numbers, DT form matching, no strength inferred from brand numbers, dosage exclusion, response validation, atomic enrollment and normalized ingredient names. Earlier coverage includes save/reopen, empty embedding, missing medicine, eight-view cap, failed disk write without publishing a reference, enrollment conflicts, partial ambiguity, separate brands, migration and dose/voice/import rules.
- `:app:assembleDebug` passes offline. No dependency or tool version was changed. Packaged permissions were inspected: no INTERNET.
- Five offline family-page regressions pass: early future mark excluded, exact boundary, after midnight, hostile text inert, existing sample fixture unchanged.
- Debug APK installed on the connected I2501, Android 16, without clearing application data. Startup and navigation work. The initial smoke check used an empty medicine list. The user has since saved three real medicine entries and three photos, preserved across updates. No fake medicine was entered by these checks.
- Gemma file retained at 2,588,147,712 bytes. GPU startup was observed at 2,197 ms on the updated build. A typed "Can I take it with milk?" request completed in 6,088 ms and produced the fixed doctor/chemist refusal. No transcript is logged.
- English voice is installed. Hindi offline voice is missing, so Hindi speech fallback must be expected. Camera and microphone permissions are granted.

Initial UI checks were manual smoke checks. The follow-up added six native instrumentation checks for bitmap ownership, isolated persistence/reopen, OCR and local vision, including two read-only actual strip photos. This does not establish broad camera accuracy. Live multi-strip grouping, photo-crop alignment, glare, damaged strips and second-reader accuracy still need physical trials.

## Main source files

Paths below are under `app/src/main/java/app/stripsense`:

- Teaching and storage: `ui/TeachingCamera.kt`, `ui/TeachingCapture.kt`, `ui/TeachTab.kt`, `data/Store.kt`, `data/FilesIO.kt`, `core/StoreModels.kt`, `core/EnrollmentEvidence.kt`.
- Recognition: `ui/MultiStripScreen.kt`, `ui/SceneBoxes.kt`, `ui/CameraWithReader.kt`, `ml/OcrReader.kt`, `ml/ReferenceFingerprint.kt`, `core/StripGroups.kt`, `core/PartialReferences.kt`, `core/LookCheck.kt`, `core/Decider.kt`, `core/TextNorm.kt`.
- Personal setup: `ui/MedicineEditor.kt`, `ui/ListTab.kt`, `ui/CoreLabels.kt`, `ui/Labels.kt`, `ui/LabelTable.kt`, `ui/TodayScreen.kt`, `ui/AppRoot.kt`, `ui/SettingsTab.kt`, `core/UserDataMigration.kt`, `core/Csv.kt`, `MainActivity.kt`.
- Existing lifecycle repairs: `AppState.kt`, `ui/AnswerFlow.kt`, `ui/AnswerScreen.kt`, `ui/AskController.kt`, `ui/AskScreen.kt`, `ui/AskBubble.kt`, `ui/ModelPicker.kt`, `ui/CheckScreen.kt`, `ui/PhoneChecks.kt`, `ui/StoreWarning.kt`, `ml/Gemma.kt`, `core/Sentences.kt`. `laptop/family.html` fixes the evening summary.

New English/Hindi labels live in `CoreLabels.kt` for personal medicine entry, capture/review, reference counts, conflicts, comparison and scene-check states. `Sentences.referenceCandidate` is the fixed partial-reference sentence. No generative medical answer was added.

## Three-step phone acceptance

1. Use List > Scan a new medicine, or Teach > Read label and update details for an existing row. Capture the name and composition; capture another side if needed. Review the extracted fields and prescribed times, then save. Confirm the reference count and photos survive force-stop and restart.
2. Show two or three separated real strips. Verify that only the scheduled, unmarked verified medicine gets green. Tap it and check the answer belongs to that box. Confirm and undo one real mark with the family's agreement. Check repeated scans do not reopen the mark question or mark automatically.
3. Try a wrong strength, conflicting PLUS/SR variant, covered strength, unrelated foil and similar-looking medicines. Conflicts must refuse. Partial candidates must stay comparisons and never offer a dose question. Test Help, back, background and camera permission revocation/regrant during these paths.

## Next debugging work for a smaller model

Start with actual observed failures and filtered logs: `adb logcat -d -s StripSense:* AndroidRuntime:E`. Do not log photographs, audio or transcripts. Do not clear the store/model or add demo data. Do not run `.finale-build/sync_sources.py` or regenerate staged files from old prepare scripts; they can overwrite newer work.

Record each physical trial's medicine ID, full/partial strip, lighting, number of strips, expected result, actual result and latency. Use the Numbers tool only for explicit trials. Measure false identities and wrong-strength acceptance separately from refusals. Do not loosen the full-print conflict rules to improve a demo score.

Check CameraX preview/analysis geometry on the target phone in portrait and landscape. The current preview uses FIT_CENTER, a compatible TextureView and the same resolution selector as analysis. Boxes surround OCR regions rather than a trained physical-strip detector. Touching/overlapping strips and one OCR line containing two brands can remain ambiguous; ask the user to separate them.

The MobileNet embedding and RGB/layout fingerprint are comparison features, not a medicine-trained recognition model. Partial retrieval uses an initial 0.80 score and 0.08 margin; these are uncalibrated against this family's real strips. Do not present them as measured confidence. Up to four regions receive a feature comparison per 800 ms batch; no overlapping feature job is queued. Threshold calibration or a stronger local matching/detection method needs a real dataset and a separate evaluation.

Second-reader ON/STRICT runs after selection. A green schedule reminder is not the second reader's final answer. Verify its disagreement, timeout and STRICT-not-ready behavior. Test microphone yes/no on a real open question, native cancellation and leaving/reentering Ask while a request is running. Hindi speech needs the offline voice pack before validation.

Test imported renames/removals against historical IDs, actual exports, font scaling, and low-storage/retry behavior. The list editor and teaching wait for durable acknowledgements; existing ordinary settings/dose mutations use the store's queued writer and failure warning. Do not claim disk-full behavior is completely validated on Android from the JVM tests.

The kit contains a historical `sample-week.csv` only as an offline regression fixture. It is not loaded into the app or presented as a real user's week. The current `build-record.json` identifies the APK and test totals. Gradle reports deprecated features; this repair deliberately leaves dependency/tool versions unchanged.
