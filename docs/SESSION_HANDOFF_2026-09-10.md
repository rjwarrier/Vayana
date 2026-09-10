# Session handoff — 2026-09-10

Uncommitted work on `main` (last commit `ab156eb feat: replace reader sync toasts with a status dot`).
`./gradlew :app:assembleDebug` passes; the debug build is installed on the user's Pixel 9 Pro XL.
`GoodreadsParsingTest` (8 tests) passes. Nothing below was committed, and most of it was **not exercised
on-device** beyond the user's own spot checks — see "Unverified" at the end.

Database is now **v15** (see `docs/DATABASE_CHANGELOG.md`). The user's phone has already migrated.

---

## 1. Reader

**Files:** `feature/reader/.../ReaderScreen.kt`, `reader/engine-api/.../Locator.kt`,
`reader/engine-web/.../FoliateBookEngine.kt`, `reader/engine-web/src/main/assets/bridge.js`

- **Contents panel highlights the current chapter** (tinted row, bold; inverted on E-Ink) and opens scrolled to it.
- **Contents panel shows each chapter's start page** in the same estimated numbering as the "page X of Y" footer.
  - `bridge.js` `bookPageStats()` now also returns `tocPages` (TOC href → start page) using the same
    bytes-per-page estimate as `currentPage`; TOC hrefs resolve to sections once per book (`tocSectionIndexes()`,
    reset on `open`). Entries sharing one section (anchors) share its start page.
  - The `relocate` event now carries `tocHref` (current TOC item) and `tocPages`.
  - `Locator.href` is now populated (was always null) with the current TOC entry href; new `Locator.tocPages`.
    Side effect: bookmarks now get a `chapterHref`.
- **Landscape header:** header chips cap their top gap at `Spacing.sm` in landscape (the configured gap is tuned
  for a portrait cutout); all header/footer chips add horizontal `safeDrawing` insets so they line up with the text.

## 2. Library grid / "mark as finished"

**Files:** `feature/library/.../LibraryScreen.kt`, `LibraryViewModel.kt`

- Tick overlay (`BookFinishedTick`) on every grid cover and the "continue reading" hero card. Unfinished: tappable
  outline → confirm dialog → `markFinished(bookId, announce = false)` + snackbar. Finished: filled badge.
- `LibraryViewModel.markFinished` gained `announce` (the detail screen still posts its snackbar).
- No "mark as unread" exists (user was told; not requested).

## 3. Book detail screen

- **Cover picker** uses the Android photo picker (`PickVisualMedia.ImageOnly`) instead of the document picker.
- **New top layout:** cover (`Sizes.coverWidthDetail` = 120dp) on the left with a subtle "Edit cover" text button
  under it; title/author/series/Goodreads line on the right; rating, tags, format full-width below.
- **Edit cover dialog** (`EditCoverDialog`): live preview, Your cover / Goodreads switch (only when both exist),
  Remove and Change. Replaced the old `CoverActionButtons` row and inline switch.
- **Reset reading stats** — see §7.

## 4. Share cards

**Files:** `core/designsystem/.../sharecard/ShareCard.kt`, `core/common/.../ShareImage.kt`,
`core/designsystem/.../tokens/{Sizes,Typography}.kt`, `LibraryScreen.kt`

- **Export is a full-bleed square** (cards no longer clip themselves); only the dialog preview is rounded
  (16dp, was `Radii.appIconShape` 22%). Transparent PNG corners used to render black in share targets.
- Book card wordmark has no book glyph (`ShareCardWordmark(showGlyph = false)`); quote card keeps it.
- **Live-preview options UI:** `ShareCardDialog(options = { ... }, title = ...)` renders options inline under a
  pinned preview (replaced the separate `shareImageOptionsDialog` popup). M3: segmented buttons for Layout and
  Theme, `FilterChip`s (FlowRow) for Include; chips that a layout can't draw are hidden; Rating/Tags chips hidden
  when the book has none. Tags and Imported date are **mutually exclusive** (default: tags on, date off).
- **Layouts** (`BookShareCardLayout`): `CLASSIC` (cover left, stats full-width underneath), `SPOTLIGHT` (small centred
  cover, stats and tags folded into one line each), `MINIMAL` (large title + progress bar, no cover), `BACKDROP`
  (MINIMAL over the cover stretched full-bleed at `BackdropCoverAlpha` = 0.3). Content is pre-filtered into
  `BookShareContent` so each layout only decides placement. The cover slot is passed `Modifier.fillMaxSize()`.
- **Export pipeline:** a second `GraphicsLayer` is recorded directly at `Sizes.shareCardExportPx` (1600) instead of
  upscaling a screen capture; hardware bitmaps are copied before encoding; `shareBitmap` is now `suspend`, encodes
  JPEG q95 (`ShareImageFormat`) on `Dispatchers.IO`, and prunes cached share images older than an hour.

## 5. Goodreads import (biggest change)

**Files:** `feature/library/.../GoodreadsMetadataFetcher.kt` (new), `GoodreadsBrowser.kt` (new),
`LibraryViewModel.kt`, `LibraryScreen.kt`, `core/database/...` (see §6),
`feature/library/src/test/.../GoodreadsParsingTest.kt` (new; `testImplementation(kotlin("test"))` added)

