package com.vayana.feature.library

import com.vayana.core.database.model.Book

internal sealed interface SeriesLibraryItem {
    data class Single(val book: Book) : SeriesLibraryItem
    data class Folder(val key: String, val title: String, val books: List<Book>) : SeriesLibraryItem
}

/** Preserve the active library sort by placing a folder at its first member's position. */
internal fun seriesLibraryItems(books: List<Book>): List<SeriesLibraryItem> {
    val groups = books.asSequence()
        .filter { !it.series.isNullOrBlank() }
        .groupBy { it.series!!.metadataKey() }
        .filterKeys { it.isNotEmpty() }
    val emitted = mutableSetOf<String>()
    return books.mapNotNull { book ->
        val key = book.series?.metadataKey().orEmpty()
        val members = groups[key]
        if (members == null || members.size < 2) {
            SeriesLibraryItem.Single(book)
        } else if (emitted.add(key)) {
            SeriesLibraryItem.Folder(key, members.first().series!!.trim(), members.sortedForSeries())
        } else {
            null
        }
    }
}

internal fun List<Book>.sortedForSeries(): List<Book> = sortedWith(
    compareBy<Book, Double?>(nullsLast()) { it.seriesNumber?.trim()?.toDoubleOrNull() }
        .thenBy { it.title.lowercase() }
        .thenBy { it.id },
)
