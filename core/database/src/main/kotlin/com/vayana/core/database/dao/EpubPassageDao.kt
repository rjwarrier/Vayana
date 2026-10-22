package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.entity.EpubChapterEntity
import com.vayana.core.database.entity.EpubPassageEntity
import com.vayana.core.database.entity.EpubPassageTextEntity
import com.vayana.core.database.search.EpubPassageIndex
import kotlinx.coroutines.flow.Flow

data class IndexedEpubFile(val bookId: Long, val fileHash: String)

/** [filePath] is root-relative, as stored. */
data class LocalEpubFile(val bookId: Long, val fileHash: String, val filePath: String, val format: String = "EPUB")

/** A chapter to index, with the `[start, end]` stretches of [text] that become searchable passages. */
class EpubChapterText(val href: String, val title: String, val text: String, val passages: List<IntRange>)

/** A matching passage; its text is `[startOffset, endOffset)` of chapter [chapterId]'s text ([EpubPassageDao.chapterTexts]). */
data class EpubPassageHit(
    val id: Long,
    val bookId: Long,
    val chapterId: Long,
    val chapterHref: String,
    val chapterTitle: String,
    val startOffset: Int,
    val endOffset: Int,
)

class EpubChapterBlob(val id: Long, val compressedText: ByteArray)

class ChapterPassage(val id: Long, val startOffset: Int, val endOffset: Int)

/**
 * Writes go a chapter at a time in batches: its passages in one insert, their text staged in another, and one
 * `INSERT … SELECT` to index them, rather than two statements per passage.
 */
@Dao
abstract class EpubPassageDao {
    @Query("SELECT DISTINCT c.bookId, c.fileHash FROM epub_chapters c JOIN books b ON b.id = c.bookId WHERE b.isDeleted = 0 AND b.fileAvailability = 'LOCAL' AND " + CurrentContentSource)
    abstract suspend fun indexedFiles(): List<IndexedEpubFile>

    // Only the columns the index needs: this is watched for the app's lifetime and books change on every
    // reading-progress write.
    @Query("SELECT id AS bookId, fileHash, filePath, format FROM books WHERE isDeleted = 0 AND format IN ('EPUB', 'PDF') AND fileAvailability = 'LOCAL' AND filePath != '' ORDER BY createdAt DESC")
    abstract fun observeLocalEpubFiles(): Flow<List<LocalEpubFile>>

    @Query("SELECT EXISTS(SELECT 1 FROM epub_chapters WHERE bookId = :bookId AND fileHash = :hash)")
    abstract suspend fun isIndexed(bookId: Long, hash: String): Boolean

    /** Replaces the book's index; a failure while [chapters] is still being produced rolls the whole book back. */
    @Transaction
    open suspend fun replace(bookId: Long, fileHash: String, chapters: Sequence<EpubChapterText>) {
        deleteForBook(bookId)
        chapters.forEach { chapter ->
            val chapterId = insertChapter(EpubChapterEntity(bookId = bookId, fileHash = fileHash, href = chapter.href,
                title = chapter.title, compressedText = EpubPassageIndex.compress(chapter.text)))
            if (chapter.passages.isEmpty()) return@forEach
            val ids = insertPassages(chapter.passages.map { range ->
                EpubPassageEntity(chapterId = chapterId, startOffset = range.first, endOffset = range.last + 1)
            })
            stage(chapter.passages.mapIndexed { index, range -> EpubPassageTextEntity(ids[index], chapter.text.substring(range)) })
            indexStaged()
            clearStaging()
        }
    }

    /**
     * Removes the book's chapters, passages and their words in the index. FTS4 reads the words to remove from the
     * content table, so each chapter's passages are staged there (one chapter's text in memory at a time) first.
     */
    @Transaction
    open suspend fun deleteForBook(bookId: Long) {
        val chapterIds = chapterIds(bookId)
        if (chapterIds.isEmpty()) return
        chapterIds.forEach { chapterId ->
            val passages = chapterPassages(chapterId)
            if (passages.isEmpty()) return@forEach
            val text = EpubPassageIndex.decompress(chapterBlobs(listOf(chapterId)).single().compressedText)
            stage(passages.map { EpubPassageTextEntity(it.id, text.substring(it.startOffset, it.endOffset)) })
        }
        unindexStaged()
        clearStaging()
        deleteChapters(bookId)
    }

    /** Drops the index of books that are gone, no longer local EPUBs/PDFs, or whose source file changed. */
    @Transaction
    open suspend fun discardUnavailable() {
        staleBookIds().forEach { deleteForBook(it) }
    }

