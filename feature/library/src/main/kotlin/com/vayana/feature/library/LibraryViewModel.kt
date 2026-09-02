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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of one import batch — surfaced as a one-line summary (PROMPT2appbuild.md §4.1's fuller per-file sheet is a later pass). */
data class ImportSummary(val imported: Int, val duplicates: Int, val unsupported: Int, val failed: Int)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookFileImporter: BookFileImporter,
    private val storageRoots: StorageRoots,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** [Book.coverPath] comes back root-relative from the repository — resolved to an absolute
     * path here so the UI (Coil) can load it directly without depending on [StorageRoots] itself. */
    val books: StateFlow<List<Book>> = bookRepository.observeAll()
        .map { books -> books.map { it.withAbsoluteCoverPath() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun Book.withAbsoluteCoverPath(): Book {
        val relativePath = coverPath ?: return this
        return copy(coverPath = storageRoots.resolve(relativePath).absolutePath)
    }

    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary: StateFlow<ImportSummary?> = _importSummary

    fun onImportSummaryShown() {
        _importSummary.value = null
    }

    fun importFiles(contentResolver: ContentResolver, uris: List<Uri>) {
        viewModelScope.launch {
            val results = withContext(dispatchers.io) {
                uris.map { uri -> importOne(contentResolver, uri, displayNameOf(contentResolver, uri)) }
            }
            _importSummary.value = results.summarize()
        }
    }

    fun importFolder(contentResolver: ContentResolver, treeUri: Uri) {
        viewModelScope.launch {
            val results = withContext(dispatchers.io) {
                val root = DocumentFile.fromTreeUri(appContext, treeUri)
                val files = root?.listFiles()?.filter { it.isFile } ?: emptyList()
                files.map { doc -> importOne(contentResolver, doc.uri, doc.name.orEmpty()) }
            }
            _importSummary.value = results.summarize()
        }
    }

    private sealed interface ImportResult {
        data object Imported : ImportResult
        data object Duplicate : ImportResult
        data object Unsupported : ImportResult
        data object Failed : ImportResult
    }

    private suspend fun importOne(contentResolver: ContentResolver, uri: Uri, displayName: String): ImportResult {
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val format = BookFormat.entries.firstOrNull { it.name.equals(extension, ignoreCase = true) }
            ?: return ImportResult.Unsupported
        if (format != BookFormat.EPUB) return ImportResult.Unsupported // only EPUB is parsed natively so far

        return runCatching {
            val imported = bookFileImporter.import(uri, extension)
            val metadata = EpubParser.parse(imported.file)
            val coverPath = metadata.coverBytes?.let { bytes -> saveCover(bytes) }

            val book = bookRepository.insertIfNew(
                title = metadata.title,
                author = metadata.author,
                description = metadata.description,
                coverPath = coverPath?.let { storageRoots.relativize(it) },
                filePath = storageRoots.relativize(imported.file),
                format = format,
                fileHash = imported.sha256,
            )
            if (book != null) ImportResult.Imported else ImportResult.Duplicate
        }.getOrElse { ImportResult.Failed }
    }

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
