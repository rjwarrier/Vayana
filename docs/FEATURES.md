# Features: onboarding, search, Read Next, deleting books, opening files, read aloud, highlight review

How these features behave, where their code lives, and the rules that aren't obvious from
reading one file. Keep this in step with the code when any of them changes.

---

## Reading tools

- **Smart shelves:** Library overflow → Smart shelves, or the button in Shelves. Save combined search,
  author, exact tag, reading status, dormant-day and notes/highlights rules. Membership follows book
  progress, metadata and annotations automatically; dormant rules refresh while the dialog is open.
  Community quotes and empty bookmarks do not count as personal notes.
- **Bulk actions:** Library overflow → Select books. Select from the current filtered library, then
  add to a shelf, add comma-separated tags, or download cloud copies. Tags preserve existing values;
  downloads run sequentially and results distinguish completed, failed and skipped books.
- **Reading presets:** Reader → Style → Reading presets. Save the effective current style under a name.
  Typography follows the existing per-book custom-style toggle; theme and text options remain global.
  Imported fonts fall back to the saved built-in family when unavailable on another device.
- **Finish-by plans:** Book details → Finish by. Choose a date to calculate daily pages, measured
  reading minutes, or a progress percentage when no pace is available. Today counts as a reading day.
- **Reading journal:** Reader → Notes → Reading journal. Entries retain the current position as normal
  notes, can be edited/exported in Notes, and appear in the return recap after an extended absence.
- **Markdown notebooks:** Notes toolbar → Export Markdown notebooks. Select books and share one ZIP
  with a Markdown file per book, including title/author/tag frontmatter, chapters, quotes and notes.

Smart shelves, presets and finish dates are registered structured DataStore settings included in
settings backups and portable snapshot exports. Normal library sync keeps these settings local.
Finish dates use book sync identities. Journals use ordinary synced
annotation records. These tools do not require another database schema change.

## Onboarding

**Code:** `feature/onboarding/` (`OnboardingScreen.kt`, `OnboardingViewModel.kt`), `app/.../VayanaAppRoot.kt`,
`app/.../AppSettingsViewModel.kt`.

- Five pages: Welcome → Appearance → Import → Sync → Done, with Skip / Next / Finish and page dots.
- The Appearance page writes `SettingsRegistry.DisplayProfile` (Standard or E-Ink) straight away.
- Finishing or skipping sets the DataStore flag `onboarding.completed`.
- **Gate:** `VayanaAppRoot` shows `OnboardingRoute` instead of the app while the flag is false.
- **No first-frame flash:** `AppSettingsViewModel.settings` is `null` until DataStore's first read, and the root
  draws nothing until then. Rendering default settings first would briefly show onboarding and the wrong theme.
- **Existing installs skip it:** if the flag is false but the library already has books
  (`BookRepository.hasAnyBooks()`), the view model marks onboarding complete instead of showing it.

## Global search

**Code:** `feature/search/` (`SearchScreen.kt`, `SearchViewModel.kt`), `core/database/.../search/SearchText.kt`,
`core/database/.../entity/SearchFtsEntities.kt`, `BookDao.observeSearchIds`, `AnnotationDao.observeSearch`,
`core/filesystem/.../ResolvedBooks.kt`. Opened from the search icon in the Library top bar (`SearchRoute`).

**Matching (SQLite FTS, DB v18):**
- `books_fts` indexes title, author, series, series number, tags and description; `annotations_fts` indexes
  highlighted text, note and chapter title (FTS4, `unicode61` tokenizer). `annotations_fts` is Room's
  external-content table with its sync triggers; `books_fts` is a standalone copy kept current by the triggers in
  `search/BookSearchIndex.kt`, which fire only when a book's searchable text changes (DB changelog v18–v19).
- The query is split into at most 8 lower-cased words; punctuation (including FTS syntax) is dropped.
  Combining marks count as letters, so Malayalam and other Indic words stay whole.
- Every word must appear somewhere in the row, each as the **start of a word**: `tolk` finds "Tolkien";
  `olkien` does not.
- Results: up to 30 books (most recently read first, then title) and up to 80 notes/highlights (newest first),
  excluding deleted rows and notes on deleted books.

**Screen behaviour:**
- Typing is debounced by 150 ms; clearing the box applies immediately. While a newer query is pending, the previous
  results stay on screen (`isSearching`) instead of flashing "No matches".
