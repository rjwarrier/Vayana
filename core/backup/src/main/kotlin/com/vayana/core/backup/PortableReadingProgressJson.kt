package com.vayana.core.backup

import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.PhysicalBookOwnership
import org.json.JSONArray
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
    val readNextStates: List<PortableReadNextState> = emptyList(),
)

data class PortableReadNextState(
    val syncId: String,
    val fileHash: String,
    val addedAt: Long?,
    val updatedAt: Long,
    val pinned: Boolean = false,
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
    /** Null only for books read outside the app (physical, audiobook or another app's ebook), which have no file. */
    val fileAsset: PortableAsset?,
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
    val readNextUpdatedAt: Long? = null,
    val deletionUpdatedAt: Long? = null,
    val goodreadsUrl: String? = null,
    val goodreadsRating: Float? = null,
    val goodreadsRatingsCount: Int? = null,
    val originalPublicationYear: Int? = null,
    val physicalOwnership: PhysicalBookOwnership? = null,
    val borrowReturnAt: Long? = null,
    val gutenbergId: Long? = null,
    val readNextPinned: Boolean = false,
    val readingDisposition: String = "ACTIVE",
    val dispositionReason: String? = null,
    val dispositionUpdatedAt: Long? = null,
)

data class PortableReadingProgressPatch(
    val fileHash: String,
    val lastLocator: String?,
    val readingPercent: Float,
    val lastReadAt: Long?,
    val startedReadingAt: Long?,
    val finishedReadingAt: Long?,
    val totalReadingSeconds: Long,
    val readNextAddedAt: Long? = null,
    val readNextUpdatedAt: Long? = null,
    val readNextPinned: Boolean = false,
)

data class PortableReadingProgressPatchResult(
    val jsonText: String,
    val patched: Int,
    val sessionsAdded: Int = 0,
    val wordLookupCountersMerged: Int = 0,
    val tombstonesMerged: Int = 0,
)

fun parsePortableReadingProgresses(jsonText: String): List<PortableReadingProgress> =
    parsePortableReadingProgressSnapshot(jsonText).progresses

