package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/** A disposable, device-local index. Never included in backups or cloud snapshots. */
@Entity(tableName = "epub_passages", foreignKeys = [ForeignKey(
    entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("bookId")])
data class EpubPassageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val fileHash: String,
    val chapterHref: String,
    val chapterTitle: String,
    val text: String,
)

@Fts4(contentEntity = EpubPassageEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "epub_passages_fts")
data class EpubPassageFtsEntity(val text: String)
