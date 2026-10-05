package com.vayana.core.wear

import com.vayana.core.common.ReadingInterval
import com.vayana.core.common.ReadingIntervals

enum class PhysicalTimerPhase { RUNNING, PAUSED, STOPPED }

/** Monotonic time measures reading; wall-clock time is used only to date the saved log. */
data class PhysicalTimerSession(
    val bookId: Long,
    val bookTitle: String,
    val syncId: String,
    val startedAt: Long,
    val startPage: Int,
    val accumulatedMillis: Long = 0,
    val runningSince: Long,
    val bootCount: Int,
    val phase: PhysicalTimerPhase = PhysicalTimerPhase.RUNNING,
    val endedAt: Long = 0,
    val pageCount: Int? = null,
    val activeIntervals: String? = null,
    val anchorWall: Long = startedAt,
    val anchorMono: Long = runningSince,
    val clockChanged: Boolean = false,
) {
    fun elapsedMillis(now: Long): Long = accumulatedMillis +
        if (phase == PhysicalTimerPhase.RUNNING) (now - runningSince).coerceAtLeast(0) else 0

    fun pause(now: Long): PhysicalTimerSession = if (phase != PhysicalTimerPhase.RUNNING) this
        else copy(accumulatedMillis = elapsedMillis(now), phase = PhysicalTimerPhase.PAUSED,
            activeIntervals = closedIntervals(now))

    fun resume(now: Long): PhysicalTimerSession = if (phase != PhysicalTimerPhase.PAUSED) this
        else copy(runningSince = now, phase = PhysicalTimerPhase.RUNNING)

    fun stop(now: Long, wallTime: Long): PhysicalTimerSession = if (phase == PhysicalTimerPhase.STOPPED) this
        else copy(accumulatedMillis = elapsedMillis(now), phase = PhysicalTimerPhase.STOPPED,
            activeIntervals = closedIntervals(now),
            clockChanged = clockChanged || kotlin.math.abs(wallTime - (anchorWall + (now - anchorMono))) > 60_000,
            endedAt = if (activeIntervals != null) (anchorWall + (now - anchorMono)).coerceAtLeast(startedAt + elapsedMillis(now))
                else wallTime.coerceAtLeast(startedAt + elapsedMillis(now)))

    fun recoverBoot(boot: Int, now: Long, wall: Long): PhysicalTimerSession {
        if (bootCount == boot || phase == PhysicalTimerPhase.STOPPED) return this
        val lastEnd = activeIntervals?.let { ReadingIntervals.decode(it).lastOrNull()?.end } ?: startedAt
        return copy(phase = PhysicalTimerPhase.PAUSED, bootCount = boot, runningSince = now,
            anchorMono = now, anchorWall = maxOf(wall, lastEnd, startedAt + accumulatedMillis), clockChanged = true)
    }

    fun checkpoint(now: Long) = copy(accumulatedMillis = elapsedMillis(now), runningSince = now,
        activeIntervals = closedIntervals(now))

    private fun closedIntervals(now: Long): String? {
        val encoded = activeIntervals ?: return null
        val duration = if (phase == PhysicalTimerPhase.RUNNING) (now - runningSince).coerceAtLeast(0) else 0
        if (duration == 0L) return encoded
        val start = anchorWall + (runningSince - anchorMono)
        return ReadingIntervals.encode(ReadingIntervals.union(ReadingIntervals.decode(encoded) + ReadingInterval(start, start + duration)))
    }
}
