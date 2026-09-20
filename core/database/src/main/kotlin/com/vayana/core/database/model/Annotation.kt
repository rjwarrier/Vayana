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
)

/** Locators of imported Goodreads quotes: `quote:` is the older form, `goodreads-quote:` the current one. */
fun isCommunityQuoteLocator(locator: String): Boolean =
    locator.startsWith("quote:") || locator.startsWith("goodreads-quote:")

/** A popular quote imported from Goodreads (including entries created before source-specific locators). */
fun Annotation.isCommunityQuote(): Boolean =
    type == AnnotationType.UNDERLINE && colorKey == "popular" && isCommunityQuoteLocator(locator)
