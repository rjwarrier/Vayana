# Features: onboarding, search, Read Next, deleting books

How three recently added features behave, where their code lives, and the rules that aren't obvious from
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

**Tests:** `feature/library/.../PermanentBookDeletionTest.kt` (Robolectric + Room),
`core/sync/.../CloudAssetDeletionProcessorTest.kt`, `GitHubContentsAssetStoreTest` (delete cases),
`core/filesystem/.../BookFileCleanerTest.kt`, `core/database/.../BookPurgeTombstoneIdTest.kt`.