**Flow:** book detail ⋮ → "Import from Goodreads" → paste a link → Import. No review step (user's choice):
everything the page has is applied, then the dialog closes and a snackbar reports the result.

- **Applied:** series + number (Goodreads' `#` stripped), first 5 genres merged into tags, description,
  high-res cover, original publication year, Goodreads average rating + count, and quotes.
  Series/tags/description go through `updateMetadata` (sync like manual edits); rating/year/URL and the covers
  are **local-only** columns.
- **Covers:** `coverPath` is still "the cover in use". `customCoverPath`/`goodreadsCoverPath` hold the two
  switchable alternates. Import keeps whatever was showing as "your cover". Change-cover replaces only the custom
  one; Remove falls back to the other. `withAbsolutePaths()` drops alternates whose file is gone.
- **Quotes → popular highlights:** exactly the existing pasted-quote shape (`UNDERLINE`, colorKey `popular`,
  `quote:` locator, "N highlights" note; now shared as `ParsedQuote.toPopularHighlight`). Deduped against the
  book's existing annotations by `quoteMatchKey` (letters+digits, lower-cased), so re-import is safe.
- **Fetching** (`GoodreadsMetadataFetcher`): only `https://www.goodreads.com/book/show/<id>` and
  `/work/quotes/<workId>?page=N` are requested (input reduced to a numeric id; anchored regex rejects look-alike
  hosts; redirects off-host refused). Book data comes from the page's `__NEXT_DATA__` → `apolloState`; the quotes
  URL is derived from the Work `legacyId` found there. Quote pages are the **mobile** layout (phone UA), read until
  an empty page, max 10. One retry on timeout/429/5xx. Cover must be on Goodreads/Amazon image hosts and decode as
  an image. Cover + quotes download in parallel. HTML decoding is pure Kotlin (testable).
- **Bot blocking — important:** Goodreads' AWS WAF returns **HTTP 202 + a JS challenge page** to non-browser
  clients once a network looks automated (it started blocking the user's network during this session). The
  fetcher detects this (`GoodreadsFetchError.BLOCKED`, never retried) and the dialog says so.
  **Deliberately not built:** anything that solves/bypasses the challenge automatically (e.g. a hidden WebView).
- **In-app browser fallback** (`GoodreadsBrowserDialog`): "Open Goodreads" in the import dialog opens a visible,
  user-driven WebView (the pasted book, else a Goodreads search for title+author). Top-level navigation is kept
  on goodreads.com; no JS interface is exposed (`evaluateJavascript` only). "Import this book" reads
  `__NEXT_DATA__` from the page the user is on, then loads the quote pages in the same visible view (polling past
  a challenge page, 30s timeout per page) and hands off to `importFromGoodreadsCapture`. Quote regexes accept both
  `class='…'` (served) and `class="…"` (serialized DOM).
- Scraping Goodreads is against their ToS; the user was told. Their internal page structure can change at any time
  — every field is optional so a missing one drops out rather than failing the import.

## 6. Database v15 + sync-safety

**Files:** `BookEntity`, `Book`, `BookDao`, `BookRepository(+Impl)`, `Migrations.kt` (`MIGRATION_14_15`),
`VayanaDatabase.kt`, schema `15.json`

- New nullable `books` columns: `goodreadsUrl`, `goodreadsRating`, `goodreadsRatingsCount`,
  `originalPublicationYear`, `customCoverPath`, `goodreadsCoverPath`. **Local-only** — not in the cloud record.
- `BookRepositoryImpl.mergeCloudBookLocked`: the cloud-only rewrite path (`toCloudOnlyEntity`) now carries these
  over via `withLocalOnlyFieldsFrom(existing)` (it would otherwise null them on every sync).
- New repo calls `updateGoodreadsInfo` and `updateCoverAlternates` deliberately don't bump `updatedAt`.

## 7. Reset reading stats

**Files:** `BookDao.resetReadingStats`, `ReadingSessionDao` (+`syncIdsForBook`, `deleteForBook`, `deleteBySyncId`,
`deleteForBookStartedBefore`), `BookRepository(+Impl).resetReadingStats/applyReadingStatsReset`,
`ReadingSessionRepository(+Impl).deleteBySyncId`, `TombstoneEntityType` (+`READING_SESSION`,
`READING_PROGRESS_RESET`), `LibraryViewModel.applyCloudTombstone`, `LibraryScreen` (⋮ menu + confirm)

- Clears progress, locator, started/finished, total seconds, last-read and all sessions; keeps annotations, rating,
  metadata. No schema change — it rides the existing `tombstones` table:
  - one `READING_SESSION` tombstone per deleted session → `mergeCloudSession` skips tombstoned ids;
  - one `READING_PROGRESS_RESET` tombstone, syncId `reset:<bookSyncId>`, `deletedAt` = reset time →
    `applySyncedReadingProgress` treats it as local progress at that moment (older remote progress loses), and the
    cloud-only rewrite keeps reset values (`keepingResetProgress`).
- Tombstones sync, and `applyCloudTombstone` now handles both types, so other devices apply the reset too — unless
  they've read the book *after* the reset. Older app versions store unknown tombstone types and ignore them.

---

## Unverified / worth checking first

1. **Reset reading stats across a real GitHub sync** — only reasoned through; confirm stats stay at zero and
   propagate to a second device.
2. **In-app Goodreads browser end to end** on a network that's currently challenged (challenge polling, quote paging).
3. **Share image export via the 1600px recorded layer** — if an exported image is blank/cropped, fall back to the
   old screen capture + scale.
4. **Share card text overflow** with every option on and long titles (Spotlight/Minimal are tightly sized).
5. **Landscape header** behaviour on a device with a side cutout.
6. The TOC-page estimate for books whose TOC entries are anchors within a single file (all share one page).

## Housekeeping left

- `docs/HANDOFF.md` is badly stale (predates annotations, sync, stats, git) — worth a rewrite.
- Nothing is committed; the user hasn't asked for a commit. Suggested split: reader TOC/landscape, library
  tick + detail layout, share cards, Goodreads + DB v15, reset stats.
- A few now-unused strings may remain (e.g. `share_card_image_options_title`); `ElevatedButton` import in
  `LibraryScreen.kt` may be unused.
