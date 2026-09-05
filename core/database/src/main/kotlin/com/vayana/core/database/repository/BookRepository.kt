package com.vayana.core.database.repository

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeAll(): Flow<List<Book>>

    /** Soft-deleted books, newest deletion first - backs the "Recently deleted" restore screen. */
    fun observeDeleted(): Flow<List<Book>>

    suspend fun getById(id: Long): Book?

    suspend fun updateLocator(id: Long, locator: String, readingPercent: Float)

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
}
