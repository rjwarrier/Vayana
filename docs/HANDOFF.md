# Handoff

Status for picking this project up in a new session. Last rewritten **2026-09-21**. Check `git log` for anything
newer; this file describes structure and rules, not every change.

## Read first

1. **`PROMPT2appbuild.md`** — the product spec and engineering rules (§0). Still the source of truth for anything not yet built.
2. **`Design/design_handoff_vayana/README.md`** (+ the HTML beside it) — the visual source of truth.
3. **`docs/DECISIONS.md`** — read before touching **any** dependency version. The toolchain pins (AGP 9.x,
   Kotlin/KSP, Hilt, material3 alpha, Compose BOM, compileSdk 37) and the `android.builtInKotlin=false` /
   `android.newDsl=false` flags in `gradle.properties` are load-bearing; the reasons are recorded there.
4. **`docs/DATABASE_CHANGELOG.md`** — Room schema history. Every `DATABASE_VERSION` bump needs a real migration,
   a changelog entry and the generated `core/database/schemas/.../<n>.json` committed.
5. **`docs/FEATURES.md`** — behaviour and non-obvious rules for onboarding, global search, Read Next and deleting books.
6. **`docs/GITHUB_SYNC_IMPLEMENTATION_PLAN.md`** — design of the GitHub snapshot sync.

`docs/IMPLEMENTATION_PLAN.md`, `docs/FEATURE_VALUE_RECOMMENDATIONS.md` and `docs/SESSION_HANDOFF_2026-09-10.md`
are historical: useful context, not a current status.

## Build and test

```bash
./gradlew :app:assembleDebug testDebugUnitTest
```

- Unit tests are JVM tests (JUnit 5 / `kotlin.test`). `feature/settings` also uses **Robolectric 4.13** (backup
  restore validation against checked-in schemas), so Android-dependent tests are possible there.
- `TokenUsageKonsistTest` (in `core/designsystem`) scans **all** production code: no bare `N.dp` / `N.sp` or hex
  `Color(0x…)` outside `core/designsystem/.../tokens/`. Add a token instead.
- Reader engine DOM tests: `npm ci && npm test` in `reader/engine-web/tests` (test-only dependencies).
- `local.properties` is machine-specific and gitignored.

## Architecture map

```
:app                    Nav host (Library / Notes / Statistics + Reader, Search, Settings, Help, Diagnostics…),
                        onboarding gate, adaptive nav rail on wide screens
:core:designsystem      Tokens, theme (light/dark, softer/true-black, E-Ink profile, motion), dialogs, share cards
:core:resources         All user-facing strings
:core:common            DispatcherProvider, @ApplicationScope, hashing, quote parsing
:core:database          Room (DB v25): books, annotations, sessions, shelves, vocabulary, sync identity
                        (aliases, tombstones), FTS search tables; repositories
:core:datastore         Settings registry + DataStore (settings, onboarding flag, recent searches)
:core:filesystem        StorageRoots, SAF import copy-in, ResolvedBooks (shared absolute-path book flow)
:core:backup            Portable snapshot model/JSON used by backup and sync
:core:sync              GitHub snapshot sync: sliced snapshots, encrypted book/cover assets, progress-only sync
:core:diagnostics       Crash reporter and diagnostics log
:feature:library        Library grid/list, book detail, metadata/cover dialogs, Goodreads import, shelves,
                        Read Next, recently deleted + permanent deletion, sync/import progress, launch progress check
:feature:reader         WebView reader chrome: contents, bookmarks, progress, notes, in-book search, style,
                        dictionary lookup, reading-time tracking, reading-position prompts
:feature:notes          Global notes & highlights
:feature:statistics     Reading stats (heatmap, pace), vocabulary / learn-words review
:feature:search         Global FTS search (see docs/FEATURES.md)
:feature:settings       Settings hub, sync setup/health, backup & restore, diagnostics, Help & About, share app
:feature:help           Help screen (opened from Settings → Help & About)
:feature:onboarding     First-run pages
:reader:engine-api      Pure-Kotlin engine interface (no Android)
:reader:engine-web      foliate-js (vendored) in a WebView, served via WebViewAssetLoader
:format:epub            Hand-rolled EPUB metadata/cover parser
:format:convert         EMPTY — no TXT/MOBI/AZW3/FB2/PDF conversion yet
:dictionary:api/stardict Offline dictionary (downloadable English WordNet)
```

Not built yet: format conversion. Read aloud uses the installed Android TTS engines. Note the app now makes network calls for GitHub sync,
Goodreads import and the dictionary download — PROMPT2's original "no network" rule no longer holds as written.

## Rules and gotchas

- **Sync merges are last-write-wins on `books.updatedAt` for metadata, but reading progress also bumps
  `updatedAt`.** A field that can change independently of reading (e.g. Read Next) needs its own version
  column; see `readNextUpdatedAt` and `docs/FEATURES.md`.
- **FTS table SQL in migrations must match Room's generated `createSql`** in the schema JSON. `annotations_fts` uses
  Room's content-sync triggers; `books_fts` is standalone, with its own triggers in `search/BookSearchIndex.kt`
  (created by `MIGRATION_18_19` and, on a fresh install, `BookSearchIndexCallback`). Update both if the searchable
  book columns change.
- **Book deletes use their own version.** `books.deletionUpdatedAt` (DB v21) decides synced deletes and restores;
  never compare deletions against `updatedAt`, which reading bumps. Book deletions are pushed and applied by the
  lightweight reading-progress sync and the launch check, not only by a full sync.
- **Permanent deletion is tombstone-driven.** `book_purge` tombstones (`purge:<syncId>`) beat newer edits and block
  that sync id in book merges forever; cloud files are deleted only after a full sync has published the tombstones.
  Never garbage-collect tombstones. See `docs/FEATURES.md` → Deleting books.
- **Big files:** `LibraryViewModel.kt` (~2.7k lines, one class) and `ReaderScreen.kt` (~2.4k) are the remaining
  giants. `LibraryScreen.kt` and `SettingsScreen.kt` were split by feature on 2026-09-13.
- **Hilt + KSP + Room** run through convention plugins in `build-logic`. `ProjectExtensions.kt` there must keep its
  `package` line (see DECISIONS).
- Strings live only in `core/resources`; dispatchers are injected (`DispatcherProvider`), never `Dispatchers.*` in
  classes that tests would need to control.

## Unverified on a device (as of 2026-09-13)

- DB migrations 16→17 (`readNextUpdatedAt`), 17→18 (FTS tables + rebuild) and 18→19 (standalone `books_fts`) on the user's phone.
- Read Next sync between two devices (queue on A, read the book on B, sync both).
- DB migration 20→21 (`deletionUpdatedAt`) and automatic delete sync: delete on A, then only open the app / read on B —
  the book should leave B's library with a notice; read the book on B before it syncs and it should still go;
  restore on B, full-sync both, and it should come back on A.
- DB migration 19→20 (`pending_cloud_deletions`) and permanent deletion across two devices: book, files and cloud
  assets gone on both; an offline device's unsynced highlight for the book isn't republished; re-importing the
  same EPUB syncs as a new book.
- Search with a Malayalam title; highlight rendering on E-Ink.
- Share-app image: the WebP promo is re-encoded to JPEG when shared.
