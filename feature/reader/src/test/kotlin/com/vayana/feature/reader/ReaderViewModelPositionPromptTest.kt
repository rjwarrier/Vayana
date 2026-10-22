package com.vayana.feature.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.di.DatabaseModule
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.database.repository.VocabularyCardRepository
import com.vayana.core.database.repository.WordLookupStatRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.diagnostics.DiagnosticsLogStore
import com.vayana.core.filesystem.BookFileCleaner
import com.vayana.core.filesystem.StorageRoots
import com.vayana.core.sync.RemoteBookDeletionNotices
import com.vayana.core.sync.SyncOperationCoordinator
import com.vayana.core.sync.SyncedBookDeletionApplier
import com.vayana.core.sync.asset.CloudAssetDeletionProcessor
import com.vayana.core.sync.progress.ReadingProgressOnlySyncer
import com.vayana.core.sync.usage.SyncDataUsageLog
import com.vayana.dictionary.api.DictionaryRepository
import com.vayana.dictionary.api.OnlineDictionary
import com.vayana.format.ocr.BookRecognition
import com.vayana.format.ocr.OcrModel
import com.vayana.format.ocr.RecognizedPage
import com.vayana.format.ocr.RecognizedWord
import com.vayana.reader.api.BookEngine
import com.vayana.reader.api.Locator
import com.vayana.reader.api.NavTarget
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ReaderViewModelPositionPromptTest {
    private val main = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + main)
    private val lifecycle = ViewModelStore()
    private lateinit var context: Context
    private lateinit var database: VayanaDatabase
    private lateinit var reader: ReaderViewModel
    private var persistedPosition: Pair<String, Float>? = null
    private val navigated = mutableListOf<NavTarget>()
    private val bookHash = "a".repeat(64)
    private lateinit var textTarget: String

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(main)
        context = RuntimeEnvironment.getApplication()
        database = DatabaseModule.provideVayanaDatabase(context)
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = this@ReaderViewModelPositionPromptTest.main
            override val io: CoroutineDispatcher = main
            override val default: CoroutineDispatcher = main
        }
        val settings = stub<SettingsRepository> { method, _ ->
            when (method) {
                "getSnapshot" -> MutableStateFlow(SettingsSnapshot())
                else -> error("Unexpected settings call: $method")
            }
        }
        val books = stub<BookRepository> { method, args ->
            when (method) {
                "updateLocator" -> {
                    assertEquals(1L, args[0])
                    persistedPosition = (args[1] as String) to (args[2] as Float)
                    Unit
                }
                else -> error("Unexpected book call: $method")
            }
        }
        val sessions = unused<ReadingSessionRepository>()
        val words = stub<WordLookupStatRepository> { method, _ ->
            when (method) {
                "observeRecent" -> MutableStateFlow(emptyList<com.vayana.core.database.model.WordLookupStat>())
                else -> error("Unexpected words call: $method")
            }
        }
        val roots = StorageRoots(context)
        val syncer = ReadingProgressOnlySyncer(
            settings, books, sessions, words, database.tombstoneDao(), dispatchers,
            DiagnosticsLogStore(context), SyncedBookDeletionApplier(books, BookFileCleaner(roots, dispatchers)),
            CloudAssetDeletionProcessor(database.pendingCloudDeletionDao()), RemoteBookDeletionNotices(),
            SyncOperationCoordinator(), database.syncedSnapshotSliceDao(), SyncDataUsageLog(context),
        )
        reader = ReaderViewModel(
            SavedStateHandle(mapOf("bookId" to 1L)), books, ReadAloudStatusHolder(), unused<AnnotationRepository>(),
            database, roots, settings, unused<DictionaryRepository>(), unused<OnlineDictionary>(), words, sessions,
            unused<VocabularyCardRepository>(), syncer, dispatchers, scope, context,
        )
        lifecycle.put("reader", reader)
        main.scheduler.runCurrent()
        val engine = stub<BookEngine> { method, args ->
            when (method) {
                "goTo" -> { navigated += args[0] as NavTarget; Unit }
                "close" -> Unit
                else -> error("Unexpected engine call: $method")
            }
        }
        // Seed an already-open text view: these tests exercise the ViewModel prompt actions, without a WebView.
        reader.setField("boundEngine", engine)
        reader.setField("textView", true)
        reader.setField("textViewPages", 200)
        reader.setField("recognitionBook", File(context.filesDir, "scan.pdf") to bookHash)
        reader.state<ReaderUiState>("_uiState").value = ReaderUiState.Loaded(
            bookTitle = "Scanned book", toc = emptyList(),
            currentLocator = Locator(
                cfi = "epubcfi(/6/4!/4/2/1:12)", href = "OEBPS/c002.xhtml", progression = 0.72f,
                chapterTitle = "Second chapter", currentPage = 216, totalPages = 300, printedPageIndex = 39,
            ),
        )
        reader.state<ReadingPositionPrompt?>("_readingPositionPrompt").value = ReadingPositionPrompt(
            targetLocator = scanPageCfi(99), targetProgress = 0.5f, targetPage = 100,
            currentProgress = 0.2f, currentPage = 40,
        )
        // Build the real OCR text artifact and map, without depending on its private/versioned file names.
        val recognized = BookRecognition.store(context)
        for (page in listOf(0, 99, 199)) {
            recognized.save(bookHash, page, RecognizedPage(listOf(listOf(
                RecognizedWord("Text$page", 30f, 60f, 90f, 70f, 95f),
            ))), OcrModel.EnglishFast.id)
        }
        recognized.buildTextEpub(bookHash, "Scanned book", null, "en")
        textTarget = "${recognized.textPageMap(bookHash)[99]}#page-99"
    }

    @AfterTest
    fun tearDown() {
        lifecycle.clear()
        scope.cancel()
        main.scheduler.runCurrent()
        database.close()
        context.deleteDatabase("vayana.db")
        File(context.filesDir, "ocr/$bookHash").deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test
    fun dismissingTextViewPromptSavesPrintedScanPosition() {
        reader.dismissReadingPositionPrompt()
        main.scheduler.runCurrent()

        assertEquals(scanPageCfi(39) to 0.2f, persistedPosition)
        assertNull(reader.readingPositionPrompt.value)
        assertEquals(emptyList(), navigated)
    }

    @Test
    fun acceptingScanProgressInTextViewJumpsToItsPrintedPageMarker() {
        reader.acceptReadingPositionPrompt()
        main.scheduler.runCurrent()

        val target = navigated.single() as NavTarget.ToLocator
        assertEquals(textTarget, target.locator.cfi)
        assertNull(persistedPosition)
        assertNull(reader.readingPositionPrompt.value)
    }

    @Test
    fun dismissingBeforeThePrintedPageIsKnownClosesWithoutSaving() {
        val state = reader.uiState.value as ReaderUiState.Loaded
        reader.state<ReaderUiState>("_uiState").value = state.copy(
            currentLocator = state.currentLocator!!.copy(printedPageIndex = null),
        )

        reader.dismissReadingPositionPrompt()

        assertNull(reader.readingPositionPrompt.value)
        main.scheduler.runCurrent()
        assertNull(persistedPosition)
        assertEquals(emptyList(), navigated)
    }

    @Test
    fun acceptingBeforeThePageMapLoadsClosesWithoutJumping() {
        reader.setField("textViewPages", 0)

        reader.acceptReadingPositionPrompt()

        assertNull(reader.readingPositionPrompt.value)
        main.scheduler.runCurrent()
        assertEquals(emptyList(), navigated)
        assertNull(persistedPosition)
    }

    @Test
    fun acceptingWithAnUnavailablePageMapStillClosesThePrompt() {
        BookRecognition.store(context).delete(bookHash)

        reader.acceptReadingPositionPrompt()
        main.scheduler.runCurrent()

        assertNull(reader.readingPositionPrompt.value)
        assertEquals(emptyList(), navigated)
        assertNull(persistedPosition)
    }

    @Test
    fun scanViewKeepsItsOriginalPositionOnDismissAndAccept() {
        reader.setField("textView", false)
        reader.state<ReaderUiState>("_uiState").value = ReaderUiState.Loaded(
            bookTitle = "Scanned book", toc = emptyList(),
            currentLocator = Locator(scanPageCfi(39), null, 0.2f, null, currentPage = 40, totalPages = 200),
        )
        reader.dismissReadingPositionPrompt()
        main.scheduler.runCurrent()
        assertEquals(scanPageCfi(39) to 0.2f, persistedPosition)

        reader.state<ReadingPositionPrompt?>("_readingPositionPrompt").value = ReadingPositionPrompt(
            scanPageCfi(99), 0.5f, 100, 0.2f, 40,
        )
        reader.acceptReadingPositionPrompt()
        main.scheduler.runCurrent()
        assertEquals(scanPageCfi(99), (navigated.single() as NavTarget.ToLocator).locator.cfi)
    }

    private fun ReaderViewModel.setField(name: String, value: Any) {
        javaClass.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> ReaderViewModel.state(name: String): MutableStateFlow<T> =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as MutableStateFlow<T>

    private inline fun <reified T> unused(): T = stub { method, _ -> error("Unexpected ${T::class.java.simpleName} call: $method") }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> stub(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            invoke(method.name, args ?: emptyArray())
        } as T
}
