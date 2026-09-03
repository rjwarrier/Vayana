# Database changelog

Every bump to `DATABASE_VERSION` (`core/database/.../VayanaDatabase.kt`) gets an entry here and a real Room `Migration`.

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