- Chips filter the results: All / Books / Notes, each showing its count.
- Matched word prefixes are shown bold in the primary colour in titles, authors, highlights and notes. Chips under
  each result name the fields that matched.
- Opening a note result opens the reader at its locator; notes on physical books open the book's detail page.
- **Recent searches:** the last 8 queries (newest first, no case-insensitive repeats) are stored locally in DataStore
  (`search.recent`, not synced or backed up). A query is recorded when the user presses the keyboard's search
  action or opens one of its results. They are listed, with Clear, when the search box is empty.

**Shared book paths:** Search and Library both use `ResolvedBooks.all`, one app-wide flow of books with absolute
cover and file paths, instead of each resolving paths on every library change.

## Read Next

**Code:** `feature/library/ReadNext.kt` (shelf, suggestion logic, series-break warning and dialog),
`BookDetailScreen.kt` (queue button, bump snackbar), `ShelvesScreen.kt` (full queue),
`BookRepositoryImpl.setReadNext` / `mergeCloudBook`, `BookDao` read-next queries.

**Queue:**
- A book is queued from its detail page ("Read next" button; hidden for physical books) and removed there, from
  the Library shelf, or from Shelves.
- The queue holds **2 books** (`MaxReadNextQueueBooks`). Queueing past that drops the books added longest ago;
  `setReadNext` returns them, and Book Detail shows a snackbar naming the book that left. The cap lives only in
  `BookRepositoryImpl` (`MaxReadNextQueueBooks`); Library and Shelves take the queue from the books in memory.
- Library shows the queue as a shelf above the grid/list; "View all" opens Shelves.

**Suggestions** (shown on the shelf only while the queue is empty) come from the pure function
`suggestedReadNext(books, currentBook)`:
1. The current book must be started and unfinished, and be part of a series.
2. Suggest the lowest-numbered unread, non-physical book in that series numbered after the current one
   (series names compare case- and space-insensitively; numbers like `2.5` sort correctly).
3. Otherwise suggest the same author's next unread book, preferring other series.

**Series-break warning:** queueing a book outside the series you're reading, while that series has a next unread
book, opens "Break series order?". "Follow current series" queues the next series book instead; "Queue anyway"
queues the chosen one.

**Sync:**
- `books.readNextAddedAt` (non-null = queued, ordering) and `books.readNextUpdatedAt` (when it was last queued or
  removed) are part of the synced book record (DB v17).
- Merges compare `readNextUpdatedAt` — **not** the book's `updatedAt`, which reading progress also bumps — and
  the newer change wins; ties keep the local state. Records from builds without the field fall back to
  `readNextAddedAt`.
- When a merge applies a queued record, the queue is trimmed back to 2 without touching `updatedAt`, since that isn't
  a user edit. (A record that isn't queued can't grow the queue, so it skips the check.)

**Tests:** `feature/library/.../ReadNextTest.kt`, `core/database/.../repository/ReadNextStateTest.kt`.

## Offline books (physical, audiobooks, other ebooks)

**Code:** `feature/library/OfflineBooksScreen.kt`, `AddOfflineBookDialog.kt`, `ReadingStatsCard.kt`
(`OfflineReadingStatsCard`), `BookShareStats.kt`, `ReadingDatePickerDialog.kt` (shared with Book details),
`OfflinePages` (`core/database/model/Book.kt`), `BookFormat.isOffline` (`core/database/model/Book.kt`),
`BookRepositoryImpl.insertOfflineBook` / `updateOfflineFormat` / `mergeCloudBook`.

- Books read outside the app are ordinary `books` rows with format `PHYSICAL`, `AUDIOBOOK` or `OTHER_EBOOK` (an ebook
  read in another app or device), `filePath = ""` and a unique `fileHash` (`<format>:<uuid>`).
- Library menu (three dots) → **Offline books** lists them; they are kept off the Books list, series folders and
  Read Next, but appear in Search, Shelves, Notes and Statistics.
- Adding one asks for title, author, type, optional start/finish dates, and "Fetch details from Goodreads" (on by
  default), which opens the new book's details straight into the Goodreads picker (`BookDetailRoute.openGoodreads`).
- The finish date drives progress (`BookRepositoryImpl.updateReadingDates` sets `readingPercent` to 1, or back to 0 when the finish is cleared), so
  finished offline books count towards the yearly goal like any other.
