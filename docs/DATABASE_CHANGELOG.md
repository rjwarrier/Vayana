# Database changelog

Every bump to `DATABASE_VERSION` (`core/database/.../VayanaDatabase.kt`) gets an entry here and a real Room `Migration`.

## Version 17
Adds nullable `books.readNextUpdatedAt` (epoch millis): when the book was last added to or removed from "Read next".
Sync merges the queue on this instead of `updatedAt`, which reading progress also bumps. Existing queued rows are
backfilled from `readNextAddedAt`. Exported in the snapshot as optional `readNextUpdatedAt`; records without it fall
back to `readNextAddedAt`.

## Version 16
Data repair only, no schema change. Android's `org.json` returns the text `"null"` from `optString` for an
explicit JSON null, so cloud sync had stored `"null"` as a real value in synced text columns. The migration
sets these back to `NULL`: `books.author`, `series`, `seriesNumber`, `description`, `lastLocator`,
`customFontFamily`, `tagsCsv`, `goodreadsUrl`; `annotations.chapterTitle`, `chapterHref`, `readerNote`;
`vocabulary_cards.sentence`, `bookTitle`. `annotations.selectedText` (non-null) becomes `''`.

## Version 15
Adds Goodreads import extras and switchable covers to `books` (all nullable, existing rows migrate as null):

- `goodreadsUrl`, `goodreadsRating`, `goodreadsRatingsCount`, `originalPublicationYear` — filled by
  "Import from Goodreads". **Local to the device**: not part of the cloud book record, and the cloud-only
  rewrite in `BookRepositoryImpl.mergeCloudBookLocked` carries them over (`withLocalOnlyFieldsFrom`).
- `customCoverPath`, `goodreadsCoverPath` — root-relative paths of the two covers a book can switch
  between. `coverPath` remains the cover in use and may equal either. Also local-only.

No schema change for "Reset reading stats" (same release): it uses the existing `tombstones` table with two
new `entityType` values, `reading_session` and `reading_progress_reset` (syncId `reset:<bookSyncId>`).

## Version 14
Adds a non-unique index on `books.fileHash`. `findByHash`/`findActiveBySyncIdOrHash` run on every
import dedupe check and every GitHub sync merge; without an index those were full table scans.

## Version 13
Adds identity-reconciliation and hard-deletion bookkeeping for GitHub sync milestone 1
(docs/GITHUB_SYNC_IMPLEMENTATION_PLAN.md §4, §10):

- New `book_aliases` table (`syncId` primary key, unique `fileHash`, `createdAt`) mapping a
  content hash to a canonical book `syncId`, for reconciling independently imported copies of the
  same file across devices. Schema only - nothing reads or writes it yet.
- New `tombstones` table (`syncId` primary key, `entityType`, `deletedAt`) recording hard
  deletions, including future shelf memberships, so a merge can tell "never existed here" apart
  from "existed and was purged". Schema only - nothing calls into it yet.

## Version 12
Adds comma-separated book tags:

- `books` gains nullable `tagsCsv` for user-entered tags. Existing books migrate with no tags.
- Tags are normalized on metadata save and exported in portable book metadata for backup/sync.

## Version 11
Adds cloud-backed file state for GitHub sync:

- `books` gains `fileAvailability` (`LOCAL`, `CLOUD_ONLY`, `MISSING`, `UPLOAD_PENDING`) so a
  fresh device can show library metadata before the book file is present locally.
- `books` gains encrypted asset reference metadata for book files and custom covers:
  `fileAssetId`, `fileAssetSha256`, `fileAssetSizeBytes`, `fileAssetUploadedAt`,
  `coverAssetId`, `coverAssetSha256`, `coverAssetSizeBytes`, and `coverAssetUploadedAt`.
  These are opaque GitHub asset references, not local paths.

## Version 10
Adds the identity and counter foundation needed for GitHub/Kindle-style sync:

- `books`, `annotations`, `reading_sessions`, `shelves`, and `vocabulary_cards` gain stable
  `syncId` text columns with unique indexes. Existing rows are backfilled during migration so
  generated local numeric IDs never have to leave the device as portable identifiers.
- `book_shelf_cross_ref` gains `createdAt` so shelf membership ordering can be synced and merged.
- `word_lookup_stats` changes from one row per word to one row per `(word, writerOrigin)`.
  Existing counts are seeded under `legacy-local`; UI queries still aggregate by word.

## Version 9
Adds schema for the remaining feature-value recommendations (personal shelves/read-next, vocabulary
review cards, per-book reader preferences):

- `books` gains `customFontSizePercent`, `customLineHeight`, `customFontFamily`,
  `customSideMarginPercent` (all nullable - null means "use the global reader setting") and
  `readNextAddedAt` (non-null while queued in "Read next", ordered ascending).
- New `shelves` table (`id`, `name`, `createdAt`, `updatedAt`) and `book_shelf_cross_ref` join
  table (`bookId`, `shelfId`, composite PK, cascade-delete both ways) for many-to-many personal
  shelves.
- New `vocabulary_cards` table (`id`, `word`, `definition`, `sentence`, `bookId`, `bookTitle`,
  `createdAt`, `lastReviewedAt`, `known`) for saved dictionary lookups turned into flashcards.
  `bookId` is a plain column, not a foreign key - a card should outlive its source book being
  deleted, it just loses the "jump back to book" affordance.

## Version 7
Adds soft-delete to `annotations`:

- `isDeleted` (integer, default 0).
- `observeAll`/`observeForBook` now filter `WHERE isDeleted = 0`. Deleting a note sets this flag
  immediately (durable even if the app is killed a moment later); the Notes screen's undo
  snackbar clears it back to 0, or a hard `DELETE` purges the row once the undo window passes.
  Fixes a bug where the previous timer-based delete silently never ran if the user navigated
  away from the Notes screen within the undo window, leaving "deleted" notes still in the
  database and reappearing later.

## Version 6
Adds `word_lookup_stats` for the "words you looked up" vocabulary statistic (PROMPT2appbuild.md §4.6):

- `word` (text, primary key), `count` (integer), `lastLookedUpAt` (epoch millis).
- One row per distinct word ever looked up in the offline dictionary; `count` increments on every lookup via an upsert.
- Feeds a Statistics tile ranking the words read most often.

## Version 5
Adds reading statistics tracking to `books`:

- Nullable `startedReadingAt` integer (epoch millis) column on `books`.
- Nullable `finishedReadingAt` integer (epoch millis) column on `books`.
- Non-null `totalReadingSeconds` integer (default 0) column on `books`.
- Captures when user starts reading, when completed, foreground reading time, and days taken for the book details screen.

## Version 4
Adds editable book number metadata for series:

- Nullable `seriesNumber` text column on `books`.
- Existing books migrate with `seriesNumber = NULL`.
- Book Details can view and edit both series name and book number.

## Version 3
Adds editable book series metadata:

- Nullable `series` text column on `books`.
- Existing books migrate with `series = NULL`.
- Book Details can view and edit the series name with the rest of the editable metadata.

## Version 2
Adds `annotations` for Phase 4's annotation foundation:

- `bookId`, `type`, `colorKey`, `locator`, chapter metadata, selected text, optional reader note, and timestamps.
- Foreign key to `books(id)` with cascade on physical delete.
- Indexes on `bookId`, `type`, `colorKey`, and `updatedAt` for book-local rendering and Notes filters.

## Version 1 (initial)
`books` table only (`BookEntity`): the fields PROMPT2appbuild.md §2 lists for `Book`, minus `lastReadAt`-driven stats and cross-references (`BookGroup`, `Tag`, `Annotation`, etc.) — those land with the features that need them.
