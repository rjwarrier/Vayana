# Debug pass — October 21, 2026

## Review follow-up — early prompts and abandoned downloads

Both requested corrections are implemented:

- Accept and dismiss close the reading-position prompt immediately, before requiring an engine, printed page or page map. Missing position information skips only persistence/navigation. Pending accepted-position navigation is cancelled by a later prompt action, and engine/view changes are still checked after reading the map.
- Opening OCR model storage removes `.part` files older than 24 hours from its `tessdata` directory. Recent partials remain available to concurrent downloads; installed models are untouched.
- Three additional ViewModel tests cover missing printed-page information, a not-yet-loaded page map and an unavailable page map. An OCR storage test covers expired partial removal and preservation of recent partials/installed models. All four regressions failed before the corrections and pass afterward.
- The harmless extra refresh on opening a completed book and the existing private-field test fixture are unchanged.

**Repository-wide validation:** `gradlew.bat testDebugUnitTest --continue --console=plain` completed every debug unit-test task. JUnit XML totals: **935 tests, 934 passed, 1 failed, 0 errors, 0 skipped** across 171 suites. Reader JavaScript: **188 passed, zero failed/skipped**. This is host-side unit testing, not device instrumentation or release-variant testing.

The sole failure is `HardcodedTextKonsistTest.user-facing text comes from string resources` in `:core:designsystem`. It flags `feature/settings/src/main/kotlin/com/vayana/feature/settings/DataUsageScreen.kt:179–180`: Wi-Fi/mobile labels already use `stringResource`, but the guard's template regex stops at the nested lambda's first closing brace. These expressions are unchanged from HEAD (last changed in `3d6e9651`, October 19). The test and those unrelated expressions were left intact; the full suite is therefore **not green**.

Evidence: `build/debug-fixes-2026-10-21/review-followup-red.log`, `full-android-tests.log`, `full-android-tests-complete.log`, and `followup-reader-tests.log`. The initial full run stopped after the guard failed; the second used `--continue` to run the remaining tasks while retaining the failure. No test was skipped, disabled or weakened.

## Fix follow-up

**F1–F5 are fixed on `codex/debug-pass-fixes`.** The original findings and baseline evidence are preserved below; their source line numbers refer to revision `0f5fac54`.

| Finding | Resolution | Regression proof |
| --- | --- | --- |
| F1 | Dismissing a Text-view position prompt converts its displayed locator to the printed scan page before persistence. Immediate remote events also compare positions in the scan domain. | Actual `ReaderViewModel` dismiss test: printed page 40/200 persists as `epubcfi(/6/80)` and `0.2`, rather than the EPUB's `0.72`. |
| F2 | Accepting remote scan progress resolves the generated EPUB chapter/page marker before navigation. The engine, view and prompt are checked again after reading the map. | Actual `ReaderViewModel` accept test using a generated OCR EPUB/page map; a third test preserves Scan-view accept/dismiss behavior. |
| F4 | OCR refresh clears all cached search text. A small state tracker refreshes when the on-disk line-joins marker becomes ready, even with an unchanged page count. | JavaScript split-word cache regression plus two completion-refresh state tests. |
| F3 | Each model installation has its own temporary file in the destination directory and publishes verified bytes with replacement. | Deterministic concurrent same-model installation test; the existing size/hash rejection tests still pass. |
| F5 | Logs exceeding 1 MiB trim to at most 512 KiB, preserving the newest eligible rows and leaving room for later appends. | New byte-bound/newest-entry test plus the existing retention/growth tests. |

All new regressions failed before their corresponding fixes and passed afterward. The ViewModel tests construct the real ViewModel with Robolectric and test dependencies, seed an already-open view, and call its public prompt actions. They do not drive a WebView or live sync repository.

**Initial fix validation:** 710 Android unit tests across the ten modules listed below, plus 188 reader JavaScript tests; zero failures/errors/skips. Gradle completed successfully in **2m 16s**. Up-to-date test results were reused where Gradle considered inputs unchanged. A separate read-only review found no additional actionable issue. The later repository-wide result is recorded above.

The existing sync-log growth test passed in **26.083 seconds**, compared with **393.579 seconds** during the original debug pass. These are local JVM test observations, not Android performance benchmarks. No tests were skipped, disabled or weakened.

Follow-up logs: `build/debug-fixes-2026-10-21/reader-vm-green.log`, `reader-tests.log`, and `gradle-tests.log`. Expected-failure logs: `pdf-red.log`, `ocr-sync-red.log`, `refresh-red.log`, plus `build/debug-pass-2026-10-21/reader-vm-red.log`. Release notes were updated. No APK was installed and no live device/sync behavior was exercised during the fixes.

## Scope

