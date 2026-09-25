package com.vayana.core.designsystem.component

import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CloudSyncTimePresentationTest {
    private val zoneId = ZoneId.of("UTC")

    @Test
    fun `same-day sync uses minutes below one hour`() {
        val now = instant("2026-09-25T12:45:00Z")

        assertEquals(
            CloudSyncTimePresentation.MinutesAgo(35),
            cloudSyncTimePresentation(instant("2026-09-25T12:10:00Z"), now, zoneId),
        )
    }

    @Test
    fun `same-day sync uses hours from one hour`() {
        val now = instant("2026-09-25T18:45:00Z")

        assertEquals(
            CloudSyncTimePresentation.HoursAgo(6),
            cloudSyncTimePresentation(instant("2026-09-25T12:10:00Z"), now, zoneId),
        )
    }

    @Test
    fun `previous calendar day uses an absolute date`() {
        val now = instant("2026-09-25T00:10:00Z")

        assertEquals(
            CloudSyncTimePresentation.OnAnotherDay,
            cloudSyncTimePresentation(instant("2026-09-24T23:55:00Z"), now, zoneId),
        )
    }

    @Test
    fun `very recent and future times never show zero or negative minutes`() {
        val now = instant("2026-09-25T12:45:00Z")

        assertEquals(
            CloudSyncTimePresentation.MinutesAgo(1),
            cloudSyncTimePresentation(now - 10_000L, now, zoneId),
        )
        assertEquals(
            CloudSyncTimePresentation.MinutesAgo(1),
            cloudSyncTimePresentation(now + 10_000L, now, zoneId),
        )
    }

    private fun instant(value: String): Long = Instant.parse(value).toEpochMilli()
}
