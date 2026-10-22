# Feature and optimization review — October 21, 2026

**Recommendation:** finish the scanned-book workflow, improve portability of reader customizations, and reduce memory pressure around downloads and OCR. Vayana already has a broad feature set; these extensions make existing capabilities more useful and dependable.

This is a report, not an implementation. Existing source changes from the debug passes were preserved.

**Implementation update:** the subsequent value-for-effort pass implements Optimization 1 (dictionary short-circuiting), Feature 2 (font/pronunciation backup coverage), and the empty-OCR correctness prerequisite. The assessment and measurements below describe the scan before those changes. See [implementation notes](D:/AntiGravity/Vayana/docs/VALUE_IMPLEMENTATION_2026-10-21.md) for scope and verification.

**Next implementation pass:** Features 1, 3 and 4 are now implemented: recognized-PDF library search, durable offline preparation, and OCR storage controls. See [the next-feature implementation notes](D:/AntiGravity/Vayana/docs/NEXT_FEATURES_IMPLEMENTATION_2026-10-21.md) for behavior, verification and limits. The original assessment below is retained as the recommendation record.

## Scope and confidence

- Reviewed the current working tree on `codex/debug-pass-fixes`, based on `0f5fac54833b943a5f4a1a685770bc539f302837`, including the uncommitted reader/OCR fixes.
- Inventoried **441 source/build files** across app, baseline profiles, core, dictionary, feature, format, reader and Wear modules. Used targeted cross-module reads of the reader, OCR finalization, search, dictionary lookup, cloud assets, backup, storage, library actions, statistics and release configuration. This is not a claim that every line of those files was reviewed.
- Compared suggestions against existing functionality and [recorded performance work](D:/AntiGravity/Vayana/docs/PERFORMANCE.md).
- Ran a synthetic JVM probe against compiled production OCR storage/reflow code, three trials at each of two book sizes. Inspected an existing APK's contents. These measurements are detailed below.
- Checked current official Android guidance for release optimization, benchmarking and persistent background work.

**Confidence labels:** “confirmed” means the behavior or work is visible in source or a controlled probe; “hypothesis” means the likely device benefit still needs measurement; “product hypothesis” means reader demand has not been validated. No phone/E-Ink performance measurements, live cloud downloads, new APK installation, OCR accuracy comparison or user research were performed for this report.

## Ranked shortlist

Feature and optimization ranks are separate. Scope is relative implementation effort, not a delivery estimate.

| Rank | Feature | Reader benefit | Scope | Evidence |
| --- | --- | --- | --- | --- |
| 1 | Search recognized PDF contents across the library | Find a passage in a scanned book without opening books individually | Medium–large | Confirmed indexing gap; demand is a product hypothesis |
| 2 | Restore custom fonts and pronunciation corrections with backups | A restored library feels like the previous installation | Medium | Confirmed backup coverage gap |
| 3 | Durable “prepare these books offline” queue | Leave a shelf downloading/recognizing, return when it is ready | Medium–large | Existing UI-bound download action and separate OCR work |
| 4 | Manage OCR models and per-book derived data | Reclaim storage without removing books or notes | Medium | Existing storage/cleanup tools cover only parts of this workflow |

| Rank | Optimization | Why it is worth considering | Scope | Confidence |
| --- | --- | --- | --- | --- |
| 1 | Stop dictionary searches after enough successful extra languages | Avoids provably unnecessary dictionary lookups | Small | Confirmed extra work; latency gain unmeasured |
| 2 | Build a release performance baseline, including scanned PDFs | Makes the larger optimizations reviewable and catches regressions | Medium | Confirmed journey coverage gap |
| 3 | Enable release code/resource optimization in a controlled comparison | Release shrinking is currently disabled | Medium | Configuration confirmed; size/startup benefit unmeasured |
| 4 | Stage large cloud downloads through files | Avoids keeping encrypted and decrypted book arrays together in application code | Medium–large | Allocation shape confirmed; Android provider behavior needs profiling |
| 5 | Finalize OCR with bounded page residency | Avoids reconstructing the entire book's word geometry for successive stages | Large | Source and synthetic probe support investigation |
| 6 | Group statistics by month once | Removes repeated scans when constructing long-range charts | Small initially | Algorithmic work confirmed; user-visible cost unmeasured |

