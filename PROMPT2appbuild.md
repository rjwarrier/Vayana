# PROMPT 2 — Build the App (paste into Claude Code / any coding LLM)

---

Build **an offline-first Android e-reader**, Jetpack Compose + **Material 3 Expressive**, that also runs well on **E-Ink devices** (Boox, Onyx, Kobo-class Android panels). Everything is local: **no network permission in the manifest at all**, no accounts, no telemetry, no cloud sync, no AI calls. If a feature can't work offline, it doesn't ship.

Work incrementally: scaffold → data layer → reader engine → feature by feature. Compile and run after each milestone. Ask before choosing between two genuinely different architectures; otherwise pick the sane default and note it.

---

## 0. Non-negotiable engineering rules (the whole point of this rewrite)

These override convenience. Enforce them with lint where possible.

1. **No hardcoded user-visible strings.** Every string lives in `strings.xml` (and per-locale variants). No `Text("Settings")` anywhere. Enums that surface text carry a `@StringRes val labelRes: Int` — never a `when` block returning literals.
2. **No magic numbers in UI code.** No bare `16.dp`, no bare `Color(0xFF...)`, no bare `999`. Everything comes from token objects:
   - `Spacing` (xs/sm/md/lg/xl/xxl), `Sizes` (icon sizes, touch target, cover ratios), `Radii`, `Elevations` (as surface tiers), `Durations` + `Springs`, `Opacities`, `Strokes`.
   - Screen-level padding, list-row padding, chip padding etc. are named `Paddings.*` constants, not inline `PaddingValues`.
   - Add a custom lint rule (or a Konsist/detekt test) that fails the build on a literal `.dp`/`.sp`/hex color outside the `design/tokens` package.
3. **No hardcoded preference keys or defaults scattered around.** One typed settings registry (see §5). Adding a setting = adding one declaration, and it automatically gets persistence, a default, a reset, and export/import.
4. **Feature registries, not `when` ladders.** Statistics tiles, settings entries, note colors, export formats, page-turn actions, book formats, dictionary providers — each is a registry (`Map<Key, Descriptor>` or a sealed hierarchy with a list). Adding a new one is a single registration; no other file changes.
5. **Strict layering**, enforced by module boundaries: `ui → domain → data`. UI never touches Room/DataStore/File. Domain has zero Android imports except `@StringRes`-style annotations kept in a thin `core:resources` module.
6. **Everything user-visible is theme-driven and e-ink-aware.** No component reads `isSystemInDarkTheme()` directly; they read from the theme/`LocalDisplayProfile`.
7. **Every public behavior gets a unit test**; every screen gets at least a smoke Compose test.
8. **No `!!`, no swallowed exceptions.** Use `Result`/sealed error types with `@StringRes` messages.

---

## 1. Stack

- Kotlin 2.x, JDK 17, Compose BOM (latest), **`androidx.compose.material3` 1.4+ for Expressive APIs** (`MaterialExpressiveTheme`, `MotionScheme`, `ButtonGroup`, `SplitButton`, `FloatingActionButtonMenu`, `LoadingIndicator`, wavy progress indicators, `ToggleButton`, shape morphing via `androidx.graphics.shapes`).
- `androidx.compose.material3.adaptive` for phone/tablet/foldable panes.
- **Room** (+ FTS4/FTS5 tables) for the library, notes, reading sessions.
- **DataStore Preferences** for settings.
- **Hilt** for DI. **Kotlin Coroutines + Flow** end-to-end (no LiveData).
- **Navigation Compose** with type-safe routes (serializable route objects).
- **WorkManager** for import/conversion/hashing/backup jobs.
- Charts: **Vico** (Compose-native, themeable) — wrapped behind a `Chart` composable API so the library can be swapped.
- `androidx.documentfile` + SAF for file access (scoped storage; no legacy storage permission).
- Testing: JUnit5, Turbine, Room in-memory, Compose UI test, Roborazzi or Paparazzi for screenshot tests (including e-ink variants).