- **Pages:** total pages live in the existing `pageEstimate` column (`Book.pageCount`); the current page is
  `readingPercent × pageCount`, so page progress needs no schema change and syncs with the book. Reaching the last
  page finishes the book and going back un-finishes it (`BookRepositoryImpl.updateOfflinePages`).
- **Physical reading timer:** Book details offers M3 Expressive connected Start, Pause/Resume and Stop controls.
  Start confirms the starting page; Stop opens a page checkpoint dialog. Saving atomically records active time,
  starting/ending pages and progress, including completion at the last page. One durable timer runs at a time and
  survives screen changes, phone locking and app process recreation; paused time is excluded. Dismissing the Stop
  dialog keeps the stopped session pending, while Discard explicitly drops it. Forward page movement in timed logs
  provides pages/hour and estimated reading time remaining when total pages is known. Existing untimed progress
  does not contribute to this pace. Saved time contributes to book time and Statistics, and optional page checkpoints
  travel through backup and sync. After a phone reboot a running timer is restored paused at its last saved duration.
- **Currently reading physical books:** Book details offers **Mark as reading** and **Read today** without adding
  guessed reading time. Starting/resuming the timer and changing the current page update last-read status. Home
  shows the three most recently read unfinished physical books in both list and grid views, including a library
  with no digital books. An active or pending timer is pinned first with its clock and status; tapping opens the
  book, and **View all** opens books read outside Vayana. Searching/filtering unrelated states hides this shelf.
- **Physical session insights:** The timer shows average logged session duration and a scrollable complete session
  history with local start times, active durations and page checkpoints. Pages/hour and remaining-time estimates
  use the five most recent valid forward-reading sessions, displaying the measured page and session counts;
  untimed progress, stationary pages and backwards movement do not supply pace. History uses lazy rows, and the
  active clock alone updates each second while the screen is visible.
  The timer loads a count/time summary and only three recent checkpoints/five pace samples; the full history flow
  is collected only while the history dialog is open. Stop validates both starting and ending pages against totals.
- **Manual physical sessions:** Reading timer → **Add session manually** records a date, start time, duration,
  starting/current pages and optional total pages with the same logs, pace, Statistics and sync support. Future
  sessions, zero durations and invalid pages are rejected. **Update current page** is optional and defaults off
  when choosing a past date, so historical entries preserve current progress and completion. Last-read dates never
  move backwards, and retries reuse one session ID to avoid double-counting time.
- Physical books can be marked **Owned** or **Borrowed**. A borrowed book may include a return date; ownership and
  return date are editable from Book details and travel with the synced book metadata. Sunday return dates move to
  Saturday. While pages remain, Book details shows the available reading days and the rounded-up pages-per-day target
  needed to finish by the day before return.
- Book details shows the wavy progress bar (once pages are known) and page, start, finish, days and type tiles, each
  editable (tapping type cycles Physical → Audiobook → Other ebook); the share card shows format and days in place of progress and
  reading time.
- **Sync:** the full GitHub sync carries them as book records without a `fileAsset` (`parsePortableCloudBooks`
  accepts that only for offline formats) and creates them `LOCAL` on other devices. Offline books have no reader
  position, so their dates and type travel with the book metadata (LWW on `updatedAt`), not the progress-only sync.
  Older app versions skip them.

**Tests:** `feature/library/.../OfflineBookRepositoryTest.kt`, `core/backup/.../PortableReadingProgressJsonTest.kt`.

### Home Library mirror (read-only)

**Code:** `core/homelibrary` (`HomeLibrarySyncEngine`, `HomeLibrarySource`, `RoomHomeLibraryStore`,
`HomeLibrarySync`, `HomeLibraryCovers`, `HomeLibraryLauncher`), `feature/settings/HomeLibraryCard.kt`,
`feature/library/HomeLibraryDetailsSection.kt`. DB v30 (`books.syncUuid`, `source`, `sourceUpdatedAt`,
`sourceHasCover`, `sourceMetadata`).

Home Library (`com.mj.homelibrary`, same developer and signing key) publishes its catalog through a read-only
ContentProvider (`content://com.mj.homelibrary.catalog`). When it is installed on the same phone, Vayana mirrors it into
Offline books. Home Library owns the data; Vayana never writes to it.

