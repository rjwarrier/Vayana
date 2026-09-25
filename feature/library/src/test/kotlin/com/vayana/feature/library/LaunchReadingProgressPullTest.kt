package com.vayana.feature.library

import com.vayana.core.datastore.settings.LaunchReadingProgressCheckMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LaunchReadingProgressPullTest {
    private val markers = FakeMarkerStore()
    private val diagnostics = mutableListOf<String>()
    private var now = 1_000_000L
    private val pull = LaunchReadingProgressPull(
        markers = markers,
        recordDiagnostic = { _, message, _ -> diagnostics += message },
        now = { now },
    )
    private val pullCalls = mutableListOf<String?>()

    @Test
    fun newRemoteProgressIsPulledWithoutAnyUploadAndMarkerIsWritten() = runBlocking {
        val result = pull.run(bookId = BookId, syncTarget = Target) { skipSha ->
            pullCalls += skipSha
            ReadingProgressMergeSummary(
                applied = 1,
                skipped = 2,
                remoteSnapshotSha = "sha-new",
                remoteDeviceLabel = "Tablet",
                remoteSyncedAt = 999_000L,
            )
        }

        assertEquals(listOf<String?>(null), pullCalls)
        assertEquals(LaunchProgressCheckOutcome.CHECKED, result.launchProgressCheckOutcome)
        assertEquals(1, result.progressUpdated)
        assertEquals(2, result.skipped)
        assertEquals("Tablet", result.syncedDeviceLabel)
        assertEquals(999_000L, result.syncedAt)
        assertNoUploads(result)
        assertEquals(
            LaunchReadingProgressCheckMarker(
                bookId = BookId,
                syncTarget = Target,
                remoteSnapshotSha = "sha-new",
                checkedAt = now,
                outcome = LaunchProgressCheckOutcome.CHECKED.name,
            ),
            markers.written.single(),
        )
    }

    @Test
    fun freshMarkerSkipsPullEntirely() = runBlocking {
        markers.stored = marker(checkedAt = now - 60_000L)

        val result = pull.run(bookId = BookId, syncTarget = Target) { skipSha ->
            pullCalls += skipSha
            ReadingProgressMergeSummary()
        }

        assertTrue(pullCalls.isEmpty())
        assertEquals(LaunchProgressCheckOutcome.SKIPPED_FRESH_MARKER, result.launchProgressCheckOutcome)
        assertNoUploads(result)
        assertTrue(markers.written.isEmpty())
        assertEquals(listOf("Silent launch progress check skipped by fresh local marker"), diagnostics)
    }

    @Test
    fun staleMarkerPassesRemoteShaSoUnchangedSnapshotIsSkipped() = runBlocking {
        markers.stored = marker(checkedAt = now - 10 * 60_000L, sha = "sha-old")

        val result = pull.run(bookId = BookId, syncTarget = Target) { skipSha ->
            pullCalls += skipSha
            ReadingProgressMergeSummary(remoteSnapshotSha = skipSha, skippedAlreadyChecked = true)
        }

        assertEquals(listOf<String?>("sha-old"), pullCalls)
        assertEquals(LaunchProgressCheckOutcome.SKIPPED_UNCHANGED_REMOTE, result.launchProgressCheckOutcome)
        assertEquals(0, result.progressUpdated)
        assertNoUploads(result)
        assertEquals("sha-old", markers.written.single().remoteSnapshotSha)
        assertEquals(now, markers.written.single().checkedAt)
    }

    @Test
    fun markerForAnotherBookOrTargetIsIgnored() = runBlocking {
        markers.stored = marker(checkedAt = now, bookId = BookId + 1)
        pull.run(bookId = BookId, syncTarget = Target) { skipSha ->
            pullCalls += skipSha
            ReadingProgressMergeSummary(remoteSnapshotSha = "sha-new")
        }

        markers.stored = marker(checkedAt = now, syncTarget = "other/repo")
        pull.run(bookId = BookId, syncTarget = Target) { skipSha ->
            pullCalls += skipSha
            ReadingProgressMergeSummary(remoteSnapshotSha = "sha-new")
        }

        assertEquals(listOf<String?>(null, null), pullCalls)
    }

    @Test
    fun failedOrMissingSnapshotReportsFailureAndLeavesMarkerAlone() = runBlocking {
        val failed = pull.run(bookId = BookId, syncTarget = Target) {
            ReadingProgressMergeSummary(failed = true, failureMessage = "boom")
        }
        val missing = pull.run(bookId = BookId, syncTarget = Target) {
            ReadingProgressMergeSummary(missingRemoteSnapshot = true)
        }

        for (result in listOf(failed, missing)) {
            assertEquals(LaunchProgressCheckOutcome.FAILED, result.launchProgressCheckOutcome)
            assertTrue(result.pullFailed)
            assertNoUploads(result)
        }
        assertEquals("boom", failed.failureMessage)
        assertTrue(markers.written.isEmpty())
    }

    @Test
    fun withoutBookIdMarkerIsNeitherReadNorWritten() = runBlocking {
        markers.stored = marker(checkedAt = now)

        val result = pull.run(bookId = null, syncTarget = Target) { skipSha ->
            pullCalls += skipSha
            ReadingProgressMergeSummary(remoteSnapshotSha = "sha-new")
        }

        assertEquals(listOf<String?>(null), pullCalls)
        assertEquals(LaunchProgressCheckOutcome.CHECKED, result.launchProgressCheckOutcome)
        assertEquals(0, markers.reads)
        assertTrue(markers.written.isEmpty())
    }

    private fun assertNoUploads(result: GitHubSyncNowResult.Complete) {
        assertEquals(0, result.uploaded)
        assertEquals(0, result.progressUploaded)
        assertEquals(0, result.cloudBooksCreated)
        assertEquals(0, result.cloudBooksUpdated)
    }

    private fun marker(
        checkedAt: Long,
        bookId: Long = BookId,
        syncTarget: String = Target,
        sha: String = "sha-old",
    ) = LaunchReadingProgressCheckMarker(bookId, syncTarget, sha, checkedAt, "CHECKED")

    private class FakeMarkerStore : LaunchProgressMarkerStore {
        var stored: LaunchReadingProgressCheckMarker? = null
        var reads = 0
        val written = mutableListOf<LaunchReadingProgressCheckMarker>()

        override suspend fun read(): LaunchReadingProgressCheckMarker? {
            reads += 1
            return stored
        }

        override suspend fun write(marker: LaunchReadingProgressCheckMarker) {
            written += marker
        }
    }

    private companion object {
        const val BookId = 42L
        const val Target = "owner/repo/main/devices/phone.json"
    }
}
