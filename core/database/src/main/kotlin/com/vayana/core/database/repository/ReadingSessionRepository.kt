package com.vayana.core.database.repository

import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.PhysicalReadingSessionSummary
import kotlinx.coroutines.flow.Flow

data class CloudReadingSessionRecord(
    val syncId: String,
    val bookSyncId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
    val startPage: Int? = null,
    val endPage: Int? = null,
)

enum class ReadingSessionMergeResult {
    CREATED,
    SKIPPED,
}

interface ReadingSessionRepository {
    fun observeAll(): Flow<List<ReadingSession>>
    fun observeForBook(bookId: Long): Flow<List<ReadingSession>>
    fun observePhysicalSummary(bookId: Long): Flow<PhysicalReadingSessionSummary>
    fun observeRecentPhysicalSessions(bookId: Long, limit: Int, forwardOnly: Boolean = false): Flow<List<ReadingSession>>

    /** Sessions (this device's and synced ones) started at or after [since], epoch millis. */
    fun observeSince(since: Long): Flow<List<ReadingSession>>

    /** Aggregate without loading or mapping the session rows. Same start-time boundary as [observeSince]. */
    fun observeSecondsSince(since: Long): Flow<Long>

    /** Records a finished session. Callers should drop sessions that never really started (0s). */
    suspend fun record(bookId: Long, startedAt: Long, endedAt: Long, durationSeconds: Long? = null)

    suspend fun mergeCloudSession(record: CloudReadingSessionRecord): ReadingSessionMergeResult

    /** Removes one session, for a synced reading-stats reset. Returns how many rows went (0 or 1). */
    suspend fun deleteBySyncId(syncId: String): Int
}
