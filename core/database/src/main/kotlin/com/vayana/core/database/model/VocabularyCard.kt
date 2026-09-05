package com.vayana.core.database.model

data class VocabularyCard(
    val id: Long,
    val word: String,
    val definition: String,
    val sentence: String?,
    val bookId: Long?,
    val bookTitle: String?,
    val createdAt: Long,
    val lastReviewedAt: Long?,
    val known: Boolean,
)