**Modules** (Gradle, with convention plugins — no copy-pasted build files):
```
:app
:core:designsystem     tokens, theme, ColorScheme builders, e-ink profile, shared components
:core:resources        strings, plurals, drawables, icon registry
:core:common           Result, dispatchers, time/format utils, logging
:core:database         Room entities, DAOs, migrations, FTS
:core:datastore        settings registry + persistence
:core:filesystem       storage roots, SAF wrappers, migration, size reporting
:feature:library
:feature:reader
:feature:notes
:feature:statistics
:feature:search
:feature:settings
:feature:onboarding
:reader:engine-api     BookEngine interface, Locator/CFI model, annotation model
:reader:engine-web     default engine: foliate-js in a WebView
:format:epub           parse/metadata/cover
:format:convert        TXT / MOBI / AZW3 / FB2 / PDF → EPUB pipeline
:dictionary:api        LookupProvider interface
:dictionary:stardict   offline StarDict/DICT importer + reader
```

---

## 2. Data model (Room)

```
Book(id, title, author, description, coverPath, filePath, format, fileHash,
     lastLocator, readingPercent, rating, groupId, isDeleted,
     wordCount, pageEstimate, createdAt, updatedAt, lastReadAt)

BookGroup(id, name, parentId, isDeleted, createdAt, updatedAt)   -- nested folders

Tag(id, name, colorArgb, createdAt)
BookTagCrossRef(bookId, tagId)

Annotation(id, bookId, type /*HIGHLIGHT|UNDERLINE|BOOKMARK*/, colorKey,
           locator /*CFI-equivalent*/, chapterTitle, chapterHref,
           selectedText, readerNote, createdAt, updatedAt)

ReadingSession(id, bookId, dateEpochDay, seconds, startedAt, endedAt)

ReadTheme(id, name, isBuiltIn, backgroundArgb, textArgb,
          backgroundImagePath, nightImagePath, imageFit, blur, opacity)

BookStyle(id, ...every typography/layout field from §4.3)

ChapterSplitRule(id, name, pattern, caseSensitive, multiline, isBuiltIn, enabled, sortOrder)

DictionaryEntry / dictionary index tables (from :dictionary:stardict)

FTS: BookFts(title, author, description), AnnotationFts(selectedText, readerNote, chapterTitle)
```

Files live under an app-owned root the user can relocate: `books/`, `covers/`, `fonts/`, `dicts/`, `backups/`, `logs/`, `cache/`. Never store absolute paths in the DB — store **root-relative** paths and resolve through a `StorageRoots` provider, so relocating the library is a single migration job.

Migrations: real Room migrations from day one, each with a migration test. A `DatabaseVersion` constant + changelog file.

---

## 3. Reader engine

Define `:reader:engine-api`:

```kotlin
interface BookEngine {
  suspend fun open(book: Book): Result<OpenBook>
  val location: StateFlow<Locator>          // href + progression + CFI-equivalent
  val toc: StateFlow<List<TocEntry>>
  suspend fun goTo(target: NavTarget)        // Locator | Href | Percentage | Chapter±1 | Page±1
  suspend fun applyStyle(style: BookStyle, theme: ReadTheme, rules: ReadingRules)
  suspend fun renderAnnotations(list: List<Annotation>)
  fun events(): Flow<EngineEvent>            // Selected, Tapped(zone), LinkClicked, FootnoteOpened, ImageOpened, PageChanged, SearchHit
  suspend fun search(query: String): Flow<SearchHit>
  suspend fun textForTts(from: Locator): Flow<TtsSentence>
}
```

**Default implementation `:reader:engine-web`:** bundle **foliate-js** in `assets/` and drive it from a `WebView` inside an `AndroidView`, with a typed JS bridge (`@JavascriptInterface` on one side, a generated TS-ish wrapper on the other). Reasons: mature EPUB pagination, CFI locators, column layout, custom CSS injection, vertical writing mode, highlight rendering. JavaScript stays enabled for the *renderer shell*; expose a setting to disable book-supplied JS. Serve book resources through a local `WebViewAssetLoader` (no HTTP server, no network permission needed).