fun patchPortableReadingProgressOnly(
    jsonText: String,
    patches: List<PortableReadingProgressPatch>,
    exportedAt: Long,
    readingSessions: List<PortableReadingSession> = emptyList(),
    wordLookupCounters: List<PortableWordLookupCounter> = emptyList(),
    tombstones: List<PortableTombstone> = emptyList(),
): PortableReadingProgressPatchResult {
    require(jsonText.length <= MaxPortableProgressJsonChars) { "Portable snapshot is too large" }
    require(patches.size <= MaxPortableProgressBooks) { "Portable progress patch has too many books" }
    require(readingSessions.size <= MaxPortableProgressReadingSessions) { "Portable progress patch has too many reading sessions" }
    require(wordLookupCounters.size <= MaxPortableProgressWordLookupCounters) { "Portable progress patch has too many word lookup counters" }
    require(tombstones.size <= MaxPortableProgressTombstones) { "Portable progress patch has too many tombstones" }
    require(exportedAt > 0L) { "Portable progress patch export time is invalid" }
    val root = JSONObject(jsonText)
    // Sliced snapshots keep sessions, counters and tombstones in separate documents with no "books" array;
    // those still get patched, there are just no positions to update in them.
    val books = root.optJSONArray("books") ?: JSONArray()
    require(books.length() <= MaxPortableProgressBooks) { "Portable snapshot has too many books" }
    val patchesByFileHash = patches
        .asSequence()
        .filter { patch -> patch.fileHash.isNotBlank() && patch.fileHash.length <= MaxFileHashChars }
        .associateBy { it.fileHash }
    var patched = 0

    for (index in 0 until books.length()) {
        val book = books.optJSONObject(index) ?: continue
        if (book.optBoolean("isDeleted", false)) continue
        book.optBoundedString("syncId", MaxSyncIdChars) ?: continue
        val fileHash = book.optBoundedString("fileHash", MaxFileHashChars) ?: continue
        val patch = patchesByFileHash[fileHash] ?: continue
        var bookPatched = false
        val remoteProgressVersion = book.optPositiveLongOrNull("lastReadAt")
            ?: book.optPositiveLongOrNull("updatedAt")
            ?: 0L

        val localReadNextVersion = patch.readNextUpdatedAt ?: patch.readNextAddedAt ?: 0L
        val remoteReadNextVersion = book.optPositiveLongOrNull("readNextUpdatedAt")
            ?: book.optPositiveLongOrNull("readNextAddedAt")
            ?: 0L
        val validReadNextState = localReadNextVersion > 0L &&
            (patch.readNextAddedAt == null || patch.readNextAddedAt > 0L)
        if (validReadNextState && localReadNextVersion > remoteReadNextVersion) {
            book.putNullable("readNextAddedAt", patch.readNextAddedAt)
            book.put("readNextUpdatedAt", localReadNextVersion)
            book.put("readNextPinned", patch.readNextPinned)
            book.put("updatedAt", maxOf(book.optLong("updatedAt", 0L), localReadNextVersion))
            bookPatched = true
        }

        val localProgressVersion = patch.lastReadAt ?: 0L
        val validProgress = !patch.lastLocator.isNullOrBlank() &&
            patch.lastLocator.length <= MaxLocatorChars &&
            patch.readingPercent.isFinite() &&
            localProgressVersion > 0L
        if (validProgress && localProgressVersion > remoteProgressVersion) {
            book.put("lastLocator", patch.lastLocator)
            book.put("readingPercent", patch.readingPercent.coerceIn(0f, 1f).toDouble())
            book.putNullable("lastReadAt", patch.lastReadAt)
            book.putNullable("startedReadingAt", patch.startedReadingAt)
            book.putNullable("finishedReadingAt", patch.finishedReadingAt)
            book.put("totalReadingSeconds", patch.totalReadingSeconds.coerceAtLeast(0L))
            book.put("updatedAt", maxOf(book.optLong("updatedAt", 0L), localProgressVersion))
            bookPatched = true
        }
        if (bookPatched) patched += 1
    }

    val sessionsAdded = root.appendMissingReadingSessions(readingSessions)
    val wordLookupCountersMerged = root.mergeWordLookupCounters(wordLookupCounters)
    val tombstonesMerged = root.mergeTombstones(tombstones)
    val changed = patched > 0 || sessionsAdded > 0 || wordLookupCountersMerged > 0 || tombstonesMerged > 0

    if (changed) {
        root.put("exportedAt", exportedAt)
    }
    return PortableReadingProgressPatchResult(
        jsonText = if (changed) root.toString(2) else jsonText,
        patched = patched,
        sessionsAdded = sessionsAdded,
        wordLookupCountersMerged = wordLookupCountersMerged,
        tombstonesMerged = tombstonesMerged,
    )
}

private fun JSONObject.mergeTombstones(tombstones: List<PortableTombstone>): Int {
    if (tombstones.isEmpty()) return 0
    val array = optJSONArray("tombstones") ?: JSONArray().also { put("tombstones", it) }
    require(array.length() <= MaxPortableProgressTombstones) { "Portable snapshot has too many tombstones" }
    val existingBySyncId = mutableMapOf<String, JSONObject>()
    for (index in 0 until array.length()) {
        val tombstone = array.optJSONObject(index) ?: continue
        val syncId = tombstone.optBoundedString("syncId", MaxSyncIdChars) ?: continue
        existingBySyncId[syncId] = tombstone
    }
    var merged = 0
    tombstones.forEach { tombstone ->
        val syncId = tombstone.syncId.trim().takeIf { it.isNotEmpty() && it.length <= MaxSyncIdChars } ?: return@forEach
        val entityType = tombstone.entityType.trim().takeIf { it.isNotEmpty() && it.length <= MaxEntityTypeChars } ?: return@forEach
        if (tombstone.deletedAt <= 0L) return@forEach
        val existing = existingBySyncId[syncId]
        if (existing == null) {
            if (array.length() >= MaxPortableProgressTombstones) return@forEach
            val added = JSONObject()
                .put("syncId", syncId)
                .put("entityType", entityType)
                .put("deletedAt", tombstone.deletedAt)
            array.put(added)
            existingBySyncId[syncId] = added
            merged += 1
        } else if (tombstone.deletedAt > existing.optLong("deletedAt", 0L)) {
            existing.put("entityType", entityType)
            existing.put("deletedAt", tombstone.deletedAt)
            merged += 1
        }
    }
    return merged
}

