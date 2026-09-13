package com.vayana.core.database.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadNextStateTest {
    @Test
    fun newerRemoteQueueWins() {
        val local = ReadNextState(addedAt = null, updatedAt = 1_000)
        val remote = ReadNextState(addedAt = 2_000, updatedAt = 2_000)

        assertEquals(remote, local.mergedWith(remote))
    }

    @Test
    fun newerRemoteRemovalWins() {
        val local = ReadNextState(addedAt = 1_000, updatedAt = 1_000)
        val remote = ReadNextState(addedAt = null, updatedAt = 3_000)

        assertEquals(remote, local.mergedWith(remote))
    }

    @Test
    fun olderRemoteKeepsLocal() {
        val local = ReadNextState(addedAt = null, updatedAt = 5_000)
        val remote = ReadNextState(addedAt = 2_000, updatedAt = 2_000)

        assertEquals(local, local.mergedWith(remote))
    }

    @Test
    fun tieKeepsLocal() {
        val local = ReadNextState(addedAt = 4_000, updatedAt = 4_000)
        val remote = ReadNextState(addedAt = null, updatedAt = 4_000)

        assertEquals(local, local.mergedWith(remote))
    }

    @Test
    fun legacyRowsFallBackToAddedAt() {
        val local = ReadNextState(addedAt = 1_000, updatedAt = null)
        val remote = ReadNextState(addedAt = 2_000, updatedAt = null)

        assertEquals(remote, local.mergedWith(remote))
        assertEquals(local, local.mergedWith(ReadNextState(addedAt = null, updatedAt = null)))
    }
}
