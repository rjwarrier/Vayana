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
    /** When the card is next due for review; null means new, so due now. */
    val dueAt: Long? = null,
    val intervalDays: Int = 0,
    val easeFactor: Float = 2.5f,
    val repetitions: Int = 0,
)
