package com.vayana.feature.statistics

import com.vayana.core.database.model.ReadingSession
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceStatsTest {
    private fun session(device: String?, seconds: Long, endedAt: Long) =
        ReadingSession(0, "s-$device-$endedAt", 1, endedAt - seconds * 1000, endedAt, seconds, deviceName = device)

    @Test
    fun devicesRankedByTimeWithSessionsAndLastRead() {
        val stats = deviceStats(listOf(
            session("Pixel", 600, 5_000_000), session("Kindle", 1200, 3_000_000),
            session("Pixel", 900, 9_000_000), session(null, 3000, 1_000_000),
        ))
        assertEquals(listOf(DeviceStat("Pixel", 1500, 2, 9_000_000), DeviceStat("Kindle", 1200, 1, 3_000_000)), stats)
    }

    @Test
    fun oneNamedDeviceShowsNothing() {
        assertEquals(emptyList(), deviceStats(listOf(session("Pixel", 600, 5_000_000), session(null, 600, 6_000_000))))
    }
}
