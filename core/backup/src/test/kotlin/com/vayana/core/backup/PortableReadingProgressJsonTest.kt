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
                  "goodreadsUrl": "https://www.goodreads.com/book/show/16046748",
                  "goodreadsRating": 4.1,
                  "goodreadsRatingsCount": 12345,
                  "originalPublicationYear": 2012,
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
        assertEquals("https://www.goodreads.com/book/show/16046748", books.single().goodreadsUrl)
        assertEquals(4.1f, books.single().goodreadsRating)
        assertEquals(12345, books.single().goodreadsRatingsCount)
        assertEquals(2012, books.single().originalPublicationYear)
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
    fun nullAndLiteralNullTextFieldsReadAsAbsent() {
        val books = parsePortableCloudBooks(
            """
            {
              "books": [
                {
                  "syncId": "book-cloud",
                  "title": "Remote Book",
                  "author": null,
                  "series": "null",
                  "seriesNumber": "null",
                  "description": null,
                  "tagsCsv": "null",
                  "customFontFamily": "null",
                  "format": "EPUB",
                  "fileHash": "hash-cloud",
                  "fileAsset": {
                    "id": "abcdEFGH1234_wxyz",
                    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "sizeBytes": 42,
                    "uploadedAt": 3000
                  },
                  "lastLocator": "null",
                  "createdAt": 1000,
                  "updatedAt": 2000
                }
              ]
            }
            """.trimIndent(),
        )

        val book = books.single()
        assertEquals(null, book.author)
        assertEquals(null, book.series)
        assertEquals(null, book.seriesNumber)
        assertEquals(null, book.description)
        assertEquals(null, book.tagsCsv)
        assertEquals(null, book.customFontFamily)
        assertEquals(null, book.lastLocator)
    }

    @Test
    fun sanitizesUnsafeGoodreadsCloudBookFields() {
        val books = parsePortableCloudBooks(
            """
            {
              "books": [
                {
                  "syncId": "book-cloud",
                  "title": "Remote Book",
                  "format": "EPUB",
                  "fileHash": "hash-cloud",
                  "fileAsset": {
                    "id": "abcdEFGH1234_wxyz",
                    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "sizeBytes": 42,
                    "uploadedAt": 3000
                  },
                  "goodreadsUrl": "https://evil.example/book/show/16046748",
                  "goodreadsRating": 9.9,
                  "goodreadsRatingsCount": 1500000000,
                  "originalPublicationYear": 5000,
                  "createdAt": 1000,
                  "updatedAt": 2000
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, books.size)
        assertEquals(null, books.single().goodreadsUrl)
        assertEquals(5f, books.single().goodreadsRating)
        assertEquals(null, books.single().goodreadsRatingsCount)
        assertEquals(null, books.single().originalPublicationYear)
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
                  "goodreadsUrl": "https://www.goodreads.com/book/show/16046748",
                  "goodreadsRating": 4.1,
                  "goodreadsRatingsCount": 12345,
                  "originalPublicationYear": 2012,
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
        assertEquals("https://www.goodreads.com/book/show/16046748", book.getString("goodreadsUrl"))
        assertEquals(4.1, book.getDouble("goodreadsRating"))
        assertEquals(12345, book.getInt("goodreadsRatingsCount"))
        assertEquals(2012, book.getInt("originalPublicationYear"))
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
                    syncId = "session-book-not-yet-in-snapshot",
                    bookSyncId = "book-pending-full-sync",
                    startedAt = 70000,
                    endedAt = 130000,
                    durationSeconds = 60,
                ),
            ),
        )

        val root = JSONObject(result.jsonText)
        val sessions = root.getJSONArray("readingSessions")

        assertEquals(0, result.patched)
        // Progress-only sync never uploads book/asset metadata, so a session must still be appended even when
        // its book (e.g. one imported moments ago) hasn't reached this document's "books" array yet via a full
        // sync - otherwise that session would never leave the device it started on. The pulling side handles an
        // as-yet-unknown book by skipping the session gracefully (ReadingSessionRepositoryImpl.mergeCloudSession)
        // until a later sync catches it up.
        assertEquals(2, result.sessionsAdded)
        assertEquals(2000, root.getLong("exportedAt"))
        assertEquals(3, sessions.length())
        assertEquals("session-new", sessions.getJSONObject(1).getString("syncId"))
        assertEquals("session-book-not-yet-in-snapshot", sessions.getJSONObject(2).getString("syncId"))
        assertEquals("book-pending-full-sync", sessions.getJSONObject(2).getString("bookSyncId"))
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
    fun progressOnlyPatchMergesResetTombstones() {
        val result = patchPortableReadingProgressOnly(
            jsonText = """
            {
              "exportedAt": 1000,
              "books": [],
              "tombstones": [
                {"syncId":"session-existing","entityType":"reading_session","deletedAt":1200}
              ]
            }
            """.trimIndent(),
            patches = emptyList(),
            exportedAt = 2000,
            tombstones = listOf(
                PortableTombstone(
                    syncId = "session-existing",
                    entityType = "reading_session",
                    deletedAt = 1100,
                ),
                PortableTombstone(
                    syncId = "reset:book-a",
                    entityType = "reading_progress_reset",
                    deletedAt = 1600,
                ),
            ),
        )

        val root = JSONObject(result.jsonText)
        val tombstones = root.getJSONArray("tombstones")

        assertEquals(1, result.tombstonesMerged)
        assertEquals(2000, root.getLong("exportedAt"))
        assertEquals(2, tombstones.length())
        assertEquals("session-existing", tombstones.getJSONObject(0).getString("syncId"))
        assertEquals(1200, tombstones.getJSONObject(0).getLong("deletedAt"))
        assertEquals("reset:book-a", tombstones.getJSONObject(1).getString("syncId"))
        assertEquals("reading_progress_reset", tombstones.getJSONObject(1).getString("entityType"))
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
                {"syncId":"shelf-deleted","entityType":"shelf","deletedAt":1500},
                {"syncId":"shelf_membership:book-a:shelf-a","entityType":"shelf_membership","deletedAt":1600}
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
        assertEquals(2, tombstones.size)
        assertEquals("shelf", tombstones[0].entityType)
        assertEquals("shelf_membership", tombstones[1].entityType)
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

    @Test
    fun writesSlicedSnapshotManifestAndVersionedSlices() {
        val snapshot = testPortableSnapshot()

        val documents = snapshot.toSlicedJsonDocuments()
        val manifestDocument = documents.last()
        val manifest = JSONObject(manifestDocument.jsonText)
        val slices = manifest.getJSONObject("slices")

        assertEquals(9, documents.size)
        assertEquals(PortableSnapshotLatestPath, manifestDocument.path)
        assertEquals(CurrentPortableSnapshotSliceVersion, manifest.getInt("sliceVersion"))
        assertEquals("vayana/snapshot-slices/12345/annotations.json", slices.getString("annotations"))
        assertEquals("vayana/snapshot-slices/12345/reading-sessions.json", slices.getString("readingSessions"))
        assertEquals(false, manifest.has("annotations"))
        assertEquals(false, manifest.has("readingSessions"))
        assertEquals(1, manifest.getJSONArray("books").length())
    }

    @Test
    fun mergesSlicedSnapshotDocumentsForLegacyParsers() {
        val documents = testPortableSnapshot().toSlicedJsonDocuments()
        val manifest = documents.last { it.path == PortableSnapshotLatestPath }.jsonText
        val slicesByKey = portableSnapshotSlicePaths(manifest).mapValues { (_, path) ->
            documents.single { it.path == path }.jsonText
        }

        val merged = mergePortableSnapshotSlices(manifest, slicesByKey)

        assertEquals(true, portableSnapshotHasSlices(manifest))
        assertEquals("note-a", parsePortableAnnotations(merged).single().syncId)
        assertEquals("shelf-a", parsePortableShelves(merged).single().syncId)
        assertEquals("session-a", parsePortableReadingSessions(merged).single().syncId)
        assertEquals("word", parsePortableVocabularyCards(merged).single().word)
        assertEquals("lookup", parsePortableWordLookupCounters(merged).single().word)
        assertEquals("book-a", parsePortableBookAliases(merged).single().syncId)
        assertEquals("note-a", parsePortableTombstones(merged).single().syncId)
    }

    @Test
    fun ignoresUnsafeSlicedSnapshotManifestPaths() {
        val manifest = JSONObject()
            .put(
                "slices",
                JSONObject()
                    .put("annotations", "vayana/snapshot-slices/12345/../annotations.json")
                    .put("readingSessions", "vayana/snapshot-slices/0/reading-sessions.json")
                    .put("shelves", "vayana/snapshot-slices/12345/shelves.json"),
            )

        val paths = portableSnapshotSlicePaths(manifest.toString())

        assertEquals(mapOf("shelves" to "vayana/snapshot-slices/12345/shelves.json"), paths)
    }

    private fun testPortableSnapshot(): PortableSnapshot =
        PortableSnapshot(
            formatVersion = 1,
            exportedAt = 12345,
            deviceLabel = "Phone",
            books = listOf(
                PortableBook(
                    syncId = "book-a",
                    title = "Book A",
                    author = "Author",
                    series = null,
                    seriesNumber = null,
                    description = null,
                    tagsCsv = null,
                    format = "EPUB",
                    fileHash = "hash-a",
                    fileAvailability = "LOCAL",
                    fileAvailableLocally = true,
                    fileAsset = null,
                    coverAvailableLocally = false,
                    coverAsset = null,
                    lastLocator = "epubcfi(/6/2)",
                    readingPercent = 0.25f,
                    rating = 0f,
                    groupId = null,
                    isDeleted = false,
                    wordCount = null,
                    pageEstimate = null,
                    createdAt = 1000,
                    updatedAt = 2000,
                    lastReadAt = 2000,
                    startedReadingAt = null,
                    finishedReadingAt = null,
                    totalReadingSeconds = 60,
                    customFontSizePercent = null,
                    customLineHeight = null,
                    customFontFamily = null,
                    customSideMarginPercent = null,
                    readNextAddedAt = null,
                    goodreadsUrl = "https://www.goodreads.com/book/show/16046748",
                    goodreadsRating = 4.1f,
                    goodreadsRatingsCount = 12345,
                    originalPublicationYear = 2012,
                ),
            ),
            annotations = listOf(
                PortableAnnotation(
                    syncId = "note-a",
                    bookSyncId = "book-a",
                    type = "HIGHLIGHT",
                    colorKey = "yellow",
                    locator = "epubcfi(/6/4)",
                    chapterTitle = "Chapter",
                    chapterHref = "chapter.xhtml",
                    selectedText = "Selected text",
                    readerNote = null,
                    createdAt = 2000,
                    updatedAt = 2100,
                    isDeleted = false,
                ),
            ),
            shelves = listOf(PortableShelf(syncId = "shelf-a", name = "Favorites", createdAt = 1000, updatedAt = 1000)),
            shelfMemberships = listOf(PortableShelfMembership(bookSyncId = "book-a", shelfSyncId = "shelf-a", createdAt = 1100)),
            readingSessions = listOf(
                PortableReadingSession(syncId = "session-a", bookSyncId = "book-a", startedAt = 1200, endedAt = 1260, durationSeconds = 60),
            ),
            vocabularyCards = listOf(
                PortableVocabularyCard(
                    syncId = "card-a",
                    word = "word",
                    definition = "definition",
                    sentence = null,
                    bookSyncId = "book-a",
                    bookTitle = "Book A",
                    createdAt = 1300,
                    lastReviewedAt = null,
                    known = false,
                ),
            ),
            wordLookupCounters = listOf(PortableWordLookupCounter(word = "lookup", writerOrigin = "origin", count = 2, lastLookedUpAt = 1400)),
            bookAliases = listOf(PortableBookAlias(syncId = "book-a", fileHash = "hash-old", createdAt = 1500)),
            tombstones = listOf(PortableTombstone(syncId = "note-a", entityType = "annotation", deletedAt = 1600)),
            settings = mapOf("theme" to "sepia"),
        )
}
