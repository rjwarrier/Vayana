package com.vayana.core.database.model

enum class AnnotationType {
    HIGHLIGHT, UNDERLINE, BOOKMARK, NOTE
}

data class Annotation(
    val id: Long,
    val bookId: Long,
    val type: AnnotationType,
    val colorKey: String,
    val locator: String,
    val chapterTitle: String?,
    val chapterHref: String?,
    val selectedText: String,
    val readerNote: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    /** Stable identity across devices and edits; blank on an annotation that has not been saved yet. */
    val syncId: String = "",
    val reviewQuestion: String? = null,
)

/** Locators of imported Goodreads quotes: `quote:` is the older form, `goodreads-quote:` the current one. */
fun isCommunityQuoteLocator(locator: String): Boolean =
    locator.startsWith("quote:") || locator.startsWith("goodreads-quote:")

/** A popular quote imported from Goodreads (including entries created before source-specific locators). */
fun Annotation.isCommunityQuote(): Boolean =
    type == AnnotationType.UNDERLINE && colorKey == "popular" && isCommunityQuoteLocator(locator)

/**
 * How many Goodreads readers highlighted this community quote, from the "N highlights" note its import writes;
 * null for anything else, or a quote whose note has since been edited away from that form.
 */
fun Annotation.communityHighlightCount(): Int? =
    if (!isCommunityQuote()) null else CommunityHighlightCountRegex.find(readerNote.orEmpty())?.groupValues?.get(1)?.toIntOrNull()

private val CommunityHighlightCountRegex = Regex("""^\s*(\d+)\s+highlights?\b""", RegexOption.IGNORE_CASE)