Keep the interface honest enough that a native **Readium Kotlin Toolkit** engine could be dropped in later.

**Formats:** open EPUB natively. **TXT, MOBI, AZW3, FB2, PDF** go through `:format:convert` → EPUB on import (background WorkManager job, progress surfaced in the import sheet). TXT conversion uses the **chapter-split rule engine**: an ordered list of named regexes (built-in presets: mixed-language default, Chinese `第X章`, English `Chapter N`, English `Volume/Book`) with a user rule editor and a sample tester. Detect encoding (UTF-8/GBK/Big5/UTF-16) before splitting.

---

## 4. Features

### 4.1 Library
- Import via SAF picker (multi-select), **share-target intent** (`ACTION_SEND` / `ACTION_VIEW` for the ebook MIME types), and drag-and-drop on large screens.
- On import: copy into the library root, compute **SHA-256/MD5 hash → skip duplicates** (report them), extract metadata + cover, estimate word count, insert.
- Grid with adjustable cover width (columns derived from a width slider), uniform vs original aspect ratio, generated covers (title/author typeset over a color hashed from the title — deterministic).
- Drag-to-reorder; drag one book onto another to create a **group**; nested groups; two folder render styles.
- Tags with colors; filter chips (All / Reading / Finished / Not started / by tag); sort by title, author, last-read, progress, import date × asc/desc.
- Per-book bottom sheet: read, details, edit metadata, **replace source file** (keeps annotations, warns on format change), share file, delete (soft delete + purge job).
- Long-press multi-select for bulk tag/group/delete.

### 4.2 Reading
- Chrome-less by default; tap the center zone to reveal top bar + bottom action rail.
- **Tap-zone engine:** a 3×3 grid, each cell mapped to `PREV | NEXT | MENU | NONE`; a "simple" mode (left/right/center) and a "custom" editor with a live diagram; swap-sides toggle; volume-key page turn; keyboard shortcuts (arrows/space/PgUp/PgDn) for tablets and e-ink devices with hardware keys.
- Page-turn animation: none / slide / scroll (continuous) / curl. **`none` is forced when e-ink mode is on.**
- Header/footer info strip: each slot independently configurable — none / chapter title / chapter progress / book progress / battery / clock / battery+clock; alignment left/center/right.
- Reading history stack (back/forward across jumps), jump-to-percentage, chapter pager, "N pages left in chapter".
- Bookmarks (add here / add at position, list, jump, delete).
- Full-screen mode, keep-awake with a timeout, auto-theme-follows-system, minute clock overlay.
- Footnote popups, in-book image viewer (zoom, save to gallery), external-link confirmation dialog.
- **Reading-time tracking:** accumulate foreground reading seconds per book per day, pause on background/idle, write `ReadingSession` rows; this is the sole input to analytics.

### 4.3 Typography & theming of the text
Every one of these is a persisted, live-updating setting: font family (system fonts + **user-imported .ttf/.otf** parsed for family name), font size, font weight, heading font size, line height, letter spacing, word spacing, paragraph spacing, first-line indent, text alignment (auto/left/center/right/justify), side/top/bottom margins, column count (auto/1/2) with a column-width threshold, writing mode (horizontal / vertical), **use-publisher-styles** toggle, **custom CSS editor** (with brace/semicolon validation + restore-default), code-highlight theme, **bionic reading** toggle, Chinese conversion (off/simplified/traditional).
Reading themes: built-in set + user-created (background color, text color, optional background image with fit/blur/opacity and a separate night image). Auto-switch day/night themes.

### 4.4 Annotations
- Selection menu: **highlight, underline** (5 colors, from a registry), write a note, copy, **look up word**, translate (offline only — see §4.6), search in book, share as excerpt, read aloud from here, delete.
- "Auto-mark selection" option (selecting immediately creates a highlight in the last-used color/type).
- Notes are attached to a locator and survive reflow; re-render on every style change.
- Global Notes screen: all annotations across books, grouped by book (sticky headers) or date; filter by book/color/type/has-note; sort by time or position; swipe to edit/delete; tap to open the book at that spot.
- **Export** per-book or global to **Markdown / CSV / TXT**, with a "merge by chapter" option; save via SAF or share.
- **Excerpt share cards**: render the quote to a bitmap with 4 templates, chosen font, background color/image, title+author footer; save to gallery or share.

