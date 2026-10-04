package com.vayana.feature.notes

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import com.vayana.core.common.ApplicationScope
import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.Reader
import com.vayana.core.database.model.Book
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A separate generated directory keeps automatic exports away from handwritten notes. */
@Singleton
class AutomaticNotebookExport @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val books: BookRepository,
    private val annotations: AnnotationRepository,
    private val settings: SettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var job: Job? = null
    private val _status = MutableStateFlow(NotebookExportStatus.OFF)
    val status = _status.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        if (job != null) return
        job = scope.launch {
            settings.observe(SettingsRegistry.NotebookExportFolder).distinctUntilChanged().collectLatest { folder ->
                val manager = WorkManager.getInstance(context)
                if (folder.isBlank()) {
                    manager.cancelUniqueWork(WorkName)
                    _status.value = NotebookExportStatus.OFF
                } else {
                    manager.enqueueUniquePeriodicWork(WorkName, ExistingPeriodicWorkPolicy.KEEP,
                        PeriodicWorkRequestBuilder<NotebookExportWorker>(15, TimeUnit.MINUTES).build())
                    // Only observe expensive notebook data while export is enabled. Reader progress, queue and
                    // status changes do not affect the Markdown and must not trigger folder scans.
                    combine(books.observeAll().map { library -> library.map(::notebookMetadata) }.distinctUntilChanged(),
                        annotations.observeAll()) { library, notes -> library to notes }
                        .distinctUntilChanged().debounce(2000).collect { export() }
                }
            }
        }
    }

    suspend fun export(): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        val folder = settings.observe(SettingsRegistry.NotebookExportFolder).first()
        if (folder.isBlank()) { _status.value = NotebookExportStatus.OFF; return@withLock true }
        try {
            val root = requireNotNull(DocumentFile.fromTreeUri(context, Uri.parse(folder)))
            require(root.isDirectory && root.canWrite())
            val destination = root.findFile(ExportDirectory) ?: root.createDirectory(ExportDirectory)
            requireNotNull(destination)
            require(destination.isDirectory && destination.canWrite())
            val library = books.observeAll().first().associateBy { it.id }
            val notebooks = annotations.observeAll().first().filterNot { it.isDeleted }.groupBy { it.bookId }
            // DocumentFile.findFile lists the entire directory. Inventory it once, instead of once per book.
            val files = destination.listFiles().associateBy { it.name }
            for ((id, book) in library) {
                currentCoroutineContext().ensureActive()
                val notes = notebooks[id].orEmpty()
                val identity = notebookIdentity(book.syncId)
                val name = "book-$identity.md"
                val marker = "<!-- Vayana generated notebook: $identity -->"
                val existing = files[name]
                if (notes.isEmpty() && existing == null) continue
                val content = "$marker\n" + highlightsMarkdown(book, notes)
                val unchanged = existing?.let { file ->
                    require(file.length() <= 32L * 1024 * 1024)
                    requireNotNull(context.contentResolver.openInputStream(file.uri)).bufferedReader().use { reader ->
                        notebookIsUnchanged(reader, content, marker)
                    }
                } ?: false
                if (unchanged) continue
                val staged = requireNotNull(destination.createFile("text/markdown", ".vayana-${UUID.randomUUID()}.md"))
                try {
                    requireNotNull(context.contentResolver.openOutputStream(staged.uri, "wt")).bufferedWriter().use { it.write(content) }
                    if (existing == null) require(staged.renameTo(name))
                    else {
                        require(existing.isFile && existing.canWrite())
                        require(existing.renameTo(".vayana-backup-${UUID.randomUUID()}.md"))
                        if (!staged.renameTo(name)) {
                            existing.renameTo(name)
                            error("Folder provider cannot replace a notebook")
                        }
                        existing.delete()
                    }
                } finally {
                    // After a successful rename this wrapper points at the final notebook.
                    if (staged.name != name) staged.delete()
                }
            }
            _status.value = NotebookExportStatus.DONE
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _status.value = NotebookExportStatus.FAILED; false }
    } }
}

enum class NotebookExportStatus { OFF, DONE, FAILED }
internal fun notebookIdentity(syncId: String): String = MessageDigest.getInstance("SHA-256")
    .digest(syncId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
internal fun automaticNotebookFileName(syncId: String): String = "book-${notebookIdentity(syncId)}.md"

/** Only fields that appear in the generated notebook, including its stable identity. */
internal data class NotebookMetadata(val id: Long, val syncId: String, val title: String, val author: String?, val tags: String?)
internal fun notebookMetadata(book: Book) = NotebookMetadata(book.id, book.syncId, book.title, book.author, book.tagsCsv)

/** Check ownership first, then compare in fixed-size buffers rather than loading another notebook-sized string. */
internal fun notebookIsUnchanged(reader: Reader, content: String, marker: String): Boolean {
    val prefix = "$marker\n"
    for (character in prefix) require(reader.read() == character.code) { "Foreign notebook" }
    var offset = prefix.length
    val buffer = CharArray(8192)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) return offset == content.length
        if (offset + count > content.length) return false
        for (index in 0 until count) if (buffer[index] != content[offset + index]) return false
        offset += count
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotebookExportEntryPoint { fun exporter(): AutomaticNotebookExport }

class NotebookExportWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val exporter = EntryPointAccessors.fromApplication(applicationContext, NotebookExportEntryPoint::class.java).exporter()
        return if (exporter.export()) Result.success() else Result.retry()
    }
}

private const val WorkName = "vayana-notebook-export"
private const val ExportDirectory = "Vayana"
