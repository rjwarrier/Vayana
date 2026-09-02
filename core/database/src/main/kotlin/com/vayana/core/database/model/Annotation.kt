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
)
