package com.vayana.feature.search

import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.dao.EpubPassageDao
import com.vayana.core.database.dao.IndexedEpubFile
import com.vayana.core.database.entity.EpubPassageEntity
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.filesystem.ResolvedBooks
import com.vayana.format.epub.EpubTextExtractor
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class IndexProgress(val done: Int = 0, val total: Int = 0, val failures: Int = 0) {
    val running: Boolean get() = done < total
}

@Singleton
class EpubContentIndex @Inject constructor(
    private val books: ResolvedBooks,
    private val dao: EpubPassageDao,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private val lock = Mutex()
    private val _progress = MutableStateFlow(IndexProgress())
    val progress = _progress.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            books.all.map { library -> library.filter { it.format == BookFormat.EPUB &&
                it.fileAvailability == BookFileAvailability.LOCAL && it.filePath.isNotBlank() }
                .map { Triple(it.id, it.fileHash, it.filePath) } }
                .distinctUntilChanged().collectLatest { local ->
                    lock.withLock {
                        dao.discardUnavailable()
                        val indexed = dao.indexedFiles().toHashSet()
                        val pending = local.filterNot { (id, hash, _) -> IndexedEpubFile(id, hash) in indexed }
                        _progress.value = IndexProgress(total = pending.size)
                        var failures = 0
                        pending.forEachIndexed { index, (id, hash, path) ->
                            currentCoroutineContext().ensureActive()
                            val result = runCatchingCancellable {
                                val chapters = EpubTextExtractor.extract(File(path))
                                val passages = chapters.asSequence().flatMap { chapter -> passageChunks(chapter.text).map { text ->
                                    EpubPassageEntity(bookId = id, fileHash = hash, chapterHref = chapter.href, chapterTitle = chapter.title, text = text)
                                } }
                                dao.replaceStreaming(id, passages)
                            }
                            if (result.isFailure) failures++
                            _progress.value = IndexProgress(index + 1, pending.size, failures)
                        }
                    }
                }
        }
    }

    fun retry() { job?.cancel(); job = null; start() }
}

/** Overlap avoids losing queries at chunk boundaries; prefer a word boundary for passage navigation. */
internal fun chunkPassage(text: String, size: Int = 1800, overlap: Int = 240): List<String> =
    passageChunks(text, size, overlap).toList()

internal fun passageChunks(text: String, size: Int = 1800, overlap: Int = 240): Sequence<String> {
    require(size > overlap && overlap >= 0)
    return sequence {
        var start = 0
        while (start < text.length) {
            val end = minOf(start + size, text.length)
            yield(text.substring(start, end).trim())
            if (end == text.length) break
            val candidate = end - overlap
            start = text.indexOf(' ', candidate).takeIf { it in candidate until end }?.plus(1) ?: candidate
        }
    }
}
