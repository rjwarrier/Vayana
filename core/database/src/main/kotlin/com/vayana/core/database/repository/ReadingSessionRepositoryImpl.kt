package com.vayana.core.database.repository

import com.vayana.core.database.dao.ReadingSessionDao
import com.vayana.core.database.entity.ReadingSessionEntity
import com.vayana.core.database.model.ReadingSession
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReadingSessionRepositoryImpl @Inject constructor(
    private val readingSessionDao: ReadingSessionDao,
) : ReadingSessionRepository {

    override fun observeAll(): Flow<List<ReadingSession>> =
        readingSessionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeForBook(bookId: Long): Flow<List<ReadingSession>> =
        readingSessionDao.observeForBook(bookId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun record(bookId: Long, startedAt: Long, endedAt: Long) {
        val durationSeconds = ((endedAt - startedAt) / 1000L).coerceAtLeast(0L)
        if (durationSeconds <= 0L) return
        readingSessionDao.insert(
            ReadingSessionEntity(
                bookId = bookId,
                startedAt = startedAt,
                endedAt = endedAt,
                durationSeconds = durationSeconds,
            ),
        )
    }
}

private fun ReadingSessionEntity.toDomain(): ReadingSession = ReadingSession(
    id = id,
    bookId = bookId,
    startedAt = startedAt,
    endedAt = endedAt,
    durationSeconds = durationSeconds,
)
