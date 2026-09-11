package com.vayana.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class LaunchReadingProgressCoordinatorTest {
    @Test
    fun completedCheckStoresPendingPromptAndSkipsDuplicateChecks() = runBlocking {
        val coordinator = LaunchReadingProgressCoordinator()
        var syncCalls = 0
        val change = progressChange(bookId = 42L)

        coordinator.checkOnce(bookId = 42L) {
            syncCalls += 1
            BookProgressSyncOutcome(successResult(), change)
        }
        coordinator.checkOnce(bookId = 42L) {
            syncCalls += 1
            BookProgressSyncOutcome(successResult(), null)
        }

        assertEquals(1, syncCalls)
        assertEquals(change, coordinator.pendingProgressChange.value)
    }

    @Test
    fun failedCheckDoesNotMarkBookAsChecked() = runBlocking {
        val coordinator = LaunchReadingProgressCoordinator()
        var syncCalls = 0

        coordinator.checkOnce(bookId = 7L) {
            syncCalls += 1
            BookProgressSyncOutcome(
                GitHubSyncNowResult.Complete(
                    uploaded = 0,
                    failed = 0,
                    progressUpdated = 0,
                    cloudBooksCreated = 0,
                    cloudBooksUpdated = 0,
                    conflicts = 0,
                    skipped = 0,
                    pullFailed = true,
                    metadataSynced = false,
                ),
                null,
            )
        }
        coordinator.checkOnce(bookId = 7L) {
            syncCalls += 1
            BookProgressSyncOutcome(successResult(), progressChange(bookId = 7L))
        }

        assertEquals(2, syncCalls)
        assertEquals(7L, coordinator.pendingProgressChange.value?.bookId)
    }

    @Test
    fun acknowledgeClearsOnlyMatchingPrompt() = runBlocking {
        val coordinator = LaunchReadingProgressCoordinator()
        val change = progressChange(bookId = 9L)

        coordinator.checkOnce(bookId = 9L) {
            BookProgressSyncOutcome(successResult(), change)
        }
        coordinator.acknowledge(bookId = 10L)
        assertEquals(change, coordinator.pendingProgressChange.value)

        coordinator.acknowledge(bookId = 9L)
        assertNull(coordinator.pendingProgressChange.value)
    }

    private fun successResult() = GitHubSyncNowResult.Complete(
        uploaded = 0,
        failed = 0,
        progressUpdated = 1,
        cloudBooksCreated = 0,
        cloudBooksUpdated = 0,
        conflicts = 0,
        skipped = 0,
        pullFailed = false,
        metadataSynced = true,
    )

    private fun progressChange(bookId: Long) = BookProgressChange(
        bookId = bookId,
        previousLocator = "old",
        previousPercent = 0.1f,
        newPercent = 0.2f,
        previousUpdatedAt = 1L,
        newUpdatedAt = 2L,
        previousLastReadAt = 1L,
        newLastReadAt = 2L,
    )
}
