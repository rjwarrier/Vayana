package com.vayana.core.database.repository

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import kotlinx.coroutines.flow.Flow

sealed interface ReadingProgressMergeResult {
    data object AppliedRemote : ReadingProgressMergeResult
    data object LocalNewer : ReadingProgressMergeResult
    data object NoLocalMatch : ReadingProgressMergeResult
    data object InvalidRemote : ReadingProgressMergeResult
    data class ConflictLocalKept(
        val local: ReadingProgressVersion,
        val remote: ReadingProgressVersion,
        val reason: ReadingProgressConflictReason,
    ) : ReadingProgressMergeResult
}

data class ReadingProgressVersion(
    val syncId: String,
    val fileHash: String,
    val locator: String?,
    val readingPercent: Float,
    val lastReadAt: Long?,
    val updatedAt: Long,
)

data class RemoteReadingProgressApplied(
    val bookId: Long,
    val locator: String,
    val readingPercent: Float,
    val version: Long,
)

enum class ReadingProgressConflictReason {
    SAME_TIMESTAMP_DIFFERENT_LOCATOR,
    INCOMPATIBLE_FILE_REVISION,
}

data class CloudBookRecord(
    val syncId: String,
    val title: String,
    val author: String?,
    val series: String?,
    val seriesNumber: String?,
    val description: String?,
    val tagsCsv: String?,
    val format: BookFormat,
    val fileHash: String,
    val assetId: String,
    val assetSha256: String,
    val assetSizeBytes: Long,
    val assetUploadedAt: Long,
    val coverAssetId: String?,
    val coverAssetSha256: String?,
    val coverAssetSizeBytes: Long?,
    val coverAssetUploadedAt: Long?,
    val lastLocator: String?,
    val readingPercent: Float,
    val rating: Float,
    val wordCount: Int?,
    val pageEstimate: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastReadAt: Long?,
    val startedReadingAt: Long?,
    val finishedReadingAt: Long?,
    val totalReadingSeconds: Long,
    val customFontSizePercent: Int?,
    val customLineHeight: Float?,
    val customFontFamily: String?,
    val customSideMarginPercent: Int?,
    val readNextAddedAt: Long?,
    val readNextUpdatedAt: Long?,
    val goodreadsUrl: String?,
    val goodreadsRating: Float?,
    val goodreadsRatingsCount: Int?,
    val originalPublicationYear: Int?,
    /** When the book was last deleted or restored on the device that exported it; null from older app versions. */
    val deletionUpdatedAt: Long? = null,
)

enum class CloudBookMergeResult {
    CREATED,
    UPDATED,
    SKIPPED,
}

interface BookRepository {
    fun observeAll(): Flow<List<Book>>

    suspend fun hasAnyBooks(): Boolean

    /** The most recently read book that can open in the reader on this device, or null. */
    suspend fun lastReadOpenableBookId(): Long?

    /** Ids of books whose text matches every word of [text] (as word prefixes), most recently read first. */
    fun observeSearchIds(text: String, limit: Int): Flow<List<Long>>

    val remoteReadingProgressApplied: Flow<RemoteReadingProgressApplied>

    /** Soft-deleted books, newest deletion first - backs the "Recently deleted" restore screen. */
    fun observeDeleted(): Flow<List<Book>>

    /** Ids of books that were moved to Recently deleted before [cutoff] (epoch millis). */
    suspend fun deletedBookIdsBefore(cutoff: Long): List<Long>

    suspend fun getById(id: Long): Book?

    suspend fun findActiveBySyncIdOrHash(syncId: String, fileHash: String): Book?

    /** Saves the reading position; the first time [readingPercent] reaches [finishedThreshold], stamps the finish date. */
    suspend fun updateLocator(id: Long, locator: String, readingPercent: Float, finishedThreshold: Float)

    suspend fun applySyncedReadingProgress(
        syncId: String,
        fileHash: String,
        locator: String,
        readingPercent: Float,
        lastReadAt: Long?,
        remoteUpdatedAt: Long,
        startedReadingAt: Long?,
        finishedReadingAt: Long?,
        totalReadingSeconds: Long,
    ): ReadingProgressMergeResult

    /** Applies a newer remote Read Next change without letting ordinary reading timestamps override it. */
    suspend fun applySyncedReadNext(
        syncId: String,
        fileHash: String,
        addedAt: Long?,
        remoteUpdatedAt: Long,
    ): Boolean

    suspend fun addReadingTime(id: Long, addedSeconds: Long)

    suspend fun recordBookOpened(id: Long)

    suspend fun updateMetadata(id: Long, title: String, author: String?, series: String?, seriesNumber: String?, description: String?, tagsCsv: String?)

