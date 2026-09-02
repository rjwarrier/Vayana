package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("bookId"),
        Index("type"),
        Index("colorKey"),
        Index("updatedAt"),
    ],
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val type: String,
    val colorKey: String,
    val locator: String,
    val chapterTitle: String?,
    val chapterHref: String?,
    val selectedText: String,
    val readerNote: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
