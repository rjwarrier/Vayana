# Optimization plan — 21 September 2026

Status: O1–O6 implemented with focused regression coverage; O7's allocation and concurrent-read changes implemented. JavaScript tests pass (22/22), and `:app:assembleDebug testDebugUnitTest lint` passed after the first implementation pass. Device timing, memory, energy, and E-Ink validation remain open.

Reviewed branch: `feat/book-details-share-cards-finish-prompt`, HEAD `1fb48535eaa51fde3d878cccd40590683ec38528`.
Scope: the 14 commits from `edb1f2b` through `1fb4853` in the preceding 72 hours, plus the existing code paths they exercise. The cumulative change touches 140 files. This document began as the investigation record; implementation was added in the subsequent working-tree pass.

The highest-value work is reducing repeated reader work and making highlight review load only the data it displays. Notes sorting is a smaller, inexpensive improvement. Speech cancellation needs focused regression tests before further concurrency changes. Actual device latency, memory, and energy improvements remain to be measured.

### Implementation record

| ID | Implemented change | Verification / remaining limit |
| --- | --- | --- |
| O1 | Notes partitions annotations and caches parsed community counts by input revision. | Ordering remains stable; no device recomposition trace yet. |
| O2 | Section-indexed badge candidates, cached counts, grouped geometry, and document/revision matching guard. | JavaScript stress test: 10,000 annotations, 100 current-section badges; a warm layout performs zero annotation scans, count parses, or CFI resolutions, and one frame geometry read. |
| O3 | Cached section prefix estimates and contents map; relocation sends the map only on revision changes. | JavaScript warm-turn test visits zero contents entries after the first map build; real WebView repagination and second-book checks remain device work. |
| O4 | Bounded due/count/practice Room queries; review screen and Statistics due preview use them. | Repository and module tests pass; general Statistics totals still observe all annotations. No 50,000-row query-plan trace yet. |
| O5 | Playback and queue generations, cancellable speech loads, one pending chapter transition, and bridge-request `finally` cleanup. | Deferred race tests and existing reader tests pass; lock-screen and screen-off device checks remain. |
| O6 | Background annotation mapping and serialization; WebView applies one update at a time and retains the newest waiting snapshot. Quote additions now finish before a newer snapshot is diffed, and text-match misses are cleared on annotation changes. | JavaScript rapid-snapshot, late-quote-deletion, and edited-quote retry tests confirm final overlay; renderer recovery needs device validation. |
| O7 | Slice uploads encode one at a time; scoped concurrent slice reads share one load, including oversized slices, and recover after cancellation. | Sync tests cover those concurrent paths; allocation and network reductions are source-level expectations, not measured results. Progress now reports document completion when exact total bytes are unknown. |

The synthetic tests measure repeated operations, not elapsed UI time. No claim about frame rate, battery, or peak memory is supported until before/after traces use the same device and fixture. The app's existing debug build can be used for the device matrix below without reading or uploading a user's library as a benchmark fixture.

The connected device during implementation was a Pixel 9 Pro XL with an existing `com.vayana.app` installation. It was not reinstalled or used as a performance fixture, avoiding changes to its current library. The initial full gate exposed three `RestrictedApi` lint errors on the existing `MainActivity.kt` dispatch-key override; a method-level suppression documents that intentional public Activity override, and the complete gate then passed.

## 1. Evidence and priorities