### 4.5 Search
- Global search over books (title/author/description) and annotations via **Room FTS**, plus in-book full-text search through the engine.
- Results segmented Books | Notes | This book, with highlighted match spans and chapter context. Recent queries persisted.

### 4.6 Dictionary & word lookup (offline)
- `:dictionary:api` defines `LookupProvider { suspend fun lookup(word: String, lang: Locale): List<Definition> }`, registered in a provider registry.
- Ship two providers:
  1. **StarDict/DICT importer** — user imports a `.ifo/.idx/.dict(.dz)` bundle from storage; index it into Room for fast prefix + exact lookup.
  2. **System fallback** — `Intent.ACTION_DEFINE` / `ACTION_PROCESS_TEXT` handoff when no local dictionary is installed.
- Lookup UI: popup by default, full-screen option; headword, phonetics, senses, examples; "add to notes" action; graceful "no dictionary installed → import one" state.
- Also support a simple offline **word-frequency/vocabulary list**: words looked up get counted, surfaced in statistics ("words you looked up most").
- *(No online translation. If a translation feature is wanted, it must be a locally-installed dictionary or nothing.)*

### 4.7 Read aloud (TTS)
Android system TTS only (offline voices). Engine/voice picker, rate, pitch, volume, sleep timer, sentence-level highlight synced with playback, media-session controls + notification, prev/next sentence and section, mix-with-other-audio toggle, resume from last position.

### 4.8 Statistics
- Tile registry: `StatTile { key, @StringRes titleRes, @StringRes descriptionRes, defaultSpan, content: @Composable, detail: @Composable }`. Ship these tiles: total time, library totals, period summary, books read, reading days, notes total, **reading streak (current + best)**, **random highlight resurfacer**, reading-duration trend 7d, trend 30d, completion progress, top book, continue reading.
- Dashboard is a **user-reorderable, add/remove grid**; order persisted as a list of tile keys.
- Year **heatmap calendar**, week/month/year/all-time bar & line charts, per-book breakdown, swipe-to-delete a day's record.
- Tile → detail screen with the full chart + a data table.
- All aggregation happens in SQL (grouped queries), not in Kotlin loops over the whole table.

### 4.9 Storage, backup, maintenance
- User-selectable storage root with a **migration job** (progress UI, resumable, verify-then-delete).
- Size breakdown per category; clear cache; log viewer with level filter + export.
- **Backup/restore**: zip of database + books + covers + fonts + settings JSON to a user-chosen location; restore with a confirm dialog and a version check. This is the *only* "sync" story — file in, file out.
- Duplicate/hash management screen: books missing hashes, "calculate missing" batch job.

### 4.10 Onboarding, hints, changelog
- 3–4 page onboarding (welcome / appearance + **e-ink toggle** / import a book / done), skippable, re-runnable from settings.
- Dismissible **hint banners** keyed by a `HintKey` enum with a "show all hints again" reset.
- Changelog screen shown once after an update.

---

## 5. Settings architecture (get this right first — it's the spine)

```kotlin
sealed interface Setting<T> {
  val key: Preferences.Key<*>
  val default: T
  @get:StringRes val titleRes: Int
  @get:StringRes val subtitleRes: Int?
  val group: SettingsGroup
  val visibleWhen: (SettingsSnapshot) -> Boolean   // e.g. hide animations when e-ink is on
}
class BoolSetting(...) : Setting<Boolean>
class EnumSetting<E : Enum<E>>(... val entries: List<E>, val labelOf: (E) -> Int) : Setting<E>
class FloatSetting(... val range: ClosedFloatingPointRange<Float>, val step: Float) : Setting<Float>
class ColorSetting(...) : Setting<Int>
class TextSetting(...) : Setting<String>
```
- A single `SettingsRegistry` object holds every declaration, grouped. The settings **UI is generated from the registry** — one composable per `Setting` subtype. Adding a setting means adding one line; no new screen code.
- `SettingsRepository` exposes `Flow<T>` per setting and one `SettingsSnapshot` flow for the reader.
- Export/import settings to JSON falls out of the registry for free.
- Reset-to-default, per-setting and global, falls out for free.

