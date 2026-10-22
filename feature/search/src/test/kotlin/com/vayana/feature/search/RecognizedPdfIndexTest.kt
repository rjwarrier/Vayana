package com.vayana.feature.search

import android.content.Context
import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.search.EpubPassageIndexCallback
import com.vayana.core.filesystem.StorageRoots
import com.vayana.format.ocr.*
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RecognizedPdfIndexTest {
    @Test fun finalizedPdfTextIsIndexedAndLiveRemovalAndReplacementInvalidateIt() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val roots = StorageRoots(context)
        File(roots.booksDir, "scan.pdf").writeText("PDF fixture; only saved OCR is read")
        val db = Room.inMemoryDatabaseBuilder(context, VayanaDatabase::class.java)
            .addCallback(EpubPassageIndexCallback).allowMainThreadQueries().build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val hash = "a".repeat(64)
            val id = db.bookDao().insert(BookEntity(title = "Scan", author = null, series = null, seriesNumber = null,
                description = null, coverPath = null, filePath = "books/scan.pdf", format = "PDF", fileHash = hash,
                lastLocator = null, readingPercent = 0f, rating = 0f, groupId = null, isDeleted = false,
                wordCount = null, pageEstimate = null, createdAt = 1, updatedAt = 1, lastReadAt = null))
            val store = BookRecognition.store(context)
            fun save(page: Int, text: String) {
                store.save(hash, page, RecognizedPage(listOf(listOf(RecognizedWord(text, 0f, 0f, 80f, 8f, 90f)))), "eng-fast")
                store.saveLineJoins(hash)
            }
            save(7, "river")
            EpubContentIndex(roots, db.epubPassageDao(), scope, context).start()
            val dao = db.epubPassageDao()
            val first = withTimeout(15000) { dao.search("river*", 10).first { it.isNotEmpty() }.single() }
            assertEquals(id, first.bookId)
            assertEquals("epubcfi(/6/16)", first.chapterHref)
            val indexedHash = dao.indexedFiles().single().fileHash
            assertEquals("$hash:ocr:${OcrStore(File(context.filesDir, "ocr")).searchRevision(hash)}", indexedHash)

            store.delete(hash)
            withTimeout(15000) { dao.search("river*", 10).first { it.isEmpty() } }
            save(3, "mountain")
            val replacement = withTimeout(15000) { dao.search("mountain*", 10).first { it.isNotEmpty() }.single() }
            assertEquals("epubcfi(/6/8)", replacement.chapterHref)
            assertNotEquals(indexedHash, dao.indexedFiles().single().fileHash)
            assertTrue(dao.search("river*", 10).first().isEmpty())
            db.openHelper.writableDatabase.execSQL("UPDATE books SET fileHash = 'replacement' WHERE id = $id")
            withTimeout(15000) { dao.search("mountain*", 10).first { it.isEmpty() } }
            Unit
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.close()
        }
    }
}
