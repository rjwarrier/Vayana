package com.vayana.core.database.repository

import androidx.room.withTransaction
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.BookAliasDao
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.dao.ReadingSessionDao
import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.database.entity.ReadingSessionEntity
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.PhysicalReadingSessionSummary
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReadingSessionRepositoryImpl @Inject constructor(
    private val database: VayanaDatabase,
    private val readingSessionDao: ReadingSessionDao,
    private val bookDao: BookDao,
    private val bookAliasDao: BookAliasDao,
    private val tombstoneDao: TombstoneDao,
) : ReadingSessionRepository {

    override fun observeAll(): Flow<List<ReadingSession>> =
        readingSessionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeSince(since: Long): Flow<List<ReadingSession>> =
        readingSessionDao.observeSince(since).map { entities -> entities.map { it.toDomain() } }

    override fun observeSecondsSince(since: Long): Flow<Long> = readingSessionDao.observeSecondsSince(since)

    override fun observeForBook(bookId: Long): Flow<List<ReadingSession>> =
        readingSessionDao.observeForBook(bookId).map { entities -> entities.map { it.toDomain() } }

    override fun observePhysicalSummary(bookId: Long): Flow<PhysicalReadingSessionSummary> =
        readingSessionDao.observePhysicalSummary(bookId)

    override fun observeRecentPhysicalSessions(bookId: Long, limit: Int, forwardOnly: Boolean): Flow<List<ReadingSession>> {
        require(limit > 0)
        return readingSessionDao.observeRecentPhysicalSessions(bookId, limit, forwardOnly)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun record(bookId: Long, startedAt: Long, endedAt: Long, durationSeconds: Long?) {
        val durationSeconds = (durationSeconds ?: ((endedAt - startedAt) / 1000L)).coerceAtLeast(0L)
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

    override suspend fun updatePages(
        syncId: String,
        startPage: Int,
        endPage: Int,
        pageCount: Int?,
    ): Boolean {
        require(syncId.isNotBlank() && startPage >= 0 && endPage >= 0)
        require(pageCount == null || (pageCount > 0 && startPage <= pageCount && endPage <= pageCount))
        return readingSessionDao.updatePages(syncId, startPage, endPage) == 1
    }

    override suspend fun deleteBySyncId(syncId: String): Int = readingSessionDao.deleteBySyncId(syncId)

    override suspend fun mergeCloudSession(record: CloudReadingSessionRecord): ReadingSessionMergeResult {
        if (record.syncId.isBlank() || record.bookSyncId.isBlank()) return ReadingSessionMergeResult.SKIPPED
        if (record.startedAt <= 0L || record.endedAt < record.startedAt || (record.durationSeconds < 0L || (record.durationSeconds == 0L && record.activeIntervals != ""))) {
            return ReadingSessionMergeResult.SKIPPED
        }
        if (record.activeIntervals != null && !runCatching {
            val intervals = com.vayana.core.common.ReadingIntervals.decode(record.activeIntervals)
            intervals.all { it.start >= record.startedAt && it.end <= record.endedAt } &&
                (if (intervals.isEmpty()) record.durationSeconds == 0L || record.durationSeconds == 1L
                else (com.vayana.core.common.ReadingIntervals.millis(intervals) / 1000).coerceAtLeast(1) == record.durationSeconds)
        }.getOrDefault(false)) return ReadingSessionMergeResult.SKIPPED
        return database.withTransaction {
            // Cleared by a reading-stats reset: the cloud copy mustn't bring it back.
            if (tombstoneDao.findBySyncId(record.syncId) != null) return@withTransaction ReadingSessionMergeResult.SKIPPED
            val book = bookDao.findActiveBySyncIdOrAlias(record.bookSyncId, bookAliasDao) ?: return@withTransaction ReadingSessionMergeResult.SKIPPED
            val insertedId = readingSessionDao.insertIgnore(
                ReadingSessionEntity(
                    syncId = record.syncId,
                    bookId = book.id,
                    startedAt = record.startedAt,
                    endedAt = record.endedAt,
                    durationSeconds = record.durationSeconds,
                    startPage = record.startPage?.takeIf { it >= 0 },
                    endPage = record.endPage?.takeIf { it >= 0 },
                    activeIntervals = record.activeIntervals,
                ),
            )
            if (insertedId == -1L) ReadingSessionMergeResult.SKIPPED else ReadingSessionMergeResult.CREATED
        }
    }
}

private fun ReadingSessionEntity.toDomain(): ReadingSession = ReadingSession(
    id = id,
    syncId = syncId,
    bookId = bookId,
    startedAt = startedAt,
    endedAt = endedAt,
    durationSeconds = durationSeconds,
    startPage = startPage,
    endPage = endPage,
    activeIntervals = activeIntervals,
)
