package com.vayana.feature.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.QuoteParser
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.filesystem.BookFileImporter
import com.vayana.core.filesystem.StorageRoots
import com.vayana.format.epub.EpubParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of one import batch, still kept for the final Snackbar summary. */
data class ImportSummary(val imported: Int, val duplicates: Int, val unsupported: Int, val failed: Int)

enum class ImportRowStatus { QUEUED, COPYING, PARSING, IMPORTED, DUPLICATE, UNSUPPORTED, FAILED }

sealed interface BookDetailMessage {
    data object METADATA_SAVED : BookDetailMessage
    data object COVER_UPDATED : BookDetailMessage
    data object COVER_REMOVED : BookDetailMessage
    data object COVER_FAILED : BookDetailMessage
    data object SOURCE_REPLACED : BookDetailMessage
    data object SOURCE_DUPLICATE : BookDetailMessage
    data object SOURCE_UNSUPPORTED : BookDetailMessage
    data object SOURCE_FAILED : BookDetailMessage
    data class QUOTES_IMPORTED(val count: Int) : BookDetailMessage
    data object MARKED_FINISHED : BookDetailMessage
}

data class ImportProgressRow(
    val id: String,
    val fileName: String,
    val status: ImportRowStatus,
)

data class ImportProgressState(
    val rows: List<ImportProgressRow> = emptyList(),
    val isRunning: Boolean = false,
) {
    val summary: ImportSummary
        get() = rows.summarize()
}

enum class LibrarySort { IMPORT_DATE, TITLE, AUTHOR, LAST_READ, PROGRESS }

enum class LibraryFilter { ALL, READING, FINISHED, NOT_STARTED }

enum class LibraryGroupBy { NONE, AUTHOR, SERIES }

