package com.vayana.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Where a highlight stands in spaced review (the same SM-2 style spacing as vocabulary cards). Kept per device and
 * not synced: reviewing must not touch the annotation itself, whose `updatedAt` decides sync merges. It is keyed by
 * the annotation's `syncId`, its identity across edits and devices, and is deliberately not a foreign key - sync
 * merges replace annotation rows, which would silently wipe the schedule.
 */
@Entity(tableName = "highlight_reviews", indices = [Index("dueAt")])
data class HighlightReviewEntity(
    @PrimaryKey val annotationSyncId: String,
    val dueAt: Long,
    @ColumnInfo(defaultValue = "0") val intervalDays: Int = 0,
    @ColumnInfo(defaultValue = "2.5") val easeFactor: Float = 2.5f,
    @ColumnInfo(defaultValue = "0") val repetitions: Int = 0,
    val lastReviewedAt: Long,
)
