package com.vayana.feature.reader

import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.di.DatabaseModule
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.AnnotationRepositoryImpl
import com.vayana.core.database.repository.BookRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ViewAnnotationsTest {
    private lateinit var db: VayanaDatabase
    private lateinit var stored: AnnotationRepositoryImpl
    private var bookId = 0L
    private var textView = false
    private lateinit var view: ViewAnnotations

    @BeforeTest fun setup() = runBlocking {
        db = DatabaseModule.provideVayanaDatabase(RuntimeEnvironment.getApplication())
        val books = BookRepositoryImpl(db, db.bookDao(), db.bookAliasDao(), db.tombstoneDao(),
            db.readingSessionDao(), db.vocabularyCardDao(), db.pendingCloudDeletionDao())
        stored = AnnotationRepositoryImpl(db, db.annotationDao(), db.bookDao(), db.bookAliasDao(), db.tombstoneDao())
        bookId = books.insertOfflineBook("Scanned", "Author", BookFormat.PHYSICAL, null, null).id
        view = ViewAnnotations(stored) { textView }
    }
    @AfterTest fun close() {
        db.close()
        RuntimeEnvironment.getApplication().deleteDatabase("vayana.db")
    }

    private suspend fun mark(locator: String, text: String) =
        view.create(bookId, AnnotationType.HIGHLIGHT, "yellow", locator, null, null, text, null)

    @Test fun eachViewSeesAndWritesOnlyItsOwnMarks() = runBlocking {
        textView = false
        mark("epubcfi(/6/22!/4/2,/1:0,/1:5)", "scan")
        view.create(bookId, AnnotationType.NOTE, "default", "", null, null, "", "about the book")
        textView = true
        val inText = mark("epubcfi(/6/4!/4/2,/1:0,/1:5)", "text")
        // the text view's mark comes back as its engine knows it, and is stored marked as the text view's
        assertEquals("epubcfi(/6/4!/4/2,/1:0,/1:5)", inText.locator)
        assertTrue(stored.observeForBook(bookId).first().any { it.locator == TextViewLocatorPrefix + "epubcfi(/6/4!/4/2,/1:0,/1:5)" })

        assertEquals(setOf("text", ""), view.observeForBook(bookId).first().map { it.selectedText }.toSet())
        // an edit in the text view keeps the mark the text view's
        view.update(inText.copy(colorKey = "blue"))
        assertEquals("blue", stored.observeForBook(bookId).first().single { it.selectedText == "text" }.colorKey)
        assertTrue(stored.observeForBook(bookId).first().single { it.selectedText == "text" }.locator.startsWith(TextViewLocatorPrefix))

        textView = false
        assertEquals(setOf("scan", ""), view.observeForBook(bookId).first().map { it.selectedText }.toSet())
    }

    @Test fun aScanPageAndItsLocatorConvertBothWays() {
        assertEquals("epubcfi(/6/20)", scanPageCfi(9))
        assertEquals(9, scanPageIndex("epubcfi(/6/20)"))
        assertEquals(0, scanPageIndex(scanPageCfi(0)))
        assertNull(scanPageIndex("epubcfi(/6/20!/4/2)"))
        assertNull(scanPageIndex("c003.xhtml#page-9"))
    }
}