| ID | Priority | Opportunity | Evidence | Estimated implementation effort |
| --- | --- | --- | --- | --- |
| O1 | P2, quick win | Compute community-quote sort values once per annotation revision | Confirmed repeated parsing inside the comparator; sorting occurs in composition | 0.5 day |
| O2 | P1 | Limit badge layout to loaded sections and skip completed quote-matching passes | Actual badge function exercised with deterministic counters | 1.5–2 days |
| O3 | P2 | Cache page estimates and send the contents-page map only when it changes | Actual page-statistics function exercised with deterministic counters; inherited code | 1–1.5 days |
| O4 | P1 | Fetch bounded review sessions with database queries | Confirmed full-library loads and full-list sorts for a ten-item session | 2–3 days |
| O5 | P1 reliability | Cancel obsolete speech work and always clean up bridge requests | Concrete asynchronous control-flow risks; device/user failure not reproduced | 1–1.5 days |
| O6 | P2 | Move annotation serialization off the UI thread and serialize overlapping bridge updates | Confirmed whole-book payload construction on the reader collector's context | 1–2 days |
| O7 | Conditional | Reduce sync allocation peaks and duplicate concurrent slice downloads | Allocation pattern and cache race visible; material runtime cost not measured | 0.5 day investigation; implementation separately estimated |

Allow approximately 8–12 engineer-days for the baseline, O1–O6, integration, and documentation. These are planning estimates, including focused tests, rather than measured delivery times. O7 implementation is outside that estimate.

### Checks completed

- Current working tree was clean before this document was added.
- `npm.cmd test` in `reader/engine-web/tests`: **16 passed, 0 failed**.
- A temporary Node VM probe executed the real `layoutPopularBadges()` function with 10,000 popular annotations across 100 sections, one section loaded. It counted operations, using mocked geometry; it did not measure WebView rendering time.
- A second temporary Node VM probe executed the real `bookPageStats()` with 1,000 sections and 1,000 contents entries, advancing one page within the same chapter.
- Android builds, Kotlin tests, device profiling, and battery measurements were not run during this planning pass.

| Probe | First call | Second call with warmed caches |
| --- | --- | --- |
| Badge annotation visits | 10,000 | 10,000 |
| Badge popularity-count parses | 10,000 | 10,000 |
| Badge CFI resolutions | 10,000 | 0 |
| Badge range anchors / frame geometry reads | 100 / 100 | 100 / 100 |
| Page-stat section-size reads | 2,002 | 2,002 |
| Page-stat contents entries visited | 1,000 | 1,000 |
| Serialized contents-page map | 24,780 UTF-8 bytes | 24,780 UTF-8 bytes |

Both probes use synthetic stress fixtures. Their counts establish repeated work; they do not establish typical library sizes or device speedups.

### Existing improvements to preserve

The recent code already has an eight-utterance TTS queue, direct Range-based speech overlays, throttled listening-time updates, distinct notification snapshots, reader-style change detection, bionic document caching, batched annotation insertion/deletion, cached library sort keys, remembered shelf flows, and image encoding on `Dispatchers.IO`. Speech sliders commit on release. Reimplementing these would add little value.

## 2. Establish a baseline before implementation

Use the existing test stack and debug instrumentation first. Keep dependency versions and the Android toolchain pinned.

1. Add reproducible fixtures for a small book, a long chapter, a book with many chapters, and a book with many imported quotes. Suggested stress levels: 0 / 500 / 5,000 / 10,000 annotations and 50 / 250 / 1,000 sections. These are test sizes, not claims about normal use.
2. Measure warm page turns separately from chapter loads, first annotation matching, and repagination. Record operation counts, bridge payload bytes, UI-thread time, frame timing, and retained memory.
3. Exercise review datasets with 100 / 10,000 / 50,000 annotations, including mostly community quotes, mostly personal annotations, mostly future reviews, and mostly overdue reviews.
4. Measure Notes sorting after switching sort modes and after editing a count. Capture initial load and repeated recomposition separately.
5. Record device model, display profile, refresh rate, Android/WebView version, build variant, and fixture version. Use the same device and build configuration for before/after comparisons.
6. Capture foreground and screen-off read-aloud traces. Validate an actual e-ink device before claiming reduced panel activity or battery savings.

Use deterministic operation counts as regression gates. Report median and p95 timings on a fixed device as supporting evidence; avoid fragile wall-clock thresholds in ordinary unit tests. New permanent tracing should use the existing diagnostics conventions and omit book text, notes, credentials, and imported content.

