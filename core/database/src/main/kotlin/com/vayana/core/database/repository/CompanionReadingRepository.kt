package com.vayana.core.database.repository

import androidx.room.withTransaction
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.dao.ReadingSessionDao
import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.database.entity.ReadingSessionEntity
import javax.inject.Inject
import kotlin.math.roundToInt
import com.vayana.core.common.ReadingInterval
import com.vayana.core.common.ReadingIntervals

/** Import and progress conflict detection run in the same transaction as deduplication. */
class CompanionReadingRepository @Inject constructor(
    private val database: VayanaDatabase,
    private val books: BookDao,
    private val sessions: ReadingSessionDao,
    private val tombstones: TombstoneDao,
) {
    suspend fun importSession(
        bookSyncId: String, sessionId: String, startedAt: Long, endedAt: Long, seconds: Long,
        startPage: Int, endPage: Int, pageCount: Int?, baseVersion: Long,
        activeIntervals: String? = null, clockChanged: Boolean = false, resolution: String = "auto",
    ): String = database.withTransaction {
        require(sessionId.isNotBlank() && startedAt > 0 && endedAt >= startedAt && seconds > 0)
        require(seconds <= ((endedAt - startedAt) / 1000).coerceAtLeast(1))
        require(resolution in setOf("auto", "separate", "watch_page"))
        require(startPage >= 0 && endPage >= 0 && (pageCount == null ||
            (pageCount > 0 && startPage <= pageCount && endPage <= pageCount)))
        val book = books.findBySyncId(bookSyncId) ?: return@withTransaction "book_missing"
        if (book.isDeleted || book.format != "PHYSICAL") return@withTransaction "book_missing"
        if (tombstones.findBySyncId(sessionId) != null ||
            (tombstones.findBySyncId(readingProgressResetTombstoneId(book.syncId))?.deletedAt ?: 0) >= startedAt) {
            return@withTransaction "reset"
        }
        val existing = sessions.findBySyncId(sessionId)
        if (existing != null) {
            if (existing.bookId != book.id || existing.startedAt != startedAt || existing.endedAt != endedAt ||
                existing.startPage != startPage || existing.endPage != endPage) return@withTransaction "payload_conflict"
            if (resolution == "watch_page" && book.pageEstimate != pageCount) return@withTransaction "page_kept"
            if (resolution == "watch_page" && book.pageEstimate == pageCount) {
                val percent = pageCount?.let { endPage.toFloat() / it } ?: book.readingPercent
                books.update(book.copy(readingPercent = percent,
                    finishedReadingAt = if (percent >= 1f) book.finishedReadingAt ?: endedAt else null,
                    updatedAt = System.currentTimeMillis()))
                return@withTransaction "page_applied"
            }
            return@withTransaction "duplicate"
        }
        if ((clockChanged || endedAt > System.currentTimeMillis() + 300_000) && resolution == "auto") return@withTransaction "clock_conflict"
        val source = activeIntervals?.let(ReadingIntervals::decode)
        source?.let { ranges ->
            require(ranges.all { it.start >= startedAt && it.end <= endedAt })
            require((ReadingIntervals.millis(ranges) / 1000).coerceAtLeast(1) == seconds)
        }
        val original = source ?: if (endedAt > startedAt && endedAt - startedAt <= seconds * 1000 + 999)
            listOf(ReadingInterval(startedAt, endedAt)) else null
        val coverage = mutableListOf<ReadingInterval>()
        if (resolution != "separate") for (other in sessions.overlapping(startedAt, endedAt)) {
            val ranges = other.activeIntervals?.let { runCatching { ReadingIntervals.decode(it) }.getOrNull() } ?: if
                (other.endedAt > other.startedAt && other.endedAt - other.startedAt <= other.durationSeconds * 1000 + 999)
                listOf(ReadingInterval(other.startedAt, other.endedAt)) else null
            if (original == null) return@withTransaction "timing_conflict"
            if (ranges == null) {
                if (original.any { it.start < other.endedAt && it.end > other.startedAt }) return@withTransaction "timing_conflict"
                continue
            }
            if (ReadingIntervals.millis(ReadingIntervals.subtract(original, ranges)) == ReadingIntervals.millis(original)) continue
            if (other.bookId != book.id) return@withTransaction "overlap_other_book"
            coverage += ranges
        }
        val effective = original?.let { ReadingIntervals.subtract(it, coverage) }?.let { if (coverage.isNotEmpty() && ReadingIntervals.millis(it) < 1000) emptyList() else it }
        val effectiveSeconds = if (coverage.isEmpty()) seconds else effective!!.let { ReadingIntervals.millis(it) / 1000 }
        val currentPage = book.pageEstimate?.let { (book.readingPercent * it).roundToInt() } ?: 0
        val updateProgress = companionCanUpdateProgress(book.updatedAt, baseVersion, book.lastReadAt,
            startedAt, currentPage, startPage, book.pageEstimate, pageCount)
        sessions.insertIgnore(ReadingSessionEntity(syncId = sessionId, bookId = book.id,
            startedAt = startedAt, endedAt = endedAt, durationSeconds = effectiveSeconds,
            startPage = startPage, endPage = endPage, activeIntervals = effective?.let(ReadingIntervals::encode)))
        val percent = pageCount?.let { endPage.toFloat() / it } ?: book.readingPercent
        books.update(book.copy(
            readingPercent = if (updateProgress) percent else book.readingPercent,
            totalReadingSeconds = book.totalReadingSeconds + effectiveSeconds,
            startedReadingAt = book.startedReadingAt?.coerceAtMost(startedAt) ?: startedAt,
            finishedReadingAt = if (!updateProgress) book.finishedReadingAt
                else if (percent >= 1f) book.finishedReadingAt ?: endedAt else null,
            lastReadAt = book.lastReadAt?.coerceAtLeast(endedAt) ?: endedAt,
            updatedAt = System.currentTimeMillis(),
        ))
        if (!updateProgress) "page_kept" else if (coverage.isNotEmpty()) "merged" else "saved"
    }
}

internal fun companionCanUpdateProgress(updatedAt: Long, baseVersion: Long, lastReadAt: Long?,
    startedAt: Long, currentPage: Int, startPage: Int, currentTotal: Int?, sessionTotal: Int?): Boolean =
    currentTotal == sessionTotal && (lastReadAt ?: 0) <= startedAt &&
        (updatedAt <= baseVersion || currentPage == startPage)