**My first small change would be dictionary short-circuiting. My first feature would be backup completeness, followed by library-wide recognized-PDF search.** Establish the release/device measurement baseline before undertaking the two larger memory changes.

## Feature 1 — Library-wide search of recognized PDFs

**Current gap.** [EpubContentIndex.kt](D:/AntiGravity/Vayana/feature/search/src/main/kotlin/com/vayana/feature/search/EpubContentIndex.kt:45) observes local EPUBs. [EpubPassageDao.kt](D:/AntiGravity/Vayana/core/database/src/main/kotlin/com/vayana/core/database/dao/EpubPassageDao.kt:51) restricts both indexing candidates and content hits to `format = 'EPUB'`. Recognizing a PDF creates useful text, but does not make that PDF part of the existing global content index. Searching personal annotations is a separate, already-supported capability.

**Smallest useful version:** index finalized recognized PDF text through the existing local FTS pipeline, show a page-aware excerpt, and open the matching printed page. Offer Text-view navigation only when a valid generated text book exists. Native PDF text can follow after the recognized-PDF path is reliable.

Reuse the disposable passage index and search UI. Add a source identity that includes the original PDF hash **and a recognized-text revision**. The original file hash alone is insufficient: recognition, line joining or regeneration can change the searchable text without changing the PDF. Derived-data deletion must invalidate its index.

**Acceptance:** search finds the same passage after restart; excerpts preserve Unicode; result navigation reaches the correct scan page; re-recognition replaces stale hits; deleting recognized text removes hits; empty recognition creates no content entries. Cover scan/Text-view locator conversion explicitly, because this area recently damaged reading positions.

**Why rank it first:** it connects two substantial capabilities Vayana already has—OCR and global search—without adding a new service, account, or semantic-search system. Reader demand remains to be validated; this ranking is based on capability fit.

## Feature 2 — Complete backups for reader customizations

**Confirmed gap.** [BackupManager.kt](D:/AntiGravity/Vayana/feature/settings/src/main/kotlin/com/vayana/feature/settings/backup/BackupManager.kt:74) writes the database, exported settings, books and covers. It does not write `fonts/`. Imported font bytes are saved there by [SettingsViewModel.kt](D:/AntiGravity/Vayana/feature/settings/src/main/kotlin/com/vayana/feature/settings/SettingsViewModel.kt:329). The imported-font list is also stored under a separate DataStore key rather than a persisted registry declaration; [exportToMap()](D:/AntiGravity/Vayana/core/datastore/src/main/kotlin/com/vayana/core/datastore/settings/DataStoreSettingsRepository.kt:171) exports registry entries. Copying font bytes alone would therefore be insufficient.

Per-book pronunciation corrections use the separate `reader_pronunciations` SharedPreferences in [SpeechPronunciation.kt](D:/AntiGravity/Vayana/feature/reader/src/main/kotlin/com/vayana/feature/reader/SpeechPronunciation.kt:160); the backup path does not include that store either. This is an app-created backup coverage finding, not a test of Android's platform backup service.

**Smallest useful version:** extend the versioned archive to include imported-font files, their metadata and book pronunciation corrections. Keep older archives readable. Extend the existing inspection screen to explain which customization categories are included. Reuse the current restore validation/rollback mechanism.

**Acceptance:** restore into a clean installation; the selected custom font resolves to a real file; pronunciation replacements remain attached to the intended book; missing optional entries in older backups use defaults; invalid asset paths cannot escape staging. Verify restoration of both metadata and bytes.

Do not bundle redownloadable dictionaries or OCR models by default. Share-card identity/seal data is deliberately documented as device-local in [ShareProfile.kt](D:/AntiGravity/Vayana/core/designsystem/src/main/kotlin/com/vayana/core/designsystem/sharecard/ShareProfile.kt:27); any inclusion should be an explicit backup choice, not an incidental consequence of copying every preference file.

