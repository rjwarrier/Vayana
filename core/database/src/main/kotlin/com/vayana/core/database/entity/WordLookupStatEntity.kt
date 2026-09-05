package com.vayana.core.database.entity

import androidx.room.Entity

@Entity(tableName = "word_lookup_stats", primaryKeys = ["word", "writerOrigin"])
data class WordLookupStatEntity(
    val word: String,
    val count: Int,
    val lastLookedUpAt: Long,
    val writerOrigin: String = LegacyWriterOrigin,
)

const val LegacyWriterOrigin = "legacy-local"