    fun search(match: String, limit: Int, bookId: Long? = null, shelfId: Long? = null): Flow<List<EpubPassageHit>> = searchRaw(RoomRawQuery(SearchSql) {
        it.bindText(1, match)
        if (bookId == null) it.bindNull(2) else it.bindLong(2, bookId)
        if (bookId == null) it.bindNull(3) else it.bindLong(3, bookId)
        if (shelfId == null) it.bindNull(4) else it.bindLong(4, shelfId)
        if (shelfId == null) it.bindNull(5) else it.bindLong(5, shelfId)
        it.bindLong(6, limit.toLong())
    })

    /** Full texts of the given chapters, by id; slice them with a hit's offsets. */
    suspend fun chapterTexts(ids: Collection<Long>): Map<Long, String> =
        ids.distinct().chunked(MaxBoundIds).flatMap { chapterBlobs(it) }
            .associate { it.id to EpubPassageIndex.decompress(it.compressedText) }

    @Query("SELECT DISTINCT c.bookId FROM epub_chapters c LEFT JOIN books b ON b.id = c.bookId WHERE b.id IS NULL OR b.isDeleted != 0 OR b.fileAvailability != 'LOCAL' OR NOT " + CurrentContentSource)
    protected abstract suspend fun staleBookIds(): List<Long>

    @Insert
    protected abstract suspend fun insertChapter(chapter: EpubChapterEntity): Long

    @Insert
    protected abstract suspend fun insertPassages(passages: List<EpubPassageEntity>): List<Long>

    @Insert
    protected abstract suspend fun stage(texts: List<EpubPassageTextEntity>)

    @Query("SELECT id, compressedText FROM epub_chapters WHERE id IN (:ids)")
    protected abstract suspend fun chapterBlobs(ids: List<Long>): List<EpubChapterBlob>

    @Query("SELECT id FROM epub_chapters WHERE bookId = :bookId")
    protected abstract suspend fun chapterIds(bookId: Long): List<Long>

    @Query("SELECT id, startOffset, endOffset FROM epub_passages WHERE chapterId = :chapterId")
    protected abstract suspend fun chapterPassages(chapterId: Long): List<ChapterPassage>

    @Query("DELETE FROM epub_chapters WHERE bookId = :bookId")
    protected abstract suspend fun deleteChapters(bookId: Long)

    @Query("DELETE FROM epub_passages_fts_content")
    protected abstract suspend fun clearStaging()

    @SkipQueryVerification
    @Query("INSERT INTO epub_passages_fts(docid, text) SELECT id, text FROM epub_passages_fts_content")
    protected abstract suspend fun indexStaged()

    @SkipQueryVerification
    @Query("DELETE FROM epub_passages_fts WHERE docid IN (SELECT id FROM epub_passages_fts_content)")
    protected abstract suspend fun unindexStaged()

    // Raw so Room watches the tables it knows; it can't observe the index table it didn't declare.
    @RawQuery(observedEntities = [EpubPassageEntity::class, EpubChapterEntity::class, BookEntity::class, com.vayana.core.database.entity.BookShelfCrossRefEntity::class])
    protected abstract fun searchRaw(query: RoomRawQuery): Flow<List<EpubPassageHit>>

    private companion object {
        const val MaxBoundIds = 500
        const val SearchSql = "SELECT p.id, c.bookId, p.chapterId, c.href AS chapterHref, c.title AS chapterTitle, " +
            "p.startOffset, p.endOffset FROM epub_passages_fts f JOIN epub_passages p ON p.id = f.docid " +
            "JOIN epub_chapters c ON c.id = p.chapterId JOIN books b ON b.id = c.bookId " +
            "WHERE epub_passages_fts MATCH ? AND b.isDeleted = 0 AND b.fileAvailability = 'LOCAL' " +
            "AND " + CurrentContentSource + " AND (? IS NULL OR b.id = ?) " +
            "AND (? IS NULL OR EXISTS (SELECT 1 FROM book_shelf_cross_ref s WHERE s.bookId = b.id AND s.shelfId = ?)) ORDER BY b.lastReadAt DESC, p.id LIMIT ?"
    }
}

// The PDF index is disposable derived text: its revision changes without changing the original PDF hash.
private const val CurrentContentSource = "((b.format = 'EPUB' AND c.fileHash = b.fileHash) OR " +
    "(b.format = 'PDF' AND substr(c.fileHash, 1, length(b.fileHash) + 5) = b.fileHash || ':ocr:'))"