---

## 6. Theming & E-Ink

```kotlin
enum class DisplayProfile { STANDARD, E_INK }
val LocalDisplayProfile = staticCompositionLocalOf { DisplayProfile.STANDARD }
```

`AppTheme` composes: seed color (or dynamic color on Android 12+) → `ColorScheme` → intensity/softer-dark/true-black transforms → **if `E_INK`, replace with the monochrome scheme**. Then:

- **Motion:** a `MotionTokens` object supplied via CompositionLocal. In `E_INK`, every duration collapses to `Duration.ZERO` and every spring becomes a snap — *not* "fast", zero, because a fast animation still ghosts. Also honor the system reduce-motion setting and the app's motion setting (full/reduced/off).
- **Ripples:** custom `RippleConfiguration` → no indication in e-ink mode.
- **Elevation → surface tiers** always; in e-ink, add 1px outlines instead.
- **Skeletons:** e-ink swaps shimmer for a static placeholder.
- **Charts:** e-ink variants use stroke + pattern fills; drive this from the chart wrapper, not from each call site.
- Touch targets and stroke widths come from `Sizes`/`Strokes`, which read the display profile.
- Detect likely e-ink hardware on first run (`Build.MANUFACTURER` in a known list: onyx, boox, tolino, kobo, bigme, hisense, meebook, dasung, remarkable, xiaomi-inkpalm…) and **suggest** the mode; never force it.
- Optional: on Onyx devices, expose the vendor refresh-mode API behind an interface with a no-op default implementation, so the app still builds and runs on stock Android.

Also implement `MaterialExpressiveTheme` properly: expressive shape scale, `MotionScheme.expressive()`, emphasized type roles, `ButtonGroup` for the reader action rail, `FloatingActionButtonMenu` for library import actions, wavy `LinearProgressIndicator` for long jobs (flat in e-ink mode).

---

## 7. Localization & accessibility

- English source `strings.xml` from day one, plus a translation-ready structure (no string concatenation — use placeholders and plurals).
- RTL layout support; vertical writing mode in the reader.
- Content descriptions on every icon-only control; TalkBack pass on each screen; the reader exposes chapter text to the accessibility tree.
- Respect system font scale outside the reader; the reader has its own scale.
- Minimum contrast checked in the e-ink and softer-dark variants by a test.

---

## 8. Build order

1. Scaffold: modules, convention plugins, tokens, theme (incl. e-ink profile), navigation shell with 4 tabs, lint rules from §0.
2. Data layer: Room schema + migrations + DAOs + FTS, storage roots, settings registry + a couple of real settings wired to the UI.
3. Library: import (EPUB only), covers, grid, detail, delete.
4. Reader: engine-api + engine-web with foliate-js, open/paginate/persist locator, tap zones, style panel, TOC.
5. Annotations: selection menu, highlight/underline/note, rendering, notes list, export.
6. Reading-time tracking → statistics tiles + heatmap + charts.
7. Format conversion (TXT first, then MOBI/AZW3/FB2/PDF), chapter-split rule engine + editor.
8. TTS, dictionary providers, global search.
9. Settings completion, backup/restore, storage migration, onboarding, hints, changelog.
10. Screenshot tests (standard + e-ink), accessibility pass, R8 config, release build.

At every milestone: `./gradlew build test lint` green, and a one-paragraph note in `docs/DECISIONS.md` for anything you chose that a reader of the code would otherwise wonder about.

---

## 9. Explicitly out of scope

WebDAV/cloud sync, accounts, AI chat/summaries, online translation, in-app purchases, analytics SDKs, ads, crash reporters, any runtime network call. The app must build and function with **zero `<uses-permission>` for INTERNET**.
