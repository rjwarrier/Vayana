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

data class PortableCloudBook(
    val syncId: String,
    val title: String,
    val author: String?,
    val series: String?,
    val seriesNumber: String?,
    val description: String?,
    val tagsCsv: String?,
    val format: String,
    val fileHash: String,
    val fileAsset: PortableAsset,
    val coverAsset: PortableAsset?,
    val lastLocator: String?,
    val readingPercent: Float,
    val rating: Float,
    val wordCount: Int?,
    val pageEstimate: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastReadAt: Long?,
    val startedReadingAt: Long?,
    val finishedReadingAt: Long?,
    val totalReadingSeconds: Long,
    val customFontSizePercent: Int?,
    val customLineHeight: Float?,
    val customFontFamily: String?,
    val customSideMarginPercent: Int?,
    val readNextAddedAt: Long?,
)

data class PortableReadingProgressPatch(
    val fileHash: String,
    val lastLocator: String?,
    val readingPercent: Float,
    val lastReadAt: Long?,
    val startedReadingAt: Long?,
    val finishedReadingAt: Long?,
    val totalReadingSeconds: Long,
)

data class PortableReadingProgressPatchResult(
    val jsonText: String,
    val patched: Int,
)

fun parsePortableReadingProgresses(jsonText: String): List<PortableReadingProgress> =
    parsePortableReadingProgressSnapshot(jsonText).progresses

fun patchPortableReadingProgressOnly(
    jsonText: String,
    patches: List<PortableReadingProgressPatch>,
    exportedAt: Long,
): PortableReadingProgressPatchResult {
    require(jsonText.length <= MaxPortableProgressJsonChars) { "Portable snapshot is too large" }
    require(patches.size <= MaxPortableProgressBooks) { "Portable progress patch has too many books" }
    require(exportedAt > 0L) { "Portable progress patch export time is invalid" }
    val root = JSONObject(jsonText)
    val books = root.optJSONArray("books") ?: return PortableReadingProgressPatchResult(jsonText, patched = 0)
    require(books.length() <= MaxPortableProgressBooks) { "Portable snapshot has too many books" }
    val patchesByFileHash = patches
        .asSequence()
        .filter { patch ->
            patch.fileHash.isNotBlank() &&
                patch.fileHash.length <= MaxFileHashChars &&
                !patch.lastLocator.isNullOrBlank() &&
                patch.lastLocator.length <= MaxLocatorChars &&
                !patch.readingPercent.isNaN() &&
                !patch.readingPercent.isInfinite() &&
                (patch.lastReadAt ?: 0L) > 0L
        }
        .associateBy { it.fileHash }
    var patched = 0

    for (index in 0 until books.length()) {
        val book = books.optJSONObject(index) ?: continue
        if (book.optBoolean("isDeleted", false)) continue
        book.optBoundedString("syncId", MaxSyncIdChars) ?: continue
        val fileHash = book.optBoundedString("fileHash", MaxFileHashChars) ?: continue
        val patch = patchesByFileHash[fileHash] ?: continue
        val localVersion = patch.lastReadAt ?: continue
        val remoteVersion = book.optPositiveLongOrNull("lastReadAt")
            ?: book.optPositiveLongOrNull("updatedAt")
            ?: 0L
        if (localVersion <= remoteVersion) continue

        book.put("lastLocator", patch.lastLocator)
        book.put("readingPercent", patch.readingPercent.coerceIn(0f, 1f).toDouble())
        book.putNullable("lastReadAt", patch.lastReadAt)
        book.putNullable("startedReadingAt", patch.startedReadingAt)
        book.putNullable("finishedReadingAt", patch.finishedReadingAt)
        book.put("totalReadingSeconds", patch.totalReadingSeconds.coerceAtLeast(0L))
        book.put("updatedAt", maxOf(book.optLong("updatedAt", 0L), localVersion))
        patched += 1
    }

    if (patched > 0) {
        root.put("exportedAt", exportedAt)
    }
    return PortableReadingProgressPatchResult(
        jsonText = if (patched > 0) root.toString(2) else jsonText,
        patched = patched,
    )
}