data class LibraryControls(
    val query: String = "",
    val sort: LibrarySort = LibrarySort.IMPORT_DATE,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val groupBy: LibraryGroupBy = LibraryGroupBy.NONE,
)

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val controls: LibraryControls = LibraryControls(),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val annotationRepository: AnnotationRepository,
    private val bookFileImporter: BookFileImporter,
    private val storageRoots: StorageRoots,
    private val dispatchers: DispatcherProvider,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val controls = MutableStateFlow(LibraryControls())

    /** [Book.coverPath] and [Book.filePath] come back root-relative; resolve both before UI use. */
    private val allBooks: Flow<List<Book>> = bookRepository.observeAll()
        .map { books -> books.map { it.withAbsolutePaths() } }

    val libraryBooks: StateFlow<List<Book>> =
        allBooks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<LibraryUiState> = combine(libraryBooks, controls) { books, controls ->
        LibraryUiState(
            books = books
                .filterBy(controls.filter)
                .filterByQuery(controls.query)
                .sortedBy(controls.sort),
            controls = controls,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun observeBook(bookId: Long): StateFlow<Book?> = libraryBooks
        .map { books -> books.firstOrNull { it.id == bookId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary: StateFlow<ImportSummary?> = _importSummary

    private val _importProgress = MutableStateFlow<ImportProgressState?>(null)
    val importProgress: StateFlow<ImportProgressState?> = _importProgress

    private val _bookDetailMessage = MutableStateFlow<BookDetailMessage?>(null)
    val bookDetailMessage: StateFlow<BookDetailMessage?> = _bookDetailMessage

    fun updateQuery(query: String) {
        controls.update { it.copy(query = query) }
    }

    fun updateSort(sort: LibrarySort) {
        controls.update { it.copy(sort = sort) }
    }

    fun updateFilter(filter: LibraryFilter) {
        controls.update { it.copy(filter = filter) }
    }

    fun updateGroupBy(groupBy: LibraryGroupBy) {
        controls.update { it.copy(groupBy = groupBy) }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch { bookRepository.softDelete(bookId) }
    }

    fun updateMetadata(bookId: Long, title: String, author: String, series: String, seriesNumber: String, description: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return
        viewModelScope.launch {
            bookRepository.updateMetadata(
                id = bookId,
                title = normalizedTitle,
                author = author.trim().ifBlank { null },
                series = series.trim().ifBlank { null },
                seriesNumber = seriesNumber.trim().ifBlank { null },
                description = description.trim().ifBlank { null },
            )
            _bookDetailMessage.value = BookDetailMessage.METADATA_SAVED
        }
    }

    fun markFinished(bookId: Long) {
        viewModelScope.launch {
            bookRepository.markFinished(bookId)
            _bookDetailMessage.value = BookDetailMessage.MARKED_FINISHED
        }
    }

    fun replaceSource(bookId: Long, contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _bookDetailMessage.value = withContext(dispatchers.io) { replaceSourceInLibrary(bookId, contentResolver, uri) }
        }
    }

    fun replaceCover(bookId: Long, contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _bookDetailMessage.value = withContext(dispatchers.io) { replaceCoverInLibrary(bookId, contentResolver, uri) }
        }
    }

    fun removeCover(bookId: Long) {
        viewModelScope.launch {
            _bookDetailMessage.value = withContext(dispatchers.io) { removeCoverFromLibrary(bookId) }
        }
    }

    fun importQuotes(bookId: Long, quotesText: String) {
        viewModelScope.launch {
            val quotes = withContext(dispatchers.default) {
                QuoteParser.parse(quotesText)
            }
            if (quotes.isEmpty()) return@launch

            val now = System.currentTimeMillis()
            val annotations = quotes.mapIndexed { index, quote ->
                Annotation(
                    id = 0,
                    bookId = bookId,
                    type = AnnotationType.UNDERLINE,
                    colorKey = "popular",
                    locator = "quote:$index:${UUID.randomUUID()}",
                    chapterTitle = quote.sourceTitle ?: quote.author,
                    chapterHref = null,
                    selectedText = quote.quoteText,
                    readerNote = "${quote.highlightsCount} highlights",
                    createdAt = now,
                    updatedAt = now,
                )
            }
            withContext(dispatchers.io) {
                annotationRepository.createAll(annotations)
            }
            _bookDetailMessage.value = BookDetailMessage.QUOTES_IMPORTED(quotes.size)
        }
    }

    fun importQuotesFromFile(bookId: Long, contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val text = withContext(dispatchers.io) {
                try {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
            } ?: return@launch
            importQuotes(bookId, text)
        }
    }

    fun onBookDetailMessageShown() {
        _bookDetailMessage.value = null
    }

    fun onImportSummaryShown() {
        _importSummary.value = null
    }

    fun onImportProgressDismissed() {
        if (_importProgress.value?.isRunning == false) {
            _importProgress.value = null
        }
    }

    fun importFiles(contentResolver: ContentResolver, uris: List<Uri>) {
        viewModelScope.launch {
            val candidates = uris.map { uri -> ImportCandidate(uri, displayNameOf(contentResolver, uri)) }
            startImportProgress(candidates)
            val results = withContext(dispatchers.io) {
                candidates.map { candidate -> importOne(contentResolver, candidate) }
            }
            _importSummary.value = results.summarize()
            markImportComplete()
        }
    }

    fun importFolder(contentResolver: ContentResolver, treeUri: Uri) {
        viewModelScope.launch {
            val candidates = withContext(dispatchers.io) {
                DocumentFile.fromTreeUri(appContext, treeUri)
                    ?.listFiles()
                    ?.filter { it.isFile }
                    ?.map { doc -> ImportCandidate(doc.uri, doc.name.orEmpty()) }
                    .orEmpty()
            }
            startImportProgress(candidates)
            val results = withContext(dispatchers.io) {
                candidates.map { candidate -> importOne(contentResolver, candidate) }
            }
            _importSummary.value = results.summarize()
            markImportComplete()
        }
    }

    /**
     * Adds a file-less entry for a paper book, so it can hold manually-typed quotes/notes.
     * [onCreated] fires with the new book's id once it lands, so the caller can jump straight
     * to its detail screen; it won't fire if [title] is blank or the insert somehow collides.
     */
    fun addPhysicalBook(title: String, author: String?, onCreated: (Long) -> Unit = {}) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        viewModelScope.launch {
            val book = withContext(dispatchers.io) {
                bookRepository.insertIfNew(
                    title = cleanTitle,
                    author = author?.trim()?.takeIf { it.isNotBlank() },
                    series = null,
                    seriesNumber = null,
                    description = null,
                    coverPath = null,
                    filePath = "",
                    format = BookFormat.PHYSICAL,
                    fileHash = "physical:${UUID.randomUUID()}",
                )
            }
            if (book != null) onCreated(book.id)
        }
    }

    private data class ImportCandidate(val uri: Uri, val displayName: String) {
        val id: String = "${uri}#${displayName}"
        val rowName: String = displayName.ifBlank { uri.lastPathSegment.orEmpty() }
    }

    private sealed interface ImportResult {
        val status: ImportRowStatus

        data object Imported : ImportResult {
            override val status = ImportRowStatus.IMPORTED
        }

        data object Duplicate : ImportResult {
            override val status = ImportRowStatus.DUPLICATE
        }

        data object Unsupported : ImportResult {
            override val status = ImportRowStatus.UNSUPPORTED
        }

        data object Failed : ImportResult {
            override val status = ImportRowStatus.FAILED
        }
    }

    private suspend fun importOne(contentResolver: ContentResolver, candidate: ImportCandidate): ImportResult {
        val uri = candidate.uri
        val displayName = candidate.displayName
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val format = BookFormat.entries.firstOrNull { it.name.equals(extension, ignoreCase = true) }
            ?: return finishImportRow(candidate.id, ImportResult.Unsupported)
        if (format != BookFormat.EPUB) return finishImportRow(candidate.id, ImportResult.Unsupported)

        var importedFile: File? = null
        var coverFile: File? = null
        return runCatchingCancellable {
            updateImportRow(candidate.id, ImportRowStatus.COPYING)
            val imported = bookFileImporter.import(uri, extension)
            importedFile = imported.file
            updateImportRow(candidate.id, ImportRowStatus.PARSING)
            val metadata = EpubParser.parse(imported.file)
            coverFile = metadata.coverBytes?.let { bytes -> saveCover(bytes) }

            val book = bookRepository.insertIfNew(
                title = metadata.title,
                author = metadata.author,
                series = metadata.series,
                seriesNumber = metadata.seriesNumber,
                description = metadata.description,
                coverPath = coverFile?.let { storageRoots.relativize(it) },
                filePath = storageRoots.relativize(imported.file),
                format = format,
                fileHash = imported.sha256,
            )
            if (book != null) {
                importedFile = null
                coverFile = null
                ImportResult.Imported
            } else {
                ImportResult.Duplicate
            }
        }.getOrElse { ImportResult.Failed }
            .also { result ->
                if (result != ImportResult.Imported) {
                    importedFile?.delete()
                    coverFile?.delete()
                }
            }
            .also { result -> updateImportRow(candidate.id, result.status) }
    }

    private suspend fun replaceSourceInLibrary(bookId: Long, contentResolver: ContentResolver, uri: Uri): BookDetailMessage {
        val existingBook = bookRepository.getById(bookId)?.withAbsolutePaths() ?: return BookDetailMessage.SOURCE_FAILED
        val displayName = displayNameOf(contentResolver, uri)
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val format = BookFormat.entries.firstOrNull { it.name.equals(extension, ignoreCase = true) }
            ?: return BookDetailMessage.SOURCE_UNSUPPORTED
        if (format != BookFormat.EPUB) return BookDetailMessage.SOURCE_UNSUPPORTED

        var importedFile: File? = null
        var coverFile: File? = null
        return runCatchingCancellable {
            val imported = bookFileImporter.import(uri, extension)
            importedFile = imported.file
            val metadata = EpubParser.parse(imported.file)
            coverFile = metadata.coverBytes?.let { bytes -> saveCover(bytes) }
            val replaced = bookRepository.replaceSource(
                id = bookId,
                title = metadata.title,
                author = metadata.author,
                series = existingBook.series,
                seriesNumber = existingBook.seriesNumber,
                description = metadata.description,
                coverPath = coverFile?.let { storageRoots.relativize(it) },
                filePath = storageRoots.relativize(imported.file),
                format = format,
                fileHash = imported.sha256,
            )
            if (replaced) {
                importedFile = null
                coverFile = null
                File(existingBook.filePath).delete()
                existingBook.coverPath?.let { File(it).delete() }
                BookDetailMessage.SOURCE_REPLACED
            } else {
                BookDetailMessage.SOURCE_DUPLICATE
            }
        }.getOrElse { BookDetailMessage.SOURCE_FAILED }
            .also { result ->
                if (result != BookDetailMessage.SOURCE_REPLACED) {
                    importedFile?.delete()
                    coverFile?.delete()
                }
            }
    }

    private suspend fun replaceCoverInLibrary(bookId: Long, contentResolver: ContentResolver, uri: Uri): BookDetailMessage {
        val existingBook = bookRepository.getById(bookId)?.withAbsolutePaths() ?: return BookDetailMessage.COVER_FAILED
        var coverFile: File? = null
        return runCatchingCancellable {
            val pickedCover = savePickedCover(contentResolver, uri)
            coverFile = pickedCover
            bookRepository.updateCover(bookId, storageRoots.relativize(pickedCover))
            existingBook.coverPath?.let { File(it).delete() }
            coverFile = null
            BookDetailMessage.COVER_UPDATED
        }.getOrElse { BookDetailMessage.COVER_FAILED }
            .also { result ->
                if (result != BookDetailMessage.COVER_UPDATED) {
                    coverFile?.delete()
                }
            }
    }

    private suspend fun removeCoverFromLibrary(bookId: Long): BookDetailMessage {
        val existingBook = bookRepository.getById(bookId)?.withAbsolutePaths() ?: return BookDetailMessage.COVER_FAILED
        return runCatchingCancellable {
            bookRepository.updateCover(bookId, null)
            existingBook.coverPath?.let { File(it).delete() }
            BookDetailMessage.COVER_REMOVED
        }.getOrElse { BookDetailMessage.COVER_FAILED }
    }

    private fun finishImportRow(rowId: String, result: ImportResult): ImportResult {
        updateImportRow(rowId, result.status)
        return result
    }

    private fun startImportProgress(candidates: List<ImportCandidate>) {
        _importProgress.value = ImportProgressState(
            rows = candidates.map { candidate ->
                ImportProgressRow(
                    id = candidate.id,
                    fileName = candidate.rowName,
                    status = ImportRowStatus.QUEUED,
                )
            },
            isRunning = candidates.isNotEmpty(),
        )
    }

    private fun markImportComplete() {
        _importProgress.update { state -> state?.copy(isRunning = false) }
    }

    private fun updateImportRow(rowId: String, status: ImportRowStatus) {
        _importProgress.update { state ->
            state?.copy(
                rows = state.rows.map { row ->
                    if (row.id == rowId) row.copy(status = status) else row
                },
            )
        }
    }

    private fun Book.withAbsolutePaths(): Book = copy(
        coverPath = coverPath?.let { storageRoots.resolve(it).absolutePath },
        filePath = storageRoots.resolve(filePath).absolutePath,
    )

    private fun saveCover(bytes: ByteArray): File {
        val coverFile = File(storageRoots.coversDir, "${UUID.randomUUID()}.jpg")
        coverFile.writeBytes(bytes)
        return coverFile
    }

    private fun savePickedCover(contentResolver: ContentResolver, uri: Uri): File {
        val extension = displayNameOf(contentResolver, uri)
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it in SupportedCoverExtensions }
            ?: "jpg"
        val coverFile = File(storageRoots.coversDir, "${UUID.randomUUID()}.$extension")
        contentResolver.openInputStream(uri)?.use { input ->
            coverFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cover image could not be opened")
        return coverFile
    }

    private fun displayNameOf(contentResolver: ContentResolver, uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun List<ImportResult>.summarize(): ImportSummary = ImportSummary(
        imported = count { it is ImportResult.Imported },
        duplicates = count { it is ImportResult.Duplicate },
        unsupported = count { it is ImportResult.Unsupported },
        failed = count { it is ImportResult.Failed },
    )
}

private fun List<ImportProgressRow>.summarize(): ImportSummary = ImportSummary(
    imported = count { it.status == ImportRowStatus.IMPORTED },
    duplicates = count { it.status == ImportRowStatus.DUPLICATE },
    unsupported = count { it.status == ImportRowStatus.UNSUPPORTED },
    failed = count { it.status == ImportRowStatus.FAILED },
)

private fun List<Book>.filterBy(filter: LibraryFilter): List<Book> = when (filter) {
    LibraryFilter.ALL -> this
    LibraryFilter.READING -> filter { it.readingPercent > 0f && it.readingPercent < FinishedThreshold }
    LibraryFilter.FINISHED -> filter { it.readingPercent >= FinishedThreshold }
    LibraryFilter.NOT_STARTED -> filter { it.readingPercent <= 0f }
}

private fun List<Book>.filterByQuery(query: String): List<Book> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return this
    return filter { book ->
        book.title.contains(normalizedQuery, ignoreCase = true) ||
            book.author.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
            book.series.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
            book.seriesNumber.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
            book.description.orEmpty().contains(normalizedQuery, ignoreCase = true)
    }
}

private fun List<Book>.sortedBy(sort: LibrarySort): List<Book> = when (sort) {
    LibrarySort.IMPORT_DATE -> sortedWith(compareByDescending<Book> { it.createdAt }.thenBy { it.title.lowercase() })
    LibrarySort.TITLE -> sortedWith(compareBy<Book> { it.title.lowercase() }.thenByDescending { it.createdAt })
    LibrarySort.AUTHOR -> sortedWith(compareBy<Book> { it.author.orEmpty().lowercase() }.thenBy { it.title.lowercase() })
    LibrarySort.LAST_READ -> sortedWith(compareByDescending<Book> { it.lastReadAt ?: 0L }.thenBy { it.title.lowercase() })
    LibrarySort.PROGRESS -> sortedWith(compareByDescending<Book> { it.readingPercent }.thenBy { it.title.lowercase() })
}

private const val FinishedThreshold = 0.98f

private val SupportedCoverExtensions = setOf("jpg", "jpeg", "png", "webp")
