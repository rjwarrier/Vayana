package com.vayana.core.common

data class ReadingInterval(val start: Long, val end: Long)

/** Epoch-millisecond intervals; empty is known zero time, null means legacy/unknown pause placement. */
object ReadingIntervals {
    fun decode(text: String): List<ReadingInterval> {
        require(text.length <= 60_000)
        if (text.isEmpty()) return emptyList()
        val values = text.split(',')
        require(values.size <= 1000)
        val intervals = values.map { part ->
            val pair = part.split(':')
            require(pair.size == 2)
            ReadingInterval(pair[0].toLong(), pair[1].toLong()).also { require(it.start > 0 && it.end > it.start) }
        }
        require(intervals.zipWithNext().all { (a, b) -> a.end <= b.start })
        return intervals
    }
    fun encode(intervals: List<ReadingInterval>) = intervals.joinToString(",") { "${it.start}:${it.end}" }
    fun union(intervals: List<ReadingInterval>): List<ReadingInterval> {
        val result = mutableListOf<ReadingInterval>()
        for (interval in intervals.sortedBy { it.start }) {
            val last = result.lastOrNull()
            if (last != null && interval.start <= last.end) result[result.lastIndex] = last.copy(end = maxOf(last.end, interval.end))
            else result += interval
        }
        return result
    }
    fun millis(intervals: List<ReadingInterval>) = intervals.sumOf { it.end - it.start }
    fun subtract(source: List<ReadingInterval>, covered: List<ReadingInterval>): List<ReadingInterval> {
        val blockers = union(covered)
        return source.flatMap { interval ->
            var cursor = interval.start
            val result = mutableListOf<ReadingInterval>()
            for (block in blockers) {
                if (block.end <= cursor || block.start >= interval.end) continue
                if (block.start > cursor) result += ReadingInterval(cursor, block.start)
                cursor = maxOf(cursor, block.end).coerceAtMost(interval.end)
            }
            if (cursor < interval.end) result += ReadingInterval(cursor, interval.end)
            result
        }
    }
}
