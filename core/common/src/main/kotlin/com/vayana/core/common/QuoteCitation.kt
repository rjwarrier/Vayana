package com.vayana.core.common

/** Formats a highlight/quote for sharing or copying, attributed to its source when known. */
object QuoteCitation {
    /**
     * [pattern] is the citation in the reader's language: `%1$s` the quote and `%2$s` its source (the string
     * resource `quote_citation`), so each language uses its own quotation marks.
     */
    fun format(text: String, author: String?, bookTitle: String?, chapterTitle: String?, pattern: String): String {
        if (text.isBlank()) return text
        val source = listOfNotNull(author, bookTitle, chapterTitle).filter { it.isNotBlank() }
        if (source.isEmpty()) return text
        return pattern.format(text, source.joinToString(", "))
    }
}