## 3. O1 — Precompute Notes sort values

**Evidence:** [Notes sorting][notes-sort] invokes `communityHighlightCount()` inside `sortedBy` / `sortedByDescending`. [The parser][count-parser] checks annotation classification and applies a regex. A comparison sort evaluates that work repeatedly. [The list][notes-list] remembers the sorted community list, but constructs the personal-annotation list again in the lazy-list builder.

**Implementation:**

1. Partition the input once when its annotation revision changes.
2. Decorate community entries with their parsed count and original position. Sort the cached numeric values; use original position to preserve stable ordering for ties.
3. Preserve `BOOK_ORDER`, both count sort directions, and the current `null -> 0` behavior.
4. Keep derived values local to the displayed data. Recompute after a count, source classification, or annotation changes; do not introduce a process-wide annotation cache.
5. If the baseline shows long UI-thread sorting, move preparation into the Notes presentation flow using the existing dispatcher abstraction. Do the small cached-key change first.

**Validation and acceptance:** identical annotation IDs/order for all three modes, stable ties, malformed/overflowing counts, edited notes, personal annotations, and source filters. Count parsing should occur at most once per community annotation per input revision and zero times on unrelated recompositions. Add the minimal test setup to `feature/notes` if the extracted helper is tested there; that module currently has no test source directory or explicit Kotlin test dependency.

**Risk:** retaining stale derived counts if the cache key omits changed annotation content.

## 4. O2 — Make reader annotation work proportional to the loaded chapter

**Evidence:** [Badge layout][badge-layout] rebuilds a map from every active annotation, reparses counts, reads the same iframe rectangle repeatedly, and interleaves geometry reads with DOM writes. CFI caching removes repeated resolution but does not remove the full scan. [Relocation][relocate] also schedules [quote matching][quote-match] on every page turn; completed documents still walk the annotation list to discover that entries were already resolved or absent.

**Implementation:**

1. Maintain badge metadata keyed by canonical CFI and section index. Build/update it when annotations change or a text quote resolves, rather than on each relocation.
2. Preserve deduplication: quotes sharing a passage contribute the maximum count, with a defined winning source for color and metadata. Recompute that winner after edit/deletion.
3. At layout time, inspect only entries belonging to loaded sections. Read each frame rectangle once, then gather candidate range rectangles before changing the DOM.
4. Commit badge elements in one fragment/replacement operation. Add element reuse only if traces justify it; a visible-badge pool is not required for the initial fix.
5. Track a document's completed matching revision. A repeated relocation with unchanged annotations and document structure should skip the matching pass entirely. Invalidate on relevant annotation changes, document replacement, and bionic text-node changes.
6. Keep failed annotation additions retryable. A completed revision must not hide pending failures or allow an obsolete asynchronous addition to become authoritative.
7. Keep geometry separate from resolved-CFI metadata: font changes, image loading, resize, and repagination invalidate rectangles even when annotation content is unchanged. Avoid retaining detached documents through strong caches.

**Validation and acceptance:**

- Extend the current placement tests to exercise the real layout routine with multiple sections, duplicate passages, changed counts, source deletion, resize, and unloaded/reloaded documents.
- In the 10,000-annotation / 100-current-section fixture, a warm layout should visit the 100 current-section candidates, with zero count reparses and zero new CFI resolutions; frame geometry should be read once per participating frame.
- An unchanged document/revision should execute no repeated matching scan. A newly added quote must still be matched immediately.
- Preserve margin placement, clipping, hidden badges in scrolled mode, e-ink colors, CFI stability, and retry after a failed `addAnnotation`.
- Check first-chapter matching time independently: indexing makes repeated turns cheaper but does not by itself make the first fuzzy-match pass cheap.

**Risk:** incomplete invalidation could leave stale/missing badges. Use explicit revisions and focused lifecycle tests before adding further caching.

## 5. O3 — Cache page estimates and reduce relocation payloads

