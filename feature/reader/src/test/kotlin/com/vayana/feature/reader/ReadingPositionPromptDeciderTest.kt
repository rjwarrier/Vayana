package com.vayana.feature.reader

import com.vayana.reader.api.Locator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReadingPositionPromptDeciderTest {
    private val decider = ReadingPositionPromptDecider()

    @Test
    fun promptsForNewerExternalPosition() {
        val prompt = decider.promptForRemote(
            remotePosition = SavedReadingPosition(
                locator = "remote-cfi",
                progress = 0.5f,
                version = 200L,
                syncedDeviceLabel = "Bedroom tablet",
                syncedAt = 150L,
            ),
            currentLocator = locator(cfi = "current-cfi", progress = 0.1f, currentPage = 10, totalPages = 200),
            lastReaderWrittenLocator = "current-cfi",
        )

        assertNotNull(prompt)
        assertEquals("remote-cfi", prompt.targetLocator)
        assertEquals(100, prompt.targetPage)
        assertEquals(10, prompt.currentPage)
        assertEquals("Bedroom tablet", prompt.syncedDeviceLabel)
        assertEquals(150L, prompt.syncedAt)
    }

    @Test
    fun ignoresEchoFromReaderWrittenLocator() {
        val prompt = decider.promptForRemote(
            remotePosition = SavedReadingPosition(locator = "current-cfi", progress = 0.3f, version = 200L),
            currentLocator = locator(cfi = "current-cfi", progress = 0.1f),
            lastReaderWrittenLocator = "current-cfi",
        )

        assertNull(prompt)
    }

    @Test
    fun ignoresAutoSyncedLocalLocatorEvenIfCurrentEngineLocatorHasNotCaughtUp() {
        val prompt = decider.promptForRemote(
            remotePosition = SavedReadingPosition(locator = "locally-written-cfi", progress = 0.3f, version = 200L),
            currentLocator = locator(cfi = "older-current-cfi", progress = 0.1f),
            lastReaderWrittenLocator = "locally-written-cfi",
        )

        assertNull(prompt)
    }

    @Test
    fun promptsWhenAnewerRemoteLocatorHasNearlySameProgress() {
        val prompt = decider.promptForRemote(
            remotePosition = SavedReadingPosition(locator = "remote-cfi", progress = 0.2005f, version = 200L),
            currentLocator = locator(cfi = "current-cfi", progress = 0.2f),
            lastReaderWrittenLocator = "current-cfi",
        )

        assertNotNull(prompt)
        assertEquals("remote-cfi", prompt.targetLocator)
    }

    private fun locator(
        cfi: String,
        progress: Float,
        currentPage: Int? = null,
        totalPages: Int? = null,
    ): Locator =
        Locator(
            cfi = cfi,
            href = null,
            progression = progress,
            chapterTitle = null,
            currentPage = currentPage,
            totalPages = totalPages,
        )
}
