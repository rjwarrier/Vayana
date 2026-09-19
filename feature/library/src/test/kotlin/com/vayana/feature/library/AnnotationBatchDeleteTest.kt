package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.repository.AnnotationRepositoryImpl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Batched annotation deletion and the SQL community-quote count, against a real in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AnnotationBatchDeleteTest {
    private lateinit var database: VayanaDatabase
    private lateinit var repository: AnnotationRepositoryImpl
    private var bookId = 0L

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VayanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AnnotationRepositoryImpl(
            database = database,
            annotationDao = database.annotationDao(),
            bookDao = database.bookDao(),
            bookAliasDao = database.bookAliasDao(),
            tombstoneDao = database.tombstoneDao(),
        )
        bookId = runBlocking { database.bookDao().insert(book()) }
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun softDeleteAllHidesEveryAnnotationAndTombstonesEach() = runBlocking {
        val ids = (1..3).map { index -> create(locator = "epubcfi(/6/$index)", type = AnnotationType.HIGHLIGHT).id }
        val kept = create(locator = "epubcfi(/6/9)", type = AnnotationType.HIGHLIGHT).id

        repository.softDeleteAll(ids)

        assertEquals(listOf(kept), repository.observeForBook(bookId).first().map { it.id })
        ids.forEach { id ->
            val syncId = database.annotationDao().getById(id)!!.syncId
            assertEquals("annotation", database.tombstoneDao().findBySyncId(syncId)?.entityType)
        }
        assertEquals(null, database.tombstoneDao().findBySyncId(database.annotationDao().getById(kept)!!.syncId))
    }

    @Test
    fun softDeleteAllIgnoresEmptyAndUnknownIds() = runBlocking {
        val id = create(locator = "epubcfi(/6/1)", type = AnnotationType.HIGHLIGHT).id

        repository.softDeleteAll(emptyList())
        repository.softDeleteAll(listOf(id + 100))

        assertEquals(listOf(id), repository.observeForBook(bookId).first().map { it.id })
    }

    @Test
    fun softDeleteAllHandlesMoreIdsThanOneSqlBatch() = runBlocking {
        val ids = (1..1_000).map { index -> create(locator = "epubcfi(/6/$index)", type = AnnotationType.HIGHLIGHT).id }

        repository.softDeleteAll(ids)

        assertEquals(emptyList(), repository.observeForBook(bookId).first())
    }

    @Test
    fun communityQuoteCountMatchesTheDomainDefinitionAndSkipsDeleted() = runBlocking {
        val oldQuote = create(locator = "quote:0:a", type = AnnotationType.UNDERLINE, colorKey = "popular").id
        create(locator = "goodreads-quote:0:b", type = AnnotationType.UNDERLINE, colorKey = "popular")
        create(locator = "goodreads-quote:0:c", type = AnnotationType.HIGHLIGHT, colorKey = "popular")
        create(locator = "epubcfi(/6/2)", type = AnnotationType.UNDERLINE, colorKey = "popular")
        create(locator = "goodreads-quote:0:d", type = AnnotationType.UNDERLINE, colorKey = "yellow")

        assertEquals(2, repository.observeCommunityQuoteCountForBook(bookId).first())

        repository.softDelete(oldQuote)

        assertEquals(1, repository.observeCommunityQuoteCountForBook(bookId).first())
    }

    private suspend fun create(locator: String, type: AnnotationType, colorKey: String = "yellow") = repository.create(
        bookId = bookId,
        type = type,
        colorKey = colorKey,
        locator = locator,
        chapterTitle = null,
        chapterHref = null,
        selectedText = "text for $locator",
        readerNote = null,
    )

    private fun book() = BookEntity(
        syncId = "book-a",
        title = "Book A",
        author = null,
        series = null,
        seriesNumber = null,
        description = null,
        coverPath = null,
        filePath = "books/a.epub",
        format = "EPUB",
        fileHash = "hash-a",
        fileAssetId = null,
        coverAssetId = null,
        lastLocator = null,
        readingPercent = 0f,
        rating = 0f,
        groupId = null,
        isDeleted = false,
        wordCount = null,
        pageEstimate = null,
        createdAt = 1,
        updatedAt = 1,
        lastReadAt = null,
        customCoverPath = null,
        goodreadsCoverPath = null,
        deletionUpdatedAt = null,
    )
}