**Evidence:** [Page statistics][page-stats] recomputes section totals, every section's start page, and the complete TOC map for every relocation. [The bridge receiver][locator-receiver] converts the map back into Kotlin each time. This code predates the reviewed commits (`git blame` traces the function to September 3); background speech and frequent reader updates make it relevant to the current execution path.

**Implementation:**

1. Cache estimated section counts, prefix totals, and the TOC-page map behind a layout revision.
2. Rebuild when a section's measured page count changes, a new section sample affects the average, or style/layout changes invalidate estimates. Page movement within an unchanged section updates only `currentPage`.
3. Preserve existing rounding and treatment of non-linear/zero-size sections. Derive the cached calculation against the current function before replacing it.
4. Add a contents-map revision to relocation messages and send `tocPages` only when the map changes. Update Kotlin to retain the last map for omitted payloads and clear it on book open, renderer replacement, and close. An explicit empty map must still clear it.
5. Keep this internal protocol change together across JavaScript and Kotlin in one commit.

**Validation and acceptance:** equivalence tests for current page, total pages, and every TOC page across varied section sizes and fractional estimates. Include same-file anchors, font changes, image-driven repagination, jumps, renderer restart, and a second book. A warm same-section turn should have no section/TOC traversal and no unchanged TOC map in its payload. The first message after open/restart must contain a complete map.

**Risk:** stale page numbers after a layout change, or accidentally reusing another book's TOC. This optimization requires an explicit invalidation contract.

## 6. O4 — Use bounded queries for highlight review

**Evidence:** [The review ViewModel][review-vm] concurrently loads all books, all active annotations, and all schedules, retains those collections, then [sorts eligible lists][review-selection] before taking ten entries. [The DAO][review-dao] exposes only all-schedule observation and individual lookup. [Statistics][statistics-inputs] shares annotation observation but still derives its review list from complete collections.

**Implementation:**

1. Define repository operations for a frozen due-session snapshot, eligible count, and daily practice selection. Reuse the vocabulary review query approach already present in the repository.
2. Query overdue annotations joined to review schedules and active books, ordered by `dueAt`, then annotation ID, with a limit of ten. Fill the remaining slots from never-reviewed eligible annotations ordered by `createdAt`, then ID. Run the reads as one consistent transaction.
3. Return only the selected annotation rows and the small set of book fields the cards require. Keep review schedules keyed by `syncId`, local to the device, and independent of annotation `updatedAt`.
4. Replace practice's retained whole-library list with a count plus the selected five rows. Preserve the existing day-based offset and wraparound rule using at most two bounded slices in a consistent snapshot. SQL offset work may still grow with library size; measure it separately rather than claiming constant database work.
5. Change the Statistics review preview to the same bounded due/practice API. Preserve its current maximum-ten label behavior. Refresh its time parameter on screen resume so returning later does not reuse an old due cutoff.
6. Keep the current pure selection functions as equivalence oracles during the transition. Specify eligibility once and test SQL parity for whitespace-only text, prefix casing, legacy `quote:` locators, modern `goodreads-quote:` locators, deletion, and ordering ties. The current pure predicate does not explicitly reject a bookmark with nonblank text; changing that behavior belongs in a separate correctness decision.
7. Inspect `EXPLAIN QUERY PLAN` against realistic fixtures before adding indexes. Start with existing `syncId`, `bookId`, and `dueAt` indexes. If another index is justified, create a real migration from the current version, update the entity/schema, and add the changelog entry; do not bump the database for query-only changes.
8. In a separate measured follow-up, replace Statistics' annotation totals and daily-practice input with aggregate/bounded queries. O4's first step does not eliminate the full annotation query still used by the general statistics summary.

**Validation and acceptance:** repository tests compare the exact selected IDs, order, counts, and practice wraparound against current behavior. Include 50,000 annotations with mixed sources and due states, soft-deleted books, sync row replacement retaining `syncId`, and edited highlights. Session loading should materialize no more than ten card annotations plus scalar counts/selected book metadata; practice should materialize at most five. A session must remain stable while grades or unrelated annotations change.

