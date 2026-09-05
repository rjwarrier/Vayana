package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A saved dictionary lookup turned into a flashcard-style review item (PROMPT2appbuild.md
 * vocabulary review recommendation). [bookId] is not a foreign key on purpose - a card should
 * survive its source book being deleted, it just loses the "jump back to book" affordance.
 */
@Entity(tableName = "vocabulary_cards", indices = [Index(value = ["syncId"], unique = true), Index("bookId")])
data class VocabularyCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "vocabulary-${UUID.randomUUID()}",
    val word: String,
    val definition: String,
    val sentence: String?,
    val bookId: Long?,
    val bookTitle: String?,
    val createdAt: Long,
    val lastReviewedAt: Long?,
    val known: Boolean,
)
