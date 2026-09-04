package com.vayana.core.common

/** Formats a highlight/quote for sharing or copying, attributed to its source when known. */
object QuoteCitation {
    fun format(text: String, author: String?, bookTitle: String?, chapterTitle: String?): String {
        if (text.isBlank()) return text
        val source = listOfNotNull(author, bookTitle, chapterTitle).filter { it.isNotBlank() }
        if (source.isEmpty()) return text
        return "“$text”\n— ${source.joinToString(", ")}"
    }
}
