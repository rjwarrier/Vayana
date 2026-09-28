package com.vayana.feature.statistics

import com.vayana.core.database.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryStorageTest {
    private fun ref(id: Long, format: BookFormat = BookFormat.EPUB, path: String = "books/$id", cloud: Long? = null) =
        BookFileRef(id, "Book $id", format, path, "hash$id", cloud)

    @Test
    fun sumsLocalFilesAndFindsLargestSmallestAndFormatShares() {
        val sizes = mapOf("books/1" to 3_000_000L, "books/2" to 500_000L, "books/3" to 12_000_000L)

        val storage = libraryStorage(listOf(ref(1), ref(2), ref(3, BookFormat.PDF)), sizes::get)!!

        assertEquals(15_500_000L, storage.totalBytes)
        assertEquals(3, storage.bookCount)
        assertEquals(3L, storage.largest.bookId)
        assertEquals(2L, storage.smallest.bookId)
        assertEquals(15_500_000L / 3, storage.averageBytes)
        assertEquals(
            listOf(FormatSize(BookFormat.PDF, 12_000_000L, 1), FormatSize(BookFormat.EPUB, 3_500_000L, 2)),
            storage.byFormat,
        )
    }

    @Test
    fun fallsBackToTheCloudCopySizeAndSkipsBooksWithNoSize() {
        val storage = libraryStorage(
            listOf(ref(1, path = "", cloud = 2_000L), ref(2, cloud = 900L), ref(3)),
            localSize = { null },
        )!!

        assertEquals(2_900L, storage.totalBytes)
        assertEquals(2, storage.bookCount)
    }

    @Test
    fun localFileWinsOverTheCloudSize() {
        val storage = libraryStorage(listOf(ref(1, cloud = 1L)), localSize = { 42L })!!

        assertEquals(42L, storage.totalBytes)
    }

    @Test
    fun noSizedBooksMeansNoCard() {
        assertNull(libraryStorage(listOf(ref(1)), localSize = { null }))
        assertNull(libraryStorage(emptyList(), localSize = { 1L }))
    }
}
