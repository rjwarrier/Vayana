package com.vayana.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.json.JSONObject

class PortableReadingProgressJsonTest {
    @Test
    fun extractsValidProgressRows() {
        val snapshot = parsePortableReadingProgressSnapshot(
            """
            {
              "deviceLabel": "Bedroom Kindle",
              "exportedAt": 2200,
              "books": [
                {
                  "syncId": "book-a",
                  "fileHash": "sha-a",
                  "lastLocator": "epubcfi(/6/2)",
                  "readingPercent": 0.42,
                  "lastReadAt": 2000,
                  "updatedAt": 2100,
                  "startedReadingAt": 1000,
                  "finishedReadingAt": null,
                  "totalReadingSeconds": 90
                }
              ]
            }
            """.trimIndent(),
        )

        val progresses = snapshot.progresses
        assertEquals("Bedroom Kindle", snapshot.deviceLabel)
        assertEquals(2200L, snapshot.exportedAt)
        assertEquals(1, progresses.size)
        assertEquals("book-a", progresses.single().syncId)
        assertEquals(0.42f, progresses.single().readingPercent)
        assertEquals(2000L, progresses.single().lastReadAt)
    }

    @Test
    fun skipsRowsWithoutLocator() {
        val progresses = parsePortableReadingProgresses(
            """
            {
              "books": [
                {
                  "syncId": "book-a",
                  "fileHash": "sha-a",
                  "readingPercent": 0.42,
                  "updatedAt": 2100
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), progresses)
    }

    @Test
    fun rejectsTooManyBooks() {
        val books = List(20_001) {
            """{"syncId":"book-$it","fileHash":"sha-$it","lastLocator":"cfi","updatedAt":1}"""
        }.joinToString(",")

        assertFailsWith<IllegalArgumentException> {
            parsePortableReadingProgresses("""{"books":[$books]}""")
        }
    }

    @Test
    fun extractsCloudBooksWithAssets() {
        val books = parsePortableCloudBooks(
            """
            {
              "books": [
                {
                  "syncId": "book-cloud",
                  "title": "Remote Book",
                  "author": "Writer",
                  "tagsCsv": "sci-fi, favorite",
                  "format": "EPUB",
                  "fileHash": "hash-cloud",
                  "fileAsset": {
                    "id": "abcdEFGH1234_wxyz",
                    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "sizeBytes": 42,
                    "uploadedAt": 3000
                  },
                  "coverAsset": {
                    "id": "coverEFGH1234_wxyz",
                    "sha256": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                    "sizeBytes": 24,
                    "uploadedAt": 3100
                  },
                  "lastLocator": "epubcfi(/6/2)",
                  "readingPercent": 0.35,
                  "rating": 4.5,
                  "createdAt": 1000,
                  "updatedAt": 2000,
                  "lastReadAt": 1900,
                  "totalReadingSeconds": 90
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, books.size)
        assertEquals("book-cloud", books.single().syncId)
        assertEquals("Remote Book", books.single().title)
        assertEquals("sci-fi, favorite", books.single().tagsCsv)
        assertEquals("abcdEFGH1234_wxyz", books.single().fileAsset.id)
        assertEquals("coverEFGH1234_wxyz", books.single().coverAsset?.id)
        assertEquals(0.35f, books.single().readingPercent)
    }

    @Test
    fun skipsCloudBooksWithoutDownloadableAssets() {
        val books = parsePortableCloudBooks(
            """
            {
              "books": [
                {"syncId":"deleted","title":"Deleted","format":"EPUB","fileHash":"hash-a","isDeleted":true},
                {"syncId":"physical","title":"Paper","format":"PHYSICAL","fileHash":"physical:1","updatedAt":1},
                {"syncId":"missing-asset","title":"No Asset","format":"EPUB","fileHash":"hash-b","updatedAt":1}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), books)
    }

    @Test
    fun serializesReadingPositionConflictAlternatives() {
        val snapshot = PortableSnapshot(
            formatVersion = 1,
            exportedAt = 3000,
            deviceLabel = "Phone",
            books = emptyList(),
            annotations = emptyList(),
            shelves = emptyList(),
            shelfMemberships = emptyList(),
            readingSessions = emptyList(),
            vocabularyCards = emptyList(),
            wordLookupCounters = emptyList(),
            settings = emptyMap(),
            syncConflicts = listOf(
                PortableSyncConflict(
                    type = "readingPosition",
                    syncId = "book-a",
                    reason = "SAME_TIMESTAMP_DIFFERENT_LOCATOR",
                    detectedAt = 3100,
                    localDeviceLabel = "Phone",
                    remoteDeviceLabel = "Kindle",
                    local = PortableReadingPositionAlternative(
                        fileHash = "sha-a",
                        locator = "epubcfi(/6/2)",
                        readingPercent = 0.2f,
                        lastReadAt = 2000,
                        updatedAt = 2000,
                    ),
                    remote = PortableReadingPositionAlternative(
                        fileHash = "sha-a",
                        locator = "epubcfi(/6/4)",
                        readingPercent = 0.4f,
                        lastReadAt = 2000,
                        updatedAt = 2000,
                    ),
                ),
            ),
        )

        val root = JSONObject(snapshot.toJsonString())
        val conflicts = root.getJSONArray("syncConflicts")
        val conflict = conflicts.getJSONObject(0)

        assertEquals(1, conflicts.length())
        assertEquals("readingPosition", conflict.getString("type"))
        assertEquals("Phone", conflict.getString("localDeviceLabel"))
        assertEquals("Kindle", conflict.getString("remoteDeviceLabel"))
        assertEquals("epubcfi(/6/2)", conflict.getJSONObject("local").getString("locator"))
        assertEquals("epubcfi(/6/4)", conflict.getJSONObject("remote").getString("locator"))
        assertTrue(conflict.has("detectedAt"))
    }
}