private fun JSONObject.mergeWordLookupCounters(wordLookupCounters: List<PortableWordLookupCounter>): Int {
    if (wordLookupCounters.isEmpty()) return 0
    val counters = optJSONArray("wordLookupCounters") ?: JSONArray().also { put("wordLookupCounters", it) }
    require(counters.length() <= MaxPortableProgressWordLookupCounters) { "Portable snapshot has too many word lookup counters" }
    val existingByKey = mutableMapOf<String, JSONObject>()
    for (index in 0 until counters.length()) {
        val counter = counters.optJSONObject(index) ?: continue
        val word = counter.optBoundedString("word", MaxWordChars)?.lowercase() ?: continue
        val writerOrigin = counter.optBoundedString("writerOrigin", MaxWriterOriginChars) ?: continue
        existingByKey[wordLookupCounterKey(word, writerOrigin)] = counter
    }
    var merged = 0
    wordLookupCounters.forEach { counter ->
        val word = counter.word.trim().lowercase().takeIf { it.isNotEmpty() && it.length <= MaxWordChars } ?: return@forEach
        val writerOrigin = counter.writerOrigin.trim().takeIf { it.isNotEmpty() && it.length <= MaxWriterOriginChars } ?: return@forEach
        if (counter.count <= 0 || counter.lastLookedUpAt <= 0L) return@forEach
        val key = wordLookupCounterKey(word, writerOrigin)
        val existing = existingByKey[key]
        if (existing == null) {
            if (counters.length() >= MaxPortableProgressWordLookupCounters) return@forEach
            val added = JSONObject()
                .put("word", word)
                .put("writerOrigin", writerOrigin)
                .put("count", counter.count)
                .put("lastLookedUpAt", counter.lastLookedUpAt)
            counters.put(added)
            existingByKey[key] = added
            merged += 1
        } else {
            val nextCount = maxOf(existing.optInt("count", 0), counter.count)
            val nextLastLookedUpAt = maxOf(existing.optLong("lastLookedUpAt", 0L), counter.lastLookedUpAt)
            if (nextCount != existing.optInt("count", 0) || nextLastLookedUpAt != existing.optLong("lastLookedUpAt", 0L)) {
                existing.put("count", nextCount)
                existing.put("lastLookedUpAt", nextLastLookedUpAt)
                merged += 1
            }
        }
    }
    return merged
}

private fun wordLookupCounterKey(word: String, writerOrigin: String): String = "$word\u0000$writerOrigin"

