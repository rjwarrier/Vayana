package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.entity.AnnotationEntity
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.repository.HighlightReviewRepositoryImpl
import com.vayana.core.database.repository.ReviewGrade
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Highlight review schedules against a real in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class HighlightReviewRepositoryTest {
    private lateinit var database: VayanaDatabase
    private lateinit var repository: HighlightReviewRepositoryImpl

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VayanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HighlightReviewRepositoryImpl(database.highlightReviewDao())
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun gradingBuildsTheScheduleUpReviewByReview() = runBlocking {
        val day = 24L * 60 * 60 * 1000

        val first = repository.grade(annotationSyncId = "annotation-7", grade = ReviewGrade.GOOD, now = 0)
        val second = repository.grade(annotationSyncId = "annotation-7", grade = ReviewGrade.GOOD, now = first.dueAt)
        val lapsed = repository.grade(annotationSyncId = "annotation-7", grade = ReviewGrade.AGAIN, now = second.dueAt)

        assertEquals(1 * day, first.dueAt)
        assertEquals(1, first.repetitions)
        assertEquals(2, second.repetitions)
        assertEquals(first.dueAt + 3 * day, second.dueAt)
        assertEquals(0, lapsed.repetitions)
        assertTrue(lapsed.dueAt - second.dueAt < day)
        assertEquals(lapsed, repository.observeAll().first().getValue("annotation-7"))
    }

    @Test
    fun deleteOrphansKeepsOnlySchedulesOfExistingAnnotations() = runBlocking {
        val bookId = database.bookDao().insert(book())
        val keptId = database.annotationDao().insert(annotation(bookId))
        val keptSyncId = database.annotationDao().getById(keptId)!!.syncId
        repository.grade(keptSyncId, ReviewGrade.GOOD, now = 0)
        repository.grade(annotationSyncId = "annotation-gone", grade = ReviewGrade.GOOD, now = 0)

        repository.deleteOrphans()

        assertEquals(setOf(keptSyncId), repository.observeAll().first().keys)
    }

    private fun annotation(bookId: Long) = AnnotationEntity(
        bookId = bookId, type = "HIGHLIGHT", colorKey = "yellow", locator = "epubcfi(/6/2)", chapterTitle = null,
        chapterHref = null, selectedText = "A line", readerNote = null, createdAt = 1, updatedAt = 1,
    )

    private fun book() = BookEntity(
        syncId = "book-a", title = "Book A", author = null, series = null, seriesNumber = null, description = null,
        coverPath = null, filePath = "books/a.epub", format = "EPUB", fileHash = "hash-a", fileAssetId = null,
        coverAssetId = null, lastLocator = null, readingPercent = 0f, rating = 0f, groupId = null, isDeleted = false,
        wordCount = null, pageEstimate = null, createdAt = 1, updatedAt = 1, lastReadAt = null, customCoverPath = null,
        goodreadsCoverPath = null, deletionUpdatedAt = null,
    )
}
