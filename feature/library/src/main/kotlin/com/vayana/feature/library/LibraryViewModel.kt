package com.vayana.feature.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.filesystem.BookFileImporter
import com.vayana.core.filesystem.StorageRoots
import com.vayana.format.epub.EpubParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
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

data class LibraryControls(
    val query: String = "",
    val sort: LibrarySort = LibrarySort.IMPORT_DATE,
    val filter: LibraryFilter = LibraryFilter.ALL,
)

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val controls: LibraryControls = LibraryControls(),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookFileImporter: BookFileImporter,
    private val storageRoots: StorageRoots,
    private val dispatchers: DispatcherProvider,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val controls = MutableStateFlow(LibraryControls())

    /** [Book.coverPath] and [Book.filePath] come back root-relative; resolve both before UI use. */
    private val allBooks: Flow<List<Book>> = bookRepository.observeAll()
        .map { books -> books.map { it.withAbsolutePaths() } }

    val uiState: StateFlow<LibraryUiState> = combine(allBooks, controls) { books, controls ->
        LibraryUiState(
            books = books
                .filterBy(controls.filter)
                .filterByQuery(controls.query)
                .sortedBy(controls.sort),
            controls = controls,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun observeBook(bookId: Long): StateFlow<Book?> = allBooks
        .map { books -> books.firstOrNull { it.id == bookId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary: StateFlow<ImportSummary?> = _importSummary

    private val _importProgress = MutableStateFlow<ImportProgressState?>(null)
    val importProgress: StateFlow<ImportProgressState?> = _importProgress

    fun updateQuery(query: String) {
        controls.update { it.copy(query = query) }
    }

    fun updateSort(sort: LibrarySort) {
        controls.update { it.copy(sort = sort) }
    }

    fun updateFilter(filter: LibraryFilter) {
        controls.update { it.copy(filter = filter) }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch { bookRepository.softDelete(bookId) }
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
        return runCatching {
            updateImportRow(candidate.id, ImportRowStatus.COPYING)
            val imported = bookFileImporter.import(uri, extension)
            importedFile = imported.file
            updateImportRow(candidate.id, ImportRowStatus.PARSING)
            val metadata = EpubParser.parse(imported.file)
            coverFile = metadata.coverBytes?.let { bytes -> saveCover(bytes) }

            val book = bookRepository.insertIfNew(
                title = metadata.title,
                author = metadata.author,
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
