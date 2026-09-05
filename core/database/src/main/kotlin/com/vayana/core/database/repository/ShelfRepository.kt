package com.vayana.core.database.repository

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.Shelf
import kotlinx.coroutines.flow.Flow

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
}
