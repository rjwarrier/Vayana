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
            GitHubHttpResponse(200, """{"sha":"existing-sha"}""".toByteArray()),
            GitHubHttpResponse(200, """{"content":{"sha":"new-sha"}}""".toByteArray()),
        )
        val store = testStore(client)

        store.put(CloudAssetLayout.pathFor(AssetId), "ciphertext".toByteArray())

        assertTrue(client.requests[1].bodyText().contains(""""sha":"existing-sha""""))
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
    fun rejectsPathOutsideCloudAssetLayout() = runBlocking {
        val client = RecordingGitHubHttpClient()
        val store = testStore(client)

        val failure = assertFailsWith<IllegalArgumentException> {
            store.get("vayana/assets/not-the-layout.bin")
        }
        assertTrue(failure.message.orEmpty().startsWith("Invalid cloud asset"))
    }

    @Test
    fun exposesGitHubFailuresWithStatusAndBody() = runBlocking {
        val client = RecordingGitHubHttpClient(
            GitHubHttpResponse(500, """{"message":"boom"}""".toByteArray()),
        )
        val store = testStore(client)

        val failure = assertFailsWith<GitHubAssetStoreException> {
            store.put(CloudAssetLayout.pathFor(AssetId), "ciphertext".toByteArray())
        }

        assertEquals(500, failure.statusCode)
        assertEquals("""{"message":"boom"}""", failure.responseBody)
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
