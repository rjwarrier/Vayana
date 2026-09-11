package com.vayana.core.sync.asset

import com.vayana.core.backup.PortableReadingSession
import com.vayana.core.sync.snapshot.pruneOlderPortableSnapshotSlices
import com.vayana.core.sync.snapshot.pushPortableReadingProgress
import com.vayana.core.sync.snapshot.remotePortableSnapshotDocumentFrom
import com.vayana.core.sync.snapshot.RemotePortableSnapshotDocument
import com.vayana.core.sync.snapshot.RemotePortableSnapshotSlice
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class GitHubContentsAssetStoreTest {
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
    fun getSyncDocumentWithShaReadsShaThenDownloadsThatExactBlob() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(
                200,
                """
                {
                  "sha": "$ExistingSha",
                  "encoding": "base64",
                  "content": "${Base64.getMimeEncoder().encodeToString("""{"books":[]}""".toByteArray())}"
                }
                """.trimIndent().toByteArray(),
            ),
            GitHubHttpResponse(200, """{"books":[]}""".toByteArray()),
        )
        val store = testStore(client)

        val document = store.getSyncDocumentWithSha("vayana/snapshot-latest.json")

        assertContentEquals("""{"books":[]}""".toByteArray(), document.bytes)
        assertEquals(ExistingSha, document.sha)
        assertEquals(listOf("application/vnd.github.object+json", "application/vnd.github.raw+json"), client.requests.map { it.headers["Accept"] })
        assertEquals(
            "https://api.github.test/repos/owner/repo/git/blobs/$ExistingSha",
            client.requests[1].url,
        )
        assertEquals(listOf(MaxSyncDocumentJsonBytesForTest, MaxSyncDocumentBytesForTest), client.requests.map { it.maxResponseBytes })
    }

    @Test
    fun getSyncDocumentWithShaReadsLargeContentsObjectWithoutEmbeddedContent() = runBlocking {
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
    fun remotePortableSnapshotDocumentKeepsSlicesSeparate() {
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
            GitHubHttpResponse(404, """{"message":"Not Found"}""".toByteArray()),
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
        val puts = client.requests.filter { it.method == "PUT" }
        assertEquals(2, puts.size)
        assertTrue(puts[0].url.endsWith("contents/vayana/snapshot-slices/3000/reading-sessions.json"))
        assertTrue(puts[1].url.endsWith("contents/vayana/snapshot-latest.json"))
        assertTrue(puts[1].bodyText().contains(""""sha":"$ExistingSha""""))
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