private fun JSONObject.appendMissingReadingSessions(readingSessions: List<PortableReadingSession>): Int {
    if (readingSessions.isEmpty()) return 0
    // Deliberately not gated on the session's book already being present in this document's "books" array:
    // progress-only sync never uploads book/asset metadata (that's a full sync's job), so a session for a
    // just-imported book would otherwise never leave the device it started on. A pulling device that doesn't
    // know the book yet just skips the session in ReadingSessionRepositoryImpl.mergeCloudSession (a safe,
    // self-healing no-op) until a future sync catches it up on the book.
    val sessions = optJSONArray("readingSessions") ?: JSONArray().also { put("readingSessions", it) }
    require(sessions.length() <= MaxPortableProgressReadingSessions) { "Portable snapshot has too many reading sessions" }
    val existingSessionSyncIds = buildSet {
        for (index in 0 until sessions.length()) {
            val sessionSyncId = sessions.optJSONObject(index)?.optBoundedString("syncId", MaxSyncIdChars) ?: continue
            add(sessionSyncId)
        }
    }.toMutableSet()
    var added = 0
    readingSessions.forEach { session ->
        if (sessions.length() >= MaxPortableProgressReadingSessions) return@forEach
        if (session.syncId.isBlank() || session.syncId.length > MaxSyncIdChars) return@forEach
        if (session.bookSyncId.isBlank() || session.bookSyncId.length > MaxSyncIdChars) return@forEach
        if (session.syncId in existingSessionSyncIds) return@forEach
        if (session.startedAt <= 0L || session.endedAt < session.startedAt || (session.durationSeconds < 0L || (session.durationSeconds == 0L && session.activeIntervals != ""))) return@forEach
        sessions.put(
            JSONObject()
                .put("syncId", session.syncId)
                .put("bookSyncId", session.bookSyncId)
                .put("startedAt", session.startedAt)
                .put("endedAt", session.endedAt)
                .put("durationSeconds", session.durationSeconds)
                .put("startPage", session.startPage)
                .put("endPage", session.endPage)
                .put("activeIntervals", session.activeIntervals),
        )
        existingSessionSyncIds += session.syncId
        added += 1
    }
    return added
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
            val fileHash = book.optBoundedString("fileHash", MaxFileHashChars) ?: continue
            val asset = book.optJSONObject("fileAsset")?.toPortableAssetOrNull()
            // A readable book is only useful here once its file is downloadable; one read outside the app has none.
            if (asset == null && !format.isOfflineBookFormat()) continue
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
                    readNextUpdatedAt = book.optPositiveLongOrNull("readNextUpdatedAt"),
                    readNextPinned = book.optBoolean("readNextPinned", false),
                    readingDisposition = book.optString("readingDisposition", "ACTIVE").takeIf { it in setOf("ACTIVE", "PAUSED", "DNF") } ?: "ACTIVE",
                    dispositionReason = book.optBoundedString("dispositionReason", 2000),
                    dispositionUpdatedAt = book.optPositiveLongOrNull("dispositionUpdatedAt"),
                    deletionUpdatedAt = book.optPositiveLongOrNull("deletionUpdatedAt"),
                    goodreadsUrl = book.optGoodreadsUrlOrNull(),
                    goodreadsRating = book.optFiniteFloatOrNull("goodreadsRating", min = 0f, max = 5f),
                    goodreadsRatingsCount = book.optPositiveIntOrNull("goodreadsRatingsCount")?.takeIf { it <= MaxGoodreadsRatingsCount },
                    originalPublicationYear = book.optPublicationYearOrNull(),
                    physicalOwnership = book.optBoundedString("physicalOwnership", MaxFormatChars)
                        ?.uppercase()
                        ?.let { runCatching { PhysicalBookOwnership.valueOf(it) }.getOrNull() }
                        ?.takeIf { format.equals(BookFormat.PHYSICAL.name, ignoreCase = true) },
                    borrowReturnAt = book.optPositiveLongOrNull("borrowReturnAt")
                        .takeIf { format.equals(BookFormat.PHYSICAL.name, ignoreCase = true) },
                    gutenbergId = book.optPositiveLongOrNull("gutenbergId"),
                ),
            )
        }
    }
}

private fun String.isOfflineBookFormat(): Boolean =
    BookFormat.entries.any { it.isOffline && it.name.equals(this, ignoreCase = true) }