fun parsePortableCloudBooks(jsonText: String): List<PortableCloudBook> {
    require(jsonText.length <= MaxPortableProgressJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val books = root.optJSONArray("books") ?: return emptyList()
    require(books.length() <= MaxPortableProgressBooks) { "Portable snapshot has too many books" }
    return buildList {
        for (index in 0 until books.length()) {
            val book = books.optJSONObject(index) ?: continue
            if (book.optBoolean("isDeleted", false)) continue
            val syncId = book.optBoundedString("syncId", MaxSyncIdChars) ?: continue
            val title = book.optBoundedString("title", MaxTitleChars) ?: continue
            val format = book.optBoundedString("format", MaxFormatChars) ?: continue
            if (format.equals("PHYSICAL", ignoreCase = true)) continue
            val fileHash = book.optBoundedString("fileHash", MaxFileHashChars) ?: continue
            val asset = book.optJSONObject("fileAsset")?.toPortableAssetOrNull() ?: continue
            val coverAsset = book.optJSONObject("coverAsset")?.toPortableAssetOrNull()
            val updatedAt = book.optPositiveLongOrNull("updatedAt") ?: continue
            add(
                PortableCloudBook(
                    syncId = syncId,
                    title = title,
                    author = book.optBoundedString("author", MaxTitleChars),
                    series = book.optBoundedString("series", MaxTitleChars),
                    seriesNumber = book.optBoundedString("seriesNumber", MaxTitleChars),
                    description = book.optBoundedString("description", MaxDescriptionChars),
                    tagsCsv = book.optBoundedString("tagsCsv", MaxTagsCsvChars),
                    format = format,
                    fileHash = fileHash,
                    fileAsset = asset,
                    coverAsset = coverAsset,
                    lastLocator = book.optBoundedString("lastLocator", MaxLocatorChars),
                    readingPercent = book.optDouble("readingPercent", 0.0).toFloat().coerceIn(0f, 1f),
                    rating = book.optDouble("rating", 0.0).toFloat().coerceIn(0f, 5f),
                    wordCount = book.optPositiveIntOrNull("wordCount"),
                    pageEstimate = book.optPositiveIntOrNull("pageEstimate"),
                    createdAt = book.optPositiveLongOrNull("createdAt") ?: updatedAt,
                    updatedAt = updatedAt,
                    lastReadAt = book.optPositiveLongOrNull("lastReadAt"),
                    startedReadingAt = book.optPositiveLongOrNull("startedReadingAt"),
                    finishedReadingAt = book.optPositiveLongOrNull("finishedReadingAt"),
                    totalReadingSeconds = book.optLong("totalReadingSeconds", 0L).coerceAtLeast(0L),
                    customFontSizePercent = book.optPositiveIntOrNull("customFontSizePercent"),
                    customLineHeight = book.optDoubleOrNull("customLineHeight")?.toFloat(),
                    customFontFamily = book.optBoundedString("customFontFamily", MaxTitleChars),
                    customSideMarginPercent = book.optPositiveIntOrNull("customSideMarginPercent"),
                    readNextAddedAt = book.optPositiveLongOrNull("readNextAddedAt"),
                ),
            )
        }
    }
}

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

private fun JSONObject.optPositiveIntOrNull(name: String): Int? =
    optInt(name, 0).takeIf { it > 0 }

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null

private fun JSONObject.optBoundedString(name: String, maxChars: Int): String? =
    optString(name)
        .trim()
        .takeIf { it.isNotEmpty() && it.length <= maxChars }

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
    put(name, value ?: JSONObject.NULL)

private fun JSONObject.toPortableAssetOrNull(): PortableAsset? {
    val id = optBoundedString("id", MaxAssetIdChars) ?: return null
    val sha256 = optBoundedString("sha256", MaxSha256Chars) ?: return null
    val sizeBytes = optPositiveLongOrNull("sizeBytes") ?: return null
    val uploadedAt = optPositiveLongOrNull("uploadedAt") ?: return null
    return runCatching {
        PortableAsset(
            id = id,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            uploadedAt = uploadedAt,
        )
    }.getOrNull()
}

private const val MaxPortableProgressJsonChars = 8 * 1024 * 1024
private const val MaxPortableProgressBooks = 20_000
private const val MaxSyncIdChars = 120
private const val MaxFileHashChars = 160
private const val MaxLocatorChars = 16_384
private const val MaxDeviceLabelChars = 120
private const val MaxTitleChars = 512
private const val MaxDescriptionChars = 16_384
private const val MaxTagsCsvChars = 2_048
private const val MaxFormatChars = 32
private const val MaxAssetIdChars = 128
private const val MaxSha256Chars = 128
