package com.vayana.core.database.repository

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.PhysicalBookOwnership
import com.vayana.core.database.model.normalizedBookTagsCsv
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
    val syncedDeviceLabel: String? = null,
    val syncedAt: Long? = null,
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
    /** The file asset; all null for a book read outside the app ([BookFormat.isOffline]). */
    val assetId: String?,
    val assetSha256: String?,
    val assetSizeBytes: Long?,
    val assetUploadedAt: Long?,
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
    /** Optional physical-book loan metadata; null for older clients and non-physical formats. */
    val physicalOwnership: PhysicalBookOwnership? = null,
    val borrowReturnAt: Long? = null,
    /** Optional source identity; absent in snapshots written before database version 27. */
    val gutenbergId: Long? = null,
    /** When the book was last deleted or restored on the device that exported it; null from older app versions. */
    val deletionUpdatedAt: Long? = null,
    val readNextPinned: Boolean = false,
    val readingDisposition: String = "ACTIVE",
    val dispositionReason: String? = null,
    val dispositionUpdatedAt: Long? = null,
)

enum class CloudBookMergeResult {
    CREATED,
    UPDATED,
    SKIPPED,
}

interface BookRepository {
    fun observeAll(): Flow<List<Book>>

    /** The most recently read book whose file is on this device, for the widget and the launcher shortcut. */
    fun observeContinueReading(): Flow<Book?>

    suspend fun hasAnyBooks(): Boolean

    /** Root-relative paths of every cover file any book (deleted ones included) still uses. */
    suspend fun referencedCoverPaths(): Set<String>

    /** The most recently read book that can open in the reader on this device, or null. */
    suspend fun lastReadOpenableBookId(): Long?

    /** The most recently read active book, whether or not its file is on this device. */
    suspend fun lastReadBookId(): Long?

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
        syncedDeviceLabel: String? = null,
        syncedAt: Long? = null,
    ): ReadingProgressMergeResult

    /** Applies a newer remote Read Next change without letting ordinary reading timestamps override it. */
    suspend fun applySyncedReadNext(
        syncId: String,
        fileHash: String,
        addedAt: Long?,
        remoteUpdatedAt: Long,
        pinned: Boolean = false,
    ): Boolean

    suspend fun addReadingTime(id: Long, addedSeconds: Long)

    suspend fun recordBookOpened(id: Long)

    suspend fun updateMetadata(id: Long, title: String, author: String?, series: String?, seriesNumber: String?, description: String?, tagsCsv: String?)

    /** Adds tags while retaining current metadata; returns false for unchanged or read-only books. */
    suspend fun addTags(id: Long, tagsCsv: String): Boolean {
        val book = getById(id) ?: return false
        if (book.isHomeLibrary) return false
        val merged = listOfNotNull(book.tagsCsv, tagsCsv).joinToString(",").normalizedBookTagsCsv()
        if (merged == book.tagsCsv) return false
        updateMetadata(id, book.title, book.author, book.series, book.seriesNumber, book.description, merged)
        return true
    }

    suspend fun updateRating(id: Long, rating: Float)

    /** Corrects when reading started and finished (null leaves the book unstarted or unfinished). */
    suspend fun updateReadingDates(id: Long, startedAt: Long?, finishedAt: Long?)

    /** Switches a book read outside the app to another offline type ([BookFormat.isOffline]). */
    suspend fun updateOfflineFormat(id: Long, format: BookFormat)

    /** Updates ownership and optional return date for a physical book. */
    suspend fun updatePhysicalBookLoan(id: Long, ownership: PhysicalBookOwnership, borrowReturnAt: Long?)

    /** Adds a file-less entry for a book read outside the app (see [BookFormat.isOffline]). */
    suspend fun insertOfflineBook(
        title: String,
        author: String?,
        format: BookFormat,
        startedAt: Long?,
        finishedAt: Long?,
        pageCount: Int? = null,
        currentPage: Int? = null,
        physicalOwnership: PhysicalBookOwnership = PhysicalBookOwnership.OWNED,
        borrowReturnAt: Long? = null,
    ): Book

    /**
     * Sets an offline book's total pages and the page the reader is on; progress follows, and reaching the last page
     * finishes the book. A [currentPage] without a [pageCount] is ignored.
     */
    suspend fun updateOfflinePages(id: Long, pageCount: Int?, currentPage: Int?)

    /** Marks a physical book as currently reading and records a reading occasion without inventing timed minutes. */
    suspend fun markPhysicalBookReading(id: Long)

    /** Atomically logs time, page checkpoints and progress. Retrying a session ID never counts it twice. */
    suspend fun recordPhysicalReadingSession(id: Long, syncId: String, startedAt: Long, endedAt: Long,
        durationSeconds: Long, startPage: Int, endPage: Int, pageCount: Int?, updateProgress: Boolean = true, activeIntervals: String? = null)

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

    /** Returns null if a book with the same [fileHash] already exists (import-time dedupe, docs/PRODUCT_SPEC.md §4.1). */
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
        gutenbergId: Long? = null,
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

    /** Queues or unqueues [id]. A full queue rejects additions without evicting books; the legacy removal result stays empty. */
    suspend fun setReadNext(id: Long, queued: Boolean, capacity: Int = 50): List<Book>
    suspend fun reorderReadNext(ids: List<Long>)
    suspend fun pinReadNext(id: Long, pinned: Boolean)
    suspend fun updateDisposition(id: Long, disposition: String, reason: String?)


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

    /** Installs a cleaned copy only if the source has not changed while it was being processed. */
    suspend fun attachCleanedSource(id: Long, expectedHash: String, filePath: String, fileHash: String): Boolean

    suspend fun mergeCloudBook(record: CloudBookRecord): CloudBookMergeResult
}

/** Adding a new queue entry never removes another book to make room. */
class ReadNextQueueFullException : IllegalStateException("Read Next queue is full")