- **Revision:** `0f5fac54833b943a5f4a1a685770bc539f302837`.
- **Change window:** October 16–21, 2026, Asia/Kolkata; 70 commits and 257 changed files. This uses the requested recent-history window, rather than an assumed identity for the previous assistant commit.
- **Depth:** risk-focused source review and executable checks. The changed-file inventory was reviewed; this is not a line-by-line audit of every translation, generated schema, or UI change.
- **Primary paths:** scanned-PDF OCR and model storage; scan/text-view progress and annotations; PDF rendering, range serving and caches; compact sync, Home Library records and migrations 37–41; dictionary installation/lookup; reading state, listening time, statistics and data usage.
- **Secondary paths:** share-card persistence, release/version/ABI configuration and documentation consistency.
- **Original deliverable:** findings and verification report; that pass changed no production implementation or existing tests. The follow-up above implements the fixes. Original diagnostic probes and logs live under ignored `build/debug-pass-2026-10-21/`.

## Original debug-pass outcome

**Five P2 findings were identified.** Three were reproduced with isolated executable probes; two were traced through the production reader and persistence paths. The follow-up above adds ViewModel regression tests for the latter. No P0/P1 issue was established within this pass.

The existing verification is green: **703 Android unit tests plus 187 reader JavaScript tests**, with zero failures or skips. Gradle completed successfully in 12 minutes 30 seconds. The existing sync-log growth test alone took 393.579 seconds and exposed F5's repeated-trimming behavior.

P2 means a functional defect under the stated trigger, suitable for a focused follow-up. Passing existing suites does not cover these missing scenarios.

| ID | Priority | Finding | Evidence |
| --- | --- | --- | --- |
| F1 | P2 | Keeping the current position in OCR text view can save an EPUB location as PDF progress | Source-traced |
| F2 | P2 | Accepting remote PDF progress in OCR text view navigates with a scan CFI in an EPUB | Source-traced |
| F3 | P2 | Concurrent first-use downloads of one OCR model share a temporary file | Reproduced against compiled production code |
| F4 | P2 | OCR refresh retains nonempty search text after line joining changes it | Reproduced using the production JavaScript function |
| F5 | P2 | Sync-usage trimming stays above its trigger and repeats on every append | Reproduced against compiled production code; observed in existing test |

## Findings

### F1 — Normalize the text-view position before keeping it

**Location:** `feature/reader/src/main/kotlin/com/vayana/feature/reader/ReaderViewModel.kt:1778–1785`; compare the regular relocation conversion at lines 537 and 1379–1390.

**Trigger:** Read a recognized PDF in Text view, receive a remote-position conflict, and choose to keep reading here.

**Cause:** `uiState.currentLocator` holds the generated EPUB's CFI and fraction. Normal relocation converts those to the printed PDF page through `toScanLocator()` before persisting. `dismissReadingPositionPrompt()` bypasses that conversion and calls `bookRepository.updateLocator()` with the raw EPUB CFI and fraction. The repository persists them without translating between views.

**Impact:** The shared PDF record can contain text-view progress. Reopening the scan or syncing another device can resume at the wrong printed page. The next normal relocation may overwrite the bad value, but leaving immediately after dismissing the prompt can preserve it.

**Evidence:** The production source explicitly converts normal relocations, while this direct write does not. These handlers predate OCR; the new text view introduced a second locator domain without adapting them. No live sync conflict was injected into the user's library.

**Fix direction:** Apply the same text-to-scan conversion to the keep-current write and its `lastReaderWrittenLocator` bookkeeping.

**Regression case:** Given an EPUB fraction different from `(printedPageIndex + 1) / printedPageCount`, dismiss the prompt and assert that the persisted locator/fraction belong to the PDF. Reopen in Scan view and verify the printed page.

### F2 — Translate incoming PDF progress before navigating the text view

**Location:** `feature/reader/src/main/kotlin/com/vayana/feature/reader/ReaderViewModel.kt:1766–1775`; engine forwarding at `reader/engine-web/src/main/kotlin/com/vayana/reader/web/FoliateBookEngine.kt:379–390`.

**Trigger:** While in Text view, accept a newer position synced from a device reading the scan.

**Cause:** `acceptReadingPositionPrompt()` dispatches the stored scan CFI directly. `FoliateBookEngine.goTo()` forwards that CFI to `goToHref()`; it does not use the supplied progression as a fallback. The active engine is reading the generated EPUB, where spine entries are chapters, rather than the PDF's one entry per printed page.

**Impact:** A scan location such as `epubcfi(/6/200)` for printed page 100 is interpreted as an EPUB spine location. It can point outside the generated chapter list or to the wrong chapter, so accepting the position fails to reach the intended text. The prompt is already cleared.

**Evidence:** View switching already resolves a printed page through `textPageMap()` to `OEBPS/cNNN.xhtml#page-N`. The accept handler has no corresponding translation. This is a source-confirmed domain mismatch; the full device scenario remains unexecuted.

