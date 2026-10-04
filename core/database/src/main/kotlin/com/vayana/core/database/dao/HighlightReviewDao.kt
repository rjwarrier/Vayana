package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vayana.core.database.entity.HighlightReviewEntity
import com.vayana.core.database.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightReviewDao {
    @Query("SELECT COUNT(*) FROM annotations a JOIN books b ON b.id = a.bookId LEFT JOIN highlight_reviews h ON h.annotationSyncId = a.syncId WHERE a.isDeleted = 0 AND b.isDeleted = 0 AND length(trim(a.selectedText, :whitespace)) > 0 AND instr(a.locator, 'quote:') != 1 AND instr(a.locator, 'goodreads-quote:') != 1 AND (h.dueAt <= :now OR h.annotationSyncId IS NULL)")
    fun observeDueCount(now: Long, whitespace: String): Flow<Int>

    @Query("DELETE FROM highlight_reviews WHERE annotationSyncId = :syncId")
    suspend fun reset(syncId: String)

    @Query("SELECT * FROM highlight_reviews")
    fun observeAll(): Flow<List<HighlightReviewEntity>>

    @Query("SELECT COUNT(*) FROM annotations a JOIN books b ON b.id = a.bookId " +
        "WHERE a.isDeleted = 0 AND b.isDeleted = 0 AND length(trim(a.selectedText, :whitespace)) > 0 " +
        "AND instr(a.locator, 'quote:') != 1 AND instr(a.locator, 'goodreads-quote:') != 1")
    suspend fun reviewableCount(whitespace: String): Int

    @Query("SELECT a.* FROM annotations a JOIN books b ON b.id = a.bookId " +
        "LEFT JOIN highlight_reviews h ON h.annotationSyncId = a.syncId " +
        "WHERE a.isDeleted = 0 AND b.isDeleted = 0 AND length(trim(a.selectedText, :whitespace)) > 0 " +
        "AND instr(a.locator, 'quote:') != 1 AND instr(a.locator, 'goodreads-quote:') != 1 " +
        "AND (h.dueAt <= :now OR h.annotationSyncId IS NULL) " +
        "ORDER BY CASE WHEN h.annotationSyncId IS NULL THEN 1 ELSE 0 END, h.dueAt, " +
        "CASE WHEN h.annotationSyncId IS NULL THEN a.createdAt ELSE a.id END, a.id LIMIT :limit")
    suspend fun due(now: Long, limit: Int, whitespace: String): List<AnnotationEntity>

    @Query("SELECT a.* FROM annotations a JOIN books b ON b.id = a.bookId " +
        "LEFT JOIN highlight_reviews h ON h.annotationSyncId = a.syncId " +
        "WHERE a.isDeleted = 0 AND b.isDeleted = 0 AND length(trim(a.selectedText, :whitespace)) > 0 " +
        "AND instr(a.locator, 'quote:') != 1 AND instr(a.locator, 'goodreads-quote:') != 1 " +
        "AND (h.dueAt <= :now OR h.annotationSyncId IS NULL) " +
        "ORDER BY CASE WHEN h.annotationSyncId IS NULL THEN 1 ELSE 0 END, h.dueAt, " +
        "CASE WHEN h.annotationSyncId IS NULL THEN a.createdAt ELSE a.id END, a.id LIMIT :limit")
    fun observeDue(now: Long, limit: Int, whitespace: String): Flow<List<AnnotationEntity>>

    @Query("SELECT a.* FROM annotations a JOIN books b ON b.id = a.bookId " +
        "WHERE a.isDeleted = 0 AND b.isDeleted = 0 AND length(trim(a.selectedText, :whitespace)) > 0 " +
        "AND instr(a.locator, 'quote:') != 1 AND instr(a.locator, 'goodreads-quote:') != 1 " +
        "ORDER BY a.createdAt, a.id LIMIT :limit OFFSET :offset")
    suspend fun practice(limit: Int, offset: Int, whitespace: String): List<AnnotationEntity>

    @Query("SELECT * FROM highlight_reviews WHERE annotationSyncId = :annotationSyncId")
    suspend fun getByAnnotationSyncId(annotationSyncId: String): HighlightReviewEntity?

    @Upsert
    suspend fun upsert(review: HighlightReviewEntity)

    /** Schedules of highlights that no longer exist at all (a purged book), not merely soft-deleted ones. */
    @Query("DELETE FROM highlight_reviews WHERE annotationSyncId NOT IN (SELECT syncId FROM annotations)")
    suspend fun deleteOrphans(): Int
}
