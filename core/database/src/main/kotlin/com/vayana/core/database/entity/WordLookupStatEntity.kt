package com.vayana.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_lookup_stats")
data class WordLookupStatEntity(
    @PrimaryKey val word: String,
    val count: Int,
    val lastLookedUpAt: Long,
)
