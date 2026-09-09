package com.vayana.core.database.repository

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.Shelf
import kotlinx.coroutines.flow.Flow

data class CloudShelfRecord(
    val syncId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CloudShelfMembershipRecord(
    val bookSyncId: String,
    val shelfSyncId: String,
    val createdAt: Long,
)

enum class ShelfMergeResult { CREATED, UPDATED, SKIPPED }

enum class ShelfMembershipMergeResult { CREATED, SKIPPED }

interface ShelfRepository {
    fun observeAll(): Flow<List<Shelf>>

    suspend fun create(name: String): Shelf

    suspend fun rename(id: Long, name: String)

    suspend fun delete(id: Long)

    fun observeBooksForShelf(shelfId: Long): Flow<List<Book>>

    fun observeShelvesForBook(bookId: Long): Flow<List<Shelf>>

    fun observeShelfBookCount(shelfId: Long): Flow<Int>

    suspend fun addBookToShelf(bookId: Long, shelfId: Long)

    suspend fun removeBookFromShelf(bookId: Long, shelfId: Long)

    suspend fun mergeCloudShelf(record: CloudShelfRecord): ShelfMergeResult

    suspend fun mergeCloudMembership(record: CloudShelfMembershipRecord): ShelfMembershipMergeResult

    /** Applies a cloud shelf-membership deletion tombstone, resolving [bookSyncId] through book aliases. Returns rows removed. */
    suspend fun applyMembershipTombstone(bookSyncId: String, shelfSyncId: String, deletedAt: Long): Int
}