- **Identity:** a mirrored row is matched on Home Library's `sync_uuid` only (unique index), never on title or ISBN. Its
  title, authors, series, tags, rating, page count and shelf location come from Home Library and are not editable in
  Vayana (no edit, no delete; the book goes when Home Library removes it). Reading dates, pages read and Read Next stay
  Vayana's own and are kept across updates. Everything else Home Library sends (publisher, ISBNs, room/bookcase/shelf,
  read status, ...) lives in `books.sourceMetadata` and shows on Book details with a "View in Home Library" action.
- **Native titles:** Offline books and Book details prefer the original-script title when supplied, with the catalog
  title underneath. Text shares include both. Image shares default to the native title and offer Original script /
  Catalog title choices alongside the editable title field; these never change Home Library's catalog.
- **Browse your shelves:** Offline books gains search across both titles, author, series, genre, ISBN and shelf
  location when Home Library books are present. Search normalizes Unicode to NFC. **Read from my shelves** shows owned
  Home Library books not started in Vayana, with language and genre filters; shelf locations appear in list and grid
  views. Historical Home Library read status does not override Vayana's reading dates or progress.
- **Sync:** `/info` is read first (schema version, book count, max `updated_at`); if it matches the last sync nothing is
  queried. Otherwise `/books?updated_since=&limit=500` is paged into memory, applied in one Room transaction (creates,
  updates, tombstone deletes), and only then is the checkpoint (`lastSyncUpdatedAt`, kept in its own DataStore, never
  backed up) saved. An empty cursor means "no changes", never "delete everything". With no checkpoint, or none newer than
  90 days (the tombstone retention), a full resync runs and mirrored books Home Library no longer lists are removed
  (only when `/info` answers, and not when `/info` promises books but none arrive). A schema newer than 1 is skipped and
  logged to diagnostics.
- **Triggers:** app start and foreground (`MainActivity.onStart`, throttled), a `ContentObserver` on `/books`
  (descendants, debounced 1 s) while the app runs, and a 6-hourly WorkManager fallback (no network needed). Settings →
  Library has the "Sync with Home Library" switch (default on) and a status card with "Last synced" and "Sync now".
- **Covers:** fetched after a sync commits, one at a time, from `/books/<uuid>/cover`, into the covers folder as
  `homelibrary-<uuid>-<updated_at>.<ext>`; a changed `updated_at` fetches a new file and removes the old one.
- **Not connected:** not installed, `SecurityException` or an unknown provider stops syncing quietly; Settings (and Offline
  books, once it holds mirrored books) says "Home Library not connected".
- **Kept out of GitHub sync:** mirrored rows are excluded from the snapshot (`BookDao.getAllForSync`) and from the full-sync
  change triggers, so they never recommend a full sync or reach other devices.

**Tests:** `core/homelibrary/.../HomeLibrarySyncEngineTest.kt` (fake provider), `RoomHomeLibraryStoreTest.kt` (real Room,
rollback), `HomeLibraryProviderTest.kt` (fake ContentProvider, ContentObserver), `feature/library/.../Migration29To30Test.kt`.

## Deleting books

**Code:** `feature/library/BookDeletionDialogs.kt`, `PermanentDeletionNotices.kt`, `LibraryViewModel.deleteBook` /
`deletePermanently`, `BookRepositoryImpl.purgeEverywhere` / `applyPurgeTombstone`, `core/filesystem/BookFileCleaner.kt`,
`core/sync/asset/CloudAssetDeletionProcessor.kt`. Design: `docs/PERMANENT_BOOK_DELETION_PLAN.md`.

**Two ways to delete** (Book Detail ⋮ → Delete…):
- **Move to Recently deleted** — soft delete (`isDeleted = 1` + a `book` tombstone). Other devices move it to their
  Recently deleted too; it can be restored.
- **Delete permanently everywhere** — also Recently deleted → "Delete forever". A confirmation lists what goes (file
  and covers, cloud copy, highlights and notes, reading history, shelf and Read Next entries) and needs an explicit
  "I understand" tick. Vocabulary words are kept, detached from the book.

**What a permanent delete does:**
1. One transaction: a `book` tombstone (older app versions soft-delete on it), a `book_purge` tombstone
   (`purge:<syncId>`, plus one for any alias sync id of the same file), queue the book and cover assets in
   `pending_cloud_deletions`, detach vocabulary cards, drop aliases, delete the row (highlights, notes, reading
   sessions and shelf links cascade). Runs in `NonCancellable` so closing the screen can't cut it short.
