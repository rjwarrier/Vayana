# Vayana Implementation Plan

This plan is based on the current source tree, `docs/HANDOFF.md`, `docs/DECISIONS.md`,
`docs/DATABASE_CHANGELOG.md`, `PROMPT2appbuild.md`, and the design handoff under
`Design/design_handoff_vayana/`. Treat those documents as project context; the current user goal is
to complete the remaining app work in a controlled order.

## Current Baseline

Verified on 2026-09-02:

- `.\gradlew.bat :app:assembleDebug` succeeds.
- This directory is not currently a Git repository, so the docs are the source of project history.
- The app is a multi-module Android/Kotlin project with Hilt, Room, Jetpack Compose, Material 3
  Expressive, and a WebView/Foliate reader engine.
- EPUB import works through SAF: copy into app storage, hash, parse metadata/cover, dedupe, persist,
  and show in the library grid.
- Reader opens a real EPUB through Foliate in a WebView, supports simple left/center/right tap zones,
  persists locators, and resumes from the last locator.
- Notes and Statistics are empty-state screens only.
- `:core:datastore`, `:feature:search`, `:feature:settings`, `:feature:onboarding`,
  `:format:convert`, `:dictionary:api`, and `:dictionary:stardict` are stubs.

Important constraints:

- Do not casually change AGP, Kotlin, KSP, Hilt, Material 3, Compose BOM, Gradle, or related flags.
  Read `docs/DECISIONS.md` before touching versions.
- Keep the app offline-only. No runtime network calls and no `INTERNET` permission.
- Store user-visible strings in `core/resources/src/main/res/values/strings.xml`.
- Store database paths root-relative and resolve them through `StorageRoots`.
- Every Room schema bump needs a real migration, schema export update, migration test, and
  `docs/DATABASE_CHANGELOG.md` entry.
- Prefer registries over scattered `when` ladders for settings, formats, note colors, stat tiles,
  page-turn actions, dictionary providers, and exports.

## Phase 0: Stabilization And Setup

Goal: make sure the existing foundation is dependable before adding more surface area.

Tasks:

- Confirm the reader white-screen fix on a physical device.
- If the reader is blank, use Logcat tag `FoliateReader` and inspect
  `reader/engine-web/src/main/kotlin/com/vayana/reader/web/FoliateBookEngine.kt`.
- Add a short smoke-test checklist under docs if manual device verification is repeated often.
- Decide whether to initialize this directory as a Git repository before large changes.
- Keep build outputs out of scans and future commits if Git is initialized.

Acceptance checks:

- `.\gradlew.bat :app:assembleDebug`
- Manual: import one EPUB, open it, page forward/back, close, reopen, confirm position resumes.
- Manual: pick a non-EPUB file and confirm it appears in the import summary as unsupported.

## Phase 1: Settings Spine

Goal: implement the typed settings architecture first, because reader styling, e-ink behavior,
settings UI, onboarding, backup, and several feature registries depend on it.

Primary modules:

- `:core:datastore`
- `:feature:settings`
- `:app`
- `:core:designsystem`
- `:reader:engine-api`
- `:feature:reader`

Data model:

- `SettingsKey<T>` or sealed `Setting<T>` declarations with stable keys, defaults, string resources,
  group metadata, and visibility rules.
- `SettingsSnapshot` for fast UI consumption, especially reader and theme state.
- `SettingsRegistry` as the single source of all settings.
- `SettingsRepository` with per-setting flows, snapshot flow, updates, reset-one, reset-all,
  export-to-map, and import-from-map.

Initial setting groups:

- Appearance: app theme mode, display profile, softer dark, true black, motion mode.
- Reader typography: font size percent, line height, font family.
- Reader layout: side margin, publisher styles toggle.
- Reader behavior: tap zone mode, volume keys, keep awake.
- Import/maintenance placeholders: storage root display, backup entry points.

Implementation steps:

1. Add DataStore Preferences dependency to `:core:datastore`.
2. Create setting declarations and typed read/write mapping.
3. Add Hilt binding for `SettingsRepository`.
4. Wire app theme to a settings flow in `VayanaAppRoot`.
5. Add generated settings list UI in `:feature:settings`.
6. Add navigation route to Settings from Library header or overflow menu.
7. Add strings for every label, subtitle, and content description.

Acceptance checks:

- Settings screen is reachable.
- Updating app theme/display profile updates the Compose theme live.
- Resetting one setting and all settings works.
- `.\gradlew.bat :app:assembleDebug`
- `.\gradlew.bat build test lint` when the phase is complete.

## Phase 2: Reader Chrome MVP

Goal: turn the existing reader engine hooks into usable reader controls.

Primary modules:

- `:feature:reader`
- `:reader:engine-api`
- `:reader:engine-web`
- `:core:datastore`
- `:core:resources`

Tasks:

- Replace the temporary top bar-only chrome with a hidden-by-default bottom sheet/action rail.
- Add action rail buttons: contents, notes, progress, style, read aloud, search.
- Implement Contents panel first using `ReaderUiState.Loaded.toc` and
  `ReaderViewModel.openTocEntry`.
- Implement Progress panel using current `Locator.progression` and `NavTarget.ToFraction`.
- Implement Style panel using settings flows and `BookEngine.applyStyle`.
- Persist style changes through `SettingsRepository`.
- Keep the simple three-zone tap behavior until the full tap-zone editor is built.
- Show loading and failure states without covering a successfully loaded page.

Reader engine additions:

- Expose current locator/progression in `ReaderUiState`.
- Make `ReaderViewModel` observe settings and apply style after engine bind and on setting changes.
- Add guarded calls so style application waits until the book is open.

Acceptance checks:

- Center tap opens/closes reader chrome.
- TOC panel jumps to selected entries.
- Progress slider jumps within the book and persists after close/reopen.
- Style changes affect the WebView-rendered book live and survive app restart.
- E-ink/motion-off settings disable reader chrome animation where applicable.
- `.\gradlew.bat :app:assembleDebug`

## Phase 3: Library Completion

Goal: complete the core library workflow: browse, inspect, organize lightly, and delete.

Primary modules:

- `:feature:library`
- `:core:database`
- `:core:filesystem`
- `:app`

Tasks:

- Add book detail route and screen matching the design handoff.
- Show cover, title, author, description, format, reading progress, import date, and actions.
- Add Continue Reading, Edit Metadata, Share File, Replace Source File placeholder, and Delete.
- Wire Delete to existing soft delete repository method.
- Add library search entry point, even if the first pass only searches title/author/description.
- Add sort options: import date, title, author, last read, progress.
- Add filter chips: All, Reading, Finished, Not Started.
- Improve generated covers to use the design palette instead of arbitrary HSV values.
- Add import progress sheet with per-file rows as a UI upgrade after the detail/delete flow lands.

Possible schema work:

- If groups/tags are included in this phase, add `BookGroup`, `Tag`, and `BookTagCrossRef`.
- Add one migration per schema bump and update `docs/DATABASE_CHANGELOG.md`.

Acceptance checks:

- Tapping a book can open detail and continue reading.
- Deleting a book removes it from active library views without deleting files immediately.
- Sorting and filtering are deterministic and survive rotation.
- EPUB import still dedupes by hash.
- `.\gradlew.bat :app:assembleDebug`
- Room migration tests if schema changes.

## Phase 4: Annotation Foundation And Notes

Goal: make highlights, underlines, bookmarks, and notes real domain objects.

Primary modules:

- `:core:database`
- `:reader:engine-api`
- `:reader:engine-web`
- `:feature:reader`
- `:feature:notes`

Schema:

- `Annotation(id, bookId, type, colorKey, locator, chapterTitle, chapterHref, selectedText,
  readerNote, createdAt, updatedAt)`
- Optional note color registry table only if user-created colors are required early.
- `AnnotationFts(selectedText, readerNote, chapterTitle)` once global notes search starts.

Engine/API tasks:

- Add selection events to `EngineEvent`.
- Add `renderAnnotations(list)` to the implemented engine surface.
- Add commands for create highlight, underline, bookmark, edit note, and delete.
- Bridge Foliate selection/highlight behavior into typed Kotlin events.

UI tasks:

- Add reader selection menu: highlight, underline, note, copy, look up, search in book, read aloud.
- Add note editor bottom sheet/dialog.
- Replace Notes empty state with a list grouped by book or date.
- Add filters by book, type, color, and has-note.
- Tap a note to open the book at the annotation locator.
- Add delete/edit gestures.

Acceptance checks:

- Creating a highlight persists it and renders after reopening the book.
- Style changes do not lose annotation placement.
- Notes screen lists annotations across books.
- Tapping a note jumps to the reader location.
- Migration and DAO tests cover annotation CRUD.

## Phase 5: Reading Sessions And Statistics

Goal: track foreground reading time and build useful statistics from SQL-backed aggregation.

Primary modules:

- `:core:database`
- `:feature:reader`
- `:feature:statistics`

Schema:

- `ReadingSession(id, bookId, dateEpochDay, seconds, startedAt, endedAt)`

Tracking behavior:

- Start a session when a book is visible, opened, and the app is foregrounded.
- Pause on background, screen off, reader close, or idle timeout.
- Accumulate seconds per book per day.
- Avoid creating noisy tiny sessions by coalescing adjacent spans where reasonable.

Statistics UI:

- Tile registry with title, description, span, content, and detail behavior.
- Initial tiles: total time, books read, reading days, current streak, best streak, notes total,
  top book, continue reading.
- Add trend chart for 7 days and 30 days.
- Add year heatmap after daily aggregation is stable.
- Persist dashboard tile order and visibility through settings.

Acceptance checks:

- Reading for a short known interval creates expected session data.
- Statistics update after returning from the reader.
- Aggregations are SQL queries, not full-table Kotlin loops.
- `.\gradlew.bat :app:assembleDebug`
- DAO tests for session aggregation.

## Phase 6: Search

Goal: support fast global search and in-book search without online services.

Primary modules:

- `:core:database`
- `:feature:search`
- `:feature:library`
- `:feature:reader`
- `:reader:engine-api`
- `:reader:engine-web`

Tasks:

- Add `BookFts(title, author, description)`.
- Add `AnnotationFts(selectedText, readerNote, chapterTitle)` if not already added.
- Keep FTS tables synchronized with Room triggers or explicit DAO updates.
- Implement global Search screen.
- Segment results into Books, Notes, and This Book where context allows.
- Highlight match spans.
- Persist recent queries through settings.
- Implement in-book search through Foliate and expose it through `BookEngine.search`.

Acceptance checks:

- Searching by title/author finds books.
- Searching note text finds annotations and opens the book at the right locator.
- In-book search works from reader chrome.
- FTS migration tests pass.

## Phase 7: Format Conversion

Goal: support non-EPUB formats by converting them into EPUB during import.

Primary modules:

- `:format:convert`
- `:feature:library`
- `:core:filesystem`
- `:core:database`

TXT first:

- Detect encoding: UTF-8, UTF-16, GBK, Big5.
- Normalize text safely.
- Implement chapter split rule registry.
- Built-in presets: mixed-language default, Chinese chapter headings, English Chapter N,
  English Volume/Book.
- Add a chapter split tester UI in Settings or Import flow.
- Generate valid EPUB output and feed it into the existing EPUB import path.

Then:

- MOBI and AZW3 conversion.
- FB2 conversion.
- PDF conversion only after requirements are clear, because reflow quality is hard.

WorkManager:

- Move long import/conversion/hash jobs into background workers.
- Surface progress in the import sheet.
- Preserve the offline/no-network constraint.

Acceptance checks:

- Importing TXT creates a readable EPUB-backed book.
- Chapter splitting can be previewed and adjusted.
- Unsupported formats report clearly until their converter lands.
- Large imports do not block the UI thread.

## Phase 8: Dictionary And Offline Lookup

Goal: add offline word lookup and vocabulary tracking.

Primary modules:

- `:dictionary:api`
- `:dictionary:stardict`
- `:core:database`
- `:feature:reader`
- `:feature:settings`

Tasks:

- Define `LookupProvider` and `Definition` models in `:dictionary:api`.
- Add provider registry.
- Add StarDict import flow for `.ifo`, `.idx`, `.dict`, and `.dict.dz` bundles.
- Index dictionaries into Room or a dedicated local index.
- Add lookup popup and full-screen lookup view.
- Add system fallback with `Intent.ACTION_DEFINE` or `ACTION_PROCESS_TEXT`.
- Track looked-up words for statistics.
- Add "add to notes" action.

Acceptance checks:

- Local dictionary import succeeds and survives restart.
- Lookup works from reader selection.
- No dictionary installed state offers import or system fallback.
- No online translation path exists.

