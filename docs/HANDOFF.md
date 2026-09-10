# Handoff

> **Latest session: see [`SESSION_HANDOFF_2026-09-10.md`](SESSION_HANDOFF_2026-09-10.md)** for the most recent
> (uncommitted) work. The status snapshot below is old — it predates annotations, statistics, GitHub sync and
> the book detail screen — and should be rewritten rather than trusted.

Status snapshot for picking this project up in a new session. Read in this order:

1. **`PROMPT2appbuild.md`** (repo root) — the full spec and engineering rules (§0). Still the source of truth for everything not yet built.
2. **`Design/design_handoff_vayana/README.md`** + the HTML file next to it — the visual design source of truth (exact colors/type/spacing). Supersedes `Design/PROMPT1uidesign.md`, which was only the prompt that produced it.
3. **`docs/DECISIONS.md`** — *read this before touching any dependency version.* It records a long chain of real, hard-won toolchain incompatibilities (AGP 8 vs 9, Kotlin/KSP/Hilt version walls, a material3-stable-vs-alpha API gate, a Gradle-embedded-Kotlin ceiling, a Kotlin/Compose metadata quirk). Casually "upgrading to latest" on any of AGP/Kotlin/KSP/Hilt/material3/Compose-BOM without reading this will very likely reintroduce a build break that was already solved once.
4. **`docs/DATABASE_CHANGELOG.md`** — Room schema version history; update it on every `DATABASE_VERSION` bump.

## Current state

`./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`, real APK, installed and manually exercised on both an emulator and a physical device.

| Milestone (PROMPT2 §8) | Status |
|---|---|
| 1. Scaffold (modules, tokens, theme, nav shell, lint) | **Done** |
| 2. Data layer (Room + migrations + DAOs + FTS, storage roots, settings registry) | **Partial** — only a `Book` table exists (no `BookGroup`/`Tag`/`Annotation`/`ReadingSession`/etc., no FTS, no settings registry at all) |
| 3. Library (import, covers, grid, detail, delete) | **Partial** — EPUB import works end-to-end (FAB → SAF file/folder picker → copy → parse → hash-dedupe → persist → grid); no groups/tags/sort/filter, no book detail screen, no delete, no per-file import progress sheet (just one summary line) |
| 4. Reader (engine-api + engine-web/foliate-js, open/paginate/persist locator, tap zones, style panel, TOC) | **Partial** — opens and paginates a real EPUB via foliate-js in a WebView, 3-way tap zones (prev/toggle-chrome/next), locator persisted to Room on every relocate, resumes on reopen. No TOC/Notes/Progress/Style/TTS/Search chrome panels, no text-selection menu, no annotations rendering (`BookEngine.applyStyle` exists and works but nothing calls it with real settings yet) |
| 5. Annotations | **Not started** |
| 6. Reading-time tracking → statistics | **Not started** (Statistics tab is still just an empty state) |
| 7. Format conversion (TXT/MOBI/AZW3/FB2/PDF) | **Not started** (`:format:convert` is an empty stub; non-EPUB imports are correctly detected and reported as "unsupported", not silently dropped) |
| 8. TTS, dictionary, global search | **Not started** (`:dictionary:*` are empty stubs; Notes tab and Search are not implemented) |
| 9. Settings, backup/restore, storage migration, onboarding, hints, changelog | **Not started** (`:feature:settings`, `:feature:onboarding` are empty stubs; `:core:datastore` is an empty stub) |
| 10. Screenshot tests, accessibility, R8, release build | **Not started** |

## Known open issue — verify first

Reader showed a blank white screen on first real-device test. Diagnosed and (believed) fixed, but **not yet confirmed** by the user as of this handoff:

- Root cause: `WebViewAssetLoader.AssetsPathHandler`'s automatic MIME-type lookup can't resolve `.js` → `text/javascript` on-device (`MimeTypeMap` has no default mapping for it). WebView's `<script type="module">` loader is MIME-type-strict and silently refuses to execute a module served with the wrong/missing content type — no thrown JS error, just nothing happens.
- Fix: `reader/engine-web/src/main/kotlin/com/vayana/reader/web/FoliateBookEngine.kt` now serves `/assets/*` itself (`serveAsset()`) with an explicit extension→MIME map instead of relying on `AssetsPathHandler`.
- Also added: a `WebChromeClient.onConsoleMessage` override logging to Logcat under tag `FoliateReader`, since there was previously **zero visibility** into WebView JS errors. If anything reader-related still looks broken, `adb logcat | grep FoliateReader` is the first thing to check — it wasn't wired up before this fix, so earlier failures were invisible.
- If the white screen persists after this fix: check that fix landed (`git log` isn't available — this repo isn't a git repo yet — so check the file directly), then use the console logging to see the actual JS-side error.

## Architecture map

