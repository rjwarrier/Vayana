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
)

enum class CloudBookMergeResult {
    CREATED,
    UPDATED,
    SKIPPED,
}

interface BookRepository {
    fun observeAll(): Flow<List<Book>>

    /** Soft-deleted books, newest deletion first - backs the "Recently deleted" restore screen. */
    fun observeDeleted(): Flow<List<Book>>

    suspend fun getById(id: Long): Book?

    suspend fun findActiveBySyncIdOrHash(syncId: String, fileHash: String): Book?

    suspend fun updateLocator(id: Long, locator: String, readingPercent: Float)

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

    suspend fun addReadingTime(id: Long, addedSeconds: Long)

    suspend fun recordBookOpened(id: Long)

    suspend fun updateMetadata(id: Long, title: String, author: String?, series: String?, seriesNumber: String?, description: String?)

    suspend fun updateCover(id: Long, coverPath: String?)

    /** Returns null if a book with the same [fileHash] already exists (import-time dedupe, PROMPT2appbuild.md §4.1). */
    suspend fun insertIfNew(
        title: String,
        author: String?,
        series: String?,
        seriesNumber: String?,
        description: String?,
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

    /** Permanently removes an already soft-deleted book row - never call this on a live book. */
    suspend fun purge(id: Long)

    suspend fun markFinished(id: Long)

    /** Null clears that field back to the global reader setting; non-null overrides it for this book only. */
    suspend fun updateReaderPrefs(id: Long, fontSizePercent: Int?, lineHeight: Float?, fontFamily: String?, sideMarginPercent: Int?)

    suspend fun clearReaderPrefs(id: Long)

    /** Books currently queued in "Read next", in queue order (earliest added = next up). */
    fun observeReadNextQueue(): Flow<List<Book>>

    suspend fun setReadNext(id: Long, queued: Boolean)

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
