package com.vayana.core.backup

import org.json.JSONArray
import org.json.JSONObject

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