2. `BookFileCleaner` deletes the book file and covers, only inside the app's storage root.
3. A one-time snackbar on the Library or Recently deleted screen: deleted everywhere, or "cloud copy will be removed
   on the next sync" when assets were queued.
4. Next full sync: the snapshot with the tombstones is published first, then `CloudAssetDeletionProcessor` deletes
   up to 20 queued assets per sync (404 counts as done; failures stay queued; 401/403/429 stop the batch). Settings →
   sync health shows "Cloud files waiting to be deleted" while any remain.

**Other devices:** a synced `book_purge` purges the book and its files there too, even if that device changed or read
it later. Book merges skip any record with a purge tombstone, so nothing can bring it back; importing the same file
again creates a new book with a new sync id. Older encrypted copies remain in the GitHub repository's history.

**How deletions reach other devices (both kinds):**
- **Sent right away.** Deleting a book runs the lightweight sync immediately (`ReadingProgressOnlySyncer`, skipping
  its 20-second minimum interval). That sync — and the one the reader runs as you read — now also publishes `book` and
  `book_purge` tombstones (`isSyncedWithReadingProgress`), then deletes queued cloud files once they're published.
- **Applied automatically.** The reader's lightweight sync and the silent launch check apply book deletions
  (`TombstoneMergeScope.BOOK_DELETIONS`); other deletions — notes, shelves, vocabulary — still wait for a full sync.
  A full sync applies everything.
- **Reported.** Every applied book deletion is posted to `RemoteBookDeletionNotices`; the library shows
  "“Title” was deleted on another device" (or a count) once.
- **Deletion version (DB v21).** `books.deletionUpdatedAt` is set whenever a book is deleted or restored and travels in
  the book record. A synced delete applies unless the book was deleted or restored here *later*; reading doesn't count
  (it bumps `updatedAt`, which deletions no longer look at). A stale record never brings a deleted book back or
  duplicates it; a record restored after the delete clears the tombstone and restores the book.
- **Restores spread on the next full sync**, not the lightweight one: the lightweight push patches reading data and
  tombstones, not book records.

**Tests:** `feature/library/.../PermanentBookDeletionTest.kt` (Robolectric + Room),
`core/sync/.../CloudAssetDeletionProcessorTest.kt`, `GitHubContentsAssetStoreTest` (delete cases),
`core/filesystem/.../BookFileCleanerTest.kt`, `core/database/.../BookPurgeTombstoneIdTest.kt`.

---

## Open with Vayana

An EPUB opened or shared from another app (file manager, browser download, mail attachment, Calibre transfer) is
imported into the library like any picked file.

- `MainActivity` is `singleTask` and accepts `VIEW` (`application/epub+zip`, or `application/octet-stream` with a
  `.epub` path), `SEND` and `SEND_MULTIPLE` (`application/epub+zip`). Only `content://` URIs are used; `file://` cannot
  be read on modern Android.
- `Intent.incomingBookUris()` (feature/library `IncomingBookFiles.kt`) reads the URIs; the activity hands them to the
  singleton `IncomingBookFiles`, skipping a recreated activity's old intent (`savedInstanceState != null`).
- `VayanaAppRoot` navigates to Library while files are waiting; `LibraryViewModel` drains them into `importFiles`, so
  the usual import progress and summary appear. Nothing is lost while onboarding or the reader is on screen: files wait
  until a `LibraryViewModel` exists.
- A file whose display name has no extension is still accepted when its MIME type is `application/epub+zip`.

**Tests:** `feature/library/.../IncomingBookFilesTest.kt`.

## Read aloud: focus, media controls, start from selection

- **Natural pacing.** Paragraph, heading and scene boundaries request 250, 600 and 900 ms of additional
  silence. Terminal punctuation reduces that addition (especially ellipses), and pauses scale with reading
  speed. Ordinary sentences get no added silence. Resume and direct sentence skips do not
  repeat the pause. Very long utterances split at clauses or word boundaries and preserve highlight offsets;
  pathological oversized words split without breaking an emoji's UTF-16 pair.
- **Sentence context.** Once a voice reports word timings, speech groups up to three sentences and 700
  characters from the same paragraph. Questions, dialogue quotes, ellipses and other expressive punctuation
  remain intact. Word timings map to each original sentence, so highlighting, page turns, resume and sentence
  skips retain their positions. Voices without timings keep individual sentences. E-Ink follows sentence
  boundaries without refreshing the highlight for each word. No additional model or voice is needed.
