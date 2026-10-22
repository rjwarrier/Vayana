package com.vayana.feature.library

import android.content.Context
import androidx.work.*
import com.vayana.core.common.DefaultDispatcherProvider
import com.vayana.core.database.model.*
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.filesystem.StorageRoots
import com.vayana.core.resources.LocalizedException
import com.vayana.core.sync.asset.CloudAssetStager
import com.vayana.core.sync.asset.CloudBookAssetTransfer
import com.vayana.format.ocr.BookRecognition
import com.vayana.format.ocr.OcrModel
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class OfflinePreparationTest {
    @Test fun localBookPreparationDoesNotNeedSyncOrChangeItsPosition() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val roots = StorageRoots(context)
        val book = book()
        File(roots.booksDir, "book.epub").writeText("local file")
        var settingsReads = 0
        val repository = proxy(BookRepository::class.java) { name ->
            when (name) { "findActiveBySyncIdOrHash" -> book; else -> error("Unexpected book mutation: $name") }
        }
        val settings = proxy(SettingsRepository::class.java) { name ->
            when (name) { "getSnapshot" -> { settingsReads++; flowOf(SettingsSnapshot()) }; else -> error(name) }
        }
        val dispatchers = DefaultDispatcherProvider()
        val transfer = CloudBookAssetTransfer(repository, roots, CloudAssetStager(repository, roots, dispatchers), dispatchers, context)
        val preparation = OfflinePreparation(context, repository, settings, roots, transfer)
        assertNull(preparation.prepare(book.id, book.syncId, book.fileHash, null, false))
        assertEquals(0, settingsReads)
        assertEquals("epubcfi(/6/8)", book.lastLocator)
        assertEquals(0.4f, book.readingPercent)
        assertFailsWith<LocalizedException> { preparation.prepare(book.id, "restored-different-book", book.fileHash, null) }
        assertFailsWith<LocalizedException> { preparation.prepare(book.id, book.syncId, "replacement-hash", null) }
        File(roots.booksDir, "book.epub").delete()
        assertFailsWith<LocalizedException> { preparation.prepare(book.id, book.syncId, book.fileHash, null, false) }
        assertEquals(0, settingsReads)
    }

    @Test fun requestsContainStableBookIdentityOptionsAndConstraintsWithoutCredentials() {
        val pdf = book().copy(format = BookFormat.PDF)
        val request = OfflinePreparation.request(pdf, OcrModel.EnglishFast, true)
        val data = request.workSpec.input
        assertEquals(pdf.id, data.getLong(OfflinePreparation.KeyId, -1))
        assertEquals(pdf.syncId, data.getString(OfflinePreparation.KeySync))
        assertEquals(pdf.fileHash, data.getString(OfflinePreparation.KeyHash))
        assertEquals(OcrModel.EnglishFast.id, data.getString(OfflinePreparation.KeyModel))
        assertEquals(setOf("bookId", "syncId", "hash", "model", "allowDownload"), data.keyValueMap.keys)
        assertEquals(NetworkType.UNMETERED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.workSpec.constraints.requiresStorageNotLow())
        assertTrue(BookRecognition.preparationTag(pdf.fileHash) in request.tags)
        assertTrue(BookRecognition.modelTag(OcrModel.EnglishFast) in request.tags)
        val local = OfflinePreparation.request(book(), OcrModel.EnglishFast, true, needsDownload = false)
        assertEquals(NetworkType.NOT_REQUIRED, local.workSpec.constraints.requiredNetworkType)
        assertNull(local.workSpec.input.getString(OfflinePreparation.KeyModel))
        val mobile = OfflinePreparation.request(pdf, null, false)
        assertEquals(NetworkType.CONNECTED, mobile.workSpec.constraints.requiredNetworkType)
    }

    @Test fun readinessTracksTheRecognitionJobInsteadOfOnlyTheFinishedDownload() {
        fun info(state: WorkInfo.State, tags: Set<String> = emptySet(), output: Data = Data.EMPTY) =
            WorkInfo(UUID.randomUUID(), state, tags, output, Data.EMPTY, 0)
        val download = info(WorkInfo.State.SUCCEEDED, output = workDataOf(OfflinePreparation.KeyOcrHash to "hash"))
        fun ocr(state: WorkInfo.State) = listOf(info(state, setOf(BookRecognition.bookTag("hash"))))
        assertEquals(PreparationState.WAITING, preparationState(download, emptyList()))
        assertEquals(PreparationState.RECOGNIZING, preparationState(download, ocr(WorkInfo.State.RUNNING)))
        assertEquals(PreparationState.READY, preparationState(download, ocr(WorkInfo.State.SUCCEEDED)))
        assertEquals(PreparationState.FAILED, preparationState(download, ocr(WorkInfo.State.FAILED)))
        assertEquals(PreparationState.CANCELLED, preparationState(download, ocr(WorkInfo.State.CANCELLED)))
        assertEquals(PreparationState.READY, preparationState(download, emptyList(), info(WorkInfo.State.SUCCEEDED)))
        assertEquals(PreparationState.READY, preparationState(info(WorkInfo.State.SUCCEEDED), emptyList()))
        assertEquals(PreparationState.WAITING, preparationState(info(WorkInfo.State.ENQUEUED), emptyList()))
        assertEquals(PreparationState.CANCELLED, preparationState(info(WorkInfo.State.CANCELLED), ocr(WorkInfo.State.RUNNING)))
    }

    private fun book() = Book(id = 1, syncId = "stable-book", title = "Book", author = null, series = null,
        seriesNumber = null, description = null, coverPath = null, filePath = "books/book.epub",
        fileAvailability = BookFileAvailability.LOCAL, format = BookFormat.EPUB, fileHash = "a".repeat(64),
        readingPercent = 0.4f, rating = 0f, createdAt = 1, updatedAt = 1, lastReadAt = 1,
        lastLocator = "epubcfi(/6/8)", totalReadingSeconds = 100)

    private fun <T : Any> proxy(type: Class<T>, call: (String) -> Any?): T = requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> call(method.name) }))
}
