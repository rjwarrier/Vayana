package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/** Full-text index over [BookEntity]'s searchable text; Room keeps it in sync with triggers. Row id = book id. */
@Fts4(contentEntity = BookEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "books_fts")
data class BookFtsEntity(
    val title: String,
    val author: String?,
    val series: String?,
    val seriesNumber: String?,
    val tagsCsv: String?,
    val description: String?,
)

/** Full-text index over [AnnotationEntity]'s text. Row id = annotation id. */
@Fts4(contentEntity = AnnotationEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "annotations_fts")
data class AnnotationFtsEntity(
    val selectedText: String,
    val readerNote: String?,
    val chapterTitle: String?,
)
