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
    fun progressOnlyPatchPreservesMetadataAndAssets() {
        val result = patchPortableReadingProgressOnly(
            jsonText = """
            {
              "exportedAt": 1000,
              "books": [
                {
                  "syncId": "book-cloud",
                  "title": "Remote Title",
                  "author": "Remote Author",
                  "tagsCsv": "cloud, tags",
                  "format": "EPUB",
                  "fileHash": "hash-cloud",
                  "fileAsset": {
                    "id": "asset-a",
                    "sha256": "sha-a",
                    "sizeBytes": 42,
                    "uploadedAt": 3000
                  },
                  "lastLocator": "old",
                  "readingPercent": 0.25,
                  "rating": 4.5,
                  "updatedAt": 2000,
                  "lastReadAt": 1900,
                  "totalReadingSeconds": 90
                }
              ]
            }
            """.trimIndent(),
            patches = listOf(
                PortableReadingProgressPatch(
                    fileHash = "hash-cloud",
                    lastLocator = "new",
                    readingPercent = 0.75f,
                    lastReadAt = 4000,
                    startedReadingAt = 1500,
                    finishedReadingAt = null,
                    totalReadingSeconds = 180,
                ),
            ),
            exportedAt = 5000,
        )

        val book = JSONObject(result.jsonText).getJSONArray("books").getJSONObject(0)

        assertEquals(1, result.patched)
        assertEquals("Remote Title", book.getString("title"))
        assertEquals("Remote Author", book.getString("author"))
        assertEquals("cloud, tags", book.getString("tagsCsv"))
        assertEquals(4.5, book.getDouble("rating"))
        assertEquals("asset-a", book.getJSONObject("fileAsset").getString("id"))
        assertEquals("new", book.getString("lastLocator"))
        assertEquals(0.75, book.getDouble("readingPercent"))
        assertEquals(4000L, book.getLong("lastReadAt"))
        assertEquals(5000L, JSONObject(result.jsonText).getLong("exportedAt"))
    }

    @Test
    fun progressOnlyPatchSkipsStaleLocalProgress() {
        val json = """
            {
              "exportedAt": 1000,
              "books": [
                {
                  "syncId": "book-cloud",
                  "title": "Remote Title",
                  "format": "EPUB",
                  "fileHash": "hash-cloud",
                  "lastLocator": "remote",
                  "readingPercent": 0.75,
                  "updatedAt": 4000,
                  "lastReadAt": 4000
                }
              ]
            }
        """.trimIndent()

        val result = patchPortableReadingProgressOnly(
            jsonText = json,
            patches = listOf(
                PortableReadingProgressPatch(
                    fileHash = "hash-cloud",
                    lastLocator = "local",
                    readingPercent = 0.5f,
                    lastReadAt = 3000,
                    startedReadingAt = null,
                    finishedReadingAt = null,
                    totalReadingSeconds = 120,
                ),
            ),
            exportedAt = 5000,
        )

        assertEquals(0, result.patched)
        assertEquals(json, result.jsonText)
    }

    @Test
    fun progressOnlyPatchRequiresMatchingFileHashAndLocator() {
        val json = """
            {
              "books": [
                {
                  "syncId": "book-cloud",
                  "title": "Remote Title",
                  "format": "EPUB",
                  "fileHash": "hash-cloud",
                  "lastLocator": "remote",
                  "readingPercent": 0.75,
                  "updatedAt": 2000,
                  "lastReadAt": 2000
                }
              ]
            }
        """.trimIndent()

        val result = patchPortableReadingProgressOnly(
            jsonText = json,
            patches = listOf(
                PortableReadingProgressPatch(
                    fileHash = "different-hash",
                    lastLocator = "local",
                    readingPercent = 0.5f,
                    lastReadAt = 3000,
                    startedReadingAt = null,
                    finishedReadingAt = null,
                    totalReadingSeconds = 120,
                ),
                PortableReadingProgressPatch(
                    fileHash = "hash-cloud",
                    lastLocator = null,
                    readingPercent = 0.9f,
                    lastReadAt = 3500,
                    startedReadingAt = null,
                    finishedReadingAt = null,
                    totalReadingSeconds = 240,
                ),
            ),
            exportedAt = 5000,
        )

        assertEquals(0, result.patched)
        assertEquals(json, result.jsonText)
    }

    @Test
    fun progressOnlyPatchSkipsInvalidPatchValues() {
        val json = """
            {
              "books": [
                {
                  "syncId": "book-cloud",
                  "title": "Remote Title",
                  "format": "EPUB",
                  "fileHash": "hash-cloud",
                  "lastLocator": "remote",
                  "readingPercent": 0.75,
                  "updatedAt": 2000,
                  "lastReadAt": 2000
                }
              ]
            }
        """.trimIndent()

        val result = patchPortableReadingProgressOnly(
            jsonText = json,
            patches = listOf(
                PortableReadingProgressPatch(
                    fileHash = "hash-cloud",
                    lastLocator = "local",
                    readingPercent = Float.NaN,
                    lastReadAt = 3000,
                    startedReadingAt = null,
                    finishedReadingAt = null,
                    totalReadingSeconds = 120,
                ),
                PortableReadingProgressPatch(
                    fileHash = "x".repeat(161),
                    lastLocator = "local",
                    readingPercent = 0.5f,
                    lastReadAt = 3000,
                    startedReadingAt = null,
                    finishedReadingAt = null,
                    totalReadingSeconds = 120,
                ),
                PortableReadingProgressPatch(
                    fileHash = "hash-cloud",
                    lastLocator = "x".repeat(16_385),
                    readingPercent = 0.5f,
                    lastReadAt = 3000,
                    startedReadingAt = null,
                    finishedReadingAt = null,
                    totalReadingSeconds = 120,
                ),
            ),
            exportedAt = 5000,
        )

        assertEquals(0, result.patched)
        assertEquals(json, result.jsonText)
    }

    @Test
    fun progressOnlyPatchSkipsDeletedCloudBooks() {
        val json = """
            {
              "books": [
                {
                  "syncId": "book-cloud",
                  "title": "Remote Title",
                  "format": "EPUB",
                  "fileHash": "hash-cloud",
                  "isDeleted": true,
                  "lastLocator": "remote",
                  "readingPercent": 0.1,
                  "updatedAt": 2000,
                  "lastReadAt": 2000
                }
              ]
            }
        """.trimIndent()

        val result = patchPortableReadingProgressOnly(
            jsonText = json,
            patches = listOf(
                PortableReadingProgressPatch(
                    fileHash = "hash-cloud",
                    lastLocator = "local",
                    readingPercent = 0.9f,
                    lastReadAt = 5000,
                    startedReadingAt = 1000,
                    finishedReadingAt = 5000,
                    totalReadingSeconds = 600,
                ),
            ),
            exportedAt = 6000,
        )

        assertEquals(0, result.patched)
        assertEquals(json, result.jsonText)
    }

    @Test
    fun progressOnlyPatchRejectsTooManyPatches() {
        val patches = List(20_001) {
            PortableReadingProgressPatch(
                fileHash = "hash-$it",
                lastLocator = "locator-$it",
                readingPercent = 0.5f,
                lastReadAt = 1000L + it,
                startedReadingAt = null,
                finishedReadingAt = null,
                totalReadingSeconds = 10,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            patchPortableReadingProgressOnly(
                jsonText = """{"books":[]}""",
                patches = patches,
                exportedAt = 2000,
            )
        }
    }

    @Test
    fun progressOnlyPatchRejectsInvalidExportTime() {
        assertFailsWith<IllegalArgumentException> {
            patchPortableReadingProgressOnly(
                jsonText = """{"books":[]}""",
                patches = emptyList(),
                exportedAt = 0,
            )
        }
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
