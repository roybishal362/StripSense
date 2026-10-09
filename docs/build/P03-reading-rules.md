# P03. The reading rules (pure Kotlin, with tests)

**Goal:** the parser, the decider and the scan session of spec 01, proven by unit tests. No Android code in this stage.

**Read:** `docs/spec/01-reading-and-matching.md` (all of it). Nothing else is needed.

## Build
1. `core/Models.kt` (it already holds `OcrLine` from P02; add to it): every type of spec 01 section 1 (`StrengthUnit`, `Strength`, `MedType`, `MedStatus`, `Medicine`, `OcrLine`, `ReadKey`, `Verdict`). `Medicine`, `Strength` and the enums are `@Serializable` (kotlinx.serialization); `ReadKey`, `OcrLine` and `Verdict` are not. No Android imports.
2. `core/Tuning.kt`: the `@Serializable data class Tuning` with exactly the names and defaults listed at the top of spec 01 section 2.
3. `core/StripParser.kt`: `parse(lines, meds, tuning): ReadKey`, following spec 01 section 2 step by step, in the order written there. Small private functions, one per step.
4. `core/Strengths.kt`: `sameStrengths`, `conflict`, and `display(strengths): String` ("500 mg + 250 IU").
5. `core/Decider.kt`: `decide(key, meds, tuning = Tuning()): Verdict`, rules D0 to D12 in order, each with the rule number in a comment.
6. `core/ScanSession.kt`: the class, the `feed` function and the result types exactly as written at the top of spec 01 section 5.
7. **Tests** in `app/src/test/java/app/stripsense/core/`:
   - `ParserTest`: the tokens for vectors V1, V7, V9, V12, V17, V19, V20 (assert brands, generics, strengths, brand numbers, release, extra words).
   - `DeciderTest`: **every** vector V1 to V20, V16b included. One test per vector, named with its number.
   - `RealStripTest`: any rows V21 and up that are in spec 01 section 7. They use the list in `docs/sample/list.csv`, typed into the test as a second fixture. If there are no such rows yet, skip this file.
   - `ScanSessionTest`: S1 to S7, plus one test for each hint.
   - `StrengthsTest`: `sameStrengths` (0.5 g equals 500 mg; 2.5 mg does not equal 25 mg), `conflict`, `display`.
   A small helper builds `OcrLine`s from strings (confidence 0.9, height 30, tops 0, 40, 80, ...). The list M1 to M5 is one shared fixture; V16 alone adds M6. Tests use JUnit 4 (`org.junit.Test`, `org.junit.Assert`); `kotlin.test` is not a dependency.

## Do not
- No camera, no UI, no speech.
- Do not weaken a rule to make a test pass. If a vector and a rule disagree, stop and report which one.

## Done when
- `gradlew.bat :app:testDebugUnitTest` passes, and the report lists at least 30 tests.
- `core/` has no `android.` or `androidx.` import (check with a search).

## Report
The test count, and any vector that needed a judgement call.