- **Pronunciation corrections.** Read Aloud → Pronunciations for this book lets you add, edit and remove up to
  100 names or phrases and their spoken spelling. Matching is case-insensitive and respects whole words;
  longer phrases win. Corrections are local to this device, keyed to the book's stable identity. Editing a
  correction restarts the current utterance while retaining highlights on the original book text.
- **Interjections.** Elongated murmurs and sounds (`hmmm`, `mmm`, `uhhh`, `ahhh`, `ohhh`, `shhhh`) use
  conventional short forms (`hmm`, `mmm`, `uh`, `ah`, `oh`, `shh`). English voices additionally receive
  Android text hints for consonant-only sounds, to discourage abbreviation parsing. Engines may ignore
  these hints; sound delivery depends on the voice. Book pronunciation corrections take
  priority. Whole-word matching leaves measurement units such as `mm`, uppercase acronyms and ordinary
  words unchanged, and timed highlights continue to point at the original interjection.
- **Number hints.** English-language voices receive Android `TtsSpan` hints for currency, percentages,
  common measurements, valid ISO dates and times with clock context. These hints preserve text offsets.
  Ambiguous numeric dates, ratios and other languages use the engine's own parsing. Engines may ignore hints.
- **Roman numerals.** Clear chapter, part, volume, section, act, scene and similar labels use numeric
  speech (`Chapter IV` → `Chapter 4`). Standalone multi-letter uppercase numerals and Unicode Roman
  numeral characters are also converted. Only canonical values from 1 to 3999 qualify; pronoun "I",
  initials, ordinary prose acronyms and invalid forms remain unchanged. Book pronunciation corrections
  take priority, and highlights continue to select the original numeral.
- **Typography.** A dash with no spaces between words (`stopped—then`) is spoken with a comma pause, and SHOUTED words of four or more
  letters that read as words (`SILENCE`) are spoken in lower case, so engines don't spell them out. Initialisms without vowels,
  Roman-numeral lookalikes and numeric ranges are unchanged, and highlights still select the original text.
- **Reading text cleanup.** Marked footnote references and hidden text are omitted, while mathematical
  superscripts remain. Single EPUB source line breaks and line separators are joined before sentence
  splitting, preventing artificial pauses inside prose; blank lines and block boundaries remain.
  PDF printed line breaks are joined, word-wrap hyphens are repaired (common compound
  prefixes retain their hyphens), and soft hyphens are removed from speech. Explicit page headers/footers are
  skipped; inferred PDF running headers/footers must repeat on three distinct pages in the outer 5% margin.
  The first two occurrences remain audible. Margin page numbers are omitted. The displayed book is unchanged.

- **Abbreviations.** Speech preparation keeps common titles with names, numbered references with numbers,
  and spaced name initials together. Location initials such as "in S. Place" and "towards K. bridge"
  stay with their place words. Dotted acronyms and `a.m.`/`p.m.` keep real sentence endings;
  `e.g.`, `i.e.`, `etc.` and `vs.` are spoken as words. The displayed text is unchanged, and timed highlights
  map back to the original words even when spoken text expands. Ambiguous sentence endings and paragraph
  boundaries are preserved rather than suppressing every abbreviation period.

- **Audio focus** (`PlaybackFocus.kt`). Read aloud requests focus when it starts speaking and gives it up when paused
  or stopped. A call or navigation prompt (`LOST_TEMPORARILY`) pauses it and it resumes by itself when focus returns;
  other audio taking focus for good (`LOST`) and unplugged headphones (`BECOMING_NOISY`) pause it until the reader
  presses play. A user pause during a temporary loss is never undone. If focus is refused (a call in progress) it does
  not start. The engine speaks with `USAGE_MEDIA` / `CONTENT_TYPE_SPEECH`.
- **Media controls.** `ReadAloudForegroundService` owns a `MediaSession`: lock-screen and Bluetooth/headset play,
  pause, next and previous, and a notification with previous sentence / play-pause / next sentence. Commands reach
  the reader through `ReadAloudNotificationCommands` (`ReadAloudCommand` PLAY, PAUSE, NEXT, PREVIOUS, STOP).
  `ReadAloudPlayer.skip(±n)` moves by sentence; past a chapter's end it continues into the next chapter.
- **Queue window.** Each hand-over to the speech engine is a binder call, so the player feeds a window of
  eight sentence parts (`LookaheadUtterances`), extending by up to two to complete a group, and tops it up
  as playback progresses. Skips, speed, pitch and voice changes refill this bounded window, rather than
  resending the whole remaining chapter. Grouping reduces the number of speech calls.
