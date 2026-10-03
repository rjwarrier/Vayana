package com.vayana.core.sync.asset

import com.vayana.core.backup.PortableAnnotation
import com.vayana.core.backup.PortableReadingSession
import com.vayana.core.backup.PortableSnapshot
import com.vayana.core.backup.PortableTombstone
import com.vayana.core.backup.PortableWordLookupCounter
import com.vayana.core.sync.snapshot.SnapshotSliceCache
import com.vayana.core.sync.snapshot.getLatestPortableSnapshotDocument
import com.vayana.core.sync.snapshot.pruneOlderPortableSnapshotSlices
import com.vayana.core.sync.snapshot.pushPortableReadingProgress
import com.vayana.core.sync.snapshot.putPortableSnapshotDocuments
import com.vayana.core.sync.snapshot.remotePortableSnapshotDocumentFrom
import com.vayana.core.sync.snapshot.RemotePortableSnapshotDocument
import com.vayana.core.sync.snapshot.RemotePortableSnapshotSlice
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class GitHubContentsAssetStoreTest {
    @Test
    fun fileUploadStreamsValidJsonAndCanReplayAfterConflict() = runBlocking {
        val file = java.io.File.createTempFile("asset-upload-test", ".bin")
        try {
            // Exercise every base64 padding length as well as the copy buffer boundary.
            for (size in listOf(8192, 8193, 8194)) {
                val bytes = ByteArray(size) { (it % 251).toByte() }
                file.writeBytes(bytes)
                val client = RecordingGitHubHttpClient(GitHubHttpResponse(409, byteArrayOf()), GitHubHttpResponse(201, byteArrayOf()))
                testStore(client).putFile(CloudAssetLayout.pathFor(AssetId), file)
                assertEquals(2, client.requests.size)
                for (request in client.requests) {
                    assertEquals(null, request.body)
                    val body = assertNotNull(request.streamingBody)
                    val output = java.io.ByteArrayOutputStream()
                    body.writeTo(output)
                    assertEquals(body.contentLength, output.size().toLong())
                    val json = org.json.JSONObject(output.toString("UTF-8"))
                    assertContentEquals(bytes, Base64.getDecoder().decode(json.getString("content")))
                    assertEquals("main", json.getString("branch"))
                    assertTrue(json.has("committer"))
                }
            }
        } finally {
            file.delete()
        }
    }

    @BeforeTest
    fun clearProcessCaches() {
        GitHubBlobCache.clear()
        GitHubMetadataCache.clear()
        SnapshotSliceCache.clear()
    }

    @Test
    fun concurrentSliceReadsShareOneLoad() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var loads = 0
        val first = async {
            SnapshotSliceCache.getOrLoad("scope|slice") {
                loads++
                entered.complete(Unit)
                release.await()
                "slice-json"
            }
        }
        entered.await()
        val second = async {
            SnapshotSliceCache.getOrLoad("scope|slice") { loads++; "duplicate" }
        }
        yield()
        assertEquals(1, loads)
        release.complete(Unit)
        assertEquals("slice-json", first.await())
        assertEquals("slice-json", second.await())
        assertEquals(1, loads)
    }

    @Test
    fun cancelledSliceLoaderLeavesWaitingReaderAbleToRetry() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val first = async {
            SnapshotSliceCache.getOrLoad("scope|cancelled") {
                entered.complete(Unit)
                CompletableDeferred<String>().await()
            }
        }
        entered.await()
        val second = async {
            SnapshotSliceCache.getOrLoad("scope|cancelled") { "recovered" }
        }
        yield()
        first.cancelAndJoin()
        assertEquals("recovered", second.await())
        assertEquals("recovered", SnapshotSliceCache.get("scope|cancelled"))
    }

    @Test
    fun concurrentOversizedSliceReadsStillShareTheirActiveLoad() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val largeSlice = "x".repeat(3 * 1024 * 1024 + 1)
        var loads = 0
        val first = async {
            SnapshotSliceCache.getOrLoad("scope|oversized") {
                loads++
                entered.complete(Unit)
                release.await()
                largeSlice
            }
        }
        entered.await()
        val second = async {
            SnapshotSliceCache.getOrLoad("scope|oversized") { loads++; "duplicate" }
        }
        yield()
        release.complete(Unit)
        assertTrue(first.await() === largeSlice)
        assertTrue(second.await() === largeSlice)
        assertEquals(1, loads)
        assertEquals(null, SnapshotSliceCache.get("scope|oversized"))
    }

    @Test
    fun putCreatesNewAssetAtContentsPath() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(201, """{"content":{"sha":"new-sha"}}""".toByteArray()),
        )
        val store = testStore(client)

        store.put(CloudAssetLayout.pathFor(AssetId), "ciphertext".toByteArray())

        assertEquals(listOf("PUT"), client.requests.map { it.method })
        assertEquals(
            "https://api.github.test/repos/owner/repo/contents/vayana/assets/ab/cd/$AssetId.bin",
            client.requests[0].url,
        )
        val body = client.requests[0].bodyText()
        assertTrue(body.contains(""""message":"SyncVayanaassetvayana/assets/ab/cd/$AssetId.bin""""))
        assertTrue(body.contains(""""content":"${Base64.getEncoder().encodeToString("ciphertext".toByteArray())}""""))
        assertTrue(body.contains(""""branch":"main""""))
        assertTrue(body.contains(""""name":"VayanaSync""""))
        assertTrue(body.contains(""""email":"sync@example.test""""))
        assertTrue("sha" !in body)
    }

    @Test
    fun putIncludesShaWhenReplacingExistingSyncDocument() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, """{"sha":"$ExistingSha"}""".toByteArray()),
            GitHubHttpResponse(200, """{"content":{"sha":"new-sha"}}""".toByteArray()),
        )
        val store = testStore(client)

        store.putSyncDocument("vayana/snapshot-latest.json", """{"books":[]}""".toByteArray())

        assertEquals("application/vnd.github.object+json", client.requests.first().headers["Accept"])
        assertTrue(client.requests[1].bodyText().contains(""""sha":"$ExistingSha""""))
    }

    @Test
    fun putRetriesAfterGitHubConflict() = runBlocking {
        val replacementSha = "abcdef0123456789abcdef0123456789abcdef01"
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, """{"sha":"$ExistingSha"}""".toByteArray()),
            GitHubHttpResponse(409, """{"message":"branch changed"}""".toByteArray()),
            GitHubHttpResponse(200, """{"sha":"$replacementSha"}""".toByteArray()),
            GitHubHttpResponse(200, """{"content":{"sha":"new-sha"}}""".toByteArray()),
        )
        val store = testStore(client)

        store.putSyncDocument("vayana/snapshot-latest.json", """{"books":[]}""".toByteArray())

        assertEquals(listOf("GET", "PUT", "GET", "PUT"), client.requests.map { it.method })
        assertTrue(client.requests[1].bodyText().contains(""""sha":"$ExistingSha""""))
        assertTrue(client.requests[3].bodyText().contains(""""sha":"$replacementSha""""))
    }

    @Test
    fun putTreatsEmptyRepositoryMetadataLookupAsMissingFile() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(409, """{"message":"Git Repository is empty."}""".toByteArray()),
            GitHubHttpResponse(201, """{"content":{"sha":"new-sha"}}""".toByteArray()),
        )
        val store = testStore(client)

        store.putSyncDocument("vayana/snapshot-latest.json", """{"books":[]}""".toByteArray())

        assertEquals(listOf("GET", "PUT"), client.requests.map { it.method })
        assertTrue("sha" !in client.requests[1].bodyText())
    }

    @Test
    fun getDownloadsRawAssetBytes() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, "ciphertext".toByteArray()),
        )
        val store = testStore(client)

        val bytes = store.get(CloudAssetLayout.pathFor(AssetId))

        assertContentEquals("ciphertext".toByteArray(), bytes)
        assertEquals("GET", client.requests.single().method)
        assertEquals("application/vnd.github.raw", client.requests.single().headers["Accept"])
    }

    @Test
    fun getSyncDocumentUsesTightMetadataResponseLimit() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, """{"books":[]}""".toByteArray()),
        )
        val store = testStore(client)

        val bytes = store.getSyncDocument("vayana/snapshot-latest.json")

        assertContentEquals("""{"books":[]}""".toByteArray(), bytes)
        assertEquals("application/vnd.github.raw", client.requests.single().headers["Accept"])
        assertEquals(MaxSyncDocumentBytesForTest, client.requests.single().maxResponseBytes)
    }

    @Test
    fun getSyncDocumentRetriesTransientReadFailure() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(
                statusCode = 503,
                body = """{"message":"temporarily unavailable"}""".toByteArray(),
                headers = mapOf("retry-after" to "0"),
            ),
            GitHubHttpResponse(200, """{"books":[]}""".toByteArray()),
        )
        val store = testStore(client)

        val bytes = store.getSyncDocument("vayana/snapshot-latest.json")

        assertContentEquals("""{"books":[]}""".toByteArray(), bytes)
        assertEquals(listOf("GET", "GET"), client.requests.map { it.method })
    }

    @Test
    fun getSyncDocumentWithShaUsesContentEmbeddedInTheMetadataResponse() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, embeddedObjectResponse("""{"books":[]}""")),
        )
        val store = testStore(client)

        val document = store.getSyncDocumentWithSha("vayana/snapshot-latest.json")

        assertContentEquals("""{"books":[]}""".toByteArray(), document.bytes)
        assertEquals(ExistingSha, document.sha)
        assertEquals(listOf("application/vnd.github.object+json"), client.requests.map { it.headers["Accept"] })
        assertEquals(MaxSyncDocumentJsonBytesForTest, client.requests.single().maxResponseBytes)
    }

    @Test
    fun getSyncDocumentWithShaDownloadsTheExactBlobWhenContentIsNotEmbedded() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(
                200,
                """
                {
                  "sha": "$ExistingSha",
                  "encoding": "none",
                  "content": ""
                }
                """.trimIndent().toByteArray(),
            ),
            GitHubHttpResponse(200, """{"books":[{"title":"Large"}]}""".toByteArray()),
        )
        val store = testStore(client)

        val document = store.getSyncDocumentWithSha("vayana/snapshot-latest.json")

        assertContentEquals("""{"books":[{"title":"Large"}]}""".toByteArray(), document.bytes)
        assertEquals(ExistingSha, document.sha)
        assertEquals("https://api.github.test/repos/owner/repo/git/blobs/$ExistingSha", client.requests[1].url)
        assertEquals("application/vnd.github.raw+json", client.requests[1].headers["Accept"])
        assertEquals(MaxSyncDocumentBytesForTest, client.requests[1].maxResponseBytes)
    }

    @Test
    fun unchangedSyncDocumentIsRevalidatedWithEtagAndServedFromCache() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, embeddedObjectResponse("""{"books":[]}"""), mapOf("etag" to "\"v1\"")),
            GitHubHttpResponse(304, ByteArray(0)),
        )
        val store = testStore(client)

        store.getSyncDocumentWithSha("vayana/snapshot-latest.json")
        val again = store.getSyncDocumentWithSha("vayana/snapshot-latest.json")

        assertContentEquals("""{"books":[]}""".toByteArray(), again.bytes)
        assertEquals(ExistingSha, again.sha)
        assertEquals(2, client.requests.size)
        assertEquals(null, client.requests[0].headers["If-None-Match"])
        assertEquals("\"v1\"", client.requests[1].headers["If-None-Match"])
    }

    @Test
    fun putSyncDocumentIfUnchangedUsesExpectedShaWithoutRefreshingIt() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, """{"content":{"sha":"new-sha"}}""".toByteArray()),
        )
        val store = testStore(client)

        store.putSyncDocumentIfUnchanged("vayana/snapshot-latest.json", """{"books":[]}""".toByteArray(), ExistingSha)

        assertEquals(listOf("PUT"), client.requests.map { it.method })
        assertTrue(client.requests.single().bodyText().contains(""""sha":"$ExistingSha""""))
    }

    @Test
    fun putSyncDocumentIfUnchangedDoesNotRefetchAfterConflict() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(409, """{"message":"branch changed"}""".toByteArray()),
        )
        val store = testStore(client)

        val failure = assertFailsWith<GitHubAssetStoreException> {
            store.putSyncDocumentIfUnchanged("vayana/snapshot-latest.json", """{"books":[]}""".toByteArray(), ExistingSha)
        }

        assertEquals(409, failure.statusCode)
        assertEquals(listOf("PUT"), client.requests.map { it.method })
    }

    @Test
    fun testConnectionReadsLatestSnapshotWithoutWriting() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, """{"books":[]}""".toByteArray()),
        )
        val store = testStore(client)

        val result = store.testConnection()

        assertEquals(GitHubConnectionTestResult.Connected, result)
        assertEquals(listOf("GET"), client.requests.map { it.method })
        assertEquals(
            "https://api.github.test/repos/owner/repo/contents/vayana/snapshot-latest.json?ref=main",
            client.requests.single().url,
        )
        assertEquals("application/vnd.github.raw", client.requests.single().headers["Accept"])
        assertEquals(MaxSyncDocumentBytesForTest, client.requests.single().maxResponseBytes)
    }

    @Test
    fun testConnectionTreatsMissingSnapshotAsReadyForInitialSync() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(404, ByteArray(0)),
        )
        val store = testStore(client)

        assertEquals(GitHubConnectionTestResult.ReadyForInitialSync, store.testConnection())
    }

    @Test
    fun testConnectionExposesGitHubFailureDetails() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(403, """{"message":"Resource not accessible by personal access token"}""".toByteArray()),
        )
        val store = testStore(client)

        val failure = assertFailsWith<GitHubAssetStoreException> {
            store.testConnection()
        }

        assertEquals(403, failure.statusCode)
        assertTrue(failure.responseBody.contains("Resource not accessible"))
    }

    @Test
    fun rejectsPathOutsideCloudAssetLayout() = runBlocking {
        val client = RecordingGitHubHttpClient()
        val store = testStore(client)

        val failure = assertFailsWith<IllegalArgumentException> {
            store.get("vayana/assets/not-the-layout.bin")
        }
        assertTrue(failure.message.orEmpty().startsWith("Invalid cloud asset"))
    }

    @Test
    fun rejectsUnsafeSyncDocumentPath() = runBlocking {
        val client = RecordingGitHubHttpClient()
        val store = testStore(client)

        val failure = assertFailsWith<IllegalArgumentException> {
            store.getSyncDocument("vayana/snapshots/../../settings.json")
        }

        assertEquals("Invalid sync document path", failure.message)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun acceptsVersionedSnapshotSlicePath() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, """{"annotations":[]}""".toByteArray()),
        )
        val store = testStore(client)

        val bytes = store.getSyncDocument("vayana/snapshot-slices/12345/annotations.json")

        assertContentEquals("""{"annotations":[]}""".toByteArray(), bytes)
        assertEquals(
            "https://api.github.test/repos/owner/repo/contents/vayana/snapshot-slices/12345/annotations.json?ref=main",
            client.requests.single().url,
        )
    }

    @Test
    fun remotePortableSnapshotDocumentKeepsSlicesSeparate() = runBlocking {
        val manifest = """
            {
              "books":[{"syncId":"book-a"}],
              "slices":{
                "annotations":"vayana/snapshot-slices/12345/annotations.json",
                "readingSessions":"vayana/snapshot-slices/12345/reading-sessions.json"
              }
            }
        """.trimIndent()
        val annotations = """{"annotations":[{"syncId":"note-a"}]}"""
        val readingSessions = """{"readingSessions":[{"syncId":"session-a"}]}"""
        val document = RemotePortableSnapshotDocument(
            jsonText = manifest,
            sha = ExistingSha,
            sliced = true,
            sliceJsonByKey = mapOf(
                RemotePortableSnapshotSlice.Annotations.key to annotations,
                RemotePortableSnapshotSlice.ReadingSessions.key to readingSessions,
            ),
        )

        assertEquals(true, document.sliced)
        assertEquals(manifest, document.jsonFor(RemotePortableSnapshotSlice.Books))
        assertEquals(annotations, document.jsonFor(RemotePortableSnapshotSlice.Annotations))
        assertEquals(readingSessions, document.jsonFor(RemotePortableSnapshotSlice.ReadingSessions))
    }

    @Test
    fun remotePortableSnapshotSkipsMissingSlices() = runBlocking {
        val manifest = """
            {
              "books":[{"syncId":"book-a"}],
              "slices":{
                "annotations":"vayana/snapshot-slices/12345/annotations.json",
                "readingSessions":"vayana/snapshot-slices/12345/reading-sessions.json"
              }
            }
        """.trimIndent()
        val readingSessions = """{"readingSessions":[{"syncId":"session-a"}]}"""
        val document = remotePortableSnapshotDocumentFrom(
            latestJson = manifest,
            sha = ExistingSha,
            slicePaths = mapOf(
                RemotePortableSnapshotSlice.Annotations.key to "vayana/snapshot-slices/12345/annotations.json",
                RemotePortableSnapshotSlice.ReadingSessions.key to "vayana/snapshot-slices/12345/reading-sessions.json",
            ),
            loadSlice = { path ->
                when {
                    path.endsWith("annotations.json") -> throw GitHubAssetStoreException(
                        message = "GitHub metadata download failed",
                        statusCode = 404,
                        responseBody = """{"message":"Not Found"}""",
                    )
                    path.endsWith("reading-sessions.json") -> readingSessions
                    else -> error("Unexpected slice path $path")
                }
            },
        )

        assertEquals(true, document.sliced)
        assertEquals(manifest, document.jsonFor(RemotePortableSnapshotSlice.Books))
        assertEquals(manifest, document.jsonFor(RemotePortableSnapshotSlice.Annotations))
        assertEquals(readingSessions, document.jsonFor(RemotePortableSnapshotSlice.ReadingSessions))
    }

    @Test
    fun rejectsUnsafeSnapshotSlicePath() = runBlocking {
        val client = RecordingGitHubHttpClient()
        val store = testStore(client)

        val failure = assertFailsWith<IllegalArgumentException> {
            store.getSyncDocument("vayana/snapshot-slices/12345/../annotations.json")
        }

        assertEquals("Invalid sync document path", failure.message)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun listsSnapshotSliceDirectories() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(
                200,
                """
                [
                  {"name":"12345","path":"vayana/snapshot-slices/12345","sha":"$ExistingSha","type":"dir",
                   "_links":{"self":"https://api.github.test/x","git":"https://api.github.test/g","html":"https://github.test/h"}},
                  {"name":"notes.txt","path":"vayana/snapshot-slices/notes.txt","sha":"$ExistingSha","type":"file",
                   "_links":{"self":"https://api.github.test/y","git":"https://api.github.test/g2","html":"https://github.test/h2"}}
                ]
                """.trimIndent().toByteArray(),
            ),
        )
        val store = testStore(client)

        val entries = store.listSyncDocumentDirectory("vayana/snapshot-slices")

        assertEquals(2, entries.size)
        assertEquals("12345", entries[0].name)
        assertEquals("dir", entries[0].type)
        assertEquals(ExistingSha, entries[0].sha)
        assertEquals("vayana/snapshot-slices/notes.txt", entries[1].path)
        assertEquals("file", entries[1].type)
        assertEquals(
            "https://api.github.test/repos/owner/repo/contents/vayana/snapshot-slices?ref=main",
            client.requests.single().url,
        )
    }

    @Test
    fun rejectsUnsafeSyncDocumentDirectoryPath() = runBlocking {
        val client = RecordingGitHubHttpClient()
        val store = testStore(client)

        val failure = assertFailsWith<IllegalArgumentException> {
            store.listSyncDocumentDirectory("vayana/snapshot-slices/../12345")
        }

        assertEquals("Invalid sync document directory path", failure.message)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun deletesExistingSyncDocument() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, """{"sha":"$ExistingSha"}""".toByteArray()),
            GitHubHttpResponse(200, """{"commit":{"sha":"deleted-sha"}}""".toByteArray()),
        )
        val store = testStore(client)

        store.deleteSyncDocumentIfExists("vayana/snapshot-slices/12345/annotations.json")

        assertEquals(listOf("GET", "DELETE"), client.requests.map { it.method })
        assertEquals(
            "https://api.github.test/repos/owner/repo/contents/vayana/snapshot-slices/12345/annotations.json",
            client.requests[1].url,
        )
        val body = client.requests[1].bodyText()
        assertTrue(body.contains(""""sha":"$ExistingSha""""))
        assertTrue(body.contains(""""branch":"main""""))
    }

    @Test
    fun staleSnapshotSlicePruneDeletesOnlyKnownSliceFiles() = runBlocking {
        val sliceRootListing = """
            [
              {"name":"4000","path":"vayana/snapshot-slices/4000","type":"dir","sha":"$ExistingSha"},
              {"name":"3000","path":"vayana/snapshot-slices/3000","type":"dir","sha":"$ExistingSha"},
              {"name":"2000","path":"vayana/snapshot-slices/2000","type":"dir","sha":"$ExistingSha"},
              {"name":"1000","path":"vayana/snapshot-slices/1000","type":"dir","sha":"$ExistingSha"}
            ]
        """.trimIndent().toByteArray()
        val staleDirectoryListing = """
            [
              {"name":"annotations.json","path":"vayana/snapshot-slices/1000/annotations.json","type":"file","sha":"$ExistingSha"},
              {"name":"rogue.json","path":"vayana/snapshot-slices/1000/rogue.json","type":"file","sha":"$ExistingSha"}
            ]
        """.trimIndent().toByteArray()
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, latestManifestReferencing(4000)),
            GitHubHttpResponse(200, sliceRootListing),
            GitHubHttpResponse(200, staleDirectoryListing),
            GitHubHttpResponse(200, """{"commit":{"sha":"deleted-sha"}}""".toByteArray()),
        )
        val store = testStore(client)

        val summary = store.pruneOlderPortableSnapshotSlices(currentExportedAt = 4000)

        val deleteRequests = client.requests.filter { it.method == "DELETE" }
        assertEquals(listOf("GET", "GET", "GET", "DELETE"), client.requests.map { it.method })
        assertEquals(1, summary.directoriesPruned)
        assertEquals(1, summary.filesPruned)
        assertEquals(false, summary.failed)
        assertEquals(1, deleteRequests.size)
        assertEquals(
            "https://api.github.test/repos/owner/repo/contents/vayana/snapshot-slices/1000/annotations.json",
            deleteRequests.single().url,
        )
        assertTrue(client.requests.none { it.method == "DELETE" && it.url.contains("rogue.json") })
    }

    @Test
    fun pruneKeepsSliceSetsTheLatestPointerReferencesEvenWhenTheySortOld() = runBlocking {
        // A lagging-clock device published exportedAt=500 after other devices wrote 4000/3000/2000.
        val sliceRootListing = """
            [
              {"name":"4000","path":"vayana/snapshot-slices/4000","type":"dir","sha":"$ExistingSha"},
              {"name":"3000","path":"vayana/snapshot-slices/3000","type":"dir","sha":"$ExistingSha"},
              {"name":"2000","path":"vayana/snapshot-slices/2000","type":"dir","sha":"$ExistingSha"},
              {"name":"1000","path":"vayana/snapshot-slices/1000","type":"dir","sha":"$ExistingSha"},
              {"name":"500","path":"vayana/snapshot-slices/500","type":"dir","sha":"$ExistingSha"}
            ]
        """.trimIndent().toByteArray()
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, latestManifestReferencing(500)),
            GitHubHttpResponse(200, sliceRootListing),
            GitHubHttpResponse(200, "[]".toByteArray()),
        )
        val store = testStore(client)

        val summary = store.pruneOlderPortableSnapshotSlices(currentExportedAt = 500)

        assertEquals(1, summary.directoriesPruned)
        assertTrue(client.requests.any { it.url.contains("snapshot-slices/1000?") })
        assertTrue(client.requests.none { it.url.contains("snapshot-slices/500?") })
    }

    @Test
    fun slicedProgressPushWritesNewSliceBeforeGuardedManifest() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(201, """{"content":{"sha":"slice-sha"}}""".toByteArray()),
            GitHubHttpResponse(200, """{"content":{"sha":"manifest-sha"}}""".toByteArray()),
        )
        val store = testStore(client)
        val remote = RemotePortableSnapshotDocument(
            jsonText = latestManifestReferencing(1000).toString(Charsets.UTF_8),
            sha = ExistingSha,
            sliced = true,
            sliceJsonByKey = mapOf("readingSessions" to """{"readingSessions":[]}"""),
        )

        val result = store.pushPortableReadingProgress(
            remote = remote,
            patches = emptyList(),
            exportedAt = 3000,
            readingSessions = listOf(PortableReadingSession("s-new", "book-a", 1500, 1600, 100)),
        )

        assertEquals(1, result.pushed)
        // New slice paths are create-only: no existing-SHA lookup before the write.
        assertEquals(listOf("PUT", "PUT"), client.requests.map { it.method })
        val puts = client.requests.filter { it.method == "PUT" }
        assertTrue(!puts[0].bodyText().contains(""""sha":"""))
        assertTrue(puts[0].url.endsWith("contents/vayana/snapshot-slices/3000/reading-sessions.json"))
        assertTrue(puts[1].url.endsWith("contents/vayana/snapshot-latest.json"))
        assertTrue(puts[1].bodyText().contains(""""sha":"$ExistingSha""""))
    }

    @Test
    fun slicedProgressPushPrefetchesNeededSlicesConcurrently() = runBlocking {
        val paths = mapOf(
            RemotePortableSnapshotSlice.ReadingSessions.key to "vayana/snapshot-slices/1000/reading-sessions.json",
            RemotePortableSnapshotSlice.WordLookupCounters.key to "vayana/snapshot-slices/1000/word-lookup-counters.json",
            RemotePortableSnapshotSlice.Tombstones.key to "vayana/snapshot-slices/1000/tombstones.json",
        )
        val manifest = """
            {
              "exportedAt": 1000,
              "books": [],
              "slices": {
                "readingSessions": "${paths.getValue(RemotePortableSnapshotSlice.ReadingSessions.key)}",
                "wordLookupCounters": "${paths.getValue(RemotePortableSnapshotSlice.WordLookupCounters.key)}",
                "tombstones": "${paths.getValue(RemotePortableSnapshotSlice.Tombstones.key)}"
              }
            }
        """.trimIndent()
        val startedPaths = mutableSetOf<String>()
        val allLoadsStarted = CompletableDeferred<Unit>()
        val releaseLoads = CompletableDeferred<Unit>()
        val remote = remotePortableSnapshotDocumentFrom(manifest, ExistingSha, paths) { path ->
            synchronized(startedPaths) {
                startedPaths += path
                if (startedPaths.size == paths.size) allLoadsStarted.complete(Unit)
            }
            releaseLoads.await()
            when (path) {
                paths.getValue(RemotePortableSnapshotSlice.ReadingSessions.key) -> """{"readingSessions":[]}"""
                paths.getValue(RemotePortableSnapshotSlice.WordLookupCounters.key) -> """{"wordLookupCounters":[]}"""
                else -> """{"tombstones":[]}"""
            }
        }
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(201, ByteArray(0)),
            GitHubHttpResponse(201, ByteArray(0)),
            GitHubHttpResponse(201, ByteArray(0)),
            GitHubHttpResponse(200, ByteArray(0)),
        )
        val push = async {
            testStore(client).pushPortableReadingProgress(
                remote = remote,
                patches = emptyList(),
                exportedAt = 3000,
                readingSessions = listOf(PortableReadingSession("session", "book", 1, 2, 1)),
                wordLookupCounters = listOf(PortableWordLookupCounter("word", "device", 1, 2)),
                tombstones = listOf(PortableTombstone("deleted", "book", 2)),
            )
        }

        withTimeout(1_000) { allLoadsStarted.await() }
        releaseLoads.complete(Unit)
        assertEquals(3, push.await().pushed)
        assertEquals(paths.values.toSet(), startedPaths)
    }

    @Test
    fun slicesLoadOnlyWhenAskedAndAreReusedAcrossReads() = runBlocking {
        val manifest = latestManifestReferencing(1000).toString(Charsets.UTF_8)
        val annotations = """{"annotations":[{"syncId":"note-a"}]}"""
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, embeddedObjectResponse(manifest)),
            GitHubHttpResponse(200, annotations.toByteArray()),
            GitHubHttpResponse(200, embeddedObjectResponse(manifest)),
        )
        val store = testStore(client)

        val first = store.getLatestPortableSnapshotDocument()
        assertEquals(1, client.requests.size) // manifest only; no slice downloaded yet
        assertEquals(annotations, first.jsonFor(RemotePortableSnapshotSlice.Annotations))
        assertEquals(annotations, first.jsonFor(RemotePortableSnapshotSlice.Annotations))
        assertEquals(2, client.requests.size)

        val second = store.getLatestPortableSnapshotDocument()
        assertEquals(annotations, second.jsonFor(RemotePortableSnapshotSlice.Annotations))
        assertEquals(3, client.requests.size) // slice came from the path cache
    }

    @Test
    fun fullPublishReusesUnchangedSliceInsteadOfUploadingIt() = runBlocking {
        val snapshot = PortableSnapshot(
            formatVersion = 1,
            exportedAt = 2000,
            deviceLabel = "Phone",
            books = emptyList(),
            annotations = emptyList(),
            shelves = emptyList(),
            shelfMemberships = emptyList(),
            readingSessions = emptyList(),
            vocabularyCards = emptyList(),
            wordLookupCounters = emptyList(),
            settings = emptyMap(),
        )
        val previous = RemotePortableSnapshotDocument(
            jsonText = latestManifestReferencing(1000).toString(Charsets.UTF_8),
            sha = ExistingSha,
            sliced = true,
            sliceJsonByKey = mapOf("annotations" to """{"exportedAt":1000,"deviceLabel":"Tablet","annotations":[]}"""),
            slicePaths = mapOf("annotations" to "vayana/snapshot-slices/1000/annotations.json"),
        )
        val created = GitHubHttpResponse(201, """{"content":{"sha":"x"}}""".toByteArray())
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(404, """{"message":"Not Found"}""".toByteArray()), // device snapshot SHA lookup
            created, // device snapshot
            *Array(7) { created }, // the seven slices that changed
            GitHubHttpResponse(200, """{"content":{"sha":"x"}}""".toByteArray()), // latest pointer
            GitHubHttpResponse(200, latestManifestReferencing(2000)), // prune: live pointer
            GitHubHttpResponse(200, "[]".toByteArray()), // prune: slice root listing
        )
        val store = testStore(client)

        store.putPortableSnapshotDocuments(snapshot, "vayana/snapshots/phone.json", ExistingSha, previous = previous)

        val puts = client.requests.filter { it.method == "PUT" }
        assertEquals(9, puts.size)
        assertTrue(puts.none { it.url.contains("snapshot-slices/2000/annotations.json") })
        assertTrue(puts.any { it.url.contains("snapshot-slices/2000/shelves.json") })
        val manifestBody = puts.last().body!!.toString(Charsets.UTF_8)
        val manifestJson = Regex(""""content":"([^"]+)"""").find(manifestBody)!!.groupValues[1]
        val manifest = Base64.getDecoder().decode(manifestJson).toString(Charsets.UTF_8)
        assertTrue(manifest.contains("vayana/snapshot-slices/1000/annotations.json"))
        assertTrue(manifest.contains("vayana/snapshot-slices/2000/shelves.json"))
    }

    @Test
    fun fullPublishUploadsOnlyZipForBookWithMoreThanFiftyGoodreadsQuotes() = runBlocking {
        val snapshot = PortableSnapshot(
            formatVersion = 1,
            exportedAt = 2000,
            deviceLabel = "Phone",
            books = emptyList(),
            annotations = List(51) { index ->
                PortableAnnotation(
                    syncId = "quote-$index",
                    bookSyncId = "book-a",
                    type = "POPULAR_HIGHLIGHT",
                    colorKey = "popular",
                    locator = "goodreads-quote:$index",
                    chapterTitle = null,
                    chapterHref = null,
                    selectedText = "Quote number $index",
                    readerNote = null,
                    createdAt = 1,
                    updatedAt = 1,
                    isDeleted = false,
                )
            },
            shelves = emptyList(),
            shelfMemberships = emptyList(),
            readingSessions = emptyList(),
            vocabularyCards = emptyList(),
            wordLookupCounters = emptyList(),
            settings = emptyMap(),
        )
        val created = GitHubHttpResponse(201, """{"content":{"sha":"x"}}""".toByteArray())
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(404, """{"message":"Not Found"}""".toByteArray()),
            *Array(9) { created },
            GitHubHttpResponse(200, """{"content":{"sha":"x"}}""".toByteArray()),
            GitHubHttpResponse(200, latestManifestReferencing(2000)),
            GitHubHttpResponse(200, "[]".toByteArray()),
        )
        val store = testStore(client)

        store.putPortableSnapshotDocuments(snapshot, "vayana/snapshots/phone.json", ExistingSha)

        val puts = client.requests.filter { it.method == "PUT" }
        assertTrue(puts.any { it.url.endsWith("/vayana/snapshot-slices/2000/annotations.zip") })
        assertTrue(puts.none { it.url.endsWith("/vayana/snapshot-slices/2000/annotations.json") })
        val manifest = Base64.getDecoder().decode(
            Regex(""""content":"([^"]+)"""").find(puts.last().bodyText())!!.groupValues[1],
        ).toString(Charsets.UTF_8)
        assertTrue(manifest.contains("vayana/snapshot-slices/2000/annotations.zip"))
        assertTrue(!manifest.contains("annotations.json"))
    }

    @Test
    fun exposesGitHubFailuresWithStatusAndBody() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(500, """{"message":"boom","token":"secret"}""".toByteArray()),
        )
        val store = testStore(client)

        val failure = assertFailsWith<GitHubAssetStoreException> {
            store.put(CloudAssetLayout.pathFor(AssetId), "ciphertext".toByteArray())
        }

        assertEquals(500, failure.statusCode)
        assertEquals("""{"message":"boom","token":"***"}""", failure.responseBody)
    }

    @Test
    fun rejectsMetadataWithoutValidShaBeforeReplacingSyncDocument() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, """{"sha":"not-a-git-object-sha"}""".toByteArray()),
        )
        val store = testStore(client)

        val failure = assertFailsWith<GitHubAssetStoreException> {
            store.putSyncDocument("vayana/snapshot-latest.json", """{"books":[]}""".toByteArray())
        }

        assertEquals(200, failure.statusCode)
        assertTrue(failure.message.orEmpty().contains("missing a valid SHA"))
    }

    @Test
    fun rejectsEmptyUpload() = runBlocking {
        val client = RecordingGitHubHttpClient()
        val store = testStore(client)

        val failure = assertFailsWith<IllegalArgumentException> {
            store.put(CloudAssetLayout.pathFor(AssetId), ByteArray(0))
        }

        assertEquals("Cloud asset upload is empty", failure.message)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun deleteRemovesAnAssetUsingTheShaFromItsFolderListing() = runBlocking {
        val path = CloudAssetLayout.pathFor(AssetId)
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(
                200,
                """
                    [
                      {"name":"other.bin","path":"vayana/assets/ab/cd/other.bin","type":"file","sha":"fedcba9876543210fedcba9876543210fedcba98"},
                      {"name":"$AssetId.bin","path":"$path","type":"file","sha":"$ExistingSha"}
                    ]
                """.trimIndent().toByteArray(),
            ),
            GitHubHttpResponse(200, """{"commit":{"sha":"new-sha"}}""".toByteArray()),
        )

        testStore(client).delete(path)

        assertEquals(listOf("GET", "DELETE"), client.requests.map { it.method })
        assertEquals("https://api.github.test/repos/owner/repo/contents/vayana/assets/ab/cd?ref=main", client.requests[0].url)
        assertEquals("https://api.github.test/repos/owner/repo/contents/$path", client.requests[1].url)
        assertTrue(client.requests[1].bodyText().contains(""""sha":"$ExistingSha""""))
    }

    @Test
    fun deletingAnAssetThatIsAlreadyGoneSendsNoDelete() = runBlocking {
        val missingFolder = RecordingGitHubHttpClient(GitHubHttpResponse(404, """{"message":"Not Found"}""".toByteArray()))
        testStore(missingFolder).delete(CloudAssetLayout.pathFor(AssetId))
        assertEquals(listOf("GET"), missingFolder.requests.map { it.method })

        val folderWithoutIt = RecordingGitHubHttpClient(GitHubHttpResponse(200, "[]".toByteArray()))
        testStore(folderWithoutIt).delete(CloudAssetLayout.pathFor(AssetId))
        assertEquals(listOf("GET"), folderWithoutIt.requests.map { it.method })
    }

    @Test
    fun deleteOnlyAcceptsAssetPaths() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            testStore(RecordingGitHubHttpClient()).delete("vayana/snapshot-latest.json")
        }
        Unit
    }

    private fun testStore(client: RecordingGitHubHttpClient): GitHubContentsAssetStore =
        GitHubContentsAssetStore(
            repository = GitHubRepository(
                owner = "owner",
                name = "repo",
                branch = "main",
                apiBaseUrl = "https://api.github.test",
            ),
            token = "token",
            committerName = "Vayana Sync",
            committerEmail = "sync@example.test",
            client = client,
        )
}

