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

    override suspend fun recordCheckpoint(bookId: Long, syncId: String, startedAt: Long, endedAt: Long, durationSeconds: Long) {
        // Called from fire-and-forget app-scope launches: invalid input is dropped, never thrown.
        if (syncId.isBlank() || startedAt <= 0 || endedAt < startedAt || durationSeconds <= 0) return
        database.withTransaction {
            val book = bookDao.getById(bookId)?.takeUnless { it.isDeleted } ?: return@withTransaction
            if (tombstoneDao.findBySyncId(syncId) != null) return@withTransaction
            val resetAt = tombstoneDao.findBySyncId(readingProgressResetTombstoneId(book.syncId))?.deletedAt
            if (resetAt != null && startedAt <= resetAt) return@withTransaction
            val existing = readingSessionDao.findBySyncId(syncId)
            if (existing != null) {
                if (existing.bookId != bookId || existing.startedAt != startedAt) return@withTransaction
                if (durationSeconds <= existing.durationSeconds || endedAt < existing.endedAt) return@withTransaction
                readingSessionDao.update(existing.copy(endedAt = endedAt, durationSeconds = durationSeconds))
            } else {
                readingSessionDao.insert(ReadingSessionEntity(syncId = syncId, bookId = bookId,
                    startedAt = startedAt, endedAt = endedAt, durationSeconds = durationSeconds))
            }
            val addedSeconds = durationSeconds - (existing?.durationSeconds ?: 0L)
            bookDao.update(book.copy(
                totalReadingSeconds = maxOf(book.totalReadingSeconds + addedSeconds, readingSessionDao.totalSecondsForBook(bookId)),
                startedReadingAt = book.startedReadingAt ?: startedAt,
                lastReadAt = maxOf(book.lastReadAt ?: 0L, endedAt),
                updatedAt = maxOf(book.updatedAt, endedAt),
            ))
        }
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

    override suspend fun deletePhysicalSession(bookId: Long, syncId: String): Boolean = database.withTransaction {
        require(syncId.isNotBlank())
        val session = readingSessionDao.findBySyncId(syncId) ?: return@withTransaction false
        require(session.bookId == bookId) { "Session belongs to another book" }
        val book = bookDao.getById(bookId) ?: return@withTransaction false
        require(book.format == "PHYSICAL") { "Book is no longer physical" }
        val now = System.currentTimeMillis()
        tombstoneDao.upsert(com.vayana.core.database.entity.TombstoneEntity(
            syncId = syncId, entityType = TombstoneEntityType.READING_SESSION.value, deletedAt = now))
        readingSessionDao.deleteBySyncId(syncId)
        bookDao.update(book.copy(
            totalReadingSeconds = (book.totalReadingSeconds - session.durationSeconds.coerceAtLeast(0)).coerceAtLeast(0),
            updatedAt = maxOf(book.updatedAt, now),
        ))
        true
    }

    override suspend fun deleteBySyncId(syncId: String): Int = database.withTransaction {
        val session = readingSessionDao.findBySyncId(syncId) ?: return@withTransaction 0
        val deleted = readingSessionDao.deleteBySyncId(syncId)
        if (deleted > 0) bookDao.getById(session.bookId)?.let { book ->
            bookDao.update(book.copy(totalReadingSeconds =
                (book.totalReadingSeconds - session.durationSeconds.coerceAtLeast(0)).coerceAtLeast(0)))
        }
        deleted
    }

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
            val book = bookDao.findActiveBySyncIdOrAlias(record.bookSyncId, bookAliasDao)
                ?: record.bookFileHash?.takeIf { it.isNotBlank() }?.let { bookDao.findByHash(it) }
                ?: return@withTransaction ReadingSessionMergeResult.SKIPPED
            val resetAt = tombstoneDao.findBySyncId(readingProgressResetTombstoneId(book.syncId))?.deletedAt
            if (resetAt != null && record.startedAt <= resetAt) return@withTransaction ReadingSessionMergeResult.SKIPPED
            val existing = readingSessionDao.findBySyncId(record.syncId)
            val incoming = ReadingSessionEntity(
                syncId = record.syncId,
                bookId = book.id,
                startedAt = record.startedAt,
                endedAt = record.endedAt,
                durationSeconds = record.durationSeconds,
                startPage = record.startPage?.takeIf { it >= 0 },
                endPage = record.endPage?.takeIf { it >= 0 },
                activeIntervals = record.activeIntervals,
            )
            val result = when {
                existing == null -> {
                    readingSessionDao.insert(incoming)
                    ReadingSessionMergeResult.CREATED
                }
                existing.bookId == book.id && existing.startedAt == record.startedAt &&
                    record.durationSeconds > existing.durationSeconds && record.endedAt >= existing.endedAt -> {
                    readingSessionDao.update(incoming.copy(id = existing.id))
                    ReadingSessionMergeResult.UPDATED
                }
                existing.bookId == book.id && existing.startedAt == record.startedAt &&
                    existing.durationSeconds == record.durationSeconds && existing.endedAt == record.endedAt &&
                    existing.activeIntervals == null && record.activeIntervals != null -> {
                    readingSessionDao.update(existing.copy(activeIntervals = record.activeIntervals))
                    ReadingSessionMergeResult.UPDATED
                }
                else -> ReadingSessionMergeResult.SKIPPED
            }
            // A max of per-device book totals loses independent offline reading. The merged session
            // ledger is additive; retain a larger legacy total when old history has no session rows.
            val total = maxOf(book.totalReadingSeconds, readingSessionDao.totalSecondsForBook(book.id))
            if (total != book.totalReadingSeconds) bookDao.update(book.copy(totalReadingSeconds = total))
            result
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
