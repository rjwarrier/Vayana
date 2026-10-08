package com.vayana.feature.reader

import java.util.UUID

/** One reading session; elapsed time advances only after reader input and stops at idle/pause. */
internal class ReadingTimeTracker(
    private val idleTimeoutMillis: Long,
    private val continuationGraceMillis: Long,
) {
    private var startedAt: Long? = null
    private var accountedUntil = 0L
    private var lastInteractionAt = 0L
    private var activeMillis = 0L
    private var suspendedAt: Long? = null
    private var syncId = ""

    val elapsedSeconds: Long
        get() = activeMillis / 1000L

    val isTimingActive: Boolean
        get() = startedAt != null && suspendedAt == null

    val hasSession: Boolean
        get() = startedAt != null

    private fun start(now: Long) {
        if (startedAt != null) return
        startedAt = now
        syncId = "session-${UUID.randomUUID()}"
        accountedUntil = now
        lastInteractionAt = now
        activeMillis = 0L
        suspendedAt = null
    }

    fun interact(now: Long): ReadingTimeUpdate {
        var update = flush(now)
        val suspended = suspendedAt
        if (suspended != null) {
            suspendedAt = null
            update += if (now - suspended <= continuationGraceMillis) {
                accountedUntil = now
                ReadingTimeUpdate(activeSessionSeconds = elapsedSeconds)
            } else {
                complete(suspended).also { start(now) }
            }
        } else if (startedAt == null) {
            start(now)
            update = update.copy(activeSessionSeconds = elapsedSeconds)
        }
        lastInteractionAt = now
        return update
    }

    fun flush(now: Long): ReadingTimeUpdate {
        suspendedAt?.let { suspended ->
            return if (now - suspended > continuationGraceMillis) complete(suspended) else ReadingTimeUpdate(
                activeSessionSeconds = elapsedSeconds, checkpoint = checkpoint(suspended))
        }
        val start = startedAt ?: return ReadingTimeUpdate()
        val deadline = lastInteractionAt + idleTimeoutMillis
        val end = minOf(now, deadline)
        val seconds = ((end - accountedUntil) / 1000L).coerceAtLeast(0L)
        accountedUntil += seconds * 1000L
        activeMillis += seconds * 1000L
        val saved = checkpoint(end)
        val session = if (now >= deadline) complete(deadline).session else null
        return ReadingTimeUpdate(seconds, session, elapsedSeconds, saved)
    }

    fun pause(now: Long): ReadingTimeUpdate {
        val update = flush(now)
        if (startedAt != null && suspendedAt == null) suspendedAt = now
        return update
    }

    fun finish(now: Long): ReadingTimeUpdate {
        var update = flush(now)
        if (startedAt != null) update += complete(suspendedAt ?: now)
        return update
    }

    private fun complete(endedAt: Long): ReadingTimeUpdate {
        val start = startedAt ?: return ReadingTimeUpdate()
        val seconds = elapsedSeconds
        val saved = checkpoint(endedAt)
        startedAt = null
        accountedUntil = 0L
        lastInteractionAt = 0L
        activeMillis = 0L
        suspendedAt = null
        return ReadingTimeUpdate(
            session = if (seconds > 0L) CompletedReadingSession(start, endedAt, seconds) else null,
            activeSessionSeconds = 0L,
            checkpoint = saved,
        )
    }

    private fun checkpoint(endedAt: Long): ReadingTimeCheckpoint? {
        val start = startedAt ?: return null
        // Wall clock can move backward mid-session; a checkpoint must never end before it starts.
        return if (elapsedSeconds > 0) ReadingTimeCheckpoint(syncId, start, maxOf(endedAt, start), elapsedSeconds) else null
    }
}

private operator fun ReadingTimeUpdate.plus(other: ReadingTimeUpdate): ReadingTimeUpdate =
    ReadingTimeUpdate(
        addedSeconds = addedSeconds + other.addedSeconds,
        session = other.session ?: session,
        activeSessionSeconds = other.activeSessionSeconds,
        checkpoint = other.checkpoint ?: checkpoint,
    )

internal data class CompletedReadingSession(
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
)

internal data class ReadingTimeUpdate(
    val addedSeconds: Long = 0L,
    val session: CompletedReadingSession? = null,
    val activeSessionSeconds: Long = 0L,
    val checkpoint: ReadingTimeCheckpoint? = null,
)

/** Cumulative, durable progress for a single session; retries keep the same identity. */
internal data class ReadingTimeCheckpoint(
    val syncId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
)

internal object ReadingSessionContinuationStore {
    private val trackers = mutableMapOf<Long, ReadingTimeTracker>()

    @Synchronized
    fun take(bookId: Long): ReadingTimeTracker? = trackers.remove(bookId)

    @Synchronized
    fun put(bookId: Long, tracker: ReadingTimeTracker) {
        if (tracker.hasSession) trackers[bookId] = tracker
    }

    @Synchronized
    fun drainExpired(now: Long): List<Pair<Long, ReadingTimeUpdate>> {
        val updates = mutableListOf<Pair<Long, ReadingTimeUpdate>>()
        val iterator = trackers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val update = entry.value.flush(now)
            if (update.session != null || !entry.value.hasSession) {
                iterator.remove()
                updates += entry.key to update
            }
        }
        return updates
    }
}
