package com.vayana.feature.library

import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.entity.EpubPassageEntity
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.BookRepositoryImpl
import com.vayana.core.database.repository.ReadNextQueueFullException
import com.vayana.core.common.passageLink
import com.vayana.core.common.parsePassageLink
import com.vayana.core.common.PassageLink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ReadingFeaturesTest {
    private lateinit var db: VayanaDatabase
    private lateinit var books: BookRepositoryImpl
    @BeforeTest fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VayanaDatabase::class.java).allowMainThreadQueries().build()
        books = BookRepositoryImpl(db, db.bookDao(), db.bookAliasDao(), db.tombstoneDao(), db.readingSessionDao(), db.vocabularyCardDao(), db.pendingCloudDeletionDao())
    }
    @AfterTest fun close() { db.close() }

    @Test fun todayAggregateKeepsStartTimeBoundaryAndReflectsRecordedAndDeletedSessions() = runBlocking {
        val book = books.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        val sessions = com.vayana.core.database.repository.ReadingSessionRepositoryImpl(
            db, db.readingSessionDao(), db.bookDao(), db.bookAliasDao(), db.tombstoneDao())
        assertEquals(0L, sessions.observeSecondsSince(1000).first())
        sessions.record(book.id, 999, 5000, 200)
        sessions.record(book.id, 1000, 5000, 90)
        sessions.record(book.id, 2000, 5000, 30)
        assertEquals(120L, sessions.observeSecondsSince(1000).first())
        assertEquals(30L, sessions.observeSecondsSince(1001).first())
        assertEquals(sessions.observeSince(1000).first().sumOf { it.durationSeconds }, sessions.observeSecondsSince(1000).first())
        sessions.deleteBySyncId(sessions.observeSince(1000).first().first().syncId)
        assertEquals(30L, sessions.observeSecondsSince(1000).first())
    }

    @Test fun readingDaysIgnoreMinuteTicksAndRefreshAtLocalMidnightAndZoneChanges() = runBlocking {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val date = java.time.LocalDate.of(2026, 10, 4)
        val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val days = kotlinx.coroutines.flow.flowOf(midnight, midnight + 60_000, midnight + 86_400_000)
            .readingDays { zone }.toList()
        assertEquals(listOf(date, date.plusDays(1)), days.map { it.date })
        assertEquals(listOf(midnight, midnight + 86_400_000), days.map { it.start })
        val zones = listOf(java.time.ZoneId.of("UTC"), java.time.ZoneId.of("Asia/Kolkata")).iterator()
        val changed = kotlinx.coroutines.flow.flowOf(midnight + 43_200_000, midnight + 43_200_000)
            .readingDays { zones.next() }.toList()
        assertEquals(2, changed.size)
        assertNotEquals(changed[0].start, changed[1].start)
    }

    @Test fun dashboardPreservesCurrentReadingAndOnlyTheFirstThreeActivePlans() = runBlocking {
        val paper = books.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        val current = paper.copy(id=10, syncId="current", format=BookFormat.EPUB, filePath="book.epub", lastReadAt=900, readingPercent=.5f)
        val paused = current.copy(id=11, syncId="paused", readingDisposition="PAUSED", lastReadAt=1000)
        val finished = current.copy(id=12, syncId="finished", finishedReadingAt=1100)
        val active = (1..4).map { paper.copy(id=it.toLong(), syncId="plan-$it") }
        val date = java.time.LocalDate.of(2026, 10, 4)
        val library = listOf(paused, finished) + active + current
        val state = todayLibraryState(library, library.associate { it.syncId to date.plusDays(4) }, 20, date)
        assertEquals(current, state.current)
        assertEquals(20, state.goal)
        assertEquals(listOf(1L, 2L, 3L), state.plans.map { it.first.id })
    }

    @Test fun queueCapacityReorderingAndPinsSurviveReload() = runBlocking {
        val first = books.insertOfflineBook("First", null, BookFormat.PHYSICAL, null, null)
        val second = books.insertOfflineBook("Second", null, BookFormat.PHYSICAL, null, null)
        val third = books.insertOfflineBook("Third", null, BookFormat.PHYSICAL, null, null)
        books.setReadNext(first.id, true, 2)
        books.setReadNext(second.id, true, 2)
        assertFailsWith<ReadNextQueueFullException> { books.setReadNext(third.id, true, 2) }
        assertEquals(2, orderedReadNext(books.observeAll().first()).size)
        val previousVersion = db.bookDao().latestReadNextUpdate()
        books.reorderReadNext(listOf(second.id, first.id))
        assertTrue(db.bookDao().latestReadNextUpdate() > previousVersion)
        assertEquals(listOf(second.id, first.id), orderedReadNext(books.observeAll().first()).map { it.id })
        books.pinReadNext(first.id, true)
        assertEquals(first.id, orderedReadNext(books.observeAll().first()).first().id)
        val version = books.getById(first.id)!!.readNextUpdatedAt!!
        books.pinReadNext(first.id, false)
        assertTrue(books.getById(first.id)!!.readNextUpdatedAt!! > version)
        books.pinReadNext(first.id, true)
        books.setReadNext(first.id, false)
        assertFalse(books.getById(first.id)!!.readNextPinned)
        books.setReadNext(third.id, true, 2)
        assertEquals(setOf(second.id, third.id), orderedReadNext(books.observeAll().first()).map { it.id }.toSet())
    }

    @Test fun pausedAndDnfRetainReadingHistoryAndOpeningResumes() = runBlocking {
        val original = books.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, 100, null)
        books.recordPhysicalReadingSession(original.id, "session", 1000, 61000, 60, 0, 20, 100)
        val before = books.getById(original.id)!!
        books.updateDisposition(original.id, "PAUSED", " Later ")
        var after = books.getById(original.id)!!
        assertEquals("Later", after.dispositionReason)
        assertEquals(before.totalReadingSeconds, after.totalReadingSeconds)
        assertEquals(before.readingPercent, after.readingPercent)
        assertEquals(emptyList(), physicalReadingHomeBooks(listOf(after), null))
        books.updateDisposition(original.id, "DNF", "Not for me")
        assertEquals(before.startedReadingAt, books.getById(original.id)!!.startedReadingAt)
        books.recordBookOpened(original.id)
        after = books.getById(original.id)!!
        assertEquals("ACTIVE", after.readingDisposition)
        assertEquals(before.totalReadingSeconds, after.totalReadingSeconds)
        assertNull(after.dispositionReason)
    }

    @Test fun contentFtsOnlyReturnsCurrentLocalEpubAndDeletesWithBook() = runBlocking {
        val book = books.insertOfflineBook("Book", null, BookFormat.PHYSICAL, null, null)
        val entity = db.bookDao().getById(book.id)!!
        db.bookDao().update(entity.copy(format = "EPUB", filePath = "book.epub", fileAvailability = "LOCAL"))
        val dao = db.epubPassageDao()
        dao.replace(book.id, listOf(EpubPassageEntity(bookId = book.id, fileHash = book.fileHash, chapterHref = "OEBPS/a.xhtml", chapterTitle = "Chapter", text = "Café moonlight on the river")))
        assertEquals(1, dao.search("cafe*", 10).first().size)
        assertEquals(listOf(com.vayana.core.database.dao.IndexedEpubFile(book.id, book.fileHash)), dao.indexedFiles())
        db.bookDao().update(db.bookDao().getById(book.id)!!.copy(fileAvailability = "CLOUD_ONLY"))
        assertTrue(dao.search("moon*", 10).first().isEmpty())
        assertTrue(dao.indexedFiles().isEmpty())
        dao.discardUnavailable()
        assertFalse(dao.isIndexed(book.id, book.fileHash))
        dao.replace(book.id, listOf(EpubPassageEntity(bookId = book.id, fileHash = "old-hash", chapterHref = "a", chapterTitle = "A", text = "moonlight")))
        assertTrue(dao.search("moon*", 10).first().isEmpty())
    }

    @Test fun batchedIndexReplacementRollsBackOnMidStreamFailure() = runBlocking {
        val book = books.insertOfflineBook("Book", null, BookFormat.PHYSICAL, null, null)
        db.bookDao().update(db.bookDao().getById(book.id)!!.copy(format="EPUB", filePath="book.epub", fileAvailability="LOCAL"))
        val dao = db.epubPassageDao()
        fun passage(text: String) = EpubPassageEntity(bookId=book.id, fileHash=book.fileHash, chapterHref="chapter.xhtml", chapterTitle="Chapter", text=text)
        dao.replaceStreaming(book.id, (1..251).asSequence().map { passage("moonlight $it") })
        assertEquals(251, dao.search("moonlight*", 300).first().size)
        val broken = sequence {
            repeat(180) { yield(passage("broken $it")) }
            throw IllegalStateException("Interrupted generation")
        }
        assertFailsWith<IllegalStateException> { dao.replaceStreaming(book.id, broken) }
        assertEquals(251, dao.search("moonlight*", 300).first().size)
        assertTrue(dao.search("broken*", 300).first().isEmpty())
    }

    @Test fun epubExtractionFollowsSpineAndOmitsHeadScriptsAndStyles() {
        val file = java.io.File.createTempFile("reading-index", ".epub")
        try {
            java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
                fun entry(name: String, text: String) {
                    zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
                entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
                entry("OPS/book.opf", """<package><manifest><item id="second" href="two.xhtml" media-type="application/xhtml+xml"/><item id="first" href="chapter%20one.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="first"/><itemref idref="second"/><itemref idref="nav" linear="no"/></spine></package>""")
                entry("OPS/chapter one.xhtml", """<html><head><title>Hidden title</title><style>Hidden style</style></head><body><h1>First chapter</h1><p>Café &amp; river.</p><script>Hidden script</script></body></html>""")
                entry("OPS/two.xhtml", "<html><body><h1>Second chapter</h1><p>Moonlight.</p></body></html>")
            }
            val chapters = com.vayana.format.epub.EpubTextExtractor.extract(file)
            assertEquals(listOf("OPS/chapter one.xhtml", "OPS/two.xhtml"), chapters.map { it.href })
            assertEquals("First chapter", chapters.first().title)
            assertTrue(chapters.first().text.contains("Café & river."))
            assertFalse(chapters.first().text.contains("Hidden"))
            var checkpoints = 0
            assertFailsWith<kotlinx.coroutines.CancellationException> {
                com.vayana.format.epub.EpubTextExtractor.extract(file) {
                    checkpoints++
                    if (checkpoints == 3) throw kotlinx.coroutines.CancellationException("Stop indexing")
                }
            }
            assertEquals(3, checkpoints)
            // The cancellation must release the ZIP immediately, including on Windows.
            val moved = java.io.File(file.parentFile, file.name + ".closed")
            assertTrue(file.renameTo(moved))
            assertTrue(moved.renameTo(file))
        } finally { file.delete() }
    }

    @Test fun questionsUseTheExistingReviewScheduleAndResetToDue() = runBlocking {
        val book = books.insertOfflineBook("Paper", null, BookFormat.PHYSICAL, null, null)
        val dao = db.annotationDao()
        val id = dao.insert(com.vayana.core.database.entity.AnnotationEntity(bookId=book.id, type="HIGHLIGHT", colorKey="yellow", locator="physical-page:1", chapterTitle="Page 1", chapterHref=null, selectedText="Answer", readerNote=null, createdAt=100, updatedAt=100, reviewQuestion="Question?"))
        val reviews = com.vayana.core.database.repository.HighlightReviewRepositoryImpl(db.highlightReviewDao(), db)
        val note = dao.getById(id)!!
        assertEquals(1, reviews.observeDueCount(100).first())
        assertEquals("Question?", reviews.session(100).annotations.single().reviewQuestion)
        reviews.grade(note.syncId, com.vayana.core.database.repository.ReviewGrade.GOOD, 100)
        assertEquals(0, reviews.observeDueCount(100).first())
        val annotations = com.vayana.core.database.repository.AnnotationRepositoryImpl(db, dao, db.bookDao(), db.bookAliasDao(), db.tombstoneDao())
        annotations.update(annotations.getById(id)!!.copy(reviewQuestion = "Edited question?"))
        assertEquals(1, reviews.observeDueCount(100).first())
        reviews.grade(note.syncId, com.vayana.core.database.repository.ReviewGrade.GOOD, 100)
        val current = annotations.getById(id)!!
        annotations.mergeCloudAnnotation(com.vayana.core.database.repository.AnnotationRecord(
            syncId=current.syncId, bookSyncId=book.syncId, type=current.type, colorKey=current.colorKey,
            locator=current.locator, chapterTitle=current.chapterTitle, chapterHref=current.chapterHref,
            selectedText=current.selectedText, readerNote=current.readerNote, createdAt=current.createdAt,
            updatedAt=current.updatedAt + 1, isDeleted=false, reviewQuestion="Question from another device?"))
        assertEquals(1, reviews.observeDueCount(100).first())
        assertEquals("Question from another device?", reviews.session(100).annotations.single().reviewQuestion)
    }

    @Test fun passageLinksRoundTripStableUnicodeIdsAndRejectOtherUris() {
        assertEquals(PassageLink("book & café", "note+雪"), parsePassageLink(passageLink("book & café", "note+雪")))
        assertNull(parsePassageLink("https://passage?book=x&annotation=y"))
        assertNull(parsePassageLink("vayana://passage?book=x"))
        assertNull(parsePassageLink("vayana://passage?book=%ZZ&annotation=y"))
    }
}
