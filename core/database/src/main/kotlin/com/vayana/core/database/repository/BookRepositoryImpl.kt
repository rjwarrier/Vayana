package com.vayana.core.database.repository

import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
) : BookRepository {

    override fun observeAll(): Flow<List<Book>> =
        bookDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: Long): Book? = bookDao.getById(id)?.toDomain()

    override suspend fun updateLocator(id: Long, locator: String, readingPercent: Float) {
        bookDao.updateLocator(id, locator, readingPercent, System.currentTimeMillis())
    }

    override suspend fun insertIfNew(
        title: String,
        author: String?,
        description: String?,
        coverPath: String?,
        filePath: String,
        format: BookFormat,
        fileHash: String,
    ): Book? {
        if (bookDao.findByHash(fileHash) != null) return null

        val now = System.currentTimeMillis()
        val entity = BookEntity(
            title = title,
            author = author,
            description = description,
            coverPath = coverPath,
            filePath = filePath,
            format = format.name,
            fileHash = fileHash,
            lastLocator = null,
            readingPercent = 0f,
            rating = 0f,
            groupId = null,
            isDeleted = false,
            wordCount = null,
            pageEstimate = null,
            createdAt = now,
            updatedAt = now,
            lastReadAt = null,
        )
        val id = bookDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun softDelete(id: Long) {
        bookDao.softDelete(id, System.currentTimeMillis())
    }
}

private fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    author = author,
    description = description,
    coverPath = coverPath,
    filePath = filePath,
    format = BookFormat.valueOf(format),
    fileHash = fileHash,
    readingPercent = readingPercent,
    rating = rating,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastReadAt = lastReadAt,
    lastLocator = lastLocator,
)