**Fix direction:** Resolve incoming scan progress to the generated EPUB's page marker before navigation, or switch to Scan view before applying it. Compare prompt positions in the same domain as well: the immediate remote-event path currently supplies the displayed text locator.

**Regression case:** Use a recognized book with many more printed pages than chapters. Accept remote progress to a later printed page from Text view and verify that page's marker, then verify the same action in Scan view.

### F3 — Serialize installation of a shared OCR model

**Location:** `format/ocr/src/main/kotlin/com/vayana/format/ocr/OcrModels.kt:71–89,96–122`; book-specific scheduling at `format/ocr/src/main/kotlin/com/vayana/format/ocr/BookRecognition.kt:75,121`.

**Trigger:** Start recognition for two different books using the same not-yet-installed model.

**Cause:** Work is unique per book, so separate books may run concurrently. Model files are shared across books, but every installer writes `<model>.traineddata.part` and deletes that same path in `finally`. There is no model-scoped lock around the installed check, download and publication.

**Impact:** Valid concurrent downloads can interfere and fail installation, causing unnecessary WorkManager retries and repeated downloads. The probe did not establish permanent corruption: the surviving model was valid.

**Reproduction:** `build/debug-pass-2026-10-21/OcrRaceProbe.java` calls the compiled production `installVerified()` method with two valid identical streams. It pauses the first writer after its output file opens and lets the second proceed.

```text
writer 1: success
writer 2: java.io.IOException: Could not install test
target valid: true
REPRODUCED: two valid same-model installs interfere via a shared .part file
```

**Fix direction:** Serialize the complete check/download/install sequence per model and recheck installation after acquiring the lock. Unique temporary names avoid file collisions but still need safe publication coordination.

**Regression case:** Two simultaneous installs of one missing model complete successfully and leave a verified final file without stray partials. Also cover one failed download alongside one successful download. The current deterministic reproduction ran on Windows/JVM, not Android WorkManager.

### F4 — Invalidate cached OCR search text when joins change

**Location:** `reader/engine-web/src/main/assets/foliate/pdf.js:535–538`; compare `book.getPageText` at lines 516–527 and final joining in `format/ocr/src/main/kotlin/com/vayana/format/ocr/BookRecognition.kt:157`.

**Trigger:** Search a recognized page while book recognition is still running, then finish recognition so whole-book processing joins words broken across lines.

**Cause:** `refreshRecognizedText()` clears recognized-page objects but evicts `pageTexts` entries only when they are empty. A nonempty OCR string computed before `saveLineJoins()` survives, even when the underlying recognized page has changed.

**Impact:** In-book search can keep matching pre-join word fragments and miss the corrected whole word until the book is reopened. The refreshed text layer and cached search text can disagree.

**Reproduction:** `build/debug-pass-2026-10-21/ocr-cache-probe.mjs` extracts the actual production refresh function into a Node VM, seeds nonempty pre-join text and an empty page, and invokes refresh. The empty entry is evicted; the old nonempty text remains. A separate in-memory control replacing selective eviction with full invalidation removes it. Production source is unchanged.

```text
REPRODUCED: refresh clears empty search pages but retains nonempty pre-join OCR text
cached text after recognition finalization: kilometre s
CONTROL: invalidating all page text removes the stale result; production source unchanged
```

**Fix direction:** Invalidate OCR-derived page-text entries whenever recognition or joins change. Preserve embedded PDF text by tracking provenance if needed. Ensure completion refreshes join changes even when the recognized-page count stays the same.

**Regression case:** Cache a split word during recognition, publish its joined form, refresh, and assert that `getPageText()` and search use the joined word without reopening.

### F5 — Give sync-usage trimming enough headroom to stop retriggering

**Location:** `core/sync/src/main/kotlin/com/vayana/core/sync/usage/SyncDataUsage.kt:59,70–81`.

**Trigger:** Accumulate more than 1 MiB of recent sync-usage entries within the 120-day retention window.

**Cause:** Every append checks `file.length() > MaxFileBytes` (1 MiB), but trimming only removes old entries and limits the count to 20,000. That count can still occupy well over 1 MiB. A successful trim therefore leaves the next append above the trigger, repeatedly parsing and rewriting the full log under the same lock.

**Impact:** Growing histories incur CPU, allocation and disk-write overhead on every sync instead of occasional maintenance. In the existing growth test, thousands of post-threshold appends make the work approximately quadratic until the entry cap is reached. This does not establish a main-thread freeze: the reviewed sync callers use an IO dispatcher.

**Reproduction:** `build/debug-pass-2026-10-21/UsageTrimProbe.java` seeds 15,000 recent valid rows plus one expired row and calls the compiled production logger twice. The expired row is removed, proving trimming ran, but the resulting file remains over the trigger:

