package com.vayana.feature.library

import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.ReadingProgressMergeResult
import com.vayana.core.sync.snapshot.RemotePortableSnapshotDocument
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class RemoteReadingProgressMergerTest {
    private val repositoryCalls = mutableListOf<String>()
    private val appliedSyncIds = mutableListOf<String>()
    private val appliedReadNextSyncIds = mutableListOf<String>()
    private var tombstoneMerges = 0
    private val tombstoneScopes = mutableListOf<TombstoneMergeScope>()

    /** Any BookRepository call other than applySyncedReadingProgress fails the test. */
    private val bookRepository = Proxy.newProxyInstance(
        BookRepository::class.java.classLoader,
        arrayOf(BookRepository::class.java),
    ) { _, method, args ->
        repositoryCalls += method.name
        when (method.name) {
            "applySyncedReadingProgress" -> {
                val syncId = args[0] as String
                appliedSyncIds += syncId
                if (syncId == "stale") ReadingProgressMergeResult.LocalNewer else ReadingProgressMergeResult.AppliedRemote
            }
            "applySyncedReadNext" -> {
                appliedReadNextSyncIds += args[0] as String
                true
            }
            else -> error("Unexpected BookRepository.${method.name}")
        }
    } as BookRepository

    private val merger = RemoteReadingProgressMerger(
        bookRepository = bookRepository,
        localDeviceLabel = { "Phone" },
        mergeTombstones = { _, scope ->
            tombstoneScopes += scope
            tombstoneMerges += 1
            GenericSyncMergeSummary(appliedDeletes = 1)
        },
    )

    @Test
    fun silentMergeAppliesOnlyBookDeletionsAndReadingProgress() = runBlocking {
        val summary = merger.merge(document(), tombstones = TombstoneMergeScope.BOOK_DELETIONS)

        assertEquals(listOf(TombstoneMergeScope.BOOK_DELETIONS), tombstoneScopes)
        assertTrue(repositoryCalls.all { it == "applySyncedReadingProgress" })
        assertEquals(listOf("fresh", "stale"), appliedSyncIds)
        assertEquals(1, summary.applied)
        assertEquals(setOf("fresh"), summary.appliedSyncIds)
        assertEquals(1, summary.skipped)
        assertEquals("sha-1", summary.remoteSnapshotSha)
    }

    @Test
    fun fullMergeStillAppliesTombstones() = runBlocking {
        merger.merge(document(), tombstones = TombstoneMergeScope.ALL)

        assertEquals(1, tombstoneMerges)
        assertTrue(repositoryCalls.all { it == "applySyncedReadingProgress" })
    }

    @Test
    fun mergeAppliesReadNextStateWithoutReadingPosition() = runBlocking {
        val summary = merger.mergeProgress(
            """
            {
              "books": [
                {"syncId": "queued", "fileHash": "h3", "updatedAt": 20, "readNextAddedAt": 20, "readNextUpdatedAt": 20}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("queued"), appliedReadNextSyncIds)
        assertEquals(1, summary.applied)
        assertTrue(summary.appliedSyncIds.isEmpty())
    }

    @Test
    fun failedTombstoneMergeStopsBeforeProgress() = runBlocking {
        val failing = RemoteReadingProgressMerger(
            bookRepository = bookRepository,
            localDeviceLabel = { "Phone" },
            mergeTombstones = { _, _ -> GenericSyncMergeSummary(failed = true, failureMessage = "bad tombstones") },
        )

        val summary = failing.merge(document(), tombstones = TombstoneMergeScope.ALL)

        assertTrue(summary.failed)
        assertEquals("bad tombstones", summary.failureMessage)
        assertTrue(repositoryCalls.isEmpty())
    }

    private fun document() = RemotePortableSnapshotDocument(
        jsonText = """
            {
              "deviceLabel": "Tablet",
              "books": [
                {"syncId": "fresh", "fileHash": "h1", "lastLocator": "epubcfi(/6/4)", "readingPercent": 0.5, "updatedAt": 10},
                {"syncId": "stale", "fileHash": "h2", "lastLocator": "epubcfi(/6/8)", "readingPercent": 0.2, "updatedAt": 5}
              ],
              "tombstones": [{"syncId": "gone", "entityType": "book", "deletedAt": 7}]
            }
        """.trimIndent(),
        sha = "sha-1",
        sliced = false,
    )
}