## Feature 3 — Durable offline preparation

**Current opportunity.** Bulk download already exists. [LibraryViewModel.kt](D:/AntiGravity/Vayana/feature/library/src/main/kotlin/com/vayana/feature/library/LibraryViewModel.kt:781) processes selected books sequentially, while [BulkBooksDialog](D:/AntiGravity/Vayana/feature/library/src/main/kotlin/com/vayana/feature/library/LibraryProductivityTools.kt:143) launches the action from a remembered composition coroutine scope. The dialog blocks ordinary dismissal while busy, but this path is not itself a durable download queue. OCR already has persistent per-book work and saved-page checkpoints.

**Smallest useful version:** “Prepare selected books offline” with persistent per-book states: waiting, downloading, recognizing if requested, ready, failed. Provide a compact progress surface with cancel/retry and a link back to the book. Let the reader leave the screen. Optional Wi-Fi/charging constraints belong to the job request, not a new general automation system.

Reuse existing worker identities and OCR checkpoints. Initially resume at **book/page boundaries**; resumable HTTP ranges, multiple parallel transfers and a custom job scheduler are unnecessary for the first version. Recognition should remain an explicit choice because of its CPU/battery cost.

**Acceptance:** leaving the library, ordinary process death, network loss and restart preserve completed items and safely retry incomplete ones; duplicate requests do not duplicate work; cancel cannot attach a partial file; storage exhaustion produces an actionable per-book failure. Explicit Android force-stop still requires the user to launch the app again; do not promise uninterrupted execution.

