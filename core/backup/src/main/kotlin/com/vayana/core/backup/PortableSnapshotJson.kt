package com.vayana.core.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads just the "annotations" array out of a full portable snapshot - the counterpart to
 * [parsePortableCloudBooks]/[parsePortableReadingProgressSnapshot], which read other slices of
 * the same document. There is no full-snapshot reader by design: each sync consumer decodes only
 * the fields it actually merges.
 */
fun parsePortableAnnotations(jsonText: String): List<PortableAnnotation> {
    require(jsonText.length <= MaxPortableSnapshotJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val array = root.optJSONArray("annotations") ?: return emptyList()
    require(array.length() <= MaxPortableSnapshotAnnotations) { "Portable snapshot has too many annotations" }
    return buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val syncId = obj.optSnapshotBoundedString("syncId", MaxSnapshotSyncIdChars) ?: continue
            val bookSyncId = obj.optSnapshotBoundedString("bookSyncId", MaxSnapshotSyncIdChars) ?: continue
            val type = obj.optSnapshotBoundedString("type", MaxSnapshotEnumChars) ?: continue
            val colorKey = obj.optSnapshotBoundedString("colorKey", MaxSnapshotEnumChars) ?: continue
            val locator = obj.optSnapshotBoundedString("locator", MaxSnapshotLocatorChars) ?: continue
            val createdAt = obj.optLong("createdAt", 0L).takeIf { it > 0L } ?: continue
            val updatedAt = obj.optLong("updatedAt", 0L).takeIf { it > 0L } ?: continue
            add(
                PortableAnnotation(
                    syncId = syncId,
                    bookSyncId = bookSyncId,
                    type = type,
                    colorKey = colorKey,
                    locator = locator,
                    chapterTitle = obj.optSnapshotBoundedString("chapterTitle", MaxSnapshotTitleChars),
                    chapterHref = obj.optSnapshotBoundedString("chapterHref", MaxSnapshotLocatorChars),
                    selectedText = obj.optPortableStringOrNull("selectedText").orEmpty().take(MaxSnapshotTextChars),
                    readerNote = obj.optSnapshotBoundedString("readerNote", MaxSnapshotTextChars),
                    reviewQuestion = obj.optSnapshotBoundedString("reviewQuestion", 2000),
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    isDeleted = obj.optBoolean("isDeleted", false),
                ),
            )
        }
    }
}

fun parsePortableReadingSessions(jsonText: String): List<PortableReadingSession> {
    require(jsonText.length <= MaxPortableSnapshotJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val array = root.optJSONArray("readingSessions") ?: return emptyList()
    require(array.length() <= MaxPortableSnapshotReadingSessions) { "Portable snapshot has too many reading sessions" }
    return buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val syncId = obj.optSnapshotBoundedString("syncId", MaxSnapshotSyncIdChars) ?: continue
            val bookSyncId = obj.optSnapshotBoundedString("bookSyncId", MaxSnapshotSyncIdChars) ?: continue
            val startedAt = obj.optLong("startedAt", 0L).takeIf { it > 0L } ?: continue
            val endedAt = obj.optLong("endedAt", 0L).takeIf { it >= startedAt } ?: continue
            val intervals = if (obj.has("activeIntervals") && !obj.isNull("activeIntervals")) obj.optString("activeIntervals").takeIf { it.length <= 60_000 } else null
            val durationSeconds = obj.optLong("durationSeconds", 0L).takeIf { it > 0L || (it == 0L && intervals == "") } ?: continue
            add(
                PortableReadingSession(
                    syncId = syncId,
                    bookSyncId = bookSyncId,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    durationSeconds = durationSeconds,
                    activeIntervals = intervals,
                    bookFileHash = obj.optSnapshotBoundedString("bookFileHash", MaxSnapshotFileHashChars),
                    startPage = if (obj.has("startPage") && !obj.isNull("startPage")) obj.optInt("startPage", -1).takeIf { it >= 0 } else null,
                    endPage = if (obj.has("endPage") && !obj.isNull("endPage")) obj.optInt("endPage", -1).takeIf { it >= 0 } else null,
                ),
            )
        }
    }
}

