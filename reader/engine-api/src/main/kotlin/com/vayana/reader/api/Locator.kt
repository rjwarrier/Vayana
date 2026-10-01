package com.vayana.reader.api

/**
 * A position within a book. [cfi] is the durable, reflow-safe locator; the rest is display-only.
 * [currentPage] and [totalPages] are an estimate derived from the current font size, line
 * height, and margins — they shift whenever those settings change and are null until the engine
 * has measured at least one chapter under the active layout. [chapterMinutesLeft] and
 * [bookMinutesLeft] use a fixed reading-speed assumption over remaining text, so — unlike page
 * count — they don't depend on font size or margins. [href] is the href of the TOC entry the
 * position falls under, and [tocPages] maps each TOC entry's href to the estimated page its
 * section starts on, numbered the same way as [currentPage].
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
    val tocPages: Map<String, Int> = emptyMap(),
)

data class TocEntry(
    val title: String,
    val href: String,
    val children: List<TocEntry> = emptyList(),
)

data class OpenBook(
    val title: String,
    val toc: List<TocEntry>,
    /** Pre-paginated pages (PDF): no reflowable text, so styling, annotations and read aloud don't apply. */
    val fixedLayout: Boolean = false,
    /** Printed page labels supplied by a PDF (for example "iv", "1", "A-3"), in page order. */
    val pageLabels: List<String> = emptyList(),
    /** The book's language as its metadata declares it (a primary subtag like "ml"), or null when it doesn't. */
    val language: String? = null,
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

/** Result of [BookEngine.mergeRanges]: the union's CFI and text, and which of the other CFIs it swallowed. */
data class MergedRange(
    val cfi: String,
    val text: String,
    val merged: List<String>,
)

/** Source handed to [BookEngine.open] — a resolved local file path, never a domain `Book` (§0.5: no cross-layer coupling). */
data class BookSource(val absoluteFilePath: String)

sealed interface NavTarget {
    data class ToLocator(val locator: Locator) : NavTarget
    data class ToHref(val href: String) : NavTarget
    data class ToFraction(val fraction: Float) : NavTarget
    /** Zero-based page index for a fixed-layout book. */
    data class ToPage(val pageIndex: Int) : NavTarget
    data object NextPage : NavTarget
    data object PreviousPage : NavTarget
}

sealed interface EngineEvent {
    data class Relocated(val locator: Locator) : EngineEvent
    data class SelectionChanged(val selection: ReaderSelection?) : EngineEvent
    /** A tap on a rendered annotation, positioned as fractions of the reader view. A null id dismisses its card. */
    data class AnnotationTapped(
        val annotationId: String?,
        val top: Float? = null,
        val bottom: Float? = null,
    ) : EngineEvent
    /** A completed pinch on reflowable text requested one configured font-size step. */
    data class FontSizeStepRequested(val direction: Int) : EngineEvent
    data class SearchCompleted(val query: String, val results: List<SearchResult>) : EngineEvent
    data class Error(val message: String) : EngineEvent

    /**
     * The reader got to the end of the story: the last page of its final chapter or epilogue, or paged on past it into
     * back matter (other books by the author, acknowledgements, an excerpt of the next book). Sent at most once per
     * opened book.
     */
    data object StoryEndReached : EngineEvent

    /** The engine's rendering process crashed or was killed; the engine is unusable and must be replaced. */
    data object RendererGone : EngineEvent

    /**
     * Whether the page on screen is larger than the view (a zoomed or fit-width PDF page), so a drag scrolls it and
     * must not be taken for an edge swipe.
     */
    data class PageScrollableChanged(val scrollable: Boolean) : EngineEvent

    /** A PDF cannot finish opening until the user supplies a password. */
    data class PdfPasswordRequired(val incorrect: Boolean) : EngineEvent

    /** The configured tap gesture on the book page requested the reader controls. */
    data object ControlsRequested : EngineEvent
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
    /** Top edge of the selection as a fraction of the reader view's height, 0 (top) to 1 (bottom). */
    val top: Float? = null,
    /** Bottom edge of the selection, in the same fractions as [top]. */
    val bottom: Float? = null,
    /** The word was selected by a double tap to look it up, not by the reader marking text. */
    val isWordLookup: Boolean = false,
)
