package com.vayana.core.database.repository

import com.vayana.core.database.dao.ShelfDao
import com.vayana.core.database.entity.BookShelfCrossRefEntity
import com.vayana.core.database.entity.ShelfEntity
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.Shelf
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ShelfRepositoryImpl @Inject constructor(
    private val shelfDao: ShelfDao,
) : ShelfRepository {

    override fun observeAll(): Flow<List<Shelf>> =
        shelfDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun create(name: String): Shelf {
        val now = System.currentTimeMillis()
        val entity = ShelfEntity(name = name, createdAt = now, updatedAt = now)
        val id = shelfDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun rename(id: Long, name: String) {
        shelfDao.rename(id, name, System.currentTimeMillis())
    }

    override suspend fun delete(id: Long) {
        shelfDao.delete(id)
    }

    override fun observeBooksForShelf(shelfId: Long): Flow<List<Book>> =
        shelfDao.observeBooksForShelf(shelfId).map { entities -> entities.map { it.toDomain() } }

    override fun observeShelvesForBook(bookId: Long): Flow<List<Shelf>> =
        shelfDao.observeShelvesForBook(bookId).map { entities -> entities.map { it.toDomain() } }

    override fun observeShelfBookCount(shelfId: Long): Flow<Int> = shelfDao.observeShelfBookCount(shelfId)

    override suspend fun addBookToShelf(bookId: Long, shelfId: Long) {
        shelfDao.addBookToShelf(BookShelfCrossRefEntity(bookId = bookId, shelfId = shelfId, createdAt = System.currentTimeMillis()))
    }

    override suspend fun removeBookFromShelf(bookId: Long, shelfId: Long) {
        shelfDao.removeBookFromShelf(bookId, shelfId)
    }
}

private fun ShelfEntity.toDomain(): Shelf = Shelf(id = id, name = name, createdAt = createdAt, updatedAt = updatedAt)