fun parsePortableWordLookupCounters(jsonText: String): List<PortableWordLookupCounter> {
    require(jsonText.length <= MaxPortableSnapshotJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val array = root.optJSONArray("wordLookupCounters") ?: return emptyList()
    require(array.length() <= MaxPortableSnapshotWordLookupCounters) { "Portable snapshot has too many word lookup counters" }
    return buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val word = obj.optSnapshotBoundedString("word", MaxSnapshotWordChars)?.lowercase() ?: continue
            val writerOrigin = obj.optSnapshotBoundedString("writerOrigin", MaxSnapshotWriterOriginChars) ?: continue
            val count = obj.optInt("count", 0).takeIf { it > 0 } ?: continue
            val lastLookedUpAt = obj.optLong("lastLookedUpAt", 0L).takeIf { it > 0L } ?: continue
            add(
                PortableWordLookupCounter(
                    word = word,
                    writerOrigin = writerOrigin,
                    count = count,
                    lastLookedUpAt = lastLookedUpAt,
                ),
            )
        }
    }
}


fun parsePortableShelves(jsonText: String): List<PortableShelf> {
    require(jsonText.length <= MaxPortableSnapshotJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val array = root.optJSONArray("shelves") ?: return emptyList()
    require(array.length() <= MaxPortableSnapshotShelves) { "Portable snapshot has too many shelves" }
    return buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val syncId = obj.optSnapshotBoundedString("syncId", MaxSnapshotSyncIdChars) ?: continue
            val name = obj.optSnapshotBoundedString("name", MaxSnapshotTitleChars) ?: continue
            val createdAt = obj.optLong("createdAt", 0L).takeIf { it > 0L } ?: continue
            val updatedAt = obj.optLong("updatedAt", 0L).takeIf { it > 0L } ?: continue
            add(PortableShelf(syncId = syncId, name = name, createdAt = createdAt, updatedAt = updatedAt))
        }
    }
}

fun parsePortableShelfMemberships(jsonText: String): List<PortableShelfMembership> {
    require(jsonText.length <= MaxPortableSnapshotJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val array = root.optJSONArray("shelfMemberships") ?: return emptyList()
    require(array.length() <= MaxPortableSnapshotShelfMemberships) { "Portable snapshot has too many shelf memberships" }
    return buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val bookSyncId = obj.optSnapshotBoundedString("bookSyncId", MaxSnapshotSyncIdChars) ?: continue
            val shelfSyncId = obj.optSnapshotBoundedString("shelfSyncId", MaxSnapshotSyncIdChars) ?: continue
            val createdAt = obj.optLong("createdAt", 0L).takeIf { it > 0L } ?: continue
            add(PortableShelfMembership(bookSyncId = bookSyncId, shelfSyncId = shelfSyncId, createdAt = createdAt))
        }
    }
}

fun parsePortableVocabularyCards(jsonText: String): List<PortableVocabularyCard> {
    require(jsonText.length <= MaxPortableSnapshotJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val array = root.optJSONArray("vocabularyCards") ?: return emptyList()
    require(array.length() <= MaxPortableSnapshotVocabularyCards) { "Portable snapshot has too many vocabulary cards" }
    return buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val syncId = obj.optSnapshotBoundedString("syncId", MaxSnapshotSyncIdChars) ?: continue
            val word = obj.optSnapshotBoundedString("word", MaxSnapshotWordChars) ?: continue
            val definition = obj.optSnapshotBoundedString("definition", MaxSnapshotTextChars) ?: continue
            val createdAt = obj.optLong("createdAt", 0L).takeIf { it > 0L } ?: continue
            add(
                PortableVocabularyCard(
                    syncId = syncId,
                    word = word,
                    definition = definition,
                    sentence = obj.optSnapshotBoundedString("sentence", MaxSnapshotTextChars),
                    bookSyncId = obj.optSnapshotBoundedString("bookSyncId", MaxSnapshotSyncIdChars),
                    bookTitle = obj.optSnapshotBoundedString("bookTitle", MaxSnapshotTitleChars),
                    createdAt = createdAt,
                    lastReviewedAt = obj.optLong("lastReviewedAt", 0L).takeIf { it > 0L },
                    known = obj.optBoolean("known", false),
                    dueAt = obj.optLong("dueAt", 0L).takeIf { it > 0L },
                    // Bounded so a damaged snapshot can't schedule a card centuries out or with a broken ease.
                    intervalDays = obj.optInt("intervalDays", 0).coerceIn(0, 36_500),
                    easeFactor = obj.optDouble("easeFactor", 2.5).toFloat().takeIf { it.isFinite() }?.coerceIn(1.3f, 5f) ?: 2.5f,
                    repetitions = obj.optInt("repetitions", 0).coerceAtLeast(0),
                ),
            )
        }
    }
}