Long-running work needs platform-specific scheduling decisions. Android documents that even foreground WorkManager jobs can exhaust job quotas on Android 16; a visible queue does not justify marking every task foreground or expedited. [Official long-running worker guidance](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

## Feature 4 — OCR storage controls

**Current opportunity.** The storage breakdown distinguishes books, covers, fonts and dictionaries, but its [category rules](D:/AntiGravity/Vayana/feature/statistics/src/main/kotlin/com/vayana/feature/statistics/StatisticsViewModel.kt:609) do not explicitly categorize `ocr` and `ocr-models`; unmatched data becomes Other. Privacy already shows recognized-text usage and can clear it globally. Model installation status also exists. This proposal connects and extends those tools.

**Smallest useful version:** expose installed OCR model sizes with removal for unused models; show recognized-text size by book; allow clearing one book's derived OCR/text artifacts. Explain what must be regenerated. Keep original book files, highlights and reading history intact.

**Acceptance:** active recognition cannot lose its model mid-job; clearing derived text invalidates the reader/search caches and generated Text view; regenerating does not create duplicate annotations or alter saved scan position. Use the same ownership rules as recognition rather than introducing independent file deletion code.

PDF page-image caching already has a size bound, and the prior fixes already clean aged OCR download partials. Neither should be presented as missing functionality.

## Optimization 1 — Short-circuit extra dictionary lookups

**Confirmed unnecessary work.** [OfflineDictionaryRepository.kt](D:/AntiGravity/Vayana/dictionary/stardict/src/main/kotlin/com/vayana/dictionary/stardict/OfflineDictionaryRepository.kt:113) eagerly runs `mapNotNull { lookIn(...) }` over every eligible installed translation dictionary, then deduplicates languages and takes `MaxExtraLanguages = 2`.

With ten eligible packs, this pipeline attempts all ten lookups even when the first two successful distinct languages already fill the display. That is a source-level work count, not a measured real installation or latency result. Two installed candidates yield little benefit; many missing hits still require scanning all candidates.

**Change:** make just this chain lazy, preserving preference order, language filtering, distinct-language handling and the fallback when the primary definition is absent. No cache, new interface or dependency is needed.

**Proof:** use counting dictionary doubles and compare returned entries with the current algorithm. Early successful distinct languages should stop further lookups; missing results and duplicate languages should continue as needed. Include the no-primary fallback. Measure actual tap-to-definition latency only if installed-pack counts make it material.

## Optimization 2 — Release performance baseline and PDF journeys

Vayana already ships a baseline profile and ProfileInstaller. The [profile generator](D:/AntiGravity/Vayana/baselineprofile/src/main/kotlin/com/vayana/baselineprofile/BaselineProfileGenerator.kt:29) launches the app, imports a sample EPUB, turns pages and visits Notes/Stats/Books. It does not exercise scanned PDF opening, OCR text refresh or switching views.

**Change:** extend the existing test infrastructure with release-like Macrobenchmark scenarios and representative fixtures. Keep profile generation and measured regression benchmarks distinct. Macrobenchmark supports whole-app startup/UI journeys and trace output; it is the appropriate official tool for this level of measurement. [Android Macrobenchmark guidance](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).

Start with:

| Journey | Measurement | Required distinction |
| --- | --- | --- |
| Launch into a populated library | Startup timing, first usable content, trace | Cold/warm; small/large fixture |
| Open scanned PDF and turn pages | First visible page, turn latency distribution, peak process memory | Cold render versus cached page |
| Switch scan ↔ recognized Text view | Ready-to-read time and position correctness | Map not loaded versus loaded |
| Finalize recognition of a large book | Stage duration, Java/native memory, responsiveness | Recognition engine versus joins/reflow |
| Search large local content index | Query-to-results distribution | Warm/cold index and hit/miss queries |

Use a normal phone and a representative low-memory Android E-Ink device before setting numerical budgets. Record p50/p95 where repetitions support them, and separate Java heap from total process/native memory. Inspect startup traces before moving app observers or background jobs; source alone does not establish a launch bottleneck.

Regenerate the profile after establishing the added journeys. ART profiles do not directly optimize the reader's JavaScript or Tesseract native execution, so do not attribute all PDF/OCR improvements to them. Run fixture installation/profile generation in an emulator or dedicated test installation; the current workflow can replace app data.

## Optimization 3 — Release R8 and resource shrinking

**Confirmed configuration.** [app/build.gradle.kts](D:/AntiGravity/Vayana/app/build.gradle.kts:76) sets both `isMinifyEnabled` and `isShrinkResources` to false for release. The app already produces ABI-specific APKs; proposing ABI splits again would duplicate existing work.

**Change:** enable code/resource optimization on a reviewable branch and compare release artifacts from the same commit, ABI and configuration. R8 removes unused code/resources and performs code optimizations; current Android guidance recommends optimization for release builds. [Official R8 guidance](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).

Do not promise a percentage reduction. Much of the inspected universal APK is native code, and R8 does not shrink those native libraries or arbitrary reader assets.

**Validation:** exercise WebView JavaScript bridges, OCR JNI, Room/Hilt/serialization paths, worker creation, notifications, sync, and backup/restore in the optimized release variant. Retain mapping files for crash interpretation. Add narrow keep rules only where necessary; a blanket keep-everything rule defeats the experiment. Compare startup and reader timings using Optimization 2.

### Existing APK inventory

The inspected `app-universal-debug.apk`, timestamp October 21 at 08:20, is **92,829,853 bytes / 88.5 MiB**. Its compressed ZIP entry totals were:

| Category | Stored bytes in ZIP | Approximate MiB |
| --- | --- | --- |
| Native libraries | 53,454,144 | 51.0 |
| DEX | 28,549,695 | 27.2 |
| Assets | 4,590,048 | 4.4 |
| Resources | 4,081,633 | 3.9 |
| Other entries | 50,091 | <0.1 |

ZIP overhead accounts for the remaining bytes. The existing arm64 debug APK is **58,860,605 bytes / 56.1 MiB**. These are package inventories, not installed-memory measurements or release benchmarks. The files predate the current uncommitted fixes. An older October 19 release APK is not a valid A/B comparator and was not used to estimate savings.

## Optimization 4 — File-backed cloud download staging

**Confirmed allocation shape.** [CloudBookAssetTransfer.kt](D:/AntiGravity/Vayana/core/sync/src/main/kotlin/com/vayana/core/sync/asset/CloudBookAssetTransfer.kt:83) obtains the complete encrypted asset as a byte array, decrypts it to another array, then passes that array to [CloudAssetStager.kt](D:/AntiGravity/Vayana/core/sync/src/main/kotlin/com/vayana/core/sync/asset/CloudAssetStager.kt:19). The configured maximum book asset is 60 MiB. The encrypted envelope and plaintext coexist at the decryption boundary, before accounting for networking and crypto overhead. Uploads already have streaming code; optimize the download path specifically.

**Change:** download into a unique encrypted temporary file, decrypt into a staged output file, validate authentication, expected hash and length, then publish/attach the verified book. Preserve the existing trust boundary: an unauthenticated or partially written book must never become readable or attached to the database.

**Important implementation limit:** a streaming wrapper does not by itself prove constant memory. Android AES-GCM providers may buffer until authentication completes. Profile the actual provider before promising bounded heap. Preserve the current wire format first; propose a compatible chunked format only if measurements show file staging cannot meet the memory target.

**Acceptance:** use small and near-limit assets; record peak Java heap and process memory; test bad authentication tag, wrong hash, truncated file, cancellation and storage exhaustion. Compare bytes and database attachment with the current implementation. All failures must remove partial outputs. No speedup is assumed—file staging may trade additional I/O for lower memory pressure.

## Optimization 5 — OCR finalization with bounded page residency

**Confirmed work.** [OcrStore.saveLineJoins()](D:/AntiGravity/Vayana/format/ocr/src/main/kotlin/com/vayana/format/ocr/OcrStore.kt:30) reconstructs every saved page into a map. [buildTextEpub()](D:/AntiGravity/Vayana/format/ocr/src/main/kotlin/com/vayana/format/ocr/OcrStore.kt:52) reconstructs the book again. [TextEpub.kt](D:/AntiGravity/Vayana/format/ocr/src/main/kotlin/com/vayana/format/ocr/TextEpub.kt:36) additionally builds word-height collections, header statistics and reflow text. Whole-book statistics are legitimate; retaining every word's bounding geometry through each stage is the opportunity.

**Candidate change:** use page-at-a-time passes over the existing saved JSON. Collect only whole-book summaries required for joining/reflow, then stream page transformations/chapter output. Some summaries still scale with vocabulary or headers; “bounded pages resident” does not mean every data structure becomes constant-size. Avoid adding another persistent database solely for this optimization.

Do not collapse this to a superficial `Sequence` conversion: the current algorithms explicitly consume maps and aggregate lists. Preserve joining rules, running-header detection, paragraph boundaries, chapter anchors and printed-page mapping.

### Executed synthetic probe

The probe called the compiled production `saveLineJoins()` and `buildTextEpub()` methods after saving synthetic pages. Each page had 25 lines × 12 words, with coordinates and a repeating 20,000-word vocabulary. Three fresh JVM processes per size used Java 17 with a 256 MiB heap. Seeding was outside timing; a requested GC preceded measurement; Java heap usage was sampled every 2 ms. Each run verified that an EPUB existed and the page map had the expected length.

| Workload | Median joins | Median EPUB build | Median sum of the two stages | Sampled peak Java heap range |
| --- | --- | --- | --- | --- |
| 200 pages / 60,000 words | 890.7 ms | 767.8 ms | 1,672.9 ms | 83.3–84.8 MiB |
| 1,000 pages / 300,000 words | 2,588.7 ms | 2,341.3 ms | 4,930.0 ms | 107.5–108.1 MiB |

The median stage sum is calculated per trial; it need not equal the sum of separate stage medians. Baseline sampled heap after the requested GC was about 1.1 MiB. These peaks include transient allocations and depend on GC; they are **not retained-object sizes**, exact peaks or Android RAM predictions. The probe excludes PDF rendering, Tesseract, native memory and realistic Malayalam text. It demonstrates substantial allocation during finalization in this fixture, not a device out-of-memory failure. No optimized candidate was tested, so there is no measured improvement claim.

Evidence is local and ignored by Git: [probe source](D:/AntiGravity/Vayana/build/deep-scan-2026-10-21/OcrFinalizeProbe.java), [raw six-run log](D:/AntiGravity/Vayana/build/deep-scan-2026-10-21/ocr-finalize.log). The probe was run against existing compiled classes; it does not establish a freshly built APK's provenance.

**Acceptance before landing a rewrite:** compare generated text, joins and page anchors on representative Latin/Malayalam fixtures; include blank pages and sparse text; measure device Java/native memory and finalization duration before/after. Stop if the device profile does not show a meaningful problem, or if the complexity is disproportionate to the benefit.

## Optimization 6 — Construct monthly statistics once

[PeriodStats.kt](D:/AntiGravity/Vayana/feature/statistics/src/main/kotlin/com/vayana/feature/statistics/PeriodStats.kt:89) builds each month by filtering `secondsByDate.entries` again. With M months and D recorded dates this part performs roughly M × D entry examinations.

**Small change:** group/sum by month once, then look up each displayed month. Preserve empty buckets and the current local-date/time-zone semantics. Test equivalent results across year boundaries, empty months and date boundaries. This removes repeated work without changing storage.

**Priority is conditional.** Statistics already runs aggregation off the main thread, shares flows and caches independent summary domains. Do not add another cache, rewrite all statistics as SQL or paginate the library without evidence. Replay a representative long history first; proceed beyond the simple grouping only if end-to-end chart latency justifies it.

## Existing capabilities deliberately excluded from the suggestions

These already exist and should not appear on a new-feature roadmap as missing:

- Smart shelves, bulk actions/tags/downloads, finish-by plans, journal/reflections, Today, Paused/DNF and reasons.
- Scoped/phrase content search for EPUBs, notebook export and stable deep links, vocabulary/highlight review.
- Read-aloud voices/rate/pitch/sleep timer/pronunciation corrections; offline dictionary installation and ordering.
- Backup inspection/rollback/retention; optional encrypted GitHub synchronization; Wear physical-reading sessions.
- PDF page-image caching/prefetch, shared reader layout/annotation work, idle-aware pagination, incremental notebook generation and lazy search categories.
- ABI APK splits, on-demand OCR model downloads and a shipped baseline profile.

I would defer a new AI chat/embedding subsystem, a custom cloud backend, a new job-orchestration framework and a reader-engine rewrite. Nothing measured in this scan requires those investments.

## Prerequisites and recommended sequence

**Finish the known correctness work first.** The previous debug pass reproduced the empty-recognition case that still publishes an empty EPUB and offers Text view. Gate publication/availability on readable content, including already-created empty artifacts, before expanding OCR search. Details and reproduction are in [the second debug report](D:/AntiGravity/Vayana/docs/DEBUG_PASS_2_2026-10-21.md).

The preceding full-suite baseline was **934 passed / 1 failed** out of 935 Android debug unit tests, plus **188 passing reader JavaScript tests** and 11 passing diagnostic ViewModel cases. The remaining failure is the localization guard's handling of unchanged Data Usage string expressions. These are results from the preceding debug pass, **not fresh full-suite runs for this report**. Fix the guard's parsing while retaining its purpose; do not silence it to obtain a green baseline.

Also reconcile [PRODUCT_SPEC.md](D:/AntiGravity/Vayana/docs/PRODUCT_SPEC.md:5), which still forbids network permission and cloud sync despite the shipped optional network features. Record present behavior and the intended offline/privacy boundaries so future work does not treat an obsolete specification as current product policy.

1. **Small, reviewable improvements:** empty-OCR availability fix; localization guard repair; dictionary lookup short-circuit; backup asset/metadata coverage. Stop once each behavior has focused regression proof.
2. **Performance foundation:** release-like benchmark fixtures, then same-commit release optimization comparison. Profile near-limit cloud downloads and large OCR finalization on representative devices. Only proceed with memory rewrites that have useful measured gains.
3. **Next product slice:** recognized-PDF global search, followed by durable offline preparation and OCR storage controls if readers need them. Ship each independently with explicit navigation, cancellation and derived-data invalidation checks.

No production code, dependencies or build configuration were changed for this report. The only deliverable outside ignored diagnostic files is this Markdown document.
