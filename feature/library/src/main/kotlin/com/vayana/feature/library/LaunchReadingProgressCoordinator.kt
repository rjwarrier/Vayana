package com.vayana.feature.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LaunchReadingProgressCoordinator @Inject constructor() {
    private val _pendingProgressChange = MutableStateFlow<BookProgressChange?>(null)
    val pendingProgressChange: StateFlow<BookProgressChange?> = _pendingProgressChange

    private val mutex = Mutex()
    private var checkedBookId: Long? = null
    private var recentOutcome: RecentLaunchProgressOutcome? = null

    suspend fun checkOnce(
        bookId: Long,
        sync: suspend () -> BookProgressSyncOutcome,
    ) {
        mutex.withLock {
            if (checkedBookId == bookId) return
            val outcome = sync()
            if (!outcome.result.launchCheckCompleted) return
            checkedBookId = bookId
            recentOutcome = RecentLaunchProgressOutcome(
                bookId = bookId,
                outcome = outcome,
                checkedAt = System.currentTimeMillis(),
            )
            val change = outcome.progressChange
            if (change?.bookId == bookId) {
                _pendingProgressChange.value = change
            }
        }
    }

    suspend fun recentOutcomeFor(bookId: Long): BookProgressSyncOutcome? =
        mutex.withLock {
            val outcome = recentOutcome ?: return@withLock null
            if (outcome.bookId != bookId || !outcome.isFresh()) return@withLock null
            outcome.outcome
        }

    fun acknowledge(bookId: Long) {
        if (_pendingProgressChange.value?.bookId == bookId) {
            _pendingProgressChange.value = null
        }
    }
}

private data class RecentLaunchProgressOutcome(
    val bookId: Long,
    val outcome: BookProgressSyncOutcome,
    val checkedAt: Long,
)

private val GitHubSyncNowResult.launchCheckCompleted: Boolean
    get() = when (this) {
        is GitHubSyncNowResult.Complete -> !pullFailed && metadataSynced
        GitHubSyncNowResult.SyncDisabled,
        GitHubSyncNowResult.ConfigIncomplete,
        is GitHubSyncNowResult.InitialSyncConfirmationRequired,
        -> false
    }

private fun RecentLaunchProgressOutcome.isFresh(): Boolean =
    System.currentTimeMillis() - checkedAt in 0..RecentLaunchProgressOutcomeTtlMs

private const val RecentLaunchProgressOutcomeTtlMs = 5 * 60 * 1000L
