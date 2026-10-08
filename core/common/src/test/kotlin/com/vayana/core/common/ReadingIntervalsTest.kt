package com.vayana.core.common
import kotlin.test.*
class ReadingIntervalsTest {
    @Test fun subtractMatchesCoverageForUnorderedOverlappingRanges() {
        val random = kotlin.random.Random(42)
        repeat(200) {
            val source = List(8) { val start = random.nextLong(1, 80); ReadingInterval(start, start + random.nextLong(1, 20)) }
            val covered = List(12) { val start = random.nextLong(1, 100); ReadingInterval(start, start + random.nextLong(1, 20)) }
            val expected = source.flatMap { interval ->
                (interval.start until interval.end).filter { time -> covered.none { time >= it.start && time < it.end } }
            }
            val actual = ReadingIntervals.subtract(source, covered).flatMap { it.start until it.end }
            assertEquals(expected, actual)
        }
    }
    @Test fun subtractHandlesSeveralPauseGapsAndNestedCoverage() {
        val source = ReadingIntervals.decode("1000:11000,21000:61000")
        val result = ReadingIntervals.subtract(source, ReadingIntervals.decode("2000:5000,7000:25000,30000:40000"))
        assertEquals("1000:2000,5000:7000,25000:30000,40000:61000", ReadingIntervals.encode(result))
        assertEquals(29000L, ReadingIntervals.millis(result))
    }
    @Test fun unionCountsOverlappingAndTouchingSpansOnce() {
        assertEquals("1000:5000", ReadingIntervals.encode(ReadingIntervals.union(listOf(ReadingInterval(3000,5000),ReadingInterval(1000,3000),ReadingInterval(2000,4000)))))
    }
    @Test fun invalidAndUnorderedWireIntervalsAreRejected() {
        listOf("0:2","2:1","1:4,3:5","1:2,1:2","garbage").forEach { assertFails { ReadingIntervals.decode(it) } }
    }
}
