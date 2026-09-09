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
    fun progressOnlyPatchAppendsMissingReadingSessions() {
        val result = patchPortableReadingProgressOnly(
            jsonText =
                """
                {
                  "exportedAt": 1000,
                  "books": [
                    {
                      "syncId": "book-a",
                      "fileHash": "hash-a",
                      "lastLocator": "epubcfi(/6/2)",
                      "readingPercent": 0.2,
                      "lastReadAt": 1000,
                      "updatedAt": 1000
                    }
                  ],
                  "readingSessions": [
                    {
                      "syncId": "session-existing",
                      "bookSyncId": "book-a",
                      "startedAt": 1000,
                      "endedAt": 61000,
                      "durationSeconds": 60
                    }
                  ]
                }
                """.trimIndent(),
            patches = emptyList(),
            exportedAt = 2000,
            readingSessions = listOf(
                PortableReadingSession(
                    syncId = "session-existing",
                    bookSyncId = "book-a",
                    startedAt = 1000,
                    endedAt = 61000,
                    durationSeconds = 60,
                ),
                PortableReadingSession(
                    syncId = "session-new",
                    bookSyncId = "book-a",
                    startedAt = 70000,
                    endedAt = 130000,
                    durationSeconds = 60,
                ),
                PortableReadingSession(
                    syncId = "session-unknown-book",
                    bookSyncId = "missing-book",
                    startedAt = 70000,
                    endedAt = 130000,
                    durationSeconds = 60,
                ),
            ),
        )

        val root = JSONObject(result.jsonText)
        val sessions = root.getJSONArray("readingSessions")

        assertEquals(0, result.patched)
        assertEquals(1, result.sessionsAdded)
        assertEquals(2000, root.getLong("exportedAt"))
        assertEquals(2, sessions.length())
        assertEquals("session-new", sessions.getJSONObject(1).getString("syncId"))
    }

    @Test
    fun parsesPortableReadingSessionsFromSnapshot() {
        val sessions = parsePortableReadingSessions(
            """
            {
              "readingSessions": [
                {
                  "syncId": "session-a",
                  "bookSyncId": "book-a",
                  "startedAt": 1000,
                  "endedAt": 61000,
                  "durationSeconds": 60
                },
                {
                  "syncId": "session-invalid",
                  "bookSyncId": "book-a",
                  "startedAt": 1000,
                  "endedAt": 999,
                  "durationSeconds": 60
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, sessions.size)
        assertEquals("session-a", sessions.single().syncId)
        assertEquals("book-a", sessions.single().bookSyncId)
        assertEquals(60, sessions.single().durationSeconds)
    }

    @Test
    fun progressOnlyPatchMergesWordLookupCounters() {
        val result = patchPortableReadingProgressOnly(
            jsonText = """
            {
              "exportedAt": 1000,
              "books": [],
              "wordLookupCounters": [
                {"word":"ember","writerOrigin":"Phone","count":2,"lastLookedUpAt":1200}
              ]
            }
            """.trimIndent(),
            patches = emptyList(),
            exportedAt = 2000,
            wordLookupCounters = listOf(
                PortableWordLookupCounter(
                    word = "Ember",
                    writerOrigin = "Phone",
                    count = 3,
                    lastLookedUpAt = 1500,
                ),
                PortableWordLookupCounter(
                    word = "luminous",
                    writerOrigin = "Tablet",
                    count = 1,
                    lastLookedUpAt = 1600,
                ),
            ),
        )

        val root = JSONObject(result.jsonText)
        val counters = root.getJSONArray("wordLookupCounters")

        assertEquals(0, result.patched)
        assertEquals(2, result.wordLookupCountersMerged)
        assertEquals(2000, root.getLong("exportedAt"))
        assertEquals(2, counters.length())
        assertEquals("ember", counters.getJSONObject(0).getString("word"))
        assertEquals(3, counters.getJSONObject(0).getInt("count"))
        assertEquals(1500, counters.getJSONObject(0).getLong("lastLookedUpAt"))
        assertEquals("luminous", counters.getJSONObject(1).getString("word"))
        assertEquals("Tablet", counters.getJSONObject(1).getString("writerOrigin"))
    }

    @Test
    fun parsesPortableWordLookupCountersFromSnapshot() {
        val counters = parsePortableWordLookupCounters(
            """
            {
              "wordLookupCounters": [
                {"word":"Ember","writerOrigin":"Phone","count":2,"lastLookedUpAt":1200},
                {"word":"bad","writerOrigin":"Phone","count":0,"lastLookedUpAt":1200},
                {"word":"stale","writerOrigin":"Phone","count":1,"lastLookedUpAt":0}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, counters.size)
        assertEquals("ember", counters.single().word)
        assertEquals("Phone", counters.single().writerOrigin)
        assertEquals(2, counters.single().count)
        assertEquals(1200, counters.single().lastLookedUpAt)
    }

    @Test
    fun parsesAdditionalSyncSlicesFromSnapshot() {
        val json = """
            {
              "shelves": [
                {"syncId":"shelf-a","name":"Favorites","createdAt":1000,"updatedAt":1100}
              ],
              "shelfMemberships": [
                {"bookSyncId":"book-a","shelfSyncId":"shelf-a","createdAt":1200}
              ],
              "vocabularyCards": [
                {"syncId":"vocab-a","word":"luminous","definition":"full of light","sentence":"A luminous sky","bookSyncId":"book-a","bookTitle":"Night","createdAt":1300,"lastReviewedAt":1400,"known":true}
              ],
              "bookAliases": [
                {"syncId":"book-remote","fileHash":"sha-a","createdAt":900}
              ],
              "tombstones": [
                {"syncId":"shelf-deleted","entityType":"shelf","deletedAt":1500}
              ]
            }
        """.trimIndent()

        val shelves = parsePortableShelves(json)
        val memberships = parsePortableShelfMemberships(json)
        val cards = parsePortableVocabularyCards(json)
        val aliases = parsePortableBookAliases(json)
        val tombstones = parsePortableTombstones(json)

        assertEquals("Favorites", shelves.single().name)
        assertEquals("book-a", memberships.single().bookSyncId)
        assertEquals("luminous", cards.single().word)
        assertEquals(true, cards.single().known)
        assertEquals("book-remote", aliases.single().syncId)
        assertEquals("shelf", tombstones.single().entityType)
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
