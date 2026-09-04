package com.vayana.reader.api

/**
 * A position within a book. [cfi] is the durable, reflow-safe locator; the rest is display-only.
 * [currentPage] and [totalPages] are an estimate derived from the current font size, line
 * height, and margins — they shift whenever those settings change and are null until the engine
 * has measured at least one chapter under the active layout. [chapterMinutesLeft] and
 * [bookMinutesLeft] use a fixed reading-speed assumption over remaining text, so — unlike page
 * count — they don't depend on font size or margins.
 */
data class Locator(
    val cfi: String?,
    val href: String?,
    val progression: Float,
    val chapterTitle: String?,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val chapterMinutesLeft: Int? = null,
    val bookMinutesLeft: Int? = null,
)

data class TocEntry(
    val title: String,
    val href: String,
    val children: List<TocEntry> = emptyList(),
)

data class OpenBook(
    val title: String,
    val toc: List<TocEntry>,
)

enum class ReaderAnnotationType {
    HIGHLIGHT, UNDERLINE, BOOKMARK, NOTE
}

data class ReaderAnnotation(
    val id: String,
    val type: ReaderAnnotationType,
    val cfi: String,
    val colorKey: String,
    val note: String?,
    val text: String? = null,
)

/** Source handed to [BookEngine.open] — a resolved local file path, never a domain `Book` (§0.5: no cross-layer coupling). */
data class BookSource(val absoluteFilePath: String)

sealed interface NavTarget {
    data class ToLocator(val locator: Locator) : NavTarget
    data class ToHref(val href: String) : NavTarget
    data class ToFraction(val fraction: Float) : NavTarget
    data object NextPage : NavTarget
    data object PreviousPage : NavTarget
}

sealed interface EngineEvent {
    data class Relocated(val locator: Locator) : EngineEvent
    data class SelectionChanged(val selection: ReaderSelection?) : EngineEvent
    data class SearchCompleted(val query: String, val results: List<SearchResult>) : EngineEvent
    data class Error(val message: String) : EngineEvent
}

data class SearchResult(
    val cfi: String,
    /** The matched text with a little surrounding context, for the results list. */
    val excerpt: String,
    val chapterTitle: String?,
)

data class ReaderSelection(
    val cfi: String,
    val selectedText: String,
    val chapterTitle: String?,
    /** Vertical center of the selection in its rendered page, from 0 (top) to 1 (bottom). */
    val verticalPosition: Float? = null,
)