**Risk:** eligibility/ordering drift and accidental changes to local-only review storage. Main-thread work is not the only concern: the existing background load still creates and retains unnecessary objects.

## 7. O5 — Prevent obsolete speech work and clean up bridge requests

**Evidence:** [ReadAloudPlayer][speech-player] checks active state before awaiting the initial speech chunk, but does not validate a playback generation after it returns. Initial preparation/loading is not stored in `chunkJob`. `nextChapter()` overwrites that job without coalescing an existing request, and the utterance IDs can be reused after restarts. Late work can therefore act on a newer session. [Bridge requests][bridge-request] remove their pending entry with `.also` after an await; caller cancellation can bypass that cleanup until a reply or engine close. The bridge helper predates this change window.

These are code-supported race mechanisms, not reproduced reports of user-visible failures. Establish failing deterministic tests first.

**Implementation:**

1. Introduce a playback generation invalidated by stop, release, engine switch, renderer replacement, and starting from another selection. Recheck it after readiness callbacks and every suspended engine call.
2. Own the initial load and chapter transitions through cancellable jobs. Permit at most one next-chapter request per current transition; repeated skip/done callbacks must not initiate duplicate transitions.
3. Namespace TTS callback IDs by playback/queue generation while retaining the original source sentence ID and offset for bridge marking. Ignore callbacks from flushed queues. Audit `stopSpeech()` delivery ordering so an old asynchronous cleanup cannot clear a new session.
4. Put bridge-request removal in `finally`, covering successful replies, timeout, cancellation, and failure to enqueue JavaScript. Late replies should become harmless no-ops.
5. Preserve the current queue window, focus rules, sentence-based skip behavior, selected-sentence start, and e-ink sentence-only marks.

**Validation and acceptance:** use deferred fake engine/readiness responses with a controlled coroutine scheduler: stop while starting; start/stop/start before readiness; stop during chapter loading; repeated next near a boundary; old callbacks after rate/voice flush; renderer replacement; and cancellation before a bridge reply. Obsolete work must cause zero new speech calls, state changes, or chapter requests; pending bridge entries must be removed immediately when their caller ends. Existing speech and lifecycle tests must still pass.

Add focused JavaScript tests for the recent paginator timer fallback and story-end event gating before changing adjacent speech/navigation behavior. Their intended behavior is important, and the current 16-test DOM suite does not cover those paths. Finally validate lock-screen controls, audio interruption, and screen-off chapter transitions on Android.

## 8. O6 — Reduce annotation update work across the WebView bridge

**Evidence:** [The annotation observer][annotation-observer] maps each whole-book annotation emission and invokes [JSON payload construction][annotation-payload] directly. That construction runs without an explicit background dispatcher in the reader's main-scope collection. JavaScript already diffs individual annotations, but receives the complete payload, and its asynchronous render operations can overlap subsequent updates.

**Implementation:**

1. Measure annotation mapping/serialization separately from WebView evaluation and DOM application.
2. Move pure mapping and JSON serialization to the existing background dispatcher abstraction. Keep every WebView API invocation on the main thread.
3. Use latest-snapshot preparation and one serialized JavaScript application loop. When several updates arrive during application, retain the newest pending snapshot. Cancellation on Kotlin alone cannot cancel JavaScript already submitted.
4. Carry an annotation revision and engine/book generation. Discard stale work after asynchronous additions and ensure snapshot completion/invalidation cooperates with O2's matching and badge indexes.
5. Preserve a complete initial render and complete renderer-recovery render. Ensure latest-snapshot conflation still includes every final deletion and note edit.
6. Add a native-to-JavaScript delta protocol only if measured serialization/bridge bytes remain a material cost after these changes. It requires stable annotation identity, explicit deletions, recovery/full-snapshot semantics, and extra tests; it is not the default first implementation.