fun parsePortableBookAliases(jsonText: String): List<PortableBookAlias> {
    require(jsonText.length <= MaxPortableSnapshotJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val array = root.optJSONArray("bookAliases") ?: return emptyList()
    require(array.length() <= MaxPortableSnapshotBookAliases) { "Portable snapshot has too many book aliases" }
    return buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val syncId = obj.optSnapshotBoundedString("syncId", MaxSnapshotSyncIdChars) ?: continue
            val fileHash = obj.optSnapshotBoundedString("fileHash", MaxSnapshotFileHashChars) ?: continue
            val createdAt = obj.optLong("createdAt", 0L).takeIf { it > 0L } ?: continue
            add(PortableBookAlias(syncId = syncId, fileHash = fileHash, createdAt = createdAt))
        }
    }
}

fun parsePortableTombstones(jsonText: String): List<PortableTombstone> {
    require(jsonText.length <= MaxPortableSnapshotJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val array = root.optJSONArray("tombstones") ?: return emptyList()
    require(array.length() <= MaxPortableSnapshotTombstones) { "Portable snapshot has too many tombstones" }
    return buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val syncId = obj.optSnapshotBoundedString("syncId", MaxSnapshotSyncIdChars) ?: continue
            val entityType = obj.optSnapshotBoundedString("entityType", MaxSnapshotEnumChars) ?: continue
            val deletedAt = obj.optLong("deletedAt", 0L).takeIf { it > 0L } ?: continue
            add(PortableTombstone(syncId = syncId, entityType = entityType, deletedAt = deletedAt))
        }
    }
}

private fun JSONObject.optSnapshotBoundedString(name: String, maxChars: Int): String? =
    optPortableStringOrNull(name)?.takeIf { it.length <= maxChars }

private const val MaxPortableSnapshotJsonChars = 16 * 1024 * 1024
private const val MaxPortableSnapshotAnnotations = 100_000
private const val MaxPortableSnapshotReadingSessions = 200_000
private const val MaxPortableSnapshotWordLookupCounters = 100_000
private const val MaxPortableSnapshotShelves = 20_000
private const val MaxPortableSnapshotShelfMemberships = 100_000
private const val MaxPortableSnapshotVocabularyCards = 100_000
private const val MaxPortableSnapshotBookAliases = 20_000
private const val MaxPortableSnapshotTombstones = 100_000
private const val MaxSnapshotSyncIdChars = 120
private const val MaxSnapshotEnumChars = 40
private const val MaxSnapshotLocatorChars = 16_384
private const val MaxSnapshotTitleChars = 512
private const val MaxSnapshotTextChars = 16_384
private const val MaxSnapshotWordChars = 120
private const val MaxSnapshotWriterOriginChars = 120
private const val MaxSnapshotFileHashChars = 256
private const val MaxSnapshotPathChars = 240
const val PortableSnapshotLatestPath = "vayana/snapshot-latest.json"
const val CurrentPortableSnapshotSliceVersion = 1

fun PortableSnapshot.toJsonString(): String =
    JSONObject()
        .put("formatVersion", formatVersion)
        .put("exportedAt", exportedAt)
        .put("deviceLabel", deviceLabel)
        .put("books", books.toJsonArray { it.toJson() })
        .put("annotations", annotations.toJsonArray { it.toJson() })
        .put("shelves", shelves.toJsonArray { it.toJson() })
        .put("shelfMemberships", shelfMemberships.toJsonArray { it.toJson() })
        .put("readingSessions", readingSessions.toJsonArray { it.toJson() })
        .put("vocabularyCards", vocabularyCards.toJsonArray { it.toJson() })
        .put("wordLookupCounters", wordLookupCounters.toJsonArray { it.toJson() })
        .put("bookAliases", bookAliases.toJsonArray { it.toJson() })
        .put("tombstones", tombstones.toJsonArray { it.toJson() })
        .put("settings", JSONObject(settings))
        .put("syncConflicts", syncConflicts.toJsonArray { it.toJson() })
        .toString(2)