Real code vs. empty scaffolding, module by module:

```
:app                      nav shell (3 tabs + full-screen Reader route), Hilt entry point
:core:designsystem        full token set + theme (light/dark/softer-dark/true-black/E-Ink), Konsist test
:core:resources           strings.xml (grows as features add UI)
:core:common              AppResult, DispatcherProvider (Hilt-bound), Hashing
:core:database            REAL: Book entity/DAO/repository/Hilt module. Missing: everything else in PROMPT2 §2
:core:datastore           EMPTY STUB — settings registry (§5) not started
:core:filesystem          REAL: StorageRoots, BookFileImporter (SAF copy-in + hash)
:feature:library          REAL: import FAB, grid, generated covers. Missing: groups/tags/sort/filter/detail/delete
:feature:reader           REAL: WebView host, tap zones, locator persistence. Missing: chrome panels, annotations
:feature:notes            EMPTY STATE ONLY — no data wiring
:feature:statistics       EMPTY STATE ONLY — no data wiring
:feature:search           EMPTY STUB
:feature:settings         EMPTY STUB
:feature:onboarding       EMPTY STUB
:reader:engine-api        REAL: BookEngine/Locator/TocEntry/NavTarget/EngineEvent/BookStyle/ReadTheme (pure kotlin.jvm, no Android dep by design)
:reader:engine-web        REAL: FoliateBookEngine (foliate-js vendored in assets/, WebViewAssetLoader, JS bridge)
:format:epub              REAL: hand-rolled metadata + cover parser (no third-party EPUB lib)
:format:convert           EMPTY STUB — TXT/MOBI/AZW3/FB2/PDF → EPUB pipeline, chapter-split rule engine
:dictionary:api           EMPTY STUB (pure kotlin.jvm)
:dictionary:stardict      EMPTY STUB
```

## Immediate next steps (pick one, roughly in PROMPT2 build-order priority)

1. **Confirm the reader white-screen fix** actually works on-device (see above) before building more reader chrome on top of a possibly-still-broken foundation.
2. **Reader chrome**: TOC panel (`ReaderViewModel.openTocEntry` already exists, just needs a UI sheet listing `ReaderUiState.Loaded.toc`), Style panel wired to `BookEngine.applyStyle` (already implemented and callable, nothing calls it with real user settings yet), Progress panel (chapter pager / book slider — `Locator.progression` is already flowing).
3. **Settings registry** (`:core:datastore`, PROMPT2 §5) — currently zero settings exist; needed before Style/theme panels can persist anything, and before E-Ink mode / density / motion settings (already fully supported in `:core:designsystem`'s theme layer) are user-controllable.
4. **Expand the `Book`/Room schema** (`:core:database`) — `BookGroup`, `Tag`, `Annotation`, `ReadingSession` per PROMPT2 §2, each as its own migration + `docs/DATABASE_CHANGELOG.md` entry, only as the feature that needs it gets built (Annotations before Notes tab, ReadingSession before Statistics tab).
5. **`:format:convert`** — TXT first (per PROMPT2 §8 step 7's own ordering), including the chapter-split rule engine with built-in presets.

## Gotchas for whoever picks this up

- **Don't bump AGP/Kotlin/KSP/Hilt/material3/Compose-BOM without reading `docs/DECISIONS.md` first.** The current pins (AGP 9.4.0, Kotlin 2.3.21, KSP 2.3.11, Hilt 2.60.1, material3 **1.5.0-alpha27** — deliberately not stable 1.4.0, Compose BOM 2026.08.00) are the result of working through several genuine, non-obvious incompatibilities. `gradle.properties`' `android.builtInKotlin=false` and `android.newDsl=false` are load-bearing, not leftover cruft — removing them breaks the build (the exact errors are in DECISIONS.md).
- **A `RowScope.weight()` call once failed to compile** with a Kotlin/Compose metadata error ("internal in file") identical in shape to the material3 issue, despite the resolved dependency versions looking fine. Worked around in `ReaderScreen.kt` by avoiding `weight()`. If this recurs elsewhere, it's a known pattern — see DECISIONS.md rather than re-debugging from scratch.
- **This is not a git repository.** No commit history to check `git blame`/`git log` against — `docs/DECISIONS.md` and this file are the only record of *why* things are the way they are.
- **`local.properties` is gitignored and machine-specific** (`sdk.dir`) — regenerate it if missing rather than assuming its absence means something is broken.
- The Gradle wrapper (`gradle/wrapper/gradle-wrapper.properties`) points at Gradle 9.7.1. A normal dev machine with normal networking should just work via `./gradlew`; the *sandboxed build environment used to develop this* had a Java-networking quirk (plain `HttpURLConnection` couldn't complete Gradle's own distribution-download redirect chain even though `curl` could) that forced downloading Gradle by hand — not expected to affect a real workstation.
