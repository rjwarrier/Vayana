package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vayana.core.database.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnnotationEntity>>

    /** Smart shelves need membership, not full highlighted text and note bodies. */
    @Query(
        "SELECT DISTINCT bookId FROM annotations WHERE isDeleted = 0 " +
            "AND (type != 'BOOKMARK' OR LENGTH(TRIM(COALESCE(readerNote, ''), " +
            "CHAR(9,10,11,12,13,28,29,30,31,32,160,5760,8192,8193,8194,8195,8196,8197,8198,8199,8200,8201,8202,8232,8233,8239,8287,12288))) > 0) " +
            "AND NOT (type = 'UNDERLINE' AND colorKey = 'popular' " +
            "AND (locator GLOB 'quote:*' OR locator GLOB 'goodreads-quote:*'))",
    )
    fun observePersonalNotesBookIds(): Flow<List<Long>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeForBook(bookId: Long): Flow<List<AnnotationEntity>>

    @Query("SELECT COUNT(*) FROM annotations WHERE bookId = :bookId AND isDeleted = 0")
    fun observeCountForBook(bookId: Long): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM annotations WHERE bookId = :bookId AND isDeleted = 0 " +
            "AND type = 'UNDERLINE' AND colorKey = 'popular' " +
            "AND (locator LIKE 'quote:%' OR locator LIKE 'goodreads-quote:%')",
    )
    fun observeCommunityQuoteCountForBook(bookId: Long): Flow<Int>

    @Query("SELECT * FROM annotations WHERE id = :id")
    suspend fun getById(id: Long): AnnotationEntity?

    @Query("SELECT * FROM annotations WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<AnnotationEntity>

    /** Unlike [observeAll]/[observeForBook], deliberately not filtered by isDeleted - sync merge
     *  needs to see the current deletion state to decide how to reconcile it. */
    @Query("SELECT * FROM annotations WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): AnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(annotation: AnnotationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(annotations: List<AnnotationEntity>): List<Long>

    @Update
    suspend fun update(annotation: AnnotationEntity)

    @Query("UPDATE annotations SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("UPDATE annotations SET isDeleted = 1, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun softDeleteAll(ids: List<Long>, updatedAt: Long)

    @Query("UPDATE annotations SET isDeleted = 1, updatedAt = :updatedAt WHERE syncId = :syncId AND isDeleted = 0 AND updatedAt <= :updatedAt")
    suspend fun softDeleteBySyncId(syncId: String, updatedAt: Long): Int

    @Query("UPDATE annotations SET isDeleted = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long)

    /** Permanently purges an already soft-deleted row - never call this directly on a live one. */
    @Query("DELETE FROM annotations WHERE id = :id AND isDeleted = 1")
    suspend fun purge(id: Long)

    /** Active annotations on active books matching an FTS [match], newest first. */
    @Query(
        "SELECT annotations.* FROM annotations " +
            "JOIN annotations_fts ON annotations.id = annotations_fts.rowid " +
            "JOIN books ON books.id = annotations.bookId " +
            "WHERE annotations_fts MATCH :match AND annotations.isDeleted = 0 AND books.isDeleted = 0 " +
            "ORDER BY annotations.updatedAt DESC LIMIT :limit",
    )
    fun observeSearch(match: String, limit: Int): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations ORDER BY syncId ASC")
    suspend fun getAllForSync(): List<AnnotationEntity>
}