```text
old row removed by trim: true
bytes after trim: 1275085; trigger: 1048576
first/second record ms (local JVM only): 604/212
REPRODUCED: successful trim leaves the file over its trigger, causing every subsequent append to trim again
```

The existing `SyncDataUsageLogTest.aGrowingFileKeepsOnlyRecentSyncs` passed in **393.579 seconds**. A thread dump during the run placed the test inside `record → trimLocked → toEntryOrNull → JSONObject`. Timings are local JVM observations, not Android benchmarks.

**Fix direction:** Either trim below a byte target with headroom, or use an amortized schedule/high-water mark that preserves the intended count-and-age retention policy. Avoid parsing and rewriting an unchanged retained history after every append.

**Regression case:** Seed an over-threshold recent history, append repeatedly, and verify maintenance does not run on every append while count/age/size guarantees remain satisfied. Prefer an observable maintenance count or IO count over a brittle wall-clock assertion.

## Verification

### Existing suites

**Gradle: BUILD SUCCESSFUL**, 12m 30s; 334 tasks (46 executed, 7 from cache, 281 up-to-date). The ten requested module suites contain **703 tests**, zero failures/errors/skips. Three test tasks (`:format:ocr`, `:reader:engine-web`, `:core:database`; 73 tests) reused up-to-date results; the remaining seven test tasks executed. Test counts below come from the final JUnit XML reports.

| Module | Tests in result XML | Failures/errors |
| --- | ---: | ---: |
| `:format:ocr` | 15 | 0 |
| `:reader:engine-web` | 20 | 0 |
| `:core:backup` | 48 | 0 |
| `:core:database` | 38 | 0 |
| `:core:sync` | 93 | 0 |
| `:dictionary:stardict` | 15 | 0 |
| `:feature:reader` | 151 | 0 |
| `:feature:library` | 268 | 0 |
| `:feature:settings` | 27 | 0 |
| `:feature:statistics` | 28 | 0 |

- Reader JavaScript suite: **187 passed**, zero failed/skipped.
- OCR confidence mutation check: inverted `< 50` to `>= 50` in an in-memory source string read by a temporary copy of the existing PDF tests. **21 passed, 1 failed**, specifically the OCR text-item/confidence test. This expected failure demonstrates that the assertion detects the inverted behavior; it is not a baseline suite failure.
- F3, F4 and F5 diagnostic probes reproduced the issues described above.
- A connected Android device and running Vayana process were detected. A read-only crash-buffer query for that process returned no entries. This does not establish crash freedom or current-device build equivalence.

### Commands and evidence

```powershell
node --test reader/engine-web/tests/*.test.mjs

.\gradlew.bat :format:ocr:testDebugUnitTest :reader:engine-web:testDebugUnitTest :core:backup:testDebugUnitTest :core:database:testDebugUnitTest :core:sync:testDebugUnitTest :dictionary:stardict:testDebugUnitTest :feature:reader:testDebugUnitTest :feature:library:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:statistics:testDebugUnitTest --console=plain

node build/debug-pass-2026-10-21/ocr-cache-probe.mjs
node --test build/debug-pass-2026-10-21/pdf-mutation.test.mjs
```

Raw evidence under `build/debug-pass-2026-10-21/`: `reader-tests.log`, `gradle-tests.log`, `ocr-race-probe.log`, `ocr-cache-probe.log`, `usage-trim-probe.log`, `library-test-threads.log` (despite its provisional name, this captured the core sync test worker), and `pdf-mutation.log`. The Java probes use compiled debug classes, the cached Kotlin standard library and, for the logger, JSON and Android API jars. These local build artifacts are ignored by Git and are not a permanent regression suite.

## Coverage limits and follow-up

- During the original debug pass, no APK was installed, app data altered, real sync repository written, or production fix applied. The subsequent fixes are documented above. The pre-existing untracked `vayana-eink-promo.png` was left alone throughout.
- No full APK assembly, Android lint, instrumentation run, browser/device UI pass, E-Ink performance measurement, or fresh verification of all OCR languages was performed.
- Concurrent OCR deletion/cancellation and worker interruption need device coverage: cancellation is requested without awaiting worker quiescence before deleting output. This is a follow-up risk, not an additional reproduced finding.
- Share-card export, crop gestures, visual layout, multilingual UI and accessibility were not exercised interactively.
- Existing migration, sync, dictionary and statistics tests provide regression evidence for the executed cases; they do not prove multi-device behavior against a live GitHub repository.
- General guides and earlier decision text lag the new OCR text view, ABI packaging and language support. Update those after the behavior fixes settle; the current source is the implementation reference.

The follow-up completed the requested order: the two progress-boundary paths together; OCR text-cache invalidation/completion refresh; isolated model-install temporary files; sync-log trim headroom.
