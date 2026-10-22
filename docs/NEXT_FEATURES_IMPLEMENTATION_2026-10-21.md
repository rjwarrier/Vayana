# Next three feature recommendations — October 21, 2026

Implemented the remaining three features from the [recommendations](D:/AntiGravity/Vayana/docs/FEATURE_OPTIMIZATION_REVIEW_2026-10-21.md). This follows the font/pronunciation backup work from the previous implementation pass. Existing uncommitted changes were preserved.

## Recognized PDFs in library-wide search

- Finalized OCR pages now join EPUB passages in **Search → Book contents**. Results retain the printed page index and open the original scan at that page, using the reader's existing scan CFI form.
- Reuses the existing disposable chapter/passage FTS tables. PDF source identities combine the original file hash, a search-text version and a persistent recognition revision. This is a cache change, not a Room schema migration or new synced record.
- Recognition revisions are published atomically after line joining. Writing new pages invalidates the prior revision; finalization, model changes and explicit cleanup notify app observers. The index updates after finalization and removes stale text when recognized data disappears or the original book changes.
- The integration test exercises a real Room index and the live indexing controller through initial recognition, text deletion, replacement on a different page, and original-file replacement. Separate tests check restart-stable revisions, Unicode, line joining and source availability.

**Scope:** finalized recognized PDF text. Native PDF text extraction remains a separate future extension. Each PDF page is an indexed unit, so a phrase crossing a printed-page boundary is not matched as a single passage. Search results open Scan view; the existing reader control can switch to Text view when a valid generated text book is available.

## Durable offline preparation

Open **Library → Bulk actions**, select books, then **Download selected** or **Prepare offline**. The same preparation dialog also shows ongoing requests when opened without a selection.

- Uses unique per-book WorkManager requests. Queue inputs hold stable book identity/hash and options; workers read current sync configuration at execution time. Credentials are not stored in job data.
- Choose Wi-Fi-only book downloads and optional PDF recognition with a language model. Models retain the existing Wi-Fi download policy. Already-local books can be prepared without a network constraint; if their file subsequently disappears, the worker fails instead of bypassing the requested download constraints.
- Downloads use the existing authenticated/verified cloud-asset transfer. Book identity is checked before work and at transfer boundaries, so a restored/replaced book cannot receive a stale request's asset.
- The dialog shows waiting, downloading, recognizing, ready, failed and cancelled states, with cancel/retry and a link back to the book. Readiness follows the recognition job when OCR was requested, rather than only the completed download.
- Recognition remains its existing unique per-file job and resumes saved pages. The queue handles earlier recognition jobs without the new tags. Active OCR jobs remain visible even after their original preparation history is pruned.
- Queued transfers are limited to one at a time, and one OCR book runs at a time with the existing one/two page engines inside that book. Busy work retries through WorkManager with linear backoff. This avoids multiplying the existing download buffers and native bitmap/model memory across a large selection.
- Preparation result history is retained for at least 30 days. Android controls background execution and retry timing. Android force-stop requires launching the app again; downloads resume at file boundaries, not HTTP byte ranges.

**Scope:** prepares the original book file and optionally its PDF recognition. Cover retrieval continues through the existing download/sync paths. It adds no custom scheduler, server or durable application database table.

## OCR storage management

Open **Settings → Privacy & data**:

- Installed recognition models show actual file sizes. Removal is disabled for models needed by queued/active work; the operation checks WorkManager again and also protects engines that are still shutting down after cancellation.
- Recognized text is listed by book with its disk usage. Individual removal has confirmation; the existing Remove all action uses the same coordinated cleanup.
- Cleanup cancels related preparation/recognition and waits for the worker's last file write before deleting derived data. Original book files, annotations and reading-position records are not deleted.
- Search invalidates its derived passages. The reader observes storage changes, refreshes the scan's OCR layer, and returns from a removed Text view to Scan view using its existing printed-page switch.
- Statistics now separates recognition models and recognized text from Other storage.

The small ownership helper serializes access per book and tracks active model leases. It is shared by workers, explicit cleanup and the reader's lazy finalization of older recognized books.

## Verification

Focused checks passed before the final repository run:

- Real Room/controller PDF indexing and live invalidation.
- Persistent WorkManager request deduplication, controller recreation, cancellation and retry.
- Request identity/options/network constraints and local-book preparation without sync or reading-position changes.
- Recognition-stage readiness and legacy-job state mapping.
- Pending recognition preventing model removal; book-text cleanup preserving the source PDF.
- Worker/cleanup ordering and model-lease release on cancellation.
- OCR-specific storage categorization.

Final verification:

- `:app:assembleDebug` completed and produced fresh arm64, ARMv7, x86_64 and universal debug APKs under `app/build/outputs/apk/debug/`.
- Full `testDebugUnitTest` run: **954 tests, 953 passed, 1 failed, 0 skipped**. The sole failure is the previously recorded `HardcodedTextKonsistTest`: its parser flags unchanged localized Wi-Fi/mobile interpolations in `feature/settings/.../DataUsageScreen.kt:179–180`. No guard was disabled or weakened. The combined build/test command exits unsuccessfully because of this failure, although APK assembly completed.
- All **188 reader JavaScript tests passed**.
- New resource files have matching sets of **22 keys across all nine languages**; Android resource compilation passed. `git diff --check` found no whitespace errors.
- The full run exposed and resolved two integration omissions: the debug OCR receiver now awaits scheduling from a coroutine with `goAsync()` lifetime handling, and the narrow index-observer regression test now expects local PDFs alongside EPUBs. Its full-suite rerun passed.

Evidence logs are in ignored `build/deep-scan-2026-10-21/`: `next-features-final-focused.log`, `next-features-final-full.log` and `next-features-js.log`.

**Limits:** no APK installation or real-device UI run, live authenticated GitHub download, operating-system process-kill experiment, OCR-language accuracy evaluation or device memory benchmark was performed. WorkManager tests use its test implementation and a non-network worker; OCR execution ownership is tested independently from the native Tesseract engine.
