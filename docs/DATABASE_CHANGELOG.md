# Database changelog

Every bump to `DATABASE_VERSION` (`core/database/.../VayanaDatabase.kt`) gets an entry here and a real Room `Migration`.

## Version 1 (initial)
`books` table only (`BookEntity`): the fields PROMPT2appbuild.md §2 lists for `Book`, minus `lastReadAt`-driven stats and cross-references (`BookGroup`, `Tag`, `Annotation`, etc.) — those land with the features that need them.
