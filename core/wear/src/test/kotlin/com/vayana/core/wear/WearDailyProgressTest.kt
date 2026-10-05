package com.vayana.core.wear

import kotlin.test.*
import org.junit.Test
import java.time.*

class WearDailyProgressTest {
    @Test fun midnightClippingExcludesPauseGaps() {
        assertEquals(20L, WearDailyProgress.clippedSeconds(0, 100000, 40, "10000:30000,70000:90000", 20000, 80000))
        assertEquals(30L, WearDailyProgress.clippedSeconds(0, 100000, 50, null, 20000, 80000))
    }

    @Test fun offlineSessionsCountUntilPhoneIncludesThemAndYesterdayTotalsExpire() {
        val time = Instant.parse("2026-10-05T10:00:00Z").toEpochMilli()
        val state = WatchState().start(WearBook("book", "A", 0, 100, 1), 0,
            "wear-12345678-1234-1234-1234-123456789abc", time, 1000, 1).finish(61000, time + 60000)
        val progress = WearDailyProgress("2026-10-05", 120, emptySet())
        assertEquals(180L, progress.total(state, time + 60000, ZoneOffset.UTC))
        val included = progress.copy(includedIds = setOf(state.entries.single().session.id))
        assertEquals(120L, WearDailyProgress.parse(included.json()).total(state, time + 60000, ZoneOffset.UTC))
        assertEquals(0L, included.total(state, time + 86400000, ZoneOffset.UTC))
    }
}
