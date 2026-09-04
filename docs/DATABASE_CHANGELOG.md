# Database changelog

Every bump to `DATABASE_VERSION` (`core/database/.../VayanaDatabase.kt`) gets an entry here and a real Room `Migration`.

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