**Validation and acceptance:** import followed by edit/delete while an earlier render is suspended; a failed annotation addition followed by retry; rapid color/note edits; recovery after renderer death; and switching books. Final overlays must match the newest snapshot exactly. Payload construction should produce no annotation-count-dependent work on the UI thread. There should be at most one JavaScript application in flight plus one latest pending snapshot.

**Dependencies:** O5 establishes lifecycle/cancellation conventions; O2 establishes annotation cache ownership. Avoid changing all three contracts independently in separate overlapping edits.

## 9. O7 — Profile sync memory before adding another cache

**Evidence:** [Snapshot publication][snapshot-publish] builds all JSON documents and an upload list retaining JSON plus encoded byte arrays. Compression creates additional temporary buffers. [Slice loading][slice-loading] checks a concurrent cache before loading, but does not deduplicate two concurrent misses. Existing call paths may already avoid much of that overlap, so its frequency must be measured. The LRU is already bounded and should remain bounded.

**Investigation and conditional implementation:**

1. Record peak allocation/retained memory, serialization/compression time, and request counts for cold full sync, unchanged sync, and progress-only sync with a large quote collection.
2. If byte-array retention is significant, encode/upload one changed slice at a time and release its bytes promptly. Keep accurate progress reporting, or explicitly distinguish estimated totals from completed bytes. Measure JSON retention before considering streaming serialization.
3. If overlapping reads of the same repository/path occur, share one in-flight load per scoped key. Define cancellation ownership so one cancelled consumer does not accidentally cancel other consumers; clear failed in-flight entries so retries work.
4. Preserve ZIP compatibility, decompression limits, scope-isolated caches, unchanged-slice reuse, and expected-SHA conflict handling.
5. Keep repository writes ordered and preserve the latest-pointer publication boundary. Parallel writes are not proposed as an optimization.

**Gate:** implement only a reproduced source of material peak memory or duplicated requests. Acceptance tests cover ZIP round trip/limits, concurrent success/failure/cancellation, repository isolation, unchanged-slice reuse, and a conflicting latest-pointer update. This package has no claimed device speedup yet.

## 10. Delivery order and verification

| Step | Deliverable | Completion gate |
| --- | --- | --- |
| 0 | Baseline fixtures, counters, and device trace recipe | Repeatable results tied to HEAD/device/build |
| 1 | O1 Notes sort keys | Same ordering; one parse per entry/revision |
| 2 | O5 speech lifecycle and request cleanup | Deferred-response regressions pass |
| 3 | O2 badge indexes and matching revisions | Chapter-local layout; unchanged-match pass skipped |
| 4 | O3 cached page statistics and TOC revisions | Exact page equivalence; unchanged TOC omitted |
| 5 | O4 bounded review repository and consumers | Correct frozen sessions; bounded materialization |
| 6 | O6 annotation preparation/application pipeline | Newest snapshot wins; no UI-thread serialization |
| 7 | Integration profiling, documentation, O7 decision | Recorded before/after results and remaining limits |

Use a small commit per deliverable with its tests. Work on the reviewed HEAD or explicitly account for later changes before beginning. Revert an individual optimization if its acceptance conditions fail; prefer code-level rollback over permanent user-facing switches. If an index migration is introduced, rollback must keep installed databases readable rather than downgrading their version.

Focused commands, from the repository root unless noted:

```powershell
# Reader DOM tests, from reader/engine-web/tests
npm.cmd test

# Speech, review, and database regression coverage
.\gradlew.bat :feature:reader:testDebugUnitTest :feature:statistics:testDebugUnitTest :feature:library:testDebugUnitTest :core:database:testDebugUnitTest

# After adding the Notes test setup
.\gradlew.bat :feature:notes:testDebugUnitTest

# Only when sync publication/loading changes
.\gradlew.bat :core:sync:testDebugUnitTest :core:backup:testDebugUnitTest

# Final integration and existing repository gates
.\gradlew.bat :app:assembleDebug testDebugUnitTest lint
git diff --check
```

