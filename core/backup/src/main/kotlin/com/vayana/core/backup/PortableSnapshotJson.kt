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
                    selectedText = obj.optString("selectedText").take(MaxSnapshotTextChars),
                    readerNote = obj.optSnapshotBoundedString("readerNote", MaxSnapshotTextChars),
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
            val durationSeconds = obj.optLong("durationSeconds", 0L).takeIf { it > 0L } ?: continue
            add(
                PortableReadingSession(
                    syncId = syncId,
                    bookSyncId = bookSyncId,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    durationSeconds = durationSeconds,
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
    optString(name).trim().takeIf { it.isNotEmpty() && it.length <= maxChars }

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
