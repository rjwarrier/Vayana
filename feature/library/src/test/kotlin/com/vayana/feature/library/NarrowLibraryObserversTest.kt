package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.BookNotebookFields
import com.vayana.core.database.dao.LocalEpubFile
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.entity.ReadingSessionEntity
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.BookRepositoryImpl
import com.vayana.core.database.repository.ReadingSessionRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

/** The app-lifetime observers' narrow queries must return exactly what filtering the whole library used to. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class NarrowLibraryObserversTest {
    private lateinit var db: VayanaDatabase
    private lateinit var books: BookRepositoryImpl
    private lateinit var sessions: ReadingSessionRepositoryImpl
    @BeforeTest fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VayanaDatabase::class.java)
            .allowMainThreadQueries().build()
        books = BookRepositoryImpl(db, db.bookDao(), db.bookAliasDao(), db.tombstoneDao(), db.readingSessionDao(),
            db.vocabularyCardDao(), db.pendingCloudDeletionDao())
        sessions = ReadingSessionRepositoryImpl(db, db.readingSessionDao(), db.bookDao(), db.bookAliasDao(), db.tombstoneDao())
    }
    @AfterTest fun close() = db.close()

    private suspend fun book(title: String, format: String, availability: String = "LOCAL",
        filePath: String = "books/$title", deleted: Boolean = false, createdAt: Long = 1): Long =
        db.bookDao().insert(BookEntity(title = title, author = "$title author", series = null, seriesNumber = null,
            description = null, coverPath = null, filePath = filePath, fileAvailability = availability, format = format,
            fileHash = "hash-$title", lastLocator = null, readingPercent = 0f, rating = 0f, groupId = null,
            isDeleted = deleted, wordCount = null, pageEstimate = null, createdAt = createdAt, updatedAt = 1,
            lastReadAt = null, tagsCsv = "tag-$title"))

    private suspend fun session(bookId: Long, startedAt: Long) =
        db.readingSessionDao().insert(ReadingSessionEntity(bookId = bookId, startedAt = startedAt,
            endedAt = startedAt + 60_000, durationSeconds = 60))

    @Test fun narrowQueriesMatchWholeLibraryFilters() = runBlocking {
        val epub = book("Epub", "EPUB", createdAt = 3)
        val olderEpub = book("Older", "EPUB", createdAt = 2)
        book("Cloud", "EPUB", availability = "CLOUD_ONLY")
        book("NoFile", "EPUB", filePath = "")
        book("Deleted", "EPUB", deleted = true)
        val pdf = book("Pdf", "PDF")
        val paper = book("Paper", "PHYSICAL", filePath = "")
        val deletedPaper = book("Gone paper", "PHYSICAL", filePath = "", deleted = true)
        session(epub, 1_000)
        session(paper, 2_000)
        session(paper, 5_000)
        session(pdf, 3_000)
        session(deletedPaper, 4_000)

        val library = books.observeAll().first()
        val physical = library.filter { it.format == BookFormat.PHYSICAL }
        assertEquals(listOf(paper), physical.map { it.id })
        assertEquals(physical, books.observePhysical().first())

        val physicalIds = physical.map { it.id }.toSet()
        val physicalSessions = sessions.observeAll().first().filter { it.bookId in physicalIds }
        assertEquals(2, physicalSessions.size)
        assertEquals(physicalSessions, sessions.observePhysical().first())

        assertEquals(library.map { BookNotebookFields(it.id, it.syncId, it.title, it.author, it.tagsCsv) },
            books.observeNotebookFields().first())

        assertEquals(listOf(LocalEpubFile(epub, "hash-Epub", "books/Epub"), LocalEpubFile(olderEpub, "hash-Older", "books/Older"),
            LocalEpubFile(pdf, "hash-Pdf", "books/Pdf", "PDF")),
            db.epubPassageDao().observeLocalEpubFiles().first())
    }
}
