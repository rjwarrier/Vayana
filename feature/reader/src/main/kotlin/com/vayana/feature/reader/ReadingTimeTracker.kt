package com.vayana.feature.reader

/** One foreground session; elapsed time is capped at the last interaction's idle deadline. */
internal class ReadingTimeTracker(private val idleTimeoutMillis: Long) {
    private var startedAt: Long? = null
    private var accountedUntil = 0L
    private var lastInteractionAt = 0L

    fun resume(now: Long) {
        if (startedAt != null) return
        startedAt = now
        accountedUntil = now
        lastInteractionAt = now
    }

    fun interact(now: Long): ReadingTimeUpdate {
        // Close an expired session before a new interaction can move its deadline.
        val update = flush(now)
        resume(now)
        lastInteractionAt = now
        return update
    }

    fun flush(now: Long): ReadingTimeUpdate {
        val start = startedAt ?: return ReadingTimeUpdate()
        val deadline = lastInteractionAt + idleTimeoutMillis
        val end = minOf(now, deadline)
        val seconds = ((end - accountedUntil) / 1000L).coerceAtLeast(0L)
        accountedUntil += seconds * 1000L
        val session = if (now >= deadline) {
            startedAt = null
            CompletedReadingSession(start, deadline)
        } else null
        return ReadingTimeUpdate(seconds, session)
    }

    fun pause(now: Long): ReadingTimeUpdate {
        val update = flush(now)
        val start = startedAt ?: return update
        startedAt = null
        return update.copy(session = CompletedReadingSession(start, now))
    }
}

internal data class CompletedReadingSession(val startedAt: Long, val endedAt: Long)
internal data class ReadingTimeUpdate(
    val addedSeconds: Long = 0L,
    val session: CompletedReadingSession? = null,
)
