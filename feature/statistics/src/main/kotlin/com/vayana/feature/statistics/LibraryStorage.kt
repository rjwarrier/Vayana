package com.vayana.feature.statistics

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat

/** One book's file, as far as its size goes: what [libraryStorage] needs, and all that re-measuring depends on. */
data class BookFileRef(
    val bookId: Long,
    val title: String,
    val format: BookFormat,
    val filePath: String,
    /** Changes when the file is replaced, so a new file at the same path is measured again. */
    val fileHash: String,
    /** The synced copy's size, for a book whose file isn't on this device. */
    val cloudSizeBytes: Long?,
)

data class BookSize(val bookId: Long, val title: String, val bytes: Long)

data class FormatSize(val format: BookFormat, val bytes: Long, val bookCount: Int)

/** How much room the library's book files take: covers and the app's own data aren't counted. */
data class LibraryStorage(
    val totalBytes: Long,
    val bookCount: Int,
    val largest: BookSize,
    val smallest: BookSize,
    /** Largest share first. */
    val byFormat: List<FormatSize>,
) {
    val averageBytes: Long get() = totalBytes / bookCount
}

/** Books read outside the app have no file, so they have no size either. */
fun List<Book>.fileRefs(): List<BookFileRef> = mapNotNull { book ->
    if (book.format.isOffline) return@mapNotNull null
    BookFileRef(book.id, book.title, book.format, book.filePath, book.fileHash, book.fileAssetSizeBytes)
}

/**
 * Sizes [books] by their file on this device ([localSize], null when it isn't there), else by the size recorded for
 * their synced copy. Books with neither are left out. Null when no book has a size.
 */
fun libraryStorage(books: List<BookFileRef>, localSize: (filePath: String) -> Long?): LibraryStorage? {
    val sized = books.mapNotNull { book ->
        val bytes = book.filePath.takeIf { it.isNotBlank() }?.let(localSize) ?: book.cloudSizeBytes
        bytes?.takeIf { it >= 0 }?.let { book to it }
    }
    if (sized.isEmpty()) return null
    val largest = sized.maxBy { it.second }
    val smallest = sized.minBy { it.second }
    val byFormat = sized.groupBy { it.first.format }
        .map { (format, entries) -> FormatSize(format, entries.sumOf { it.second }, entries.size) }
        .sortedByDescending { it.bytes }
    return LibraryStorage(
        totalBytes = sized.sumOf { it.second },
        bookCount = sized.size,
        largest = largest.toBookSize(),
        smallest = smallest.toBookSize(),
        byFormat = byFormat,
    )
}

private fun Pair<BookFileRef, Long>.toBookSize() = BookSize(first.bookId, first.title, second)
