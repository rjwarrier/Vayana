package com.vayana.core.sync.asset

import com.vayana.core.backup.PortableAnnotation
import com.vayana.core.backup.PortableAppBuild
import com.vayana.core.backup.PortableReadingSession
import com.vayana.core.backup.PortableSnapshot
import com.vayana.core.backup.PortableSnapshotFormat
import com.vayana.core.backup.PortableTombstone
import com.vayana.core.backup.PortableWordLookupCounter
import com.vayana.core.backup.portableSnapshotSliceDataHash
import com.vayana.core.backup.repointPortableSnapshotSlices
import com.vayana.core.backup.toSlicedJsonDocuments
import com.vayana.core.sync.snapshot.SnapshotSliceCache
import com.vayana.core.sync.snapshot.getLatestPortableSnapshotDocument
import com.vayana.core.sync.snapshot.staleSnapshotSliceFiles
import com.vayana.core.sync.snapshot.pushPortableReadingProgress
import com.vayana.core.sync.snapshot.putPortableSnapshotDocuments
import com.vayana.core.sync.snapshot.remotePortableSnapshotDocumentFrom
import com.vayana.core.sync.snapshot.RemotePortableSnapshotDocument
import com.vayana.core.sync.snapshot.RemotePortableSnapshotSlice
import com.vayana.core.sync.snapshot.SyncedSnapshotSlice
import java.util.Base64
import kotlin.test.AfterTest
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
    @Test fun recognizedTextDirectoryCanBeReadOnceAndBlobsReadByTheirKnownSha() = runBlocking {
        val path = "vayana/recognized-text/${"b".repeat(64)}.json"
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, folderListing(path to ExistingSha)),
            GitHubHttpResponse(200, "{\"version\":1}".toByteArray()),
        )
        val store = testStore(client)
        val references = store.listSyncDocumentShas("vayana/recognized-text")
        assertEquals(ExistingSha, references[path])
        assertEquals("{\"version\":1}", store.getSyncDocumentWithKnownSha(path, references.getValue(path)).bytes.toString(Charsets.UTF_8))
        assertEquals(1, client.requests.count { it.url.contains("/contents/") })
    }

    // These script the plain format's requests (inline JSON slices); compact-format tests switch it back on themselves.
    @BeforeTest
    fun usePlainSnapshotFormat() {
        PortableSnapshotFormat.compact = false
    }

    @AfterTest
    fun restoreSnapshotFormat() {
        PortableSnapshotFormat.compact = true
    }

    @Test
    fun compactPublishZipsEverySliceAndMovesBooksOutOfTheManifest() = runBlocking {
        PortableSnapshotFormat.compact = true
        // One blob per distinct zipped slice (zips are reproducible, so the two empty annotation slices share one),
        // plus the manifest, which is written to both the latest pointer and this device's snapshot.
        val distinctZips = emptySnapshot(2000).toSlicedJsonDocuments()
            .filter { it.sliceKey != null }
            .map { com.vayana.core.sync.snapshot.zipSnapshotJson(it.jsonText).toList() }
            .distinct().size
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, "[]".toByteArray()), // prune: no slices folder yet
            *singleCommitResponses(blobs = distinctZips + 1),
        )

        testStore(client).putPortableSnapshotDocuments(emptySnapshot(2000), "vayana/snapshots/phone.json", ExistingSha)

        val tree = client.requests.single { it.url.endsWith("/git/trees") }.bodyText()
        listOf("books", "reading-sessions", "annotations", "community-quotes", "tombstones").forEach { name ->
            assertTrue(tree.contains(""""path":"vayana/snapshot-slices/2000/$name.zip","mode":"100644","type":"blob","sha":"$BlobSha""""), name)
        }
        assertTrue(!tree.contains("reading-sessions.json"))
        assertTrue(tree.contains("books.zip"))
        // The manifest travels inline as an escaped JSON string.
        assertTrue(!tree.contains("\\\"books\\\":["), "the manifest no longer carries the books")
    }

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
        LastSyncCommits.clear()
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
    fun getSyncDocumentWithShaReadsTheBlobIdFromTheFolderListing() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, folderListing("vayana/snapshot-latest.json" to ExistingSha)),
            GitHubHttpResponse(200, """{"books":[]}""".toByteArray()),
        )
        val store = testStore(client)

        val document = store.getSyncDocumentWithSha("vayana/snapshot-latest.json")

        assertContentEquals("""{"books":[]}""".toByteArray(), document.bytes)
        assertEquals(ExistingSha, document.sha)
        assertEquals("https://api.github.test/repos/owner/repo/contents/vayana?ref=main", client.requests[0].url)
        assertEquals("https://api.github.test/repos/owner/repo/git/blobs/$ExistingSha", client.requests[1].url)
        assertEquals("application/vnd.github.raw+json", client.requests[1].headers["Accept"])
        assertEquals(MaxSyncDocumentBytesForTest, client.requests[1].maxResponseBytes)
    }

    @Test
    fun ownWriteSeedsTheBlobCacheSoTheNextPullDownloadsNothing() = runBlocking {
        val manifest = """{"books":[{"title":"Mine"}]}""".toByteArray()
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", gitBlobSha("hello\n".toByteArray()))
        val client = RecordingGitHubHttpClient(
            *singleCommitResponses(),
            GitHubHttpResponse(200, folderListing("vayana/snapshot-latest.json" to gitBlobSha(manifest))),
        )
        val store = testStore(client)
        store.commitSyncDocuments(
            listOf(SyncDocumentChange("vayana/snapshot-latest.json", manifest)),
            "Sync",
            "vayana/snapshot-latest.json",
            ExistingSha,
        )

        val document = store.getSyncDocumentWithSha("vayana/snapshot-latest.json")

        assertContentEquals(manifest, document.bytes)
        assertTrue(client.requests.none { it.url.contains("/git/blobs/") })
    }

    @Test
    fun unchangedSyncDocumentIsRevalidatedWithEtagAndServedFromCache() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, folderListing("vayana/snapshot-latest.json" to ExistingSha), mapOf("etag" to "\"v1\"")),
            GitHubHttpResponse(200, """{"books":[]}""".toByteArray()),
            GitHubHttpResponse(304, ByteArray(0)),
        )
        val store = testStore(client)

        store.getSyncDocumentWithSha("vayana/snapshot-latest.json")
        val again = store.getSyncDocumentWithSha("vayana/snapshot-latest.json")

        assertContentEquals("""{"books":[]}""".toByteArray(), again.bytes)
        assertEquals(ExistingSha, again.sha)
        assertEquals(3, client.requests.size)
        assertEquals(null, client.requests[0].headers["If-None-Match"])
        assertEquals("\"v1\"", client.requests[2].headers["If-None-Match"])
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
    fun remotePortableSnapshotFailsWhenAReferencedSliceIsMissing() = runBlocking {
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
        val failure = assertNotNull(document.prefetch())
        assertEquals(404, (failure as GitHubAssetStoreException).statusCode)
        assertFailsWith<GitHubAssetStoreException> {
            document.jsonFor(RemotePortableSnapshotSlice.Annotations)
        }
        assertEquals(readingSessions, document.jsonFor(RemotePortableSnapshotSlice.ReadingSessions))
    }

    @Test
    fun optionalSliceAbsentFromManifestStillFallsBackToManifest() = runBlocking {
        val manifest = """{"books":[],"annotations":[]}"""
        val document = remotePortableSnapshotDocumentFrom(manifest, ExistingSha, emptyMap()) {
            error("An absent optional slice must not be downloaded")
        }

        assertEquals(null, document.prefetch())
        assertEquals(manifest, document.jsonFor(RemotePortableSnapshotSlice.CommunityQuotes))
    }

    @Test
    fun progressPushDoesNotWriteWhenAReferencedSessionSliceIsMissing() = runBlocking {
        val path = "vayana/snapshot-slices/1000/reading-sessions.json"
        val manifest = """{"books":[],"slices":{"readingSessions":"$path"}}"""
        val document = remotePortableSnapshotDocumentFrom(manifest, ExistingSha, mapOf("readingSessions" to path)) {
            throw GitHubAssetStoreException("Missing session slice", 404, "Not Found")
        }
        val client = RecordingGitHubHttpClient()

        assertFailsWith<GitHubAssetStoreException> {
            testStore(client).pushPortableReadingProgress(
                remote = document,
                patches = emptyList(),
                exportedAt = 2000,
                readingSessions = listOf(PortableReadingSession("session-1", "book-1", 1, 61, 60)),
            )
        }
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun anyZippedSliceIsReadableButAZipOutsideTheSliceFolderIsNot() = runBlocking {
        val store = testStore(RecordingGitHubHttpClient())

        listOf("vayana/snapshots/pixel.zip", "vayana/snapshot-slices/12345/Books.zip").forEach { path ->
            val failure = assertFailsWith<IllegalArgumentException> { store.getSyncDocument(path) }
            assertEquals("Invalid sync document path", failure.message)
        }
        listOf("vayana/snapshot-slices/12345/reading-sessions.zip", "vayana/snapshot-slices/12345/books.zip").forEach { path ->
            // Passes validation; the recording client then has no response for it.
            val failure = runCatching { store.getSyncDocument(path) }.exceptionOrNull()
            assertTrue(failure?.message != "Invalid sync document path", path)
        }
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
    fun listsTheSlicesTreeFromItsParentListingWithNestedLinks() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(
                200,
                """
                [
                  {"name":"snapshot-slices","path":"vayana/snapshot-slices","sha":"$TreeSha","type":"dir",
                   "_links":{"self":"https://api.github.test/x","git":"https://api.github.test/g","html":"https://github.test/h"}},
                  {"name":"snapshot-latest.json","path":"vayana/snapshot-latest.json","sha":"$ExistingSha","type":"file",
                   "_links":{"self":"https://api.github.test/y","git":"https://api.github.test/g2","html":"https://github.test/h2"}}
                ]
                """.trimIndent().toByteArray(),
            ),
            GitHubHttpResponse(200, sliceTree("12345" to "tree", "12345/notes.txt" to "blob")),
        )

        val entries = testStore(client).listSyncDocumentTree("vayana/snapshot-slices")

        assertEquals(listOf("vayana/snapshot-slices/12345", "vayana/snapshot-slices/12345/notes.txt"), entries.map { it.path })
        assertEquals(listOf("dir", "file"), entries.map { it.type })
        assertEquals("https://api.github.test/repos/owner/repo/contents/vayana?ref=main", client.requests[0].url)
    }

    @Test
    fun rejectsUnsafeSyncDocumentDirectoryPath() = runBlocking {
        val client = RecordingGitHubHttpClient()
        val store = testStore(client)

        val failure = assertFailsWith<IllegalArgumentException> {
            store.listSyncDocumentTree("vayana/snapshot-slices/../12345")
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
    fun staleSnapshotSliceFilesAreOnlyKnownSliceFilesInOldDirectories() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, slicesFolderListing()),
            GitHubHttpResponse(
                200,
                sliceTree(
                    "4000" to "tree", "3000" to "tree", "2000" to "tree", "1000" to "tree",
                    "4000/annotations.json" to "blob",
                    "1000/annotations.json" to "blob",
                    "1000/community-quotes.zip" to "blob",
                    "1000/rogue.json" to "blob",
                ),
            ),
        )

        val stale = testStore(client).staleSnapshotSliceFiles(currentExportedAt = 4000, referencedExportTimes = setOf(4000))

        assertEquals(
            listOf("vayana/snapshot-slices/1000/annotations.json", "vayana/snapshot-slices/1000/community-quotes.zip"),
            stale,
        )
        assertEquals(2, client.requests.size) // parent listing + one recursive tree, however many folders
        assertEquals("https://api.github.test/repos/owner/repo/git/trees/$TreeSha?recursive=1", client.requests[1].url)
    }

    @Test
    fun pruneKeepsSliceSetsThePublishedSnapshotReferencesEvenWhenTheySortOld() = runBlocking {
        // A lagging-clock device publishes exportedAt=500 after other devices wrote 4000/3000/2000.
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, slicesFolderListing()),
            GitHubHttpResponse(
                200,
                sliceTree(
                    "4000" to "tree", "3000" to "tree", "2000" to "tree", "1000" to "tree", "500" to "tree",
                    "1000/tombstones.json" to "blob", "500/tombstones.json" to "blob",
                ),
            ),
        )

        val stale = testStore(client).staleSnapshotSliceFiles(currentExportedAt = 500, referencedExportTimes = setOf(500))

        assertEquals(listOf("vayana/snapshot-slices/1000/tombstones.json"), stale)
    }

    @Test
    fun slicedProgressPushCommitsNewSliceAndGuardedManifestTogether() = runBlocking {
        val client = RecordingGitHubHttpClient(*singleCommitResponses())
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
        assertEquals(listOf("GET", "GET", "POST", "POST", "PATCH"), client.requests.map { it.method })
        val tree = client.requests.single { it.url.endsWith("/git/trees") }.bodyText()
        assertTrue(tree.contains(""""path":"vayana/snapshot-slices/3000/reading-sessions.json""""))
        assertTrue(tree.contains(""""path":"vayana/snapshot-latest.json""""))
    }

    @Test
    fun progressPushLeavesOutSlicesThisDeviceAlreadyHolds() = runBlocking {
        val sessionsPath = "vayana/snapshot-slices/1000/reading-sessions.json"
        val remote = RemotePortableSnapshotDocument(
            jsonText = latestManifestReferencing(1000).toString(Charsets.UTF_8),
            sha = ExistingSha,
            sliced = true,
            slicePaths = mapOf("readingSessions" to sessionsPath),
            loadSlice = { error("a slice this device already holds must not be downloaded") },
        )
        remote.skipAlreadySynced(listOf(SyncedSnapshotSlice("readingSessions", sessionsPath, "hash")))
        val client = RecordingGitHubHttpClient() // nothing to push: no request at all

        val result = testStore(client).pushPortableReadingProgress(
            remote = remote,
            patches = emptyList(),
            exportedAt = 3000,
            readingSessions = listOf(PortableReadingSession("s-held", "book-a", 1500, 1600, 100)),
            locallyUnchangedSliceKeys = setOf("readingSessions"),
        )

        assertEquals(0, result.pushed)
        assertTrue(client.requests.isEmpty())
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
        val client = RecordingGitHubHttpClient(*singleCommitResponses())
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
            GitHubHttpResponse(200, folderListing("vayana/snapshot-latest.json" to ExistingSha)),
            GitHubHttpResponse(200, manifest.toByteArray()),
            GitHubHttpResponse(200, annotations.toByteArray()),
            GitHubHttpResponse(200, folderListing("vayana/snapshot-latest.json" to ExistingSha)),
        )
        val store = testStore(client)

        val first = store.getLatestPortableSnapshotDocument()
        assertEquals(2, client.requests.size) // listing + manifest; no slice downloaded yet
        assertEquals(annotations, first.jsonFor(RemotePortableSnapshotSlice.Annotations))
        assertEquals(annotations, first.jsonFor(RemotePortableSnapshotSlice.Annotations))
        assertEquals(3, client.requests.size)

        val second = store.getLatestPortableSnapshotDocument()
        assertEquals(annotations, second.jsonFor(RemotePortableSnapshotSlice.Annotations))
        assertEquals(4, client.requests.size) // manifest from the blob cache, slice from the path cache
    }

    @Test
    fun fullPublishReusesUnchangedSliceAndWritesEverythingElseInOneCommit() = runBlocking {
        val previous = RemotePortableSnapshotDocument(
            jsonText = latestManifestReferencing(1000).toString(Charsets.UTF_8),
            sha = ExistingSha,
            sliced = true,
            sliceJsonByKey = mapOf("annotations" to """{"exportedAt":1000,"deviceLabel":"Tablet","annotations":[]}"""),
            slicePaths = mapOf("annotations" to "vayana/snapshot-slices/1000/annotations.json"),
        )
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, "[]".toByteArray()), // prune: no slices folder yet
            *singleCommitResponses(blobs = 2), // the zipped community quotes, the manifest shared by two paths
        )

        testStore(client).putPortableSnapshotDocuments(emptySnapshot(2000), "vayana/snapshots/phone.json", ExistingSha, previous = previous)

        assertEquals(listOf("GET", "GET", "GET", "POST", "POST", "POST", "POST", "PATCH"), client.requests.map { it.method })
        assertTrue(client.requests.none { it.method == "PUT" || it.method == "DELETE" })
        val tree = client.requests.single { it.url.endsWith("/git/trees") }.bodyText()
        assertTrue(!tree.contains(""""path":"vayana/snapshot-slices/2000/annotations.json""""))
        assertTrue(tree.contains(""""path":"vayana/snapshot-slices/2000/shelves.json""""))
        assertTrue(tree.contains(""""path":"vayana/snapshots/phone.json""""))
        assertTrue(tree.contains(""""path":"vayana/snapshot-latest.json""""))
        assertTrue(blobTexts(client).any { it.contains("vayana/snapshot-slices/1000/annotations.json") }) // the manifest keeps pointing there
        assertEquals(2, Regex(""""sha":"$BlobSha"""").findAll(tree).count { true } - 1) // manifest blob at two paths, zip at one
        assertTrue(tree.contains(""""base_tree":"$TreeSha""""))
        val commit = client.requests.single { it.url.endsWith("/git/commits") }.bodyText()
        assertTrue(commit.contains(""""parents":["$HeadSha"]"""))
        val update = client.requests.single { it.method == "PATCH" }
        assertTrue(update.url.endsWith("/git/refs/heads/main"))
        assertTrue(update.bodyText().contains(""""force":false"""))
    }

    @Test
    fun alreadySyncedSliceIsNeitherDownloadedNorReuploaded() = runBlocking {
        val annotationsPath = "vayana/snapshot-slices/1000/annotations.json"
        val previous = RemotePortableSnapshotDocument(
            jsonText = latestManifestReferencing(1000).toString(Charsets.UTF_8),
            sha = ExistingSha,
            sliced = true,
            slicePaths = mapOf("annotations" to annotationsPath),
            loadSlice = { error("an already synced slice must not be downloaded") },
        )
        val emptyAnnotationsHash = portableSnapshotSliceDataHash("annotations", """{"annotations":[]}""")!!
        previous.skipAlreadySynced(
            listOf(
                SyncedSnapshotSlice("annotations", annotationsPath, emptyAnnotationsHash),
                SyncedSnapshotSlice("shelves", "vayana/snapshot-slices/999/shelves.json", "stale path, ignored"),
            ),
        )
        previous.prefetch()
        assertEquals(previous.jsonText, previous.jsonFor(RemotePortableSnapshotSlice.Annotations))
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, "[]".toByteArray()), // prune: no slices folder yet
            *singleCommitResponses(blobs = 2),
        )

        val published = testStore(client)
            .putPortableSnapshotDocuments(emptySnapshot(2000), "vayana/snapshots/phone.json", ExistingSha, previous = previous)

        val tree = client.requests.single { it.url.endsWith("/git/trees") }.bodyText()
        assertTrue(!tree.contains(""""path":"vayana/snapshot-slices/2000/annotations.json""""))
        assertEquals(SyncedSnapshotSlice("annotations", annotationsPath, emptyAnnotationsHash), published.single { it.key == "annotations" })
        assertEquals("vayana/snapshot-slices/2000/shelves.json", published.single { it.key == "shelves" }.path)
        assertEquals("vayana/snapshot-slices/2000/community-quotes.zip", published.single { it.key == "communityQuotes" }.path)
    }

    @Test
    fun sliceLeftOutOfTheExportKeepsItsPublishedFile() = runBlocking {
        val quotesPath = "vayana/snapshot-slices/1000/community-quotes.zip"
        val previous = RemotePortableSnapshotDocument(
            jsonText = latestManifestReferencing(1000).toString(Charsets.UTF_8),
            sha = ExistingSha,
            sliced = true,
            slicePaths = mapOf("communityQuotes" to quotesPath),
            loadSlice = { error("an unchanged slice must not be downloaded") },
        )
        previous.skipAlreadySynced(listOf(SyncedSnapshotSlice("communityQuotes", quotesPath, "quotes-hash")))
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, "[]".toByteArray()), // prune: no slices folder yet
            *singleCommitResponses(blobs = 1), // the manifest, shared by two paths; no quotes zip
        )

        val published = testStore(client).putPortableSnapshotDocuments(
            emptySnapshot(2000).copy(unchangedSliceKeys = setOf("communityQuotes")),
            "vayana/snapshots/phone.json",
            ExistingSha,
            previous = previous,
        )

        assertEquals(SyncedSnapshotSlice("communityQuotes", quotesPath, "quotes-hash"), published.single { it.key == "communityQuotes" })
        assertTrue(!client.requests.single { it.url.endsWith("/git/trees") }.bodyText().contains("community-quotes"))
        assertTrue(blobTexts(client).single().contains(quotesPath))
    }

    @Test
    fun unchangedSnapshotIsNotPublishedAgain() = runBlocking {
        val earlier = emptySnapshot(2000)
        val slices = earlier.toSlicedJsonDocuments().filter { it.sliceKey != null }
        val pathsByKey = slices.associate { document ->
            val key = document.sliceKey!!
            key to if (key == "communityQuotes") document.path.removeSuffix(".json") + ".zip" else document.path
        }
        val previous = RemotePortableSnapshotDocument(
            jsonText = repointPortableSnapshotSlices(earlier.toSlicedJsonDocuments().last().jsonText, pathsByKey),
            sha = ExistingSha,
            sliced = true,
            slicePaths = pathsByKey,
            loadSlice = { error("an already synced slice must not be downloaded") },
        )
        val synced = slices.map { SyncedSnapshotSlice(it.sliceKey!!, pathsByKey.getValue(it.sliceKey!!), portableSnapshotSliceDataHash(it.sliceKey!!, it.jsonText)!!) }
        previous.skipAlreadySynced(synced)
        val client = RecordingGitHubHttpClient() // any request fails the test

        val published = testStore(client).putPortableSnapshotDocuments(
            earlier.copy(exportedAt = 3000, deviceLabel = "Phone"),
            "vayana/snapshots/phone.json",
            ExistingSha,
            previous = previous,
        )

        assertTrue(client.requests.isEmpty())
        assertEquals(synced.toSet(), published.toSet())
    }

    @Test
    fun unchangedSnapshotIsRepublishedOnceToStampANewerAppBuild() = runBlocking {
        val older = PortableAppBuild("0.90", 3, "3.20261013.2200", builtAt = 1_000)
        val newer = older.copy(buildNumber = "3.20261013.2300", builtAt = 2_000)
        val earlier = emptySnapshot(2000).copy(appBuild = older)
        val slices = earlier.toSlicedJsonDocuments().filter { it.sliceKey != null }
        val pathsByKey = slices.associate { document ->
            val key = document.sliceKey!!
            key to if (key == "communityQuotes") document.path.removeSuffix(".json") + ".zip" else document.path
        }
        fun previous() = RemotePortableSnapshotDocument(
            jsonText = repointPortableSnapshotSlices(earlier.toSlicedJsonDocuments().last().jsonText, pathsByKey),
            sha = ExistingSha,
            sliced = true,
            slicePaths = pathsByKey,
            loadSlice = { error("an already synced slice must not be downloaded") },
        ).also { document ->
            document.skipAlreadySynced(slices.map { SyncedSnapshotSlice(it.sliceKey!!, pathsByKey.getValue(it.sliceKey!!), it.dataHash!!) })
        }

        val sameBuild = RecordingGitHubHttpClient()
        testStore(sameBuild).putPortableSnapshotDocuments(earlier.copy(exportedAt = 3000), "vayana/snapshots/phone.json", ExistingSha, previous = previous())
        assertTrue(sameBuild.requests.isEmpty())

        val newerBuild = RecordingGitHubHttpClient(*singleCommitResponses(blobs = 1))
        testStore(newerBuild).putPortableSnapshotDocuments(
            earlier.copy(exportedAt = 3000, appBuild = newer),
            "vayana/snapshots/phone.json",
            ExistingSha,
            previous = previous(),
        )
        assertTrue(blobTexts(newerBuild).any { it.contains("3.20261013.2300") })
    }

    @Test
    fun communityQuotesPublishZippedApartFromPersonalAnnotations() = runBlocking {
        val note = PortableAnnotation(
            syncId = "note-1", bookSyncId = "book-a", type = "HIGHLIGHT", colorKey = "yellow", locator = "epubcfi(/6/2)",
            chapterTitle = null, chapterHref = null, selectedText = "Mine", readerNote = null,
            createdAt = 1, updatedAt = 1, isDeleted = false,
        )
        val quote = note.copy(syncId = "quote-1", type = "UNDERLINE", colorKey = "popular", locator = "goodreads-quote:1", selectedText = "Quoted")
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, "[]".toByteArray()), // prune: no slices folder yet
            *singleCommitResponses(blobs = 2),
        )

        testStore(client).putPortableSnapshotDocuments(
            emptySnapshot(2000).copy(annotations = listOf(note, quote)),
            "vayana/snapshots/phone.json",
            ExistingSha,
        )

        assertTrue(client.requests.filter { it.url.endsWith("/git/blobs") }.all { it.bodyText().contains(""""encoding":"base64"""") })
        val tree = client.requests.single { it.url.endsWith("/git/trees") }.bodyText()
        assertTrue(tree.contains(""""path":"vayana/snapshot-slices/2000/community-quotes.zip","mode":"100644","type":"blob","sha":"$BlobSha""""))
        assertTrue(!tree.contains("community-quotes.json"))
        assertTrue(tree.contains("note-1"))
        assertTrue(!tree.contains("quote-1")) // only inside the zip
    }

    @Test
    fun oversizedCommunityQuotesFailBeforeAnyPublishRequest() = runBlocking {
        val quote = PortableAnnotation(
            syncId = "quote-1", bookSyncId = "book-a", type = "UNDERLINE", colorKey = "popular",
            locator = "goodreads-quote:1", chapterTitle = null, chapterHref = null,
            selectedText = "a".repeat(16_000), readerNote = null, createdAt = 1, updatedAt = 1, isDeleted = false,
        )
        val snapshot = emptySnapshot(2000).copy(annotations = (1..1100).map { quote.copy(syncId = "quote-$it") })
        val client = RecordingGitHubHttpClient()

        assertFailsWith<IllegalArgumentException> {
            testStore(client).putPortableSnapshotDocuments(snapshot, "vayana/snapshots/test.json", null)
        }
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun repositoryVisibilityComesFromTheRepositoryNotItsOwner() = runBlocking {
        val body = """{"id":1,"name":"vayana-sync","private":true,"owner":{"login":"me","site_admin":false,"private":false}}"""
        assertEquals(true, testStore(RecordingGitHubHttpClient(GitHubHttpResponse(200, body.toByteArray()))).repositoryIsPrivate())
        val public = body.replace("\"private\":true", "\"private\":false")
        assertEquals(false, testStore(RecordingGitHubHttpClient(GitHubHttpResponse(200, public.toByteArray()))).repositoryIsPrivate())
        assertEquals(null, testStore(RecordingGitHubHttpClient(GitHubHttpResponse(404, "{}".toByteArray()))).repositoryIsPrivate())
    }

    @Test
    fun transferStatsCountEveryRequestAndItsPayload() = runBlocking {
        val client = RecordingGitHubHttpClient(*singleCommitResponses())
        val store = testStore(client)

        store.commitSyncDocuments(
            changes = listOf(SyncDocumentChange("vayana/snapshot-latest.json", "{}".toByteArray())),
            message = "Sync",
            guardPath = "vayana/snapshot-latest.json",
            guardSha = ExistingSha,
        )

        val stats = store.transferStats()
        assertEquals(client.requests.size, stats.requests)
        assertEquals(client.requests.sumOf { it.body?.size ?: 0 }.toLong(), stats.upBytes)
        assertTrue(stats.downBytes > 0)
        assertEquals("githubRequests=${stats.requests}, downBytes=${stats.downBytes}, upBytes=${stats.upBytes}", store.requestStats())
    }

    @Test
    fun singleCommitDeletesStaleFilesInTheSameCommit() = runBlocking {
        val client = RecordingGitHubHttpClient(*singleCommitResponses())

        val committed = testStore(client).commitSyncDocuments(
            changes = listOf(
                SyncDocumentChange("vayana/snapshot-latest.json", "{}".toByteArray()),
                SyncDocumentChange("vayana/snapshot-slices/1000/annotations.json", null),
            ),
            message = "Sync",
            guardPath = "vayana/snapshot-latest.json",
            guardSha = ExistingSha,
        )

        assertTrue(committed)
        val tree = client.requests.single { it.url.endsWith("/git/trees") }.bodyText()
        assertTrue(tree.contains(""""path":"vayana/snapshot-slices/1000/annotations.json","mode":"100644","type":"blob","sha":null"""))
        assertTrue(tree.contains(""""path":"vayana/snapshot-latest.json","mode":"100644","type":"blob","content":"{}""""))
    }

    @Test
    fun nextCommitBuildsOnThisProcessesOwnLastCommitWithoutReReadingTheBranch() = runBlocking {
        val first = "{\"v\":1}".toByteArray()
        val second = "{\"v\":2}".toByteArray()
        val client = RecordingGitHubHttpClient(
            *singleCommitResponses(),
            GitHubHttpResponse(201, """{"sha":"${"c".repeat(40)}"}""".toByteArray()), // tree
            GitHubHttpResponse(201, """{"sha":"${"d".repeat(40)}"}""".toByteArray()), // commit
            GitHubHttpResponse(422, """{"message":"Update is not a fast forward"}""".toByteArray()),
            *singleCommitResponses(),
        )
        val store = testStore(client)
        fun write(bytes: ByteArray) = listOf(SyncDocumentChange("vayana/snapshot-latest.json", bytes))

        store.commitSyncDocuments(write(first), "Sync", "vayana/snapshot-latest.json", ExistingSha)
        val afterFirst = client.requests.size
        // Another device committed in between: the fast-forward fails, as it would after a branch read.
        val moved = assertFailsWith<GitHubAssetStoreException> {
            store.commitSyncDocuments(write(second), "Sync", "vayana/snapshot-latest.json", gitBlobSha(first))
        }
        assertEquals(409, moved.statusCode)
        val ownBase = client.requests.drop(afterFirst)
        assertEquals(listOf("POST", "POST", "PATCH"), ownBase.map { it.method }) // no branch or guard read
        assertTrue(ownBase[0].bodyText().contains(""""base_tree":"${"a".repeat(40)}"""")) // the first commit's tree
        assertTrue(ownBase[1].bodyText().contains(""""parents":["${"b".repeat(40)}"]""")) // on top of the first commit

        // The failure forgets the shortcut: the retry reads the branch again.
        store.commitSyncDocuments(write(second), "Sync", "vayana/snapshot-latest.json", ExistingSha)
        assertTrue(client.requests.drop(afterFirst + 3).first().url.contains("/branches/main"))
    }

    @Test
    fun anAssetUploadInBetweenSendsTheNextCommitBackToReadingTheBranch() = runBlocking {
        val manifest = "{\"v\":1}".toByteArray()
        val client = RecordingGitHubHttpClient(
            *singleCommitResponses(),
            GitHubHttpResponse(201, """{"content":{"sha":"x"}}""".toByteArray()), // cover upload: its own commit
            *singleCommitResponses().also { it[1] = GitHubHttpResponse(200, folderListing("vayana/snapshot-latest.json" to gitBlobSha(manifest))) },
        )
        val store = testStore(client)
        val write = listOf(SyncDocumentChange("vayana/snapshot-latest.json", manifest))

        store.commitSyncDocuments(write, "Sync", "vayana/snapshot-latest.json", ExistingSha)
        store.put(CloudAssetLayout.pathFor(AssetId), "ciphertext".toByteArray())
        val afterUpload = client.requests.size
        store.commitSyncDocuments(write, "Sync", "vayana/snapshot-latest.json", gitBlobSha(manifest))

        assertTrue(client.requests[afterUpload].url.contains("/branches/main"))
    }

    @Test
    fun singleCommitReportsAConflictWhenTheBranchMovedOrTheGuardChanged() = runBlocking {
        val change = listOf(SyncDocumentChange("vayana/snapshot-latest.json", "{}".toByteArray()))
        val branchMoved = RecordingGitHubHttpClient(
            *singleCommitResponses().dropLast(1).toTypedArray(),
            GitHubHttpResponse(422, """{"message":"Update is not a fast forward"}""".toByteArray()),
        )
        val moved = assertFailsWith<GitHubAssetStoreException> {
            testStore(branchMoved).commitSyncDocuments(change, "Sync", "vayana/snapshot-latest.json", ExistingSha)
        }
        assertEquals(409, moved.statusCode)

        val guardChanged = RecordingGitHubHttpClient(
            singleCommitResponses()[0],
            GitHubHttpResponse(200, folderListing("vayana/snapshot-latest.json" to "f".repeat(40))),
        )
        val changed = assertFailsWith<GitHubAssetStoreException> {
            testStore(guardChanged).commitSyncDocuments(change, "Sync", "vayana/snapshot-latest.json", ExistingSha)
        }
        assertEquals(409, changed.statusCode)
        assertTrue(guardChanged.requests.none { it.method == "POST" || it.method == "PATCH" })
    }

    @Test
    fun emptyRepositoryFallsBackToPerFileWrites() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(409, """{"message":"Git Repository is empty."}""".toByteArray()),
        )

        val committed = testStore(client).commitSyncDocuments(
            listOf(SyncDocumentChange("vayana/snapshot-latest.json", "{}".toByteArray())),
            "Sync",
            "vayana/snapshot-latest.json",
            null,
        )

        assertEquals(false, committed)
        assertEquals(1, client.requests.size)
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

/** The decoded contents of every blob a client uploaded. */
private fun blobTexts(client: RecordingGitHubHttpClient): List<String> =
    client.requests.filter { it.url.endsWith("/git/blobs") }.map { request ->
        val content = Regex(""""content":"([^"]+)"""").find(request.bodyText())!!.groupValues[1]
        Base64.getDecoder().decode(content).toString(Charsets.UTF_8)
    }

/** The `vayana` folder listing naming the slices folder's tree. */
private fun slicesFolderListing(): ByteArray =
    """[{"name":"snapshot-slices","path":"vayana/snapshot-slices","type":"dir","sha":"$TreeSha"}]""".toByteArray()

/** A recursive Git tree response for the slices folder: paths relative to it, "tree" or "blob". */
private fun sliceTree(vararg entries: Pair<String, String>): ByteArray =
    entries.joinToString(",", """{"sha":"$TreeSha","tree":[""", """],"truncated":false}""") { (path, type) ->
        """{"path":"$path","mode":"${if (type == "tree") "040000" else "100644"}","type":"$type","sha":"$ExistingSha"}"""
    }.toByteArray()

private fun folderListing(vararg files: Pair<String, String>): ByteArray =
    files.joinToString(",", "[", "]") { (path, sha) ->
        """{"name":"${path.substringAfterLast('/')}","path":"$path","type":"file","sha":"$sha"}"""
    }.toByteArray()


/** Branch head, unchanged guard (folder listing), [blobs] blob uploads, then tree, commit and fast-forward. */
private fun singleCommitResponses(blobs: Int = 0): Array<GitHubHttpResponse> = arrayOf(
    GitHubHttpResponse(200, """{"name":"main","commit":{"sha":"$HeadSha","commit":{"tree":{"sha":"$TreeSha"}}}}""".toByteArray()),
    GitHubHttpResponse(200, folderListing("vayana/snapshot-latest.json" to ExistingSha)),
    *Array(blobs) { GitHubHttpResponse(201, """{"sha":"$BlobSha"}""".toByteArray()) },
    GitHubHttpResponse(201, """{"sha":"${"a".repeat(40)}"}""".toByteArray()),
    GitHubHttpResponse(201, """{"sha":"${"b".repeat(40)}"}""".toByteArray()),
    GitHubHttpResponse(200, """{"ref":"refs/heads/main"}""".toByteArray()),
)

private fun emptySnapshot(exportedAt: Long) = PortableSnapshot(
    formatVersion = 1,
    exportedAt = exportedAt,
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
private const val HeadSha = "1111111111111111111111111111111111111111"
private const val TreeSha = "2222222222222222222222222222222222222222"
private const val BlobSha = "3333333333333333333333333333333333333333"
private const val MaxSyncDocumentBytesForTest = 16 * 1024 * 1024
private const val MaxSyncDocumentJsonBytesForTest = MaxSyncDocumentBytesForTest * 2