- **Playback overhead.** Completed speech batches are released as playback advances, and late callbacks
  for them are ignored. Repeated timing callbacks for the same original word reuse its highlight; the queue
  is topped up only when the sentence changes. Pronunciation offset maps use exact-sized primitive arrays
  instead of temporary per-character boxed lists.
- **Speech engine.** The Read Aloud panel lists every text-to-speech engine installed on the phone
  (`TextToSpeech.getEngines`, visible thanks to the manifest's `TTS_SERVICE` query) and reads with the chosen one
  (`reader.read_aloud_engine`, blank = default). That is how a free neural engine such as SherpaTTS or HayaiTTS
  (Piper, Kokoro) gives more natural voices without Vayana embedding a model. Voice names belong to their engine, so
  changing the engine resets the voice; an engine that is no longer installed falls back to the default one, and
  word-by-word highlighting is relearned (engines that send no word ranges highlight the whole sentence).
- **Spoken-word mark.** `bridge.js markSpeech` draws the highlight straight onto the section's overlay from the Range it
  already holds (`SpeechMarkKey`), not through `view.addAnnotation`, which built and re-resolved a CFI per word.
- **From here.** The selection toolbar has a Read aloud button (hidden when audio features are off). It restarts read
  aloud at the sentence holding the selection: `BookEngine.startSpeech(fromCfi)` → `bridge.js startSpeech(requestId,
  fromCfi)` resolves the CFI and skips sentences that end before it.

**Tests:** `ReadAloudPlayerTest` (focus, skip, start-from-CFI).

Read aloud now rejects callbacks and delayed chapter loads from older playback or speech queues. Only one chapter transition can be pending; a restart gives queued utterances new callback IDs. Bridge requests release their pending entry on completion, timeout, or cancellation.

## Highlight review (spaced)

Statistics → the highlight card opens a review of highlights that are due, instead of a fixed set of five.

- Each highlight has a schedule in `highlight_reviews` (DB v23, keyed by the annotation's `syncId` since v24), reusing `VocabularySchedule` (SM-2 style) with three
  answers: See soon (again), Got it (good), Know it well (easy).
- `dueHighlights()` picks overdue highlights first (longest overdue first), then never-reviewed ones (oldest first),
  up to 10 per session. Only the reader's own highlights and notes count: no bookmarks, no Goodreads quotes.
- With nothing due the screen says so and offers today's fixed set (`dailyHighlights`) as practice; practice answers
  are not recorded.
- Schedules are **per device and not synced** (see `docs/DATABASE_CHANGELOG.md`, version 23).
- The repository selects at most ten due cards or five daily practice cards with bounded SQL queries. A review session keeps its chosen cards fixed while grades are recorded. Statistics uses a bounded due preview and refreshes the due cutoff when its screen is entered. The general Statistics summary still observes all annotations.

**Tests:** `HighlightReviewTest`, `feature/library/.../HighlightReviewRepositoryTest.kt`.

## Reader and Notes work bounds

Notes partitions highlights and computes community quote counts once per annotation revision, then reuses those counts for sorting. The WebView reader indexes community badges by loaded section and skips quote matching for a document whose annotation revision is unchanged. Page estimates and the contents page map are cached until layout samples change; relocation messages omit an unchanged contents map. Annotation mapping and JSON serialization run off the UI thread, while WebView application serializes updates and keeps only the newest waiting snapshot. Sync uploads encode one changed snapshot slice at a time, and concurrent readers of an immutable slice share its load.

## E-Ink mode

Everything here applies when Appearance → Display profile is E-Ink; the reader settings under Text also help other screens.

- **Page keys.** Hardware page buttons (`KEYCODE_PAGE_UP/DOWN`, `NAVIGATE_PREVIOUS/NEXT`) turn pages in the reader (first press only,
  no auto-repeat) and page the list on screen elsewhere (`MainActivity.dispatchKeyEvent` -> `EinkPageKeys`, registered by `PagedLazyColumn` / `PagedLazyVerticalGrid`).
  Mapping lives in `core/designsystem/.../PageKeys.kt`.
- **Paged lists.** Library, Notes, Search, Settings, Shelves, Recently deleted, Statistics and the diagnostics/learn lists show two corner buttons that move one
  screen (90% of the viewport) at a time. Other displays get the plain list.
- **Marks in black.** Highlights, underlines, the read-aloud mark and the community-count pills are black (`setInkMarks` in `bridge.js`); a tint comes out as a pale grey.
  Highlight colours become shapes: yellow shaded, green underlined, blue wavy, pink boxed (`InkStyleByColor`).
- **Read aloud** marks the whole sentence instead of each word (`ReadAloudPlayer.start(wordHighlight = false)`), page turn animation is forced off.
- **Refresh.** Besides every N pages (Appearance setting), the panel does a clean refresh when the reader menu closes and on entering a new chapter.
  The reader clock updates on page turns only, not on a timer.
- **Covers** are drawn greyscale with more contrast (`rememberCoverColorFilter`). Outline colours are solid (black borders, `#767676` dividers).
- **Text settings** (all screens): Bolder text (`-webkit-text-stroke`), Text alignment (book / justified / left), Hyphenation (book / on / off).
  Alignment and hyphenation are portable in backups; bolder text is per device.
- Not done: vendor refresh modes (Onyx SDK); needs the SDK dependency and a Boox device to test.

**Tests:** `PageKeyTest`, `ReadAloudPlayerTest` (sentence highlight), `reader/engine-web/tests` (ink marks, bionic idempotence).

## PDF books

- Import PDFs like EPUBs: file picker, folder scan, "Open with" and Share (`application/pdf`, or `.pdf` sent as a
  generic binary), and Replace source. Title and author come from the PDF's info dictionary (else the file name);
  the cover is the first page. Password-protected PDFs are reported as unsupported.
- The reader shows one page per screen, fitted to the screen, with page taps/keys, contents (the PDF outline, with
  page numbers), the progress slider, bookmarks, headers/footers, "Book finished" on the last page, and progress sync
  like any book. The last page counts as 100%.
- Zoom: pinch (up to 5x), or double-tap a spot with no word under it (2.5x / back to fit). Pan with one finger; side
  taps still turn pages, and the next page opens at its top-left at the same zoom. The page re-renders sharp at the
  new scale when the fingers lift.
- Themes: any reader theme other than white paper (dark, sepia, true black) repaints text and drawings in the theme's
  colours through pdf.js `pageColors`; images keep their own colours. Light and E-Ink themes show the original page.
- Text PDFs: long-press selection, double-tap dictionary lookup, highlights, underlines, notes, copy and quote cards
  work as in EPUBs (CFIs point into pdf.js's text layer); marks are drawn in a layer between the page image and the
  text. Scanned PDFs have no text layer, so only zoom and bookmarks apply.
- In-book search runs over each page's text and marks the matches on the page; results are labelled by page.
- Read aloud reads the page's text layer sentence by sentence (a printed line break is a word break, except after a
  hyphen that split a word), marks each word, keeps it on screen when zoomed, and turns to the next page.
- The chapter word list covers the pages of the contents entry the current page falls under (up to 100 pages), or
  the current page and the nine after it when the PDF has no contents.
- A PDF page larger than the screen (zoomed, fit width) pans with a drag; the brightness/warm-light edge swipe steps
  aside then (`EngineEvent.PageScrollableChanged`). Rotating or opening the read-aloud panel re-fits the page.
- Import cleans embedded titles: "Microsoft Word - X.docx" becomes "X", a trailing ".pdf" goes, and placeholders
  ("Untitled", author "Administrator") fall back to the file name / no author. The reader shows the library title.
- Style panel, "PDF page" section in place of typography (fonts and spacing are printed into the page):
  Crop margins (`reader.pdf_crop_margins`: finds the printed area of the pages read so far and fits that instead
  of the whole sheet, one crop for the whole book so text keeps its size), Fit width (`reader.pdf_fit_width`: fills the width; next/previous scroll through the page before
  turning, and paging back opens at the foot of the previous page), Darken text (the shared Bolder text setting: a
  gamma curve deepens faint print on light pages and brightens it on dark ones).
- Not yet for PDFs: bionic reading, overlapping highlight merging, community (Goodreads) quotes, JPEG 2000 images.
- Code: `:format:pdf` (`PdfParser`, `PdfInfoReader`), `feature/library/BookFileMetadata.kt`, `OpenBook.fixedLayout`,
  bridge.js `fixedLayout` paths, vendored `foliate/pdf.js` + `fixed-layout.js` + `vendor/pdfjs/`.
