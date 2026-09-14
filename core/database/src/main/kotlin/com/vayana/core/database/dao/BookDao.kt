package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vayana.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun observeDeleted(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE fileHash = :fileHash AND isDeleted = 0 LIMIT 1")
    suspend fun findByHash(fileHash: String): BookEntity?

    @Query("SELECT * FROM books WHERE syncId = :syncId AND isDeleted = 0 LIMIT 1")
    suspend fun findBySyncId(syncId: String): BookEntity?

    @Query("SELECT * FROM books WHERE syncId = :syncId LIMIT 1")
    suspend fun findAnyBySyncId(syncId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET isDeleted = 1, deletionUpdatedAt = :updatedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    /** A delete synced from another device; skipped when the book was deleted or restored here after [deletedAt]. */
    @Query(
        "UPDATE books SET isDeleted = 1, deletionUpdatedAt = :deletedAt " +
            "WHERE syncId = :syncId AND isDeleted = 0 AND COALESCE(deletionUpdatedAt, 0) < :deletedAt",
    )
    suspend fun applySyncedDeletion(syncId: String, deletedAt: Long): Int

    @Query("UPDATE books SET isDeleted = 0, deletionUpdatedAt = :updatedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long)

    /** A restore synced from another device, which restored the book at [deletionUpdatedAt]. */
    @Query("UPDATE books SET isDeleted = 0, deletionUpdatedAt = :deletionUpdatedAt WHERE id = :id")
    suspend fun restoreFromSync(id: Long, deletionUpdatedAt: Long)

    /** Deletes a book row in any state; annotations, reading sessions and shelf links cascade. */
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE books SET title = :title, author = :author, series = :series, seriesNumber = :seriesNumber, description = :description, tagsCsv = :tagsCsv, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMetadata(id: Long, title: String, author: String?, series: String?, seriesNumber: String?, description: String?, tagsCsv: String?, updatedAt: Long)

    @Query("UPDATE books SET rating = :rating, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateRating(id: Long, rating: Float, updatedAt: Long)

    @Query(
        """
        UPDATE books
        SET coverPath = :coverPath,
            coverAssetId = NULL,
            coverAssetSha256 = NULL,
            coverAssetSizeBytes = NULL,
            coverAssetUploadedAt = NULL,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateCover(id: Long, coverPath: String?, updatedAt: Long)

    @Query(
        "UPDATE books SET goodreadsUrl = :goodreadsUrl, goodreadsRating = :rating, goodreadsRatingsCount = :ratingsCount, " +
            "originalPublicationYear = :originalPublicationYear WHERE id = :id",
    )
    suspend fun updateGoodreadsInfo(id: Long, goodreadsUrl: String?, rating: Float?, ratingsCount: Int?, originalPublicationYear: Int?)

    @Query("UPDATE books SET customCoverPath = :customCoverPath, goodreadsCoverPath = :goodreadsCoverPath WHERE id = :id")
    suspend fun updateCoverAlternates(id: Long, customCoverPath: String?, goodreadsCoverPath: String?)

    @Query(
        "UPDATE books SET title = :title, author = :author, series = :series, seriesNumber = :seriesNumber, description = :description, " +
            "coverPath = :coverPath, filePath = :filePath, fileAvailability = 'LOCAL', format = :format, fileHash = :fileHash, " +
            "fileAssetId = NULL, fileAssetSha256 = NULL, fileAssetSizeBytes = NULL, fileAssetUploadedAt = NULL, " +
            "coverAssetId = NULL, coverAssetSha256 = NULL, coverAssetSizeBytes = NULL, coverAssetUploadedAt = NULL, " +
            "lastLocator = NULL, readingPercent = 0, startedReadingAt = NULL, finishedReadingAt = NULL, totalReadingSeconds = 0, " +
            "updatedAt = :updatedAt, lastReadAt = NULL WHERE id = :id",
    )
    suspend fun replaceSource(
        id: Long,
        title: String,
        author: String?,
        series: String?,
        seriesNumber: String?,
        description: String?,
        coverPath: String?,
        filePath: String,
        format: String,
        fileHash: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE books SET lastLocator = :locator, readingPercent = :readingPercent, " +
            "startedReadingAt = CASE WHEN startedReadingAt IS NULL THEN :updatedAt ELSE startedReadingAt END, " +
            "finishedReadingAt = CASE WHEN :readingPercent >= :finishedThreshold THEN (CASE WHEN finishedReadingAt IS NULL THEN :updatedAt ELSE finishedReadingAt END) ELSE finishedReadingAt END, " +
            "updatedAt = :updatedAt, lastReadAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateLocator(id: Long, locator: String, readingPercent: Float, finishedThreshold: Float, updatedAt: Long)

    @Query(
        """
        UPDATE books
        SET lastLocator = :locator,
            readingPercent = :readingPercent,
            startedReadingAt = COALESCE(startedReadingAt, :startedReadingAt),
            finishedReadingAt = CASE
                WHEN :finishedReadingAt IS NOT NULL AND (finishedReadingAt IS NULL OR :finishedReadingAt < finishedReadingAt) THEN :finishedReadingAt
                ELSE finishedReadingAt
            END,
            totalReadingSeconds = CASE
                WHEN :totalReadingSeconds > totalReadingSeconds THEN :totalReadingSeconds
                ELSE totalReadingSeconds
            END,
            updatedAt = :remoteUpdatedAt,
            lastReadAt = :lastReadAt
        WHERE id = :id
        """,
    )
    suspend fun applySyncedReadingProgress(
        id: Long,
        locator: String,
        readingPercent: Float,
        lastReadAt: Long?,
        remoteUpdatedAt: Long,
        startedReadingAt: Long?,
        finishedReadingAt: Long?,
        totalReadingSeconds: Long,
    )

    @Query(
        "UPDATE books SET totalReadingSeconds = totalReadingSeconds + :addedSeconds, " +
            "startedReadingAt = CASE WHEN startedReadingAt IS NULL THEN :updatedAt ELSE startedReadingAt END, " +
            "updatedAt = :updatedAt, lastReadAt = :updatedAt WHERE id = :id",
    )
    suspend fun addReadingTime(id: Long, addedSeconds: Long, updatedAt: Long)

    @Query(
        "UPDATE books SET startedReadingAt = CASE WHEN startedReadingAt IS NULL THEN :timestamp ELSE startedReadingAt END, " +
            "lastReadAt = :timestamp, updatedAt = :timestamp WHERE id = :id",
    )
    suspend fun recordBookOpened(id: Long, timestamp: Long)

    @Query(
        "UPDATE books SET readingPercent = 1.0, " +
            "startedReadingAt = CASE WHEN startedReadingAt IS NULL THEN :timestamp ELSE startedReadingAt END, " +
            "finishedReadingAt = CASE WHEN finishedReadingAt IS NULL THEN :timestamp ELSE finishedReadingAt END, " +
            "updatedAt = :timestamp WHERE id = :id",
    )
    suspend fun markFinished(id: Long, timestamp: Long)

    @Query(
        "UPDATE books SET lastLocator = NULL, readingPercent = 0, startedReadingAt = NULL, finishedReadingAt = NULL, " +
            "totalReadingSeconds = 0, lastReadAt = NULL, updatedAt = :timestamp WHERE id = :id",
    )
    suspend fun resetReadingStats(id: Long, timestamp: Long)

    @Query(
        "UPDATE books SET customFontSizePercent = :fontSizePercent, customLineHeight = :lineHeight, " +
            "customFontFamily = :fontFamily, customSideMarginPercent = :sideMarginPercent, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateReaderPrefs(
        id: Long,
        fontSizePercent: Int?,
        lineHeight: Float?,
        fontFamily: String?,
        sideMarginPercent: Int?,
        updatedAt: Long,
    )

    @Query(
        "UPDATE books SET customFontSizePercent = NULL, customLineHeight = NULL, " +
            "customFontFamily = NULL, customSideMarginPercent = NULL, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun clearReaderPrefs(id: Long, updatedAt: Long)

    @Query("UPDATE books SET readNextAddedAt = :readNextAddedAt, readNextUpdatedAt = :updatedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setReadNext(id: Long, readNextAddedAt: Long?, updatedAt: Long)

    @Query("SELECT id FROM books WHERE readNextAddedAt IS NOT NULL AND isDeleted = 0 ORDER BY readNextAddedAt DESC")
    suspend fun getReadNextQueueIdsNewestFirst(): List<Long>

    /** Doesn't touch updatedAt: trimming the queue after a sync merge isn't a user edit. */
    @Query("UPDATE books SET readNextAddedAt = NULL WHERE id IN (:ids)")
    suspend fun clearReadNextKeepingUpdatedAt(ids: List<Long>)

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE isDeleted = 0)")
    suspend fun hasAnyBooks(): Boolean

    /** Ids of active books matching an FTS [match], most recently read first. */
    @Query(
        "SELECT books.id FROM books JOIN books_fts ON books.id = books_fts.rowid " +
            "WHERE books_fts MATCH :match AND books.isDeleted = 0 " +
            "ORDER BY COALESCE(books.lastReadAt, books.updatedAt) DESC, books.title COLLATE NOCASE LIMIT :limit",
    )
    fun observeSearchIds(match: String, limit: Int): Flow<List<Long>>

    @Query("SELECT * FROM books ORDER BY syncId ASC")
    suspend fun getAllForSync(): List<BookEntity>

    @Query(
        """
        UPDATE books
        SET fileAvailability = 'LOCAL',
            filePath = :filePath,
            fileHash = :fileHash,
            fileAssetId = :assetId,
            fileAssetSha256 = :assetSha256,
            fileAssetSizeBytes = :assetSizeBytes,
            fileAssetUploadedAt = :assetUploadedAt,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun attachDownloadedFile(
        id: Long,
        filePath: String,
        fileHash: String,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE books
        SET fileAvailability = 'CLOUD_ONLY',
            filePath = '',
            updatedAt = :updatedAt
        WHERE id = :id
            AND fileAssetId IS NOT NULL
            AND fileAssetSha256 IS NOT NULL
            AND fileAssetSizeBytes IS NOT NULL
            AND fileAssetUploadedAt IS NOT NULL
        """,
    )
    suspend fun removeLocalFile(id: Long, updatedAt: Long): Int

    @Query(
        """
        UPDATE books
        SET fileAssetId = :assetId,
            fileAssetSha256 = :assetSha256,
            fileAssetSizeBytes = :assetSizeBytes,
            fileAssetUploadedAt = :assetUploadedAt,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markFileAssetUploaded(
        id: Long,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE books
        SET coverAssetId = :assetId,
            coverAssetSha256 = :assetSha256,
            coverAssetSizeBytes = :assetSizeBytes,
            coverAssetUploadedAt = :assetUploadedAt,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markCoverAssetUploaded(
        id: Long,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE books
        SET coverPath = :coverPath,
            coverAssetId = :assetId,
            coverAssetSha256 = :assetSha256,
            coverAssetSizeBytes = :assetSizeBytes,
            coverAssetUploadedAt = :assetUploadedAt,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun attachDownloadedCover(
        id: Long,
        coverPath: String,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
        updatedAt: Long,
    )
}
