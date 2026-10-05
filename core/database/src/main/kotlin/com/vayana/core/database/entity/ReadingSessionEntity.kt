package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** One continuous stretch of reading a book - starts on open/resume, ends on pause/idle timeout. */
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["syncId"], unique = true),
        Index("bookId"),
        Index("startedAt"),
    ],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "session-${UUID.randomUUID()}",
    val bookId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
    val startPage: Int? = null,
    val endPage: Int? = null,
    val activeIntervals: String? = null,
)
