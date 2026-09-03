package com.vayana.core.database.repository

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeAll(): Flow<List<Book>>

    suspend fun getById(id: Long): Book?

    suspend fun updateLocator(id: Long, locator: String, readingPercent: Float)

    suspend fun updateMetadata(id: Long, title: String, author: String?, description: String?)

    /** Returns null if a book with the same [fileHash] already exists (import-time dedupe, PROMPT2appbuild.md §4.1). */
    suspend fun insertIfNew(
        title: String,
        author: String?,
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
        description: String?,
        coverPath: String?,
        filePath: String,
        format: BookFormat,
        fileHash: String,
    ): Boolean

    suspend fun softDelete(id: Long)
}