    suspend fun updateRating(id: Long, rating: Float)

    /** Corrects when reading started and finished (null leaves the book unstarted or unfinished). */
    suspend fun updateReadingDates(id: Long, startedAt: Long?, finishedAt: Long?)

    suspend fun updateCover(id: Long, coverPath: String?)

    /** Goodreads import extras. Saving them does not disturb reading-position conflict detection. */
    suspend fun updateGoodreadsInfo(
        id: Long,
        goodreadsUrl: String?,
        rating: Float?,
        ratingsCount: Int?,
        originalPublicationYear: Int?,
    )

    /** Records the two covers a book can switch between; [updateCover] still sets the one in use. */
    suspend fun updateCoverAlternates(id: Long, customCoverPath: String?, goodreadsCoverPath: String?)

    /** Returns null if a book with the same [fileHash] already exists (import-time dedupe, PROMPT2appbuild.md §4.1). */
    suspend fun insertIfNew(
        title: String,
        author: String?,
        series: String?,
        seriesNumber: String?,
        description: String?,
        tagsCsv: String? = null,
        coverPath: String?,
        filePath: String,
        format: BookFormat,
        fileHash: String,
    ): Book?

    /** Returns false when another active book already owns [fileHash]. */
    suspend fun replaceSource(
        id: Long,
        title: String,
        author: String?,
        series: String?,
        seriesNumber: String?,
        description: String?,
        coverPath: String?,
        filePath: String,
        format: BookFormat,
        fileHash: String,
    ): Boolean

    suspend fun softDelete(id: Long)

    /** Restores a soft-deleted book (annotations were never touched by [softDelete], so nothing else to restore). */
    suspend fun restore(id: Long)

    /**
     * Deletes a book permanently, everywhere: writes a `book` tombstone (older app versions soft-delete it) and a
     * [TombstoneEntityType.BOOK_PURGE] one, queues its cloud assets for deletion, detaches its vocabulary words and
     * removes the row with its highlights, notes, reading sessions and shelf links. Deleting the local files in
     * [PurgedBook.localFilePaths] is the caller's job once this returns. Null when there is no such book.
     */
    suspend fun purgeEverywhere(id: Long): PurgedBook?

    /** Applies a synced [TombstoneEntityType.BOOK_PURGE] for [bookSyncId]; on this device, the same as [purgeEverywhere]. */
    suspend fun applyPurgeTombstone(bookSyncId: String, deletedAt: Long): PurgedBook?

    /**
     * Applies a book delete synced from another device: moves the book to Recently deleted unless it was deleted or
     * restored here after [deletedAt]. Reading doesn't count. Returns the book's title when it was moved, else null.
     */
    suspend fun applyBookTombstone(bookSyncId: String, deletedAt: Long): String?

    suspend fun markFinished(id: Long)

    /**
     * Puts the book back to unread: progress, reading position, started/finished dates, reading time, last-read time
     * and its reading sessions all go. Highlights, notes, rating and metadata stay. Tombstones keep sync from
     * restoring what was cleared - the sessions, and any progress recorded before the reset.
     */
    suspend fun resetReadingStats(id: Long)

    /**
     * Applies a reading-stats reset made on another device at [resetAt]: clears the book's stats and its sessions from
     * before that moment, unless this device has read the book since. Returns 1 if anything was reset, else 0.
     */
    suspend fun applyReadingStatsReset(bookSyncId: String, resetAt: Long): Int

    /** Null clears that field back to the global reader setting; non-null overrides it for this book only. */
    suspend fun updateReaderPrefs(id: Long, fontSizePercent: Int?, lineHeight: Float?, fontFamily: String?, sideMarginPercent: Int?)

    suspend fun clearReaderPrefs(id: Long)

    /** Queues or unqueues [id]; returns the books dropped from "Read next" to keep it within its cap. */
    suspend fun setReadNext(id: Long, queued: Boolean): List<Book>

    suspend fun attachDownloadedFile(
        id: Long,
        filePath: String,
        fileHash: String,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
    )

    /** Marks a cloud-backed book as file-less on this device. Returns false if no cloud asset is attached. */
    suspend fun removeLocalFile(id: Long): Boolean

    suspend fun markFileAssetUploaded(
        id: Long,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
    )

    suspend fun markCoverAssetUploaded(
        id: Long,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
    )

    suspend fun attachDownloadedCover(
        id: Long,
        coverPath: String,
        assetId: String,
        assetSha256: String,
        assetSizeBytes: Long,
        assetUploadedAt: Long,
    )

    suspend fun mergeCloudBook(record: CloudBookRecord): CloudBookMergeResult
}
