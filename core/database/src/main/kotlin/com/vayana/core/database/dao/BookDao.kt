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

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE fileHash = :fileHash AND isDeleted = 0 LIMIT 1")
    suspend fun findByHash(fileHash: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("UPDATE books SET title = :title, author = :author, series = :series, description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMetadata(id: Long, title: String, author: String?, series: String?, description: String?, updatedAt: Long)

    @Query("UPDATE books SET coverPath = :coverPath, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCover(id: Long, coverPath: String?, updatedAt: Long)

    @Query(
        "UPDATE books SET title = :title, author = :author, series = :series, description = :description, " +
            "coverPath = :coverPath, filePath = :filePath, format = :format, fileHash = :fileHash, " +
            "lastLocator = NULL, readingPercent = 0, updatedAt = :updatedAt, lastReadAt = NULL WHERE id = :id",
    )
    suspend fun replaceSource(
        id: Long,
        title: String,
        author: String?,
        series: String?,
        description: String?,
        coverPath: String?,
        filePath: String,
        format: String,
        fileHash: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE books SET lastLocator = :locator, readingPercent = :readingPercent, " +
            "updatedAt = :updatedAt, lastReadAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateLocator(id: Long, locator: String, readingPercent: Float, updatedAt: Long)
}
