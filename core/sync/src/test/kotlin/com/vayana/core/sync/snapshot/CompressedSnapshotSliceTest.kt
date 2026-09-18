package com.vayana.core.sync.snapshot

import com.vayana.core.backup.PortableAnnotation
import com.vayana.core.backup.PortableSnapshot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CompressedSnapshotSliceTest {

    @Test
    fun annotationJsonZipRoundTripsAndActuallyCompresses() {
        val json = """{"annotations":[${"{\"selectedText\":\"repeated quote text\"},".repeat(200)}{}]}"""

        val zipped = zipSnapshotJson(json)

        assertTrue(zipped.size < json.toByteArray().size)
        assertEquals(json, unzipSnapshotJson(zipped))
        assertContentEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte()), zipped.copyOf(2))
    }

    @Test
    fun remoteDocumentLoadsCompressedAnnotationPath() = runBlocking {
        val loadedPaths = mutableListOf<String>()
        val compressedPath = "vayana/snapshot-slices/1000/annotations.zip"
        val document = remotePortableSnapshotDocumentFrom(
            latestJson = "{}",
            sha = "sha",
            slicePaths = mapOf("annotations" to compressedPath),
            loadSlice = { path -> loadedPaths += path; "{\"annotations\":[]}" },
        )

        assertEquals("{\"annotations\":[]}", document.jsonFor(RemotePortableSnapshotSlice.Annotations))
        assertEquals(listOf(compressedPath), loadedPaths)
        assertEquals(compressedPath, document.slicePathOrNull("annotations"))
    }

    @Test
    fun compressionStartsOnlyAfterFiftyGoodreadsQuotesForOneBook() {
        assertFalse(snapshotWithGoodreadsQuotes(50).hasLargeGoodreadsQuoteSet())
        assertTrue(snapshotWithGoodreadsQuotes(51).hasLargeGoodreadsQuoteSet())
    }

    private fun snapshotWithGoodreadsQuotes(count: Int): PortableSnapshot = PortableSnapshot(
        formatVersion = 1,
        exportedAt = 1000,
        deviceLabel = "Phone",
        books = emptyList(),
        annotations = List(count) { index ->
            PortableAnnotation(
                syncId = "quote-$index",
                bookSyncId = "book-a",
                type = "POPULAR_HIGHLIGHT",
                colorKey = "popular",
                locator = "goodreads-quote:$index",
                chapterTitle = null,
                chapterHref = null,
                selectedText = "Quote number $index",
                readerNote = null,
                createdAt = 1,
                updatedAt = 1,
                isDeleted = false,
            )
        },
        shelves = emptyList(),
        shelfMemberships = emptyList(),
        readingSessions = emptyList(),
        vocabularyCards = emptyList(),
        wordLookupCounters = emptyList(),
        settings = emptyMap(),
    )
}
