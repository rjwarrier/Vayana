package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "shelves")
data class ShelfEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Many-to-many join: a book can sit on several personal shelves. */
@Entity(
    tableName = "book_shelf_cross_ref",
    primaryKeys = ["bookId", "shelfId"],
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ShelfEntity::class, parentColumns = ["id"], childColumns = ["shelfId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId"), Index("shelfId")],
)
data class BookShelfCrossRefEntity(
    val bookId: Long,
    val shelfId: Long,
)