fun parsePortableReadingProgressSnapshot(jsonText: String): PortableReadingProgressSnapshot {
    require(jsonText.length <= MaxPortableProgressJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val books = root.optJSONArray("books") ?: return PortableReadingProgressSnapshot(
        deviceLabel = root.optBoundedString("deviceLabel", MaxDeviceLabelChars),
        exportedAt = root.optPositiveLongOrNull("exportedAt"),
        progresses = emptyList(),
        readNextStates = emptyList(),
    )
    require(books.length() <= MaxPortableProgressBooks) { "Portable snapshot has too many books" }
    val progresses = ArrayList<PortableReadingProgress>()
    val readNextStates = ArrayList<PortableReadNextState>()
    for (index in 0 until books.length()) {
        val book = books.optJSONObject(index) ?: continue
        val syncId = book.optBoundedString("syncId", MaxSyncIdChars) ?: continue
        val fileHash = book.optBoundedString("fileHash", MaxFileHashChars) ?: continue
        val readNextAddedAt = book.optPositiveLongOrNull("readNextAddedAt")
        val readNextUpdatedAt = book.optPositiveLongOrNull("readNextUpdatedAt") ?: readNextAddedAt
        if (readNextUpdatedAt != null) {
            readNextStates += PortableReadNextState(
                syncId = syncId,
                fileHash = fileHash,
                addedAt = readNextAddedAt,
                updatedAt = readNextUpdatedAt,
                pinned = book.optBoolean("readNextPinned", false),
            )
        }
        val lastLocator = book.optBoundedString("lastLocator", MaxLocatorChars) ?: continue
        val updatedAt = book.optLong("updatedAt", 0L).takeIf { it > 0L } ?: continue
        progresses += PortableReadingProgress(
            syncId = syncId,
            fileHash = fileHash,
            lastLocator = lastLocator,
            readingPercent = book.optDouble("readingPercent", 0.0).toFloat().coerceIn(0f, 1f),
            lastReadAt = book.optPositiveLongOrNull("lastReadAt"),
            updatedAt = updatedAt,
            startedReadingAt = book.optPositiveLongOrNull("startedReadingAt"),
            finishedReadingAt = book.optPositiveLongOrNull("finishedReadingAt"),
            totalReadingSeconds = book.optLong("totalReadingSeconds", 0L).coerceAtLeast(0L),
        )
    }
    return PortableReadingProgressSnapshot(
        deviceLabel = root.optBoundedString("deviceLabel", MaxDeviceLabelChars),
        exportedAt = root.optPositiveLongOrNull("exportedAt"),
        progresses = progresses,
        readNextStates = readNextStates,
    )
}

private fun JSONObject.optPositiveLongOrNull(name: String): Long? =
    optLong(name, 0L).takeIf { it > 0L }

private fun JSONObject.optPositiveIntOrNull(name: String): Int? =
    optInt(name, 0).takeIf { it > 0 }

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null

private fun JSONObject.optFiniteFloatOrNull(name: String, min: Float, max: Float): Float? =
    optDoubleOrNull(name)
        ?.takeIf { it.isFinite() }
        ?.toFloat()
        ?.coerceIn(min, max)

private fun JSONObject.optBoundedString(name: String, maxChars: Int): String? =
    optPortableStringOrNull(name)?.takeIf { it.length <= maxChars }

/**
 * A trimmed, non-empty string field, or null. Android's org.json turns an explicit JSON null into the text
 * "null" via optString (the desktop org.json used by unit tests doesn't), and earlier builds synced that text
 * back out as a real value, so the literal "null" also reads as absent.
 */
internal fun JSONObject.optPortableStringOrNull(name: String): String? {
    if (isNull(name)) return null
    return optString(name).trim().takeIf { it.isNotEmpty() && it != "null" }
}

private fun JSONObject.optGoodreadsUrlOrNull(): String? =
    optBoundedString("goodreadsUrl", MaxGoodreadsUrlChars)
        ?.takeIf { url ->
            url.startsWith("https://www.goodreads.com/", ignoreCase = true) ||
                url.startsWith("http://www.goodreads.com/", ignoreCase = true)
        }

private fun JSONObject.optPublicationYearOrNull(): Int? =
    optPositiveIntOrNull("originalPublicationYear")?.takeIf { it in MinPublicationYear..MaxPublicationYear }

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

private const val MaxPortableProgressJsonChars = 16 * 1024 * 1024
private const val MaxPortableProgressBooks = 20_000
private const val MaxPortableProgressReadingSessions = 200_000
private const val MaxPortableProgressWordLookupCounters = 100_000
private const val MaxPortableProgressTombstones = 100_000
private const val MaxSyncIdChars = 120
private const val MaxFileHashChars = 160
private const val MaxLocatorChars = 16_384
private const val MaxDeviceLabelChars = 120
private const val MaxTitleChars = 512
private const val MaxWordChars = 120
private const val MaxWriterOriginChars = 120
private const val MaxDescriptionChars = 16_384
private const val MaxTagsCsvChars = 2_048
private const val MaxGoodreadsUrlChars = 2_048
private const val MaxGoodreadsRatingsCount = 1_000_000_000
private const val MinPublicationYear = 1
private const val MaxPublicationYear = 3_000
private const val MaxFormatChars = 32
private const val MaxEntityTypeChars = 64
private const val MaxAssetIdChars = 128
private const val MaxSha256Chars = 128
