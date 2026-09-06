package com.vayana.feature.reader

import com.vayana.reader.api.Locator
import kotlin.math.abs
import kotlin.math.roundToInt

data class ReadingPositionPrompt(
    val targetLocator: String,
    val targetProgress: Float,
    val targetPage: Int?,
    val currentProgress: Float,
    val currentPage: Int?,
)

internal data class SavedReadingPosition(
    val locator: String,
    val progress: Float,
    val version: Long,
)

internal class ReadingPositionPromptDecider(
    private val progressTolerance: Float = ReadingPositionPromptProgressTolerance,
) {
    fun promptForRemote(
        remotePosition: SavedReadingPosition,
        currentLocator: Locator,
        lastReaderWrittenLocator: String?,
    ): ReadingPositionPrompt? {
        if (remotePosition.locator == lastReaderWrittenLocator || remotePosition.locator == currentLocator.cfi) return null
        if (currentLocator.progression.closeTo(remotePosition.progress, progressTolerance)) return null

        return ReadingPositionPrompt(
            targetLocator = remotePosition.locator,
            targetProgress = remotePosition.progress,
            targetPage = remotePosition.progress.estimatedPage(currentLocator.totalPages),
            currentProgress = currentLocator.progression,
            currentPage = currentLocator.currentPage,
        )
    }
}

private fun Float.closeTo(other: Float, tolerance: Float): Boolean =
    abs(this - other) < tolerance

private fun Float.estimatedPage(totalPages: Int?): Int? =
    totalPages
        ?.takeIf { it > 0 }
        ?.let { pages -> (this.coerceIn(0f, 1f) * pages).roundToInt().coerceIn(1, pages) }

private const val ReadingPositionPromptProgressTolerance = 0.001f
