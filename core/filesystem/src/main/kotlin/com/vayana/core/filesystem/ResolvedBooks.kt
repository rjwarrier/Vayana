package com.vayana.core.filesystem

import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.BookRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn

/**
 * Books come back from the database with root-relative [Book.coverPath]/[Book.filePath]; this resolves them for UI use.
 * [all] is shared app-wide so Library and Search don't each re-resolve (and stat covers) on every library change.
 */
@Singleton
class ResolvedBooks @Inject constructor(
    bookRepository: BookRepository,
    private val storageRoots: StorageRoots,
    private val dispatchers: DispatcherProvider,
    @param:ApplicationScope scope: CoroutineScope,
) {
    val all: Flow<List<Book>> = resolveAll(bookRepository.observeAll())
        .shareIn(scope, SharingStarted.WhileSubscribed(StopTimeoutMillis, replayExpirationMillis = 0), replay = 1)

    fun resolve(book: Book): Book = book.copy(
        coverPath = book.coverPath?.let { storageRoots.resolve(it).absolutePath },
        filePath = book.filePath.takeIf { it.isNotBlank() }?.let { storageRoots.resolve(it).absolutePath }.orEmpty(),
        // An alternate whose file has gone (e.g. replaced along with the source file) drops out, so the UI
        // never offers a cover it can't show.
        customCoverPath = book.customCoverPath?.let { storageRoots.resolve(it) }?.takeIf { it.isFile }?.absolutePath,
        goodreadsCoverPath = book.goodreadsCoverPath?.let { storageRoots.resolve(it) }?.takeIf { it.isFile }?.absolutePath,
    )

    /**
     * Runs on the IO dispatcher: resolving alternates stats two cover files per book. Usually one book changes per
     * emission (a progress write, a sync), so a book whose row is unchanged since the previous emission of this
     * collection keeps its resolved copy instead of being copied and stat'ed again.
     */
    fun resolveAll(books: Flow<List<Book>>): Flow<List<Book>> = flow {
        var previous = emptyMap<Long, ResolvedBook>()
        books.collect { list ->
            val next = HashMap<Long, ResolvedBook>(list.size)
            val resolved = list.map { book ->
                val cached = previous[book.id]?.takeIf { it.source == book }
                (cached ?: ResolvedBook(book, resolve(book))).also { next[book.id] = it }.resolved
            }
            previous = next
            emit(resolved)
        }
    }.flowOn(dispatchers.io)

    private class ResolvedBook(val source: Book, val resolved: Book)
}

private const val StopTimeoutMillis = 5_000L
