package com.vayana.core.database.repository

import com.vayana.core.database.model.ReadingSession
import kotlinx.coroutines.flow.Flow

interface ReadingSessionRepository {
    fun observeAll(): Flow<List<ReadingSession>>
    fun observeForBook(bookId: Long): Flow<List<ReadingSession>>

    /** Records a finished session. Callers should drop sessions that never really started (0s). */
    suspend fun record(bookId: Long, startedAt: Long, endedAt: Long)
}
