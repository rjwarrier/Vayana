package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.EpubChapterText
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.search.EpubPassageIndex
import com.vayana.core.database.search.EpubPassageIndexCallback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

/** The index keeps only words; its text lives compressed in chapters and is staged only to delete exactly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class EpubPassageIndexTest {
    private lateinit var db: VayanaDatabase
    @BeforeTest fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VayanaDatabase::class.java)
            .addCallback(EpubPassageIndexCallback).allowMainThreadQueries().build()
    }
    @AfterTest fun close() = db.close()

    private suspend fun epub(title: String, hash: String = "hash-$title"): Long = db.bookDao().insert(BookEntity(
        title = title, author = null, series = null, seriesNumber = null, description = null, coverPath = null,
        filePath = "books/$title", format = "EPUB", fileHash = hash, lastLocator = null, readingPercent = 0f, rating = 0f,
        groupId = null, isDeleted = false, wordCount = null, pageEstimate = null, createdAt = 1, updatedAt = 1, lastReadAt = null))

    private fun chapter(text: String, vararg passages: IntRange) =
        EpubChapterText("c.xhtml", "Chapter", text, passages.toList().ifEmpty { listOf(text.indices) })

    private fun count(sql: String): Long = db.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getLong(0) }

    /** Straight to the index, without the joins that would hide words left behind for passages that are gone. */
    private fun indexed(word: String): Long = count("SELECT count(*) FROM epub_passages_fts WHERE epub_passages_fts MATCH '$word'")

    private fun assertIndexMatchesPassages() {
        assertEquals(count("SELECT count(*) FROM epub_passages"), count("SELECT count(*) FROM epub_passages_fts_docsize"))
        assertEquals(0, count("SELECT count(*) FROM epub_passages_fts_content"))
    }

    @Test fun recognizedPdfHitsKeepTheirPageAndReplaceTheirRevision() = runBlocking {
        val id = epub("Scan", "pdf-hash")
        db.openHelper.writableDatabase.execSQL("UPDATE books SET format = 'PDF' WHERE id = $id")
        val dao = db.epubPassageDao()
        val text = "മലയാളം river"
        dao.replace(id, "pdf-hash:ocr:first", sequenceOf(EpubChapterText("epubcfi(/6/16)", "8", text, listOf(text.indices))))
        val hit = dao.search("river*", 10).first().single()
        assertEquals(1, dao.search("മലയാളം*", 10).first().size)
        assertEquals("epubcfi(/6/16)", hit.chapterHref)
        assertEquals("pdf-hash:ocr:first", dao.indexedFiles().single().fileHash)
        dao.discardUnavailable()
        assertEquals(1, dao.search("river*", 10).first().size)
        dao.replace(id, "pdf-hash:ocr:second", sequenceOf(chapter("new mountain")))
        assertTrue(dao.search("river*", 10).first().isEmpty())
        assertEquals(1, dao.search("mountain*", 10).first().size)
        db.openHelper.writableDatabase.execSQL("UPDATE books SET fileHash = 'replacement' WHERE id = $id")
        dao.discardUnavailable()
        assertTrue(dao.search("mountain*", 10).first().isEmpty())
        assertIndexMatchesPassages()
    }

    @Test fun reindexingABookForgetsItsOldWords() = runBlocking {
        val id = epub("Book")
        val dao = db.epubPassageDao()
        dao.replace(id, "hash-Book", sequenceOf(chapter("ancient quokka river"), chapter("second ancient chapter")))
        assertEquals(2, indexed("ancient"))
        dao.replace(id, "hash-Book", sequenceOf(chapter("brand new wombat text")))
        assertEquals(0, indexed("ancient"))
        assertEquals(0, indexed("quokka"))
        assertEquals(1, indexed("wombat"))
        assertIndexMatchesPassages()
    }

    @Test fun booksThatAreGoneOrChangedAreDiscardedWithTheirWords() = runBlocking {
        val gone = epub("Gone")
        val changed = epub("Changed")
        val kept = epub("Kept")
        val dao = db.epubPassageDao()
        dao.replace(gone, "hash-Gone", sequenceOf(chapter("vanishing platypus")))
        dao.replace(changed, "hash-Changed", sequenceOf(chapter("outdated echidna")))
        dao.replace(kept, "hash-Kept", sequenceOf(chapter("lasting numbat")))
        // A permanent delete leaves the chapters behind (no cascade, so their text stays available to unindex).
        db.openHelper.writableDatabase.execSQL("DELETE FROM books WHERE id = $gone")
        db.openHelper.writableDatabase.execSQL("UPDATE books SET fileHash = 'new' WHERE id = $changed")
        dao.discardUnavailable()
        assertEquals(0, indexed("platypus"))
        assertEquals(0, indexed("echidna"))
        assertEquals(1, indexed("numbat"))
        assertEquals(0, count("SELECT count(*) FROM epub_chapters WHERE bookId IN ($gone, $changed)"))
        assertIndexMatchesPassages()
    }

    @Test fun hitsSliceTheirPassageOutOfTheStoredChapterText() = runBlocking {
        val id = epub("Book")
        val text = "Café — moonlight on the river. 雪 falls on the second passage here."
        val second = text.indexOf("雪")
        val dao = db.epubPassageDao()
        dao.replace(id, "hash-Book", sequenceOf(chapter(text, 0 until second, second until text.length)))
        val hit = dao.search("falls*", 10).first().single()
        assertEquals("雪 falls on the second passage here.", dao.chapterTexts(listOf(hit.chapterId)).getValue(hit.chapterId).substring(hit.startOffset, hit.endOffset))
        assertEquals(1, dao.search("cafe*", 10).first().size)
    }

    @Test fun compressionRoundTripsAndShrinksProse() {
        val prose = (1..400).joinToString(" ") { "The river ran past the mill at dawn, and word $it was spoken." }
        val compressed = EpubPassageIndex.compress(prose)
        assertEquals(prose, EpubPassageIndex.decompress(compressed))
        assertTrue(compressed.size * 4 < prose.toByteArray().size)
        assertEquals("", EpubPassageIndex.decompress(EpubPassageIndex.compress("")))
    }
}