private class RecordingGitHubHttpClient(
    private vararg val responses: GitHubHttpResponse,
) : GitHubHttpClient {
    val requests = mutableListOf<GitHubHttpRequest>()
    private var responseIndex = 0

    override fun execute(request: GitHubHttpRequest): GitHubHttpResponse {
        requests += request
        return responses.getOrNull(responseIndex++)
            ?: error("No response queued for request ${request.method} ${request.url}")
    }
}

private fun embeddedObjectResponse(content: String): ByteArray =
    """
        {
          "sha": "$ExistingSha",
          "encoding": "base64",
          "content": "${Base64.getMimeEncoder().encodeToString(content.toByteArray()).replace("\r\n", "\\n")}"
        }
    """.trimIndent().toByteArray()

private fun latestManifestReferencing(exportedAt: Long): ByteArray =
    """
        {
          "exportedAt": $exportedAt,
          "books": [],
          "slices": {
            "annotations": "vayana/snapshot-slices/$exportedAt/annotations.json",
            "tombstones": "vayana/snapshot-slices/$exportedAt/tombstones.json"
          }
        }
    """.trimIndent().toByteArray()

private fun GitHubHttpRequest.bodyText(): String {
    val bytes = assertNotNull(body)
    return bytes.toString(Charsets.UTF_8).replace(Regex("\\s+"), "")
}

private const val AssetId = "abcdEFGH1234_wxyz"
private const val ExistingSha = "0123456789abcdef0123456789abcdef01234567"
private const val MaxSyncDocumentBytesForTest = 16 * 1024 * 1024
private const val MaxSyncDocumentJsonBytesForTest = MaxSyncDocumentBytesForTest * 2
