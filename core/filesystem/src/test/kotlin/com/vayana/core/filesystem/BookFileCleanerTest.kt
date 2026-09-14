package com.vayana.core.filesystem

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookFileCleanerTest {
    @Test
    fun deletesOnlyFilesInsideTheRoot() {
        val parent = createTempDirectory("cleaner").toFile()
        try {
            val root = File(parent, "root").apply { mkdirs() }
            val book = File(root, "books/a.epub").apply { parentFile.mkdirs(); writeText("book") }
            val cover = File(root, "covers/a.jpg").apply { parentFile.mkdirs(); writeText("cover") }
            val outside = File(parent, "outside.txt").apply { writeText("keep") }

            val deleted = deleteFilesUnderRoot(
                root,
                listOf("books/a.epub", "covers/a.jpg", "covers/a.jpg", "covers/missing.jpg", "../outside.txt", "books"),
            )

            assertEquals(2, deleted)
            assertFalse(book.exists())
            assertFalse(cover.exists())
            assertTrue(outside.exists())
            assertTrue(File(root, "books").isDirectory)
        } finally {
            parent.deleteRecursively()
        }
    }
}
