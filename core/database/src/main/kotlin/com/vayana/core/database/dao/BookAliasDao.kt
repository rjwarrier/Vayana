package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vayana.core.database.entity.BookAliasEntity

@Dao
interface BookAliasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: BookAliasEntity)

    @Query("SELECT * FROM book_aliases WHERE fileHash = :fileHash LIMIT 1")
    suspend fun findByFileHash(fileHash: String): BookAliasEntity?

    @Query("SELECT * FROM book_aliases WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): BookAliasEntity?

    @Query("SELECT * FROM book_aliases")
    suspend fun getAll(): List<BookAliasEntity>

    @Query("DELETE FROM book_aliases WHERE syncId = :syncId OR fileHash = :fileHash")
    suspend fun deleteForBook(syncId: String, fileHash: String)
}
