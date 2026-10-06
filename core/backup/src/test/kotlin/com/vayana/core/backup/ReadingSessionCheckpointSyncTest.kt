package com.vayana.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingSessionCheckpointSyncTest {
    @Test fun newerCheckpointReplacesCloudRowAndStaleRetriesCannotRegressIt() {
        val early = PortableReadingSession("session", "book", 1000, 61000, 60, bookFileHash = "hash")
        val later = early.copy(endedAt = 181000, durationSeconds = 180)
        val first = patchPortableReadingProgressOnly("{}", emptyList(), 61000, readingSessions = listOf(early))
        val updated = patchPortableReadingProgressOnly(first.jsonText, emptyList(), 181000, readingSessions = listOf(later))
        assertEquals(1, updated.sessionsAdded)
        assertEquals(listOf(later), parsePortableReadingSessions(updated.jsonText))
        val stale = patchPortableReadingProgressOnly(updated.jsonText, emptyList(), 182000, readingSessions = listOf(early, later))
        assertEquals(0, stale.sessionsAdded)
        assertEquals(listOf(later), parsePortableReadingSessions(stale.jsonText))
    }

    @Test fun independentOfflineSessionsAreUnionedInBothDirections() {
        val a = PortableReadingSession("a", "book-a", 1000, 601000, 600, bookFileHash = "same-file")
        val b = PortableReadingSession("b", "book-b", 1000000, 2200000, 1200, bookFileHash = "same-file")
        fun merge(first: PortableReadingSession, second: PortableReadingSession): List<PortableReadingSession> {
            val initial = patchPortableReadingProgressOnly("{}", emptyList(), 1, readingSessions = listOf(first))
            val merged = patchPortableReadingProgressOnly(initial.jsonText, emptyList(), 2, readingSessions = listOf(second))
            return parsePortableReadingSessions(merged.jsonText).sortedBy { it.syncId }
        }
        assertEquals(listOf(a, b), merge(a, b))
        assertEquals(listOf(a, b), merge(b, a))
    }

    @Test fun slicedSnapshotWritesAnUpdatedCheckpointEvenWithoutANewSessionId() {
        val early = PortableReadingSession("session", "book", 1000, 61000, 60)
        val later = early.copy(endedAt = 121000, durationSeconds = 120)
        val oldSlice = patchPortableReadingProgressOnly("{}", emptyList(), 1, readingSessions = listOf(early)).jsonText
        val result = patchSlicedPortableReadingProgress(
            manifestJson = """{"slices":{"readingSessions":"old.json"}}""",
            sliceJsonByKey = mapOf("readingSessions" to oldSlice), patches = emptyList(), exportedAt = 2,
            readingSessions = listOf(later))
        assertEquals(1, result.sessionsAdded)
        assertEquals(listOf(later), parsePortableReadingSessions(result.sliceWrites.values.single()))
    }

    @Test fun bookIdentityMatchingWorksWithoutLocatorOrAssetMetadata() {
        val json = """{"books":[{"syncId":"remote","fileHash":"same-file"},{"syncId":"deleted","fileHash":"old","isDeleted":true}]}"""
        assertEquals(mapOf("remote" to "same-file"), parsePortableBookFileHashes(json))
    }

    @Test fun upgradingAnExistingSessionAddsItsBookHashAndIntervalsWithoutDuplicatingTime() {
        val legacy = PortableReadingSession("session", "book", 1000, 181000, 120)
        val enriched = legacy.copy(bookFileHash = "hash", activeIntervals = "1000:61000,121000:181000")
        val old = patchPortableReadingProgressOnly("{}", emptyList(), 1, readingSessions = listOf(legacy))
        val updated = patchPortableReadingProgressOnly(old.jsonText, emptyList(), 2, readingSessions = listOf(enriched))
        assertEquals(1, updated.sessionsAdded)
        assertEquals(listOf(enriched), parsePortableReadingSessions(updated.jsonText))
        assertEquals(0, patchPortableReadingProgressOnly(updated.jsonText, emptyList(), 3, readingSessions = listOf(enriched)).sessionsAdded)
    }
}
