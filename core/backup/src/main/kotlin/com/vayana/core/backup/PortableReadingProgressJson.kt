package com.vayana.core.backup

import org.json.JSONObject

data class PortableReadingProgress(
    val syncId: String,
    val fileHash: String,
    val lastLocator: String,
    val readingPercent: Float,
    val lastReadAt: Long?,
    val updatedAt: Long,
    val startedReadingAt: Long?,
    val finishedReadingAt: Long?,
    val totalReadingSeconds: Long,
)

data class PortableReadingProgressSnapshot(
    val deviceLabel: String?,
    val exportedAt: Long?,
    val progresses: List<PortableReadingProgress>,
)

fun parsePortableReadingProgresses(jsonText: String): List<PortableReadingProgress> =
    parsePortableReadingProgressSnapshot(jsonText).progresses

fun parsePortableReadingProgressSnapshot(jsonText: String): PortableReadingProgressSnapshot {
    require(jsonText.length <= MaxPortableProgressJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val books = root.optJSONArray("books") ?: return PortableReadingProgressSnapshot(
        deviceLabel = root.optBoundedString("deviceLabel", MaxDeviceLabelChars),
        exportedAt = root.optPositiveLongOrNull("exportedAt"),
        progresses = emptyList(),
    )
    require(books.length() <= MaxPortableProgressBooks) { "Portable snapshot has too many books" }
    val progresses = buildList {
        for (index in 0 until books.length()) {
            val book = books.optJSONObject(index) ?: continue
            val syncId = book.optBoundedString("syncId", MaxSyncIdChars) ?: continue
            val fileHash = book.optBoundedString("fileHash", MaxFileHashChars) ?: continue
            val lastLocator = book.optBoundedString("lastLocator", MaxLocatorChars) ?: continue
            val updatedAt = book.optLong("updatedAt", 0L).takeIf { it > 0L } ?: continue
            add(
                PortableReadingProgress(
                    syncId = syncId,
                    fileHash = fileHash,
                    lastLocator = lastLocator,
                    readingPercent = book.optDouble("readingPercent", 0.0).toFloat().coerceIn(0f, 1f),
                    lastReadAt = book.optPositiveLongOrNull("lastReadAt"),
                    updatedAt = updatedAt,
                    startedReadingAt = book.optPositiveLongOrNull("startedReadingAt"),
                    finishedReadingAt = book.optPositiveLongOrNull("finishedReadingAt"),
                    totalReadingSeconds = book.optLong("totalReadingSeconds", 0L).coerceAtLeast(0L),
                ),
            )
        }
    }
    return PortableReadingProgressSnapshot(
        deviceLabel = root.optBoundedString("deviceLabel", MaxDeviceLabelChars),
        exportedAt = root.optPositiveLongOrNull("exportedAt"),
        progresses = progresses,
    )
}

private fun JSONObject.optPositiveLongOrNull(name: String): Long? =
    optLong(name, 0L).takeIf { it > 0L }

private fun JSONObject.optBoundedString(name: String, maxChars: Int): String? =
    optString(name)
        .trim()
        .takeIf { it.isNotEmpty() && it.length <= maxChars }

private const val MaxPortableProgressJsonChars = 8 * 1024 * 1024
private const val MaxPortableProgressBooks = 20_000
private const val MaxSyncIdChars = 120
private const val MaxFileHashChars = 160
private const val MaxLocatorChars = 16_384
private const val MaxDeviceLabelChars = 120
