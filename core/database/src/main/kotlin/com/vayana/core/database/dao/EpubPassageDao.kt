package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.vayana.core.database.entity.EpubPassageEntity
import kotlinx.coroutines.flow.Flow

data class IndexedEpubFile(val bookId: Long, val fileHash: String)

@Dao
interface EpubPassageDao {
    @Query("SELECT b.id AS bookId, b.fileHash FROM books b WHERE b.isDeleted = 0 AND b.format = 'EPUB' AND b.fileAvailability = 'LOCAL' AND EXISTS (SELECT 1 FROM epub_passages p WHERE p.bookId = b.id AND p.fileHash = b.fileHash)")
    suspend fun indexedFiles(): List<IndexedEpubFile>

    @Query("SELECT EXISTS(SELECT 1 FROM epub_passages WHERE bookId = :bookId AND fileHash = :hash)")
    suspend fun isIndexed(bookId: Long, hash: String): Boolean

    @Query("DELETE FROM epub_passages WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM epub_passages WHERE bookId NOT IN (SELECT id FROM books WHERE isDeleted = 0 AND format = 'EPUB' AND fileAvailability = 'LOCAL') OR fileHash != (SELECT fileHash FROM books WHERE books.id = epub_passages.bookId)")
    suspend fun discardUnavailable()

    @Insert
    suspend fun insert(passages: List<EpubPassageEntity>)

    suspend fun replace(bookId: Long, passages: List<EpubPassageEntity>) = replaceStreaming(bookId, passages.asSequence())

    /** Materialize at most one insertion batch; a failure rolls back the whole book's replacement. */
    @Transaction
    suspend fun replaceStreaming(bookId: Long, passages: Sequence<EpubPassageEntity>) {
        deleteForBook(bookId)
        passages.chunked(100).forEach { insert(it) }
    }

    @Query("SELECT p.* FROM epub_passages p JOIN epub_passages_fts f ON f.rowid = p.id JOIN books b ON b.id = p.bookId WHERE epub_passages_fts MATCH :match AND b.isDeleted = 0 AND b.fileAvailability = 'LOCAL' AND b.format = 'EPUB' AND b.fileHash = p.fileHash ORDER BY b.lastReadAt DESC, p.id LIMIT :limit")
    fun search(match: String, limit: Int): Flow<List<EpubPassageEntity>>
}