data class PortableSnapshotDocument(
    val path: String,
    val jsonText: String,
    /** The slice this document holds, or null for the latest-pointer manifest. */
    val sliceKey: String? = null,
)

fun PortableSnapshot.toSlicedJsonDocuments(): List<PortableSnapshotDocument> {
    val slices = PortableSnapshotSlice.entries.map { slice ->
        PortableSnapshotDocument(
            path = slice.path(exportedAt),
            jsonText = sliceJson(slice).toString(2),
            sliceKey = slice.key,
        )
    }
    val manifest = JSONObject()
        .put("formatVersion", formatVersion)
        .put("exportedAt", exportedAt)
        .put("deviceLabel", deviceLabel)
        .put("sliceVersion", CurrentPortableSnapshotSliceVersion)
        .put("books", books.toJsonArray { it.toJson() })
        .put("settings", JSONObject(settings))
        .put("syncConflicts", syncConflicts.toJsonArray { it.toJson() })
        .put(
            "slices",
            JSONObject().also { root ->
                PortableSnapshotSlice.entries.forEach { slice -> root.put(slice.key, slice.path(exportedAt)) }
            },
        )
    return slices + PortableSnapshotDocument(path = PortableSnapshotLatestPath, jsonText = manifest.toString(2))
}

fun portableSnapshotSlicePaths(jsonText: String): Map<String, String> {
    require(jsonText.length <= MaxPortableSnapshotJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(jsonText)
    val slices = root.optJSONObject("slices") ?: return emptyMap()
    return buildMap {
        PortableSnapshotSlice.entries.forEach { slice ->
            val path = slices.optSnapshotBoundedString(slice.key, MaxSnapshotPathChars) ?: return@forEach
            if (slice.isValidPath(path)) put(slice.key, path)
        }
    }
}

fun mergePortableSnapshotSlices(baseJson: String, sliceJsonByKey: Map<String, String>): String {
    require(baseJson.length <= MaxPortableSnapshotJsonChars) { "Portable snapshot is too large" }
    val root = JSONObject(baseJson)
    PortableSnapshotSlice.entries.forEach { slice ->
        val sliceJson = sliceJsonByKey[slice.key] ?: return@forEach
        require(sliceJson.length <= MaxPortableSnapshotJsonChars) { "Portable snapshot slice is too large" }
        val sliceRoot = JSONObject(sliceJson)
        root.put(slice.jsonArrayName, sliceRoot.optJSONArray(slice.jsonArrayName) ?: JSONArray())
    }
    return root.toString(2)
}

fun portableSnapshotHasSlices(jsonText: String): Boolean = portableSnapshotSlicePaths(jsonText).isNotEmpty()

/**
 * True when two documents of the slice [sliceKey] carry the same records, ignoring per-export envelope fields
 * such as exportedAt and deviceLabel. Order-sensitive, so it can only err toward "different" (an extra upload).
 */
fun portableSnapshotSliceDataEquals(sliceKey: String, first: String, second: String): Boolean {
    val slice = PortableSnapshotSlice.entries.firstOrNull { it.key == sliceKey } ?: return false
    return runCatching {
        val firstArray = JSONObject(first).optJSONArray(slice.jsonArrayName) ?: JSONArray()
        val secondArray = JSONObject(second).optJSONArray(slice.jsonArrayName) ?: JSONArray()
        // Both sides come from the same toJson() writers, so equal records serialize identically.
        firstArray.toString() == secondArray.toString()
    }.getOrDefault(false)
}

/** Points the manifest's slice entries in [pathsByKey] at other (already published) slice files. */
fun repointPortableSnapshotSlices(manifestJson: String, pathsByKey: Map<String, String>): String {
    if (pathsByKey.isEmpty()) return manifestJson
    val root = JSONObject(manifestJson)
    val slices = root.optJSONObject("slices") ?: JSONObject().also { root.put("slices", it) }
    PortableSnapshotSlice.entries.forEach { slice ->
        val path = pathsByKey[slice.key] ?: return@forEach
        require(slice.isValidPath(path)) { "Invalid snapshot slice path" }
        slices.put(slice.key, path)
    }
    return root.toString(2)
}

data class SlicedPortableReadingProgressPatchResult(
    val manifestJson: String,
    /** New slice documents to write before the manifest, keyed by repository path. */
    val sliceWrites: Map<String, String>,
    val patched: Int,
    val sessionsAdded: Int,
    val wordLookupCountersMerged: Int,
    val tombstonesMerged: Int,
) {
    val changed: Int
        get() = patched + sessionsAdded + wordLookupCountersMerged + tombstonesMerged
}

/**
 * Progress-only push for a sliced snapshot: reading positions are patched in the manifest's "books", while
 * sessions, word-lookup counters and tombstones are patched in their own slices. A changed slice is written
 * to a fresh `snapshot-slices/<exportedAt>/` path (slice paths are never rewritten in place, so a reader
 * holding the previous manifest keeps a consistent view) and the manifest is repointed at it. Unchanged
 * slices keep their existing paths.
 */
fun patchSlicedPortableReadingProgress(
    manifestJson: String,
    sliceJsonByKey: Map<String, String>,
    patches: List<PortableReadingProgressPatch>,
    exportedAt: Long,
    readingSessions: List<PortableReadingSession> = emptyList(),
    wordLookupCounters: List<PortableWordLookupCounter> = emptyList(),
    tombstones: List<PortableTombstone> = emptyList(),
): SlicedPortableReadingProgressPatchResult {
    val bookPatch = patchPortableReadingProgressOnly(jsonText = manifestJson, patches = patches, exportedAt = exportedAt)
    val sliceWrites = linkedMapOf<String, String>()
    fun patchSlice(slice: PortableSnapshotSlice, patch: (String) -> PortableReadingProgressPatchResult): PortableReadingProgressPatchResult {
        val base = sliceJsonByKey[slice.key] ?: JSONObject().put(slice.jsonArrayName, JSONArray()).toString()
        val result = patch(base)
        if (result.sessionsAdded + result.wordLookupCountersMerged + result.tombstonesMerged > 0) {
            sliceWrites[slice.path(exportedAt)] = result.jsonText
        }
        return result
    }
    val sessions = patchSlice(PortableSnapshotSlice.ReadingSessions) { json ->
        patchPortableReadingProgressOnly(json, emptyList(), exportedAt, readingSessions = readingSessions)
    }
    val counters = patchSlice(PortableSnapshotSlice.WordLookupCounters) { json ->
        patchPortableReadingProgressOnly(json, emptyList(), exportedAt, wordLookupCounters = wordLookupCounters)
    }
    val tombstonePatch = patchSlice(PortableSnapshotSlice.Tombstones) { json ->
        patchPortableReadingProgressOnly(json, emptyList(), exportedAt, tombstones = tombstones)
    }

    val changed = bookPatch.patched > 0 || sliceWrites.isNotEmpty()
    val manifest = if (!changed) {
        manifestJson
    } else {
        val root = JSONObject(bookPatch.jsonText)
        val slices = root.optJSONObject("slices") ?: JSONObject().also { root.put("slices", it) }
        PortableSnapshotSlice.entries.forEach { slice ->
            val path = slice.path(exportedAt)
            if (path in sliceWrites) slices.put(slice.key, path)
        }
        root.put("exportedAt", exportedAt)
        root.toString(2)
    }
    return SlicedPortableReadingProgressPatchResult(
        manifestJson = manifest,
        sliceWrites = sliceWrites,
        patched = bookPatch.patched,
        sessionsAdded = sessions.sessionsAdded,
        wordLookupCountersMerged = counters.wordLookupCountersMerged,
        tombstonesMerged = tombstonePatch.tombstonesMerged,
    )
}

private fun PortableSnapshot.sliceJson(slice: PortableSnapshotSlice): JSONObject =
    JSONObject()
        .put("formatVersion", formatVersion)
        .put("exportedAt", exportedAt)
        .put("deviceLabel", deviceLabel)
        .put(
            slice.jsonArrayName,
            when (slice) {
                PortableSnapshotSlice.Annotations -> annotations.toJsonArray { it.toJson() }
                PortableSnapshotSlice.Shelves -> shelves.toJsonArray { it.toJson() }
                PortableSnapshotSlice.ShelfMemberships -> shelfMemberships.toJsonArray { it.toJson() }
                PortableSnapshotSlice.ReadingSessions -> readingSessions.toJsonArray { it.toJson() }
                PortableSnapshotSlice.VocabularyCards -> vocabularyCards.toJsonArray { it.toJson() }
                PortableSnapshotSlice.WordLookupCounters -> wordLookupCounters.toJsonArray { it.toJson() }
                PortableSnapshotSlice.BookAliases -> bookAliases.toJsonArray { it.toJson() }
                PortableSnapshotSlice.Tombstones -> tombstones.toJsonArray { it.toJson() }
            },
        )

private enum class PortableSnapshotSlice(val key: String, val jsonArrayName: String, private val fileName: String) {
    Annotations("annotations", "annotations", "annotations.json"),
    Shelves("shelves", "shelves", "shelves.json"),
    ShelfMemberships("shelfMemberships", "shelfMemberships", "shelf-memberships.json"),
    ReadingSessions("readingSessions", "readingSessions", "reading-sessions.json"),
    VocabularyCards("vocabularyCards", "vocabularyCards", "vocabulary-cards.json"),
    WordLookupCounters("wordLookupCounters", "wordLookupCounters", "word-lookup-counters.json"),
    BookAliases("bookAliases", "bookAliases", "book-aliases.json"),
    Tombstones("tombstones", "tombstones", "tombstones.json");

    fun path(exportedAt: Long): String = "vayana/snapshot-slices/$exportedAt/$fileName"

    fun isValidPath(path: String): Boolean = path.length <= MaxSnapshotPathChars && (
        pathRegex.matches(path) || (this == Annotations && compressedPathRegex.matches(path))
    )

    private val pathRegex = Regex("^vayana/snapshot-slices/[1-9][0-9]*/${Regex.escape(fileName)}$")
    private val compressedPathRegex = Regex("^vayana/snapshot-slices/[1-9][0-9]*/annotations\\.zip$")
}

private fun PortableBook.toJson(): JSONObject =
    JSONObject()
        .put("syncId", syncId)
        .put("title", title)
        .putOptional("author", author)
        .putOptional("series", series)
        .putOptional("seriesNumber", seriesNumber)
        .putOptional("description", description)
        .putOptional("tagsCsv", tagsCsv)
        .put("format", format)
        .put("fileHash", fileHash)
        .put("fileAvailability", fileAvailability)
        .put("fileAvailableLocally", fileAvailableLocally)
        .putOptional("fileAsset", fileAsset?.toJson())
        .put("coverAvailableLocally", coverAvailableLocally)
        .putOptional("coverAsset", coverAsset?.toJson())
        .putOptional("lastLocator", lastLocator)
        .put("readingPercent", readingPercent.toDouble())
        .put("rating", rating.toDouble())
        .putOptional("groupId", groupId)
        .put("isDeleted", isDeleted)
        .putOptional("wordCount", wordCount)
        .putOptional("pageEstimate", pageEstimate)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .putOptional("lastReadAt", lastReadAt)
        .putOptional("startedReadingAt", startedReadingAt)
        .putOptional("finishedReadingAt", finishedReadingAt)
        .put("totalReadingSeconds", totalReadingSeconds)
        .putOptional("customFontSizePercent", customFontSizePercent)
        .putOptional("customLineHeight", customLineHeight?.toDouble())
        .putOptional("customFontFamily", customFontFamily)
        .putOptional("customSideMarginPercent", customSideMarginPercent)
        .putOptional("readNextAddedAt", readNextAddedAt)
        .putOptional("readNextUpdatedAt", readNextUpdatedAt)
        .put("readNextPinned", readNextPinned)
        .put("readingDisposition", readingDisposition)
        .putOptional("dispositionReason", dispositionReason)
        .putOptional("dispositionUpdatedAt", dispositionUpdatedAt)
        .putOptional("deletionUpdatedAt", deletionUpdatedAt)
        .putOptional("goodreadsUrl", goodreadsUrl)
        .putOptional("goodreadsRating", goodreadsRating?.toDouble())
        .putOptional("goodreadsRatingsCount", goodreadsRatingsCount)
        .putOptional("originalPublicationYear", originalPublicationYear)
        .putOptional("physicalOwnership", physicalOwnership)
        .putOptional("borrowReturnAt", borrowReturnAt)
        .putOptional("gutenbergId", gutenbergId)

private fun PortableAsset.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("sha256", sha256)
        .put("sizeBytes", sizeBytes)
        .put("uploadedAt", uploadedAt)

private fun PortableAnnotation.toJson(): JSONObject =
    JSONObject()
        .put("syncId", syncId)
        .put("bookSyncId", bookSyncId)
        .put("type", type)
        .put("colorKey", colorKey)
        .put("locator", locator)
        .putOptional("chapterTitle", chapterTitle)
        .putOptional("chapterHref", chapterHref)
        .put("selectedText", selectedText)
        .putOptional("readerNote", readerNote)
        .putOptional("reviewQuestion", reviewQuestion)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("isDeleted", isDeleted)

private fun PortableShelf.toJson(): JSONObject =
    JSONObject()
        .put("syncId", syncId)
        .put("name", name)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

private fun PortableShelfMembership.toJson(): JSONObject =
    JSONObject()
        .put("bookSyncId", bookSyncId)
        .put("shelfSyncId", shelfSyncId)
        .put("createdAt", createdAt)

private fun PortableReadingSession.toJson(): JSONObject =
    JSONObject()
        .put("syncId", syncId)
        .put("bookSyncId", bookSyncId)
        .put("startedAt", startedAt)
        .put("endedAt", endedAt)
        .put("durationSeconds", durationSeconds)
        .put("startPage", startPage)
        .put("endPage", endPage)
        .put("activeIntervals", activeIntervals)
        .put("bookFileHash", bookFileHash)

private fun PortableVocabularyCard.toJson(): JSONObject =
    JSONObject()
        .put("syncId", syncId)
        .put("word", word)
        .put("definition", definition)
        .putOptional("sentence", sentence)
        .putOptional("bookSyncId", bookSyncId)
        .putOptional("bookTitle", bookTitle)
        .put("createdAt", createdAt)
        .putOptional("lastReviewedAt", lastReviewedAt)
        .put("known", known)
        .putOptional("dueAt", dueAt)
        .put("intervalDays", intervalDays)
        .put("easeFactor", easeFactor.toDouble())
        .put("repetitions", repetitions)

private fun PortableWordLookupCounter.toJson(): JSONObject =
    JSONObject()
        .put("word", word)
        .put("writerOrigin", writerOrigin)
        .put("count", count)
        .put("lastLookedUpAt", lastLookedUpAt)

private fun PortableBookAlias.toJson(): JSONObject =
    JSONObject()
        .put("syncId", syncId)
        .put("fileHash", fileHash)
        .put("createdAt", createdAt)

private fun PortableTombstone.toJson(): JSONObject =
    JSONObject()
        .put("syncId", syncId)
        .put("entityType", entityType)
        .put("deletedAt", deletedAt)

private fun PortableSyncConflict.toJson(): JSONObject =
    JSONObject()
        .put("type", type)
        .put("syncId", syncId)
        .put("reason", reason)
        .put("detectedAt", detectedAt)
        .put("localDeviceLabel", localDeviceLabel)
        .putOptional("remoteDeviceLabel", remoteDeviceLabel)
        .put("local", local.toJson())
        .put("remote", remote.toJson())

private fun PortableReadingPositionAlternative.toJson(): JSONObject =
    JSONObject()
        .put("fileHash", fileHash)
        .putOptional("locator", locator)
        .put("readingPercent", readingPercent.toDouble())
        .putOptional("lastReadAt", lastReadAt)
        .put("updatedAt", updatedAt)

private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(mapper(it)) } }

private fun JSONObject.putOptional(name: String, value: Any?): JSONObject =
    put(name, value ?: JSONObject.NULL)
