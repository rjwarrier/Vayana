package com.vayana.feature.settings.backup

import kotlin.test.Test
import kotlin.test.assertFailsWith

class BackupArchiveEntryTest {
    @Test
    fun acceptsOrdinaryBackupPaths() {
        val seen = mutableSetOf<String>()
        validateEntryName("manifest.json", seen)
        validateEntryName("books/", seen)
        validateEntryName("books/example.epub", seen)
    }

    @Test
    fun rejectsPathsThatPreviewOrRestoreCannotSafelyUse() {
        listOf("", "/manifest.json", "books//book.epub", "books/../settings.json", "books/./book.epub", "books\\book.epub")
            .forEach { path ->
                assertFailsWith<IllegalStateException>(path) { validateEntryName(path, mutableSetOf()) }
            }
    }

    @Test
    fun rejectsDuplicateEntries() {
        val seen = mutableSetOf<String>()
        validateEntryName("manifest.json", seen)
        assertFailsWith<IllegalStateException> { validateEntryName("manifest.json", seen) }
        validateEntryName("books/", seen)
        assertFailsWith<IllegalStateException> { validateEntryName("books", seen) }
    }
}
