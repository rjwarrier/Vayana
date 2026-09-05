package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert
    suspend fun insert(shelf: ShelfEntity): Long

    @Query("UPDATE shelves SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM shelves WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookToShelf(crossRef: BookShelfCrossRefEntity)

    @Query("DELETE FROM book_shelf_cross_ref WHERE bookId = :bookId AND shelfId = :shelfId")
    suspend fun removeBookFromShelf(bookId: Long, shelfId: Long)

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

    @Query("SELECT COUNT(*) FROM book_shelf_cross_ref WHERE shelfId = :shelfId")
    fun observeShelfBookCount(shelfId: Long): Flow<Int>

    @Query("SELECT * FROM shelves ORDER BY syncId ASC")
    suspend fun getAllForSync(): List<ShelfEntity>

    @Query("SELECT * FROM book_shelf_cross_ref ORDER BY bookId ASC, shelfId ASC")
    suspend fun getMembershipsForSync(): List<BookShelfCrossRefEntity>
}
