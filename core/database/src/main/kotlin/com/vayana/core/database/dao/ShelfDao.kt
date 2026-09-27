package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.entity.BookShelfCrossRefEntity
import com.vayana.core.database.entity.ShelfEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {
    @Query("SELECT * FROM shelves ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ShelfEntity>>

    @Query("SELECT * FROM shelves WHERE id = :id")
    suspend fun getById(id: Long): ShelfEntity?

    @Query("SELECT * FROM shelves WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): ShelfEntity?

    @Insert
    suspend fun insert(shelf: ShelfEntity): Long

    @Update
    suspend fun update(shelf: ShelfEntity)

    @Query("DELETE FROM shelves WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM shelves WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookToShelf(crossRef: BookShelfCrossRefEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookToShelfIfAbsent(crossRef: BookShelfCrossRefEntity): Long

    @Query("DELETE FROM book_shelf_cross_ref WHERE bookId = :bookId AND shelfId = :shelfId")
    suspend fun removeBookFromShelf(bookId: Long, shelfId: Long)

    @Query("SELECT createdAt FROM book_shelf_cross_ref WHERE bookId = :bookId AND shelfId = :shelfId LIMIT 1")
    suspend fun membershipCreatedAt(bookId: Long, shelfId: Long): Long?

    @Query(
        "DELETE FROM book_shelf_cross_ref WHERE bookId = :bookId AND shelfId = :shelfId AND createdAt <= :deletedAt",
    )
    suspend fun removeBookFromShelfIfCreatedBefore(bookId: Long, shelfId: Long, deletedAt: Long): Int

    @Query(
        "SELECT books.* FROM books INNER JOIN book_shelf_cross_ref ON books.id = book_shelf_cross_ref.bookId " +
            "WHERE book_shelf_cross_ref.shelfId = :shelfId AND books.isDeleted = 0 ORDER BY books.title COLLATE NOCASE ASC",
    )
    fun observeBooksForShelf(shelfId: Long): Flow<List<BookEntity>>

    @Query(
        "SELECT shelves.* FROM shelves INNER JOIN book_shelf_cross_ref ON shelves.id = book_shelf_cross_ref.shelfId " +
            "WHERE book_shelf_cross_ref.bookId = :bookId ORDER BY shelves.name COLLATE NOCASE ASC",
    )
    fun observeShelvesForBook(bookId: Long): Flow<List<ShelfEntity>>

    /** Book counts of every shelf that has books, in one query (shelves without books are absent). */
    @Query("SELECT shelfId, COUNT(*) AS count FROM book_shelf_cross_ref GROUP BY shelfId")
    fun observeShelfBookCounts(): Flow<List<ShelfBookCount>>

    @Query("SELECT * FROM shelves ORDER BY syncId ASC")
    suspend fun getAllForSync(): List<ShelfEntity>

    @Query("SELECT * FROM book_shelf_cross_ref ORDER BY bookId ASC, shelfId ASC")
    suspend fun getMembershipsForSync(): List<BookShelfCrossRefEntity>
}

data class ShelfBookCount(val shelfId: Long, val count: Int)
