package com.vayana.feature.library

import android.content.Context
import androidx.work.*
import androidx.work.testing.WorkManagerTestInitHelper
import com.vayana.core.common.DefaultDispatcherProvider
import com.vayana.core.database.model.*
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.filesystem.StorageRoots
import com.vayana.core.sync.asset.CloudAssetStager
import com.vayana.core.sync.asset.CloudBookAssetTransfer
import com.vayana.format.ocr.*
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.Proxy
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class OfflinePreparationWorkTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @BeforeTest fun setup() {
        // No real network/recognition executes even if the test scheduler runs a request immediately.
        val factory = object : WorkerFactory() {
            override fun createWorker(context: Context, name: String, parameters: WorkerParameters): ListenableWorker =
                object : CoroutineWorker(context, parameters) { override suspend fun doWork(): Result = awaitCancellation() }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setWorkerFactory(factory).build())
    }
    @AfterTest fun close() {
        runBlocking { WorkManager.getInstance(context).cancelAllWork().await() }
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test fun duplicateRequestsAreKeptAndRequestsSurviveControllerRecreationWithCancelAndRetry() = runBlocking {
        val book = book()
        val first = controller()
        first.enqueue(listOf(book), null, true)
        val job = first.jobs.first().single()
        first.enqueue(listOf(book), null, true)
        val restoredController = controller()
        assertEquals(job.id, restoredController.jobs.first().single().id)
        restoredController.cancel(book)
        withTimeout(10000) { restoredController.jobs.first { it.single().state == WorkInfo.State.CANCELLED } }
        restoredController.enqueue(listOf(book), null, true)
        val retried = restoredController.jobs.first().single()
        assertNotEquals(job.id, retried.id)
        assertFalse(retried.state.isFinished)
        restoredController.cancel(book)
    }

    @Test fun pendingRecognitionProtectsItsModelAndBookCleanupKeepsTheSourceFile() = runBlocking {
        val book = book().copy(filePath = "books/book.pdf", fileAvailability = BookFileAvailability.LOCAL)
        val roots = StorageRoots(context)
        val source = File(roots.booksDir, "book.pdf").apply { writeText("original PDF") }
        val model = OcrModel.EnglishFast
        val modelFile = File(BookRecognition.models(context).dataPath, "tessdata/eng.traineddata")
        modelFile.parentFile?.mkdirs()
        RandomAccessFile(modelFile, "rw").use { it.setLength(model.sizeBytes) }
        val store = BookRecognition.store(context)
        store.save(book.fileHash, 0, RecognizedPage(emptyList()), model.id)
        controller().enqueue(listOf(book), model, true)
        assertFalse(BookRecognition.removeModel(context, model))
        assertTrue(modelFile.isFile)
        assertEquals(setOf(0), store.savedPages(book.fileHash))
        BookRecognition.remove(context, book.fileHash)
        assertTrue(store.savedPages(book.fileHash).isEmpty())
        assertEquals("original PDF", source.readText())
        assertTrue(BookRecognition.removeModel(context, model))
        assertFalse(modelFile.exists())
    }

    private fun controller(): OfflinePreparation {
        val roots = StorageRoots(context)
        val books = proxy(BookRepository::class.java)
        val dispatchers = DefaultDispatcherProvider()
        return OfflinePreparation(context, books, proxy(SettingsRepository::class.java), roots,
            CloudBookAssetTransfer(books, roots, CloudAssetStager(books, roots, dispatchers), dispatchers, context))
    }
    private fun <T : Any> proxy(type: Class<T>): T = requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> error("Unexpected dependency: ${method.name}") }))
    private fun book() = Book(id = 7, syncId = "stable-book", title = "Book", author = null, series = null,
        seriesNumber = null, description = null, coverPath = null, filePath = "", fileAvailability = BookFileAvailability.CLOUD_ONLY,
        format = BookFormat.PDF, fileHash = "c".repeat(64), readingPercent = 0.4f, rating = 0f, createdAt = 1,
        updatedAt = 1, lastReadAt = 1, lastLocator = "epubcfi(/6/8)", totalReadingSeconds = 100)
}
