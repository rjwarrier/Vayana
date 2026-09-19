# Features: onboarding, search, Read Next, deleting books, opening files, read aloud, highlight review

How these features behave, where their code lives, and the rules that aren't obvious from
reading one file. Keep this in step with the code when any of them changes.

---

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

- **Audio focus** (`PlaybackFocus.kt`). Read aloud requests focus when it starts speaking and gives it up when paused
  or stopped. A call or navigation prompt (`LOST_TEMPORARILY`) pauses it and it resumes by itself when focus returns;
  other audio taking focus for good (`LOST`) and unplugged headphones (`BECOMING_NOISY`) pause it until the reader
  presses play. A user pause during a temporary loss is never undone. If focus is refused (a call in progress) it does
  not start. The engine speaks with `USAGE_MEDIA` / `CONTENT_TYPE_SPEECH`.
- **Media controls.** `ReadAloudForegroundService` owns a `MediaSession`: lock-screen and Bluetooth/headset play,
  pause, next and previous, and a notification with previous sentence / play-pause / next sentence. Commands reach
  the reader through `ReadAloudNotificationCommands` (`ReadAloudCommand` PLAY, PAUSE, NEXT, PREVIOUS, STOP).
  `ReadAloudPlayer.skip(±n)` moves by sentence; past a chapter's end it continues into the next chapter.
- **From here.** The selection toolbar has a Read aloud button (hidden when audio features are off). It restarts read
  aloud at the sentence holding the selection: `BookEngine.startSpeech(fromCfi)` → `bridge.js startSpeech(requestId,
  fromCfi)` resolves the CFI and skips sentences that end before it.

**Tests:** `ReadAloudPlayerTest` (focus, skip, start-from-CFI).

## Highlight review (spaced)

Statistics → the highlight card opens a review of highlights that are due, instead of a fixed set of five.

- Each highlight has a schedule in `highlight_reviews` (DB v23), reusing `VocabularySchedule` (SM-2 style) with three
  answers: See soon (again), Got it (good), Know it well (easy).
- `dueHighlights()` picks overdue highlights first (longest overdue first), then never-reviewed ones (oldest first),
  up to 10 per session. Only the reader's own highlights and notes count: no bookmarks, no Goodreads quotes.
- With nothing due the screen says so and offers today's fixed set (`dailyHighlights`) as practice; practice answers
  are not recorded.
- Schedules are **per device and not synced** (see `docs/DATABASE_CHANGELOG.md`, version 23).

**Tests:** `HighlightReviewTest`, `feature/library/.../HighlightReviewRepositoryTest.kt`.