## Phase 9: Read Aloud

Goal: add Android system TTS for offline read-aloud.

Primary modules:

- `:feature:reader`
- `:reader:engine-api`
- `:reader:engine-web`
- `:core:datastore`

Tasks:

- Add `textForTts(from: Locator)` or equivalent sentence extraction.
- Add Android TTS manager with engine and voice selection.
- Add settings for rate, pitch, volume, sleep timer, and mix-with-other-audio.
- Add media session and notification controls.
- Add sentence-level highlight sync.
- Support start from current selection/location.

Acceptance checks:

- TTS starts from current reader position.
- Rate/pitch/voice settings persist.
- Media controls pause/resume/skip.
- TTS stops on reader close and handles missing offline voices gracefully.

## Phase 10: Storage, Backup, Maintenance

Goal: make the local library durable and maintainable.

Primary modules:

- `:core:filesystem`
- `:core:database`
- `:core:datastore`
- `:feature:settings`

Tasks:

- Add app-owned subdirectories: `fonts`, `dicts`, `backups`, `logs`, `cache` if missing.
- Add storage size breakdown.
- Add clear cache action.
- Add storage root migration job with progress, verify-then-delete, and resumability.
- Add backup zip: database, books, covers, fonts, dictionaries, settings JSON.
- Add restore flow with version checks and destructive-action confirmation.
- Add duplicate/hash maintenance screen.
- Add log viewer with export.

Acceptance checks:

- Backup restores onto a clean install.
- Storage migration leaves DB paths root-relative.
- Failed migration can resume or roll back safely.
- Cache clearing does not delete books, covers, notes, settings, or dictionaries.

## Phase 11: Onboarding, Hints, Changelog

Goal: complete first-run and post-update user guidance.

Primary modules:

- `:feature:onboarding`
- `:feature:settings`
- `:core:datastore`
- `:app`

Tasks:

- Build 3-4 onboarding pages: welcome, appearance/e-ink, import a book, done.
- Detect likely e-ink devices and suggest, not force, e-ink mode.
- Add first-run gate before Library.
- Add hint banner registry with dismiss state.
- Add reset-all-hints setting.
- Add changelog screen shown once after app update.

Acceptance checks:

- Fresh install shows onboarding once.
- Onboarding can be skipped and rerun from Settings.
- E-ink suggestion updates settings only after user confirmation.
- Changelog appears once per app version.

## Phase 12: Release Quality

Goal: harden the app for real use.

Tasks:

- Add screenshot tests for key screens in standard, dark, true-black, and e-ink profiles.
- Add accessibility pass: content descriptions, TalkBack traversal, touch targets, contrast.
- Add Room migration tests for every schema version.
- Add repository and ViewModel tests for import, settings, annotations, stats, and search.
- Add R8 rules for Hilt, Room, WebView bridge, and any conversion/dictionary libraries.
- Verify release build.
- Verify the manifest has no `INTERNET` permission.
- Verify no runtime network calls are introduced.

Acceptance checks:

- `.\gradlew.bat build test lint`
- `.\gradlew.bat :app:assembleRelease`
- Manual install of release APK.
- Manual EPUB import/read/resume/delete cycle.
- Manual backup/restore cycle once backup exists.

## Suggested Immediate Sprint

Do these next, in order:

1. Confirm reader rendering on physical device.
2. Implement `:core:datastore` settings registry and repository.
3. Add reachable generated Settings screen.
4. Wire theme/display profile to settings.
5. Add Reader Style panel using `BookEngine.applyStyle`.
6. Add Reader TOC panel using the existing TOC state.
7. Add Reader Progress panel using `NavTarget.ToFraction`.
8. Add Library book detail and soft delete.

This sprint turns the app from "working vertical slice" into a usable reader shell while preserving
the architecture needed for annotations, statistics, conversion, dictionary, and backup.

## Definition Of Done For Each Phase

- Code follows existing module boundaries and naming style.
- User-visible strings are in resources.
- New settings are declared in the registry, not scattered.
- New database schema has migrations, tests, exported schema, and changelog entry.
- New UI matches the design handoff tokens and avoids hardcoded dimensions outside the design system.
- Offline-only constraint remains intact.
- Build and relevant tests pass.
- Any non-obvious dependency or architecture choice is documented in `docs/DECISIONS.md`.
