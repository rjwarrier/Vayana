package com.vayana.core.database.repository

import com.vayana.core.database.model.ReadingSession
import kotlinx.coroutines.flow.Flow

data class CloudReadingSessionRecord(
    val syncId: String,
    val bookSyncId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
)

enum class ReadingSessionMergeResult {
    CREATED,
    SKIPPED,
}

interface ReadingSessionRepository {
    fun observeAll(): Flow<List<ReadingSession>>
    fun observeForBook(bookId: Long): Flow<List<ReadingSession>>

    /** Records a finished session. Callers should drop sessions that never really started (0s). */
    suspend fun record(bookId: Long, startedAt: Long, endedAt: Long, durationSeconds: Long? = null)

    suspend fun mergeCloudSession(record: CloudReadingSessionRecord): ReadingSessionMergeResult

    /** Removes one session, for a synced reading-stats reset. Returns how many rows went (0 or 1). */
    suspend fun deleteBySyncId(syncId: String): Int
}
