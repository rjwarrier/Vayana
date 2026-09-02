# Database changelog

Every bump to `DATABASE_VERSION` (`core/database/.../VayanaDatabase.kt`) gets an entry here and a real Room `Migration`.

## Version 2
Adds `annotations` for Phase 4's annotation foundation:

- `bookId`, `type`, `colorKey`, `locator`, chapter metadata, selected text, optional reader note, and timestamps.
- Foreign key to `books(id)` with cascade on physical delete.
- Indexes on `bookId`, `type`, `colorKey`, and `updatedAt` for book-local rendering and Notes filters.

## Version 1 (initial)
`books` table only (`BookEntity`): the fields PROMPT2appbuild.md §2 lists for `Book`, minus `lastReadAt`-driven stats and cross-references (`BookGroup`, `Tag`, `Annotation`, etc.) — those land with the features that need them.