Add engine-web JVM test dependencies only if the request-lifecycle helper needs a local test target; that module currently has no explicit test setup. Use existing reader fakes where practical. A passing test suite is a correctness check, not performance evidence.

The Android smoke matrix should cover standard and e-ink profiles, bionic on/off, paginated/scrolled reading, many community quotes, rapid style changes, read aloud across chapter boundaries with the screen off, renderer recovery, review grading/practice, and book/quote image sharing. Share capture already encodes off the UI thread; profile its capture/readback before proposing changes. E-ink's vendor-specific refresh APIs remain a separate device integration project.

Update `docs/FEATURES.md` with changed internal contracts. Add the missing v25 tag-cleanup entry to `docs/DATABASE_CHANGELOG.md`, and refresh stale handoff claims (it still lists DB v21 and read aloud as unbuilt). Record measured improvements and explicitly deferred hypotheses in this plan after implementation.

**Done means:** selected O1–O6 acceptance gates pass, existing behavior remains covered, device comparisons show the intended improvement without a material regression, and any unmet device requirement is clearly reported. Stop optional tuning once those conditions are met.

[notes-sort]: D:/AntiGravity/Vayana/feature/notes/src/main/kotlin/com/vayana/feature/notes/NotesScreen.kt:1138
[notes-list]: D:/AntiGravity/Vayana/feature/notes/src/main/kotlin/com/vayana/feature/notes/NotesScreen.kt:802
[count-parser]: D:/AntiGravity/Vayana/core/database/src/main/kotlin/com/vayana/core/database/model/Annotation.kt:36
[badge-layout]: D:/AntiGravity/Vayana/reader/engine-web/src/main/assets/bridge.js:479
[relocate]: D:/AntiGravity/Vayana/reader/engine-web/src/main/assets/bridge.js:321
[quote-match]: D:/AntiGravity/Vayana/reader/engine-web/src/main/assets/bridge.js:1234
[page-stats]: D:/AntiGravity/Vayana/reader/engine-web/src/main/assets/bridge.js:34
[locator-receiver]: D:/AntiGravity/Vayana/reader/engine-web/src/main/kotlin/com/vayana/reader/web/FoliateBookEngine.kt:516
[review-vm]: D:/AntiGravity/Vayana/feature/statistics/src/main/kotlin/com/vayana/feature/statistics/HighlightReviewViewModel.kt:67
[review-selection]: D:/AntiGravity/Vayana/feature/statistics/src/main/kotlin/com/vayana/feature/statistics/HighlightReview.kt:18
[review-dao]: D:/AntiGravity/Vayana/core/database/src/main/kotlin/com/vayana/core/database/dao/HighlightReviewDao.kt:11
[statistics-inputs]: D:/AntiGravity/Vayana/feature/statistics/src/main/kotlin/com/vayana/feature/statistics/StatisticsViewModel.kt:131
[speech-player]: D:/AntiGravity/Vayana/feature/reader/src/main/kotlin/com/vayana/feature/reader/ReadAloudPlayer.kt:80
[bridge-request]: D:/AntiGravity/Vayana/reader/engine-web/src/main/kotlin/com/vayana/reader/web/FoliateBookEngine.kt:411
[annotation-observer]: D:/AntiGravity/Vayana/feature/reader/src/main/kotlin/com/vayana/feature/reader/ReaderViewModel.kt:938
[annotation-payload]: D:/AntiGravity/Vayana/reader/engine-web/src/main/kotlin/com/vayana/reader/web/FoliateBookEngine.kt:354
[snapshot-publish]: D:/AntiGravity/Vayana/core/sync/src/main/kotlin/com/vayana/core/sync/snapshot/RemotePortableSnapshotStore.kt:300
[slice-loading]: D:/AntiGravity/Vayana/core/sync/src/main/kotlin/com/vayana/core/sync/snapshot/RemotePortableSnapshotStore.kt:61
