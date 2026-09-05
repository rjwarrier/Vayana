package com.vayana.core.sync.asset

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
            GitHubHttpResponse(404, ByteArray(0)),
            GitHubHttpResponse(201, """{"content":{"sha":"new-sha"}}""".toByteArray()),
        )
        val store = testStore(client)

        store.put(CloudAssetLayout.pathFor(AssetId), "ciphertext".toByteArray())

        assertEquals("GET", client.requests[0].method)
        assertEquals(
            "https://api.github.test/repos/owner/repo/contents/vayana/assets/ab/cd/$AssetId.bin",
            client.requests[0].url,
        )
        assertEquals("PUT", client.requests[1].method)
        val body = client.requests[1].bodyText()
        assertTrue(body.contains(""""message":"SyncVayanaassetvayana/assets/ab/cd/$AssetId.bin""""))
        assertTrue(body.contains(""""content":"${Base64.getEncoder().encodeToString("ciphertext".toByteArray())}""""))
        assertTrue(body.contains(""""branch":"main""""))
        assertTrue(body.contains(""""name":"VayanaSync""""))
        assertTrue(body.contains(""""email":"sync@example.test""""))
    }

    @Test
    fun putIncludesShaWhenReplacingExistingAsset() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, """{"sha":"$ExistingSha"}""".toByteArray()),
            GitHubHttpResponse(200, """{"content":{"sha":"new-sha"}}""".toByteArray()),
        )
        val store = testStore(client)

        store.put(CloudAssetLayout.pathFor(AssetId), "ciphertext".toByteArray())

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
        assertEquals(8 * 1024 * 1024, client.requests.single().maxResponseBytes)
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
    fun rejectsMetadataWithoutValidShaBeforeReplacing() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(200, """{"sha":"not-a-git-object-sha"}""".toByteArray()),
        )
        val store = testStore(client)

        val failure = assertFailsWith<GitHubAssetStoreException> {
            store.put(CloudAssetLayout.pathFor(AssetId), "ciphertext".toByteArray())
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

private fun GitHubHttpRequest.bodyText(): String {
    val bytes = assertNotNull(body)
    return bytes.toString(Charsets.UTF_8).replace(Regex("\\s+"), "")
}

private const val AssetId = "abcdEFGH1234_wxyz"
private const val ExistingSha = "0123456789abcdef0123456789abcdef01234567"
