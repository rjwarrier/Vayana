package com.vayana.core.database.model

data class ReadingSession(
    val id: Long,
    val bookId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
)
