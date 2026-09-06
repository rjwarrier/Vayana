package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Reconciles independently imported copies of the same book file across devices.
 *
 * When two devices import the identical EPUB independently, each gets its own local [BookEntity]
 * with its own `syncId`. This table records that `fileHash` maps to a canonical `syncId` so a
 * future sync merge can recognize the duplicate and fold the records together instead of matching
 * by title/author. Schema only for now - milestone 1 does not populate or read this table yet;
 * reconciliation logic lands with the merge milestone (docs/GITHUB_SYNC_IMPLEMENTATION_PLAN.md §4).
 */
@Entity(
    tableName = "book_aliases",
    indices = [Index(value = ["fileHash"], unique = true)],
)
data class BookAliasEntity(
    @PrimaryKey val syncId: String,
    val fileHash: String,
    val createdAt: Long,
)
