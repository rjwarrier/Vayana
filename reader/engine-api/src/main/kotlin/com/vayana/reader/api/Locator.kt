package com.vayana.reader.api

/** A position within a book. [cfi] is the durable, reflow-safe locator; the rest is display-only. */
data class Locator(
    val cfi: String?,
    val href: String?,
    val progression: Float,
    val chapterTitle: String?,
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
    data class Error(val message: String) : EngineEvent
}

data class ReaderSelection(
    val cfi: String,
    val selectedText: String,
    val chapterTitle: String?,
)
