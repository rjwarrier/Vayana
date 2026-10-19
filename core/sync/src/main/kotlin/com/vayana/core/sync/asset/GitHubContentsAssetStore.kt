package com.vayana.core.sync.asset

import com.vayana.core.sync.usage.GitHubTransferStats
import java.io.IOException
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class GitHubRepository(
    val owner: String,
    val name: String,
    val branch: String,
    val apiBaseUrl: String = "https://api.github.com",
) {
    init {
        require(owner.matches(GitHubNameRegex)) { "Invalid GitHub owner" }
        require(name.matches(GitHubNameRegex)) { "Invalid GitHub repository name" }
        require(branch.matches(GitHubBranchRegex)) { "Invalid GitHub branch" }
        val apiUri = URI(apiBaseUrl)
        require(apiUri.scheme == "https") { "GitHub API base URL must use HTTPS" }
        require(!apiUri.host.isNullOrBlank()) { "GitHub API base URL must include a host" }
        require(apiUri.query == null && apiUri.fragment == null) { "GitHub API base URL must not include query or fragment" }
    }
}

class GitHubContentsAssetStore(
    private val repository: GitHubRepository,
    private val token: String,
    private val committerName: String,
    private val committerEmail: String,
    private val client: GitHubHttpClient = UrlConnectionGitHubHttpClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CloudAssetStore {
    init {
        require(token.isNotBlank()) { "GitHub token is required" }
        require(committerName.isNotBlank()) { "GitHub committer name is required" }
        require(committerEmail.isNotBlank()) { "GitHub committer email is required" }
    }

    private val requestCount = java.util.concurrent.atomic.AtomicInteger()
    private val bytesDown = java.util.concurrent.atomic.AtomicLong()
    private val bytesUp = java.util.concurrent.atomic.AtomicLong()

    /** GitHub calls made through this store and their payload bytes, for sync diagnostics and the data usage screen. */
    fun transferStats(): GitHubTransferStats = GitHubTransferStats(requestCount.get(), bytesDown.get(), bytesUp.get())

    fun requestStats(): String = transferStats().let { "githubRequests=${it.requests}, downBytes=${it.downBytes}, upBytes=${it.upBytes}" }

    private fun execute(request: GitHubHttpRequest): GitHubHttpResponse {
        // Every Contents API write (asset uploads, cloud file deletes) is a commit of its own: the branch head is no
        // longer the commit commitSyncDocuments could build on.
        if (request.method == "PUT" || request.method == "DELETE") LastSyncCommits.remove(cacheScope)
        requestCount.incrementAndGet()
        val payloadUp = request.body?.size?.toLong() ?: request.streamingBody?.contentLength ?: 0L
        return client.execute(request).also { response ->
            bytesUp.addAndGet(response.wireUpBytes ?: payloadUp)
            bytesDown.addAndGet(response.wireDownBytes ?: response.body.size.toLong())
        }
    }

    override suspend fun put(path: String, bytes: ByteArray): Unit = withContext(dispatcher) {
        validateAssetPath(path)
        putContents(path, bytes, "Sync Vayana asset $path", replaceExisting = false)
    }

    override suspend fun putFile(path: String, file: File): Unit = withContext(dispatcher) {
        validateAssetPath(path)
        require(file.length() in 1..MaxEncryptedAssetBytes.toLong()) { "Cloud upload is empty or too large" }
        val body = buildFilePutBody("Sync Vayana asset $path", file)
        repeat(MaxPutAttempts) { attempt ->
            val response = execute(GitHubHttpRequest(
                method = "PUT", url = contentsUrl(path), headers = jsonHeaders(), streamingBody = body,
            ))
            if (response.statusCode == HttpURLConnection.HTTP_OK || response.statusCode == HttpURLConnection.HTTP_CREATED) {
                return@withContext
            }
            if (response.statusCode != HttpURLConnection.HTTP_CONFLICT || attempt == MaxPutAttempts - 1) {
                throw GitHubAssetStoreException("GitHub asset upload failed", response.statusCode, response.safeBodyText())
            }
        }
    }

    /**
     * Deletes an asset with a new commit. Its blob SHA comes from the parent folder listing, which never embeds file
     * content (a metadata request for a large asset would). Older commits still contain the encrypted blob.
     */
    override suspend fun delete(path: String): Unit = withContext(dispatcher) {
        validateAssetPath(path)
        val listing = executeRead(
            GitHubHttpRequest(
                method = "GET",
                url = contentsUrl(path.substringBeforeLast('/'), includeRef = true),
                headers = jsonHeaders(),
                maxResponseBytes = MaxSyncDocumentJsonBytes,
            ),
        )
        val fileName = path.substringAfterLast('/')
        val sha = when (listing.statusCode) {
            HttpURLConnection.HTTP_OK -> parseContentEntries(listing.bodyText())
                .firstOrNull { it.name == fileName && it.type == "file" }
                ?.sha
                ?: return@withContext
            HttpURLConnection.HTTP_NOT_FOUND -> return@withContext
            else -> throw GitHubAssetStoreException("GitHub asset folder lookup failed", listing.statusCode, listing.safeBodyText())
        }
        val response = execute(
            GitHubHttpRequest(
                method = "DELETE",
                url = contentsUrl(path),
                headers = jsonHeaders(),
                body = buildDeleteBody("Delete Vayana asset $path", sha).toByteArray(Charsets.UTF_8),
            ),
        )
        if (response.statusCode !in AssetDeleteDoneStatusCodes) {
            throw GitHubAssetStoreException("GitHub asset delete failed", response.statusCode, response.safeBodyText())
        }
    }

    /** Identifies this repository and branch for process-wide caches shared across store instances. */
    val cacheScope: String = "${repository.apiBaseUrl.trimEnd('/')}|${repository.owner}/${repository.name}@${repository.branch}"

    suspend fun putSyncDocument(path: String, bytes: ByteArray): Unit = withContext(dispatcher) {
        validateSyncDocumentPath(path)
        putContents(path, bytes, "Sync Vayana metadata $path", replaceExisting = true)
    }

    /**
     * Creates a sync document that must not exist yet (a freshly timestamped snapshot slice), skipping the
     * existing-SHA lookup a replacing write needs. Fails rather than overwrite, which keeps slice paths
     * immutable and so safe to cache by path.
     */
    suspend fun putNewSyncDocument(path: String, bytes: ByteArray): Unit = withContext(dispatcher) {
        validateSyncDocumentPath(path)
        putContents(path, bytes, "Sync Vayana metadata $path", replaceExisting = false)
    }

    suspend fun putSyncDocumentIfUnchanged(path: String, bytes: ByteArray, expectedSha: String?): Unit = withContext(dispatcher) {
        validateSyncDocumentPath(path)
        expectedSha?.let { require(it.matches(GitHubObjectShaRegex)) { "Invalid expected GitHub object SHA" } }
        putContentsWithKnownSha(path, bytes, "Sync Vayana metadata $path", expectedSha)
    }

    /**
     * Writes [changes] as one commit through the Git Data API: a single commit however many files change (the
     * Contents API commits every file, deletes included, one at a time), and readers see all of it or none.
     * [guardPath] must still be blob [guardSha] (null: must not exist) and the branch must not move meanwhile,
     * else this fails with a 409 [GitHubAssetStoreException], as [putSyncDocumentIfUnchanged] does. Returns false,
     * writing nothing, when the branch has no commit yet: the Git Data API can't write to an empty repository.
     */
    suspend fun commitSyncDocuments(
        changes: List<SyncDocumentChange>,
        message: String,
        guardPath: String,
        guardSha: String?,
    ): Boolean = withContext(dispatcher) {
        changes.forEach { change ->
            validateSyncDocumentPath(change.path)
            change.bytes?.let { require(it.isNotEmpty() && it.size <= MaxSyncDocumentBytes) { "Invalid sync document size" } }
        }
        validateSyncDocumentPath(guardPath)
        // When this process made the last commit and the guard is what that commit wrote, build on it directly: the
        // fast-forward-only ref update below still refuses if anyone has committed since. Saves two round trips.
        val ownLastCommit = LastSyncCommits[cacheScope]?.takeIf { it.guardPath == guardPath && it.guardSha == guardSha }
        val (headSha, baseTreeSha) = if (ownLastCommit != null) {
            ownLastCommit.commitSha to ownLastCommit.treeSha
        } else {
            val branch = executeRead(GitHubHttpRequest(method = "GET", url = gitUrl("branches/${encodedBranch()}"), headers = jsonHeaders()))
            when {
                branch.statusCode == HttpURLConnection.HTTP_NOT_FOUND || branch.isEmptyRepository() -> return@withContext false
                branch.statusCode != HttpURLConnection.HTTP_OK ->
                    throw GitHubAssetStoreException("GitHub branch lookup failed", branch.statusCode, branch.safeBodyText())
            }
            val branchText = branch.bodyText()
            val head = branchText.extractJsonString("sha")?.takeIf { it.matches(GitHubObjectShaRegex) }
            val tree = BranchTreeShaRegex.find(branchText)?.groupValues?.get(1)
            if (head == null || tree == null) {
                throw GitHubAssetStoreException("GitHub branch response was missing its commit", branch.statusCode, branch.safeBodyText())
            }
            // The folder listing names the guard's blob without downloading it (the file's own metadata embeds it).
            val currentGuardSha = try {
                getSyncDocumentSha(guardPath)
            } catch (missing: GitHubAssetStoreException) {
                if (missing.statusCode != HttpURLConnection.HTTP_NOT_FOUND) throw missing
                null
            }
            if (currentGuardSha != guardSha) {
                throw GitHubAssetStoreException("Sync document changed since it was read", HttpURLConnection.HTTP_CONFLICT, guardPath)
            }
            head to tree
        }
        // JSON goes inline in the tree request. Binary content, and content written to more than one path (the
        // device snapshot is a copy of the latest pointer), is uploaded once as a blob and referenced by id.
        val contents = changes.mapNotNull { it.bytes }
        val sharedBlobShas = HashMap<String, String>()
        val entries = changes.map { change ->
            val bytes = change.bytes
            val inline = change.path.endsWith(".json") && contents.count { it.contentEquals(bytes) } == 1
            val target = when {
                bytes == null -> "\"sha\":null"
                inline -> "\"content\":\"${bytes.toString(Charsets.UTF_8).escapeJson()}\""
                else -> "\"sha\":\"${sharedBlobShas.getOrPut(gitBlobSha(bytes)) { postBlob(bytes) }}\""
            }
            "{\"path\":\"${change.path.escapeJson()}\",\"mode\":\"100644\",\"type\":\"blob\",$target}"
        }
        val treeSha = postForSha("git/trees", "{\"base_tree\":\"$baseTreeSha\",\"tree\":[${entries.joinToString(",")}]}")
        val identity = "{\"name\":\"${committerName.escapeJson()}\",\"email\":\"${committerEmail.escapeJson()}\"}"
        val commitSha = postForSha(
            "git/commits",
            "{\"message\":\"${message.escapeJson()}\",\"tree\":\"$treeSha\",\"parents\":[\"$headSha\"],\"author\":$identity,\"committer\":$identity}",
        )
        val update = execute(
            GitHubHttpRequest(
                method = "PATCH",
                url = gitUrl("git/refs/heads/${encodedBranch()}"),
                headers = jsonHeaders(),
                body = "{\"sha\":\"$commitSha\",\"force\":false}".toByteArray(Charsets.UTF_8),
            ),
        )
        when (update.statusCode) {
            HttpURLConnection.HTTP_OK -> {
                val guardChange = changes.firstOrNull { it.path == guardPath }
                guardChange?.bytes?.let { GitHubBlobCache.put(gitBlobSha(it), it) }
                val writtenGuardSha = if (guardChange == null) guardSha else guardChange.bytes?.let(::gitBlobSha)
                LastSyncCommits[cacheScope] = LastSyncCommit(guardPath, writtenGuardSha, commitSha, treeSha)
                true
            }
            // Not a fast-forward: another device committed since the branch was read.
            HttpURLConnection.HTTP_CONFLICT, 422 -> {
                LastSyncCommits.remove(cacheScope)
                throw GitHubAssetStoreException("Branch moved during sync commit", HttpURLConnection.HTTP_CONFLICT, update.safeBodyText())
            }
            else -> {
                LastSyncCommits.remove(cacheScope)
                throw GitHubAssetStoreException("GitHub branch update failed", update.statusCode, update.safeBodyText())
            }
        }
    }

    private fun postBlob(bytes: ByteArray): String =
        postForSha("git/blobs", "{\"content\":\"${Base64.getEncoder().encodeToString(bytes)}\",\"encoding\":\"base64\"}")

    private fun postForSha(endpoint: String, body: String): String {
        val response = execute(
            GitHubHttpRequest(method = "POST", url = gitUrl(endpoint), headers = jsonHeaders(), body = body.toByteArray(Charsets.UTF_8)),
        )
        if (response.statusCode != HttpURLConnection.HTTP_CREATED) {
            throw GitHubAssetStoreException("GitHub $endpoint write failed", response.statusCode, response.safeBodyText())
        }
        return response.bodyText().extractJsonString("sha")?.takeIf { it.matches(GitHubObjectShaRegex) }
            ?: throw GitHubAssetStoreException("GitHub $endpoint response was missing a valid SHA", response.statusCode, response.safeBodyText())
    }

    /** Whether the sync repository is private, as GitHub reports it; null when GitHub can't be asked. */
    suspend fun repositoryIsPrivate(): Boolean? = withContext(dispatcher) {
        runCatching {
            val response = executeRead(GitHubHttpRequest(method = "GET", url = gitUrl("").trimEnd('/'), headers = jsonHeaders()))
            if (response.statusCode != HttpURLConnection.HTTP_OK) return@runCatching null
            RepositoryPrivateRegex.find(response.bodyText())?.groupValues?.get(1)?.toBooleanStrict()
        }.getOrNull()
    }

    private fun gitUrl(endpoint: String): String =
        "${repository.apiBaseUrl.trimEnd('/')}/repos/${repository.owner}/${repository.name}/$endpoint"

    private fun encodedBranch(): String = repository.branch.split('/').joinToString("/") { segment ->
        URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
    }

    suspend fun getSyncDocument(path: String): ByteArray = withContext(dispatcher) {
        validateSyncDocumentPath(path)
        val response = executeRead(
            GitHubHttpRequest(
                method = "GET",
                url = contentsUrl(path, includeRef = true),
                headers = rawHeaders(),
                maxResponseBytes = MaxSyncDocumentBytes,
            ),
        )
        if (response.statusCode != HttpURLConnection.HTTP_OK) {
            throw GitHubAssetStoreException("GitHub metadata download failed", response.statusCode, response.safeBodyText())
        }
        require(response.body.size <= MaxSyncDocumentBytes) { "Sync document download is too large" }
        response.body
    }

    /**
     * Reads the file's current blob SHA first, then downloads that exact blob, so the bytes always belong to
     * the returned SHA. Fetching content and SHA as two independent path reads could pair one commit's bytes
     * with the next commit's SHA, letting a later `putSyncDocumentIfUnchanged` overwrite that newer commit.
     */
    suspend fun getSyncDocumentWithSha(path: String): GitHubSyncDocument =
        checkNotNull(getSyncDocumentWithShaUnless(path, skipSha = null))

    /** Like [getSyncDocumentWithSha], but returns null without downloading content when the SHA is [skipSha]. */
    suspend fun getSyncDocumentWithShaUnless(path: String, skipSha: String?): GitHubSyncDocument? = withContext(dispatcher) {
        validateSyncDocumentPath(path)
        val sha = getSyncDocumentSha(path)
        if (sha == skipSha) return@withContext null
        // Blob content is addressed by its SHA, so a cached copy (from an earlier read, or the content GitHub
        // embeds in the metadata response for smaller files) is exactly the bytes of this version.
        GitHubBlobCache.get(sha)?.let { return@withContext GitHubSyncDocument(bytes = it, sha = sha) }
        val blobResponse = executeRead(
            GitHubHttpRequest(
                method = "GET",
                url = blobUrl(sha),
                headers = rawBlobHeaders(),
                maxResponseBytes = MaxSyncDocumentBytes,
            ),
        )
        if (blobResponse.statusCode != HttpURLConnection.HTTP_OK) {
            throw GitHubAssetStoreException("GitHub metadata download failed", blobResponse.statusCode, blobResponse.safeBodyText())
        }
        require(blobResponse.body.size <= MaxSyncDocumentBytes) { "Sync document download is too large" }
        GitHubBlobCache.put(sha, blobResponse.body)
        GitHubSyncDocument(bytes = blobResponse.body, sha = sha)
    }

    /**
     * Every file and folder under [path], from one recursive Git tree read. The folder's tree id comes from its
     * parent's listing (usually a 304), where listing each subfolder through the Contents API took a request apiece.
     * Empty when the folder doesn't exist.
     */
    suspend fun listSyncDocumentTree(path: String): List<GitHubContentEntry> = withContext(dispatcher) {
        validateSyncDocumentDirectoryPath(path)
        val treeSha = try {
            getSyncDocumentSha(path)
        } catch (missing: GitHubAssetStoreException) {
            if (missing.statusCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext emptyList()
            throw missing
        }
        val response = executeRead(
            GitHubHttpRequest(
                method = "GET",
                url = gitUrl("git/trees/$treeSha?recursive=1"),
                headers = jsonHeaders(),
                maxResponseBytes = MaxSyncDocumentJsonBytes,
            ),
        )
        if (response.statusCode != HttpURLConnection.HTTP_OK) {
            throw GitHubAssetStoreException("GitHub tree listing failed", response.statusCode, response.safeBodyText())
        }
        response.bodyText().substringAfter("\"tree\"").topLevelJsonObjects().map { entry ->
            val relativePath = entry.extractJsonString("path").orEmpty()
            GitHubContentEntry(
                name = relativePath.substringAfterLast('/'),
                path = "$path/$relativePath",
                type = if (entry.extractJsonString("type") == "tree") "dir" else "file",
                sha = entry.extractJsonString("sha")?.takeIf { it.matches(GitHubObjectShaRegex) },
            )
        }
    }

    suspend fun deleteSyncDocumentIfExists(path: String): Unit = withContext(dispatcher) {
        validateSyncDocumentPath(path)
        val sha = findExistingSha(path) ?: return@withContext
        deleteSyncDocumentWithKnownSha(path, sha)
    }

    suspend fun deleteSyncDocument(path: String, sha: String): Unit = withContext(dispatcher) {
        validateSyncDocumentPath(path)
        require(sha.matches(GitHubObjectShaRegex)) { "Invalid GitHub object SHA" }
        deleteSyncDocumentWithKnownSha(path, sha)
    }

    private fun deleteSyncDocumentWithKnownSha(path: String, sha: String) {
        val body = buildDeleteBody("Prune Vayana metadata $path", sha)
        val response = execute(
            GitHubHttpRequest(
                method = "DELETE",
                url = contentsUrl(path),
                headers = jsonHeaders(),
                body = body.toByteArray(Charsets.UTF_8),
            ),
        )
        if (response.statusCode !in setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_ACCEPTED, HttpURLConnection.HTTP_NO_CONTENT)) {
            throw GitHubAssetStoreException("GitHub metadata delete failed", response.statusCode, response.safeBodyText())
        }
    }

    /**
     * The file's current blob SHA, from its folder's listing: a few hundred bytes, where the file's own metadata
     * response embeds up to 1 MB of base64 content. The bytes then come from [GitHubBlobCache] (which this device's
     * own writes seed) or one raw blob download. Sent as a conditional request against the folder's last ETag, so
     * an unchanged folder answers 304 (no body, and not counted against GitHub's rate limit).
     */
    private suspend fun getSyncDocumentSha(path: String): String {
        val folder = path.substringBeforeLast('/')
        val metadataKey = "$cacheScope|$folder/"
        val cached = GitHubMetadataCache.get(metadataKey)
        val response = executeRead(
            GitHubHttpRequest(
                method = "GET",
                url = contentsUrl(folder, includeRef = true),
                headers = jsonHeaders() + cached?.etag?.let { mapOf("If-None-Match" to it) }.orEmpty(),
                maxResponseBytes = MaxSyncDocumentJsonBytes,
            ),
        )
        val shaByPath = when {
            response.statusCode == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null -> cached.shaByPath
            // Folders too: their tree ids let [listSyncDocumentTree] read a whole subtree at once.
            response.statusCode == HttpURLConnection.HTTP_OK -> parseContentEntries(response.bodyText())
                .mapNotNull { entry -> entry.sha?.let { entry.path to it } }
                .toMap()
                .also { listing -> response.header("ETag")?.let { GitHubMetadataCache.put(metadataKey, CachedSyncDocumentMetadata(it, listing)) } }
            else -> throw GitHubAssetStoreException("GitHub metadata download failed", response.statusCode, response.safeBodyText())
        }
        return shaByPath[path]
            ?: throw GitHubAssetStoreException("GitHub metadata download failed", HttpURLConnection.HTTP_NOT_FOUND, path)
    }

    /**
     * One page of the commits that changed the latest snapshot pointer, newest first, through the GraphQL API: each
     * with its author (the publishing device's name) and the blob ids of the pointer and of every slice file then in
     * the repository. GraphQL reports a missing slice folder (snapshots older than slices) as a partial error; the
     * page's data is still used.
     */
    suspend fun snapshotHistoryPage(cursor: String?, pageSize: Int): GitHubSnapshotHistoryPage = withContext(dispatcher) {
        require(pageSize in 1..100)
        val variables = org.json.JSONObject()
            .put("owner", repository.owner)
            .put("name", repository.name)
            .put("branch", "refs/heads/${repository.branch}")
            .put("first", pageSize)
            .put("cursor", cursor ?: org.json.JSONObject.NULL)
        val body = org.json.JSONObject().put("query", SnapshotHistoryQuery).put("variables", variables).toString()
        var response: GitHubHttpResponse? = null
        for (attempt in 0 until MaxReadAttempts) {
            response = try {
                execute(GitHubHttpRequest("POST", graphQlUrl(), jsonHeaders(), body.toByteArray(Charsets.UTF_8),
                    maxResponseBytes = MaxSyncDocumentBytes))
            } catch (exception: IOException) {
                if (attempt == MaxReadAttempts - 1) throw exception
                delay(readRetryDelayMillis(attempt))
                continue
            }
            // A gateway timeout means the page was too heavy to answer: the caller asks again for fewer commits.
            if (response.statusCode !in RetryableReadStatusCodes || response.statusCode in GraphQlTimeoutStatusCodes) break
            if (attempt < MaxReadAttempts - 1) delay(response.retryDelayMillis(attempt))
        }
        val result = checkNotNull(response)
        if (result.statusCode != HttpURLConnection.HTTP_OK) {
            throw GitHubAssetStoreException("GitHub history lookup failed", result.statusCode, result.safeBodyText())
        }
        parseSnapshotHistoryPage(result.bodyText())
    }

    /** The bytes of a blob by id (for history reads; current documents go through [getSyncDocument]). */
    suspend fun getBlob(sha: String): ByteArray = withContext(dispatcher) {
        val response = executeRead(GitHubHttpRequest("GET", blobUrl(sha), rawBlobHeaders(), maxResponseBytes = MaxSyncDocumentBytes))
        if (response.statusCode != HttpURLConnection.HTTP_OK) {
            throw GitHubAssetStoreException("GitHub blob download failed", response.statusCode, response.safeBodyText())
        }
        response.body
    }

    private fun graphQlUrl(): String {
        val base = repository.apiBaseUrl.trimEnd('/')
        // GitHub Enterprise serves REST at /api/v3 and GraphQL at /api/graphql; github.com at /graphql.
        return if (base.endsWith("/api/v3")) base.removeSuffix("/v3") + "/graphql" else "$base/graphql"
    }

    suspend fun testConnection(): GitHubConnectionTestResult = withContext(dispatcher) {
        val response = executeRead(
            GitHubHttpRequest(
                method = "GET",
                url = contentsUrl("vayana/snapshot-latest.json", includeRef = true),
                headers = rawHeaders(),
                maxResponseBytes = MaxSyncDocumentBytes,
            ),
        )
        when (response.statusCode) {
            HttpURLConnection.HTTP_OK -> GitHubConnectionTestResult.Connected
            HttpURLConnection.HTTP_NOT_FOUND -> GitHubConnectionTestResult.ReadyForInitialSync
            HttpURLConnection.HTTP_CONFLICT -> if (response.isEmptyRepository()) {
                GitHubConnectionTestResult.ReadyForInitialSync
            } else {
                throw GitHubAssetStoreException("GitHub connection test failed", response.statusCode, response.safeBodyText())
            }
            else -> throw GitHubAssetStoreException("GitHub connection test failed", response.statusCode, response.safeBodyText())
        }
    }

    private suspend fun putContents(path: String, bytes: ByteArray, message: String, replaceExisting: Boolean) {
        require(bytes.isNotEmpty()) { "Cloud asset upload is empty" }
        val maxBytes = if (path.endsWith(".json")) MaxSyncDocumentBytes else MaxEncryptedAssetBytes
        require(bytes.size <= maxBytes) { "Cloud upload is too large" }
        var lastResponse: GitHubHttpResponse? = null
        repeat(MaxPutAttempts) { attempt ->
            val existingSha = if (replaceExisting) findExistingSha(path) else null
            val body = buildPutBody(message, bytes, existingSha)
            val response = execute(
                GitHubHttpRequest(
                    method = "PUT",
                    url = contentsUrl(path),
                    headers = jsonHeaders(),
                    body = body.toByteArray(Charsets.UTF_8),
                ),
            )
            if (response.statusCode in setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED)) {
                return
            }
            lastResponse = response
            if (response.statusCode != HttpURLConnection.HTTP_CONFLICT || attempt == MaxPutAttempts - 1) {
                throw GitHubAssetStoreException("GitHub asset upload failed", response.statusCode, response.safeBodyText())
            }
        }
        val response = checkNotNull(lastResponse)
        throw GitHubAssetStoreException("GitHub asset upload failed", response.statusCode, response.safeBodyText())
    }

    private fun putContentsWithKnownSha(path: String, bytes: ByteArray, message: String, expectedSha: String?) {
        require(bytes.isNotEmpty()) { "Cloud asset upload is empty" }
        val maxBytes = if (path.endsWith(".json")) MaxSyncDocumentBytes else MaxEncryptedAssetBytes
        require(bytes.size <= maxBytes) { "Cloud upload is too large" }
        val body = buildPutBody(message, bytes, expectedSha)
        val response = execute(
            GitHubHttpRequest(
                method = "PUT",
                url = contentsUrl(path),
                headers = jsonHeaders(),
                body = body.toByteArray(Charsets.UTF_8),
            ),
        )
        if (response.statusCode in setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED)) {
            // The next pull of this document finds its blob id in the folder listing and reads these bytes back.
            GitHubBlobCache.put(gitBlobSha(bytes), bytes)
            return
        }
        throw GitHubAssetStoreException("GitHub asset upload failed", response.statusCode, response.safeBodyText())
    }

    override suspend fun get(path: String): ByteArray = withContext(dispatcher) {
        validateAssetPath(path)
        val response = executeRead(
            GitHubHttpRequest(
                method = "GET",
                url = contentsUrl(path, includeRef = true),
                headers = rawHeaders(),
                maxResponseBytes = MaxEncryptedAssetBytes,
            ),
        )
        if (response.statusCode != HttpURLConnection.HTTP_OK) {
            throw GitHubAssetStoreException("GitHub asset download failed", response.statusCode, response.safeBodyText())
        }
        require(response.body.size <= MaxEncryptedAssetBytes) { "Cloud asset download is too large" }
        response.body
    }

    private suspend fun findExistingSha(path: String): String? {
        // Ask for object metadata so GitHub does not reject larger snapshot files that exceed the
        // default Contents API JSON body's embedded-content behavior.
        val response = executeRead(
            GitHubHttpRequest(
                method = "GET",
                url = contentsUrl(path, includeRef = true),
                headers = objectHeaders(),
                maxResponseBytes = MaxSyncDocumentJsonBytes,
            ),
        )
        return when (response.statusCode) {
            HttpURLConnection.HTTP_OK -> response.bodyText().extractJsonString("sha")
                ?.takeIf { it.matches(GitHubObjectShaRegex) }
                ?: throw GitHubAssetStoreException(
                    message = "GitHub asset metadata response was missing a valid SHA",
                    statusCode = response.statusCode,
                    responseBody = response.safeBodyText(),
                )
            HttpURLConnection.HTTP_NOT_FOUND -> null
            HttpURLConnection.HTTP_CONFLICT -> if (response.isEmptyRepository()) {
                null
            } else {
                throw GitHubAssetStoreException(
                    message = "GitHub asset metadata lookup failed",
                    statusCode = response.statusCode,
                    responseBody = response.safeBodyText(),
                )
            }
            else -> throw GitHubAssetStoreException(
                message = "GitHub asset metadata lookup failed",
                statusCode = response.statusCode,
                responseBody = response.safeBodyText(),
            )
        }
    }

    /** Retries only idempotent reads, keeping writes under the existing conflict protocol. */
    private suspend fun executeRead(request: GitHubHttpRequest): GitHubHttpResponse {
        require(request.method == "GET") { "Only GitHub reads are safe to retry here" }
        repeat(MaxReadAttempts) { attempt ->
            try {
                val response = execute(request)
                if (response.statusCode !in RetryableReadStatusCodes || attempt == MaxReadAttempts - 1) {
                    return response
                }
                delay(response.retryDelayMillis(attempt))
            } catch (exception: IOException) {
                if (attempt == MaxReadAttempts - 1) throw exception
                delay(readRetryDelayMillis(attempt))
            }
        }
        error("Unreachable")
    }

    private fun contentsUrl(path: String, includeRef: Boolean = false): String {
        val encodedPath = path.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
        }
        val ref = if (includeRef) {
            "?ref=${URLEncoder.encode(repository.branch, Charsets.UTF_8.name()).replace("+", "%20")}"
        } else {
            ""
        }
        return "${repository.apiBaseUrl.trimEnd('/')}/repos/${repository.owner}/${repository.name}/contents/$encodedPath$ref"
    }

    private fun blobUrl(sha: String): String {
        require(sha.matches(GitHubObjectShaRegex)) { "Invalid GitHub object SHA" }
        return "${repository.apiBaseUrl.trimEnd('/')}/repos/${repository.owner}/${repository.name}/git/blobs/$sha"
    }

    private fun rawBlobHeaders(): Map<String, String> = commonHeaders() + mapOf(
        "Accept" to "application/vnd.github.raw+json",
    )

    private fun jsonHeaders(): Map<String, String> = commonHeaders() + mapOf(
        "Accept" to "application/vnd.github+json",
        "Content-Type" to "application/json; charset=utf-8",
    )

    private fun rawHeaders(): Map<String, String> = commonHeaders() + mapOf(
        "Accept" to "application/vnd.github.raw",
    )

    private fun objectHeaders(): Map<String, String> = commonHeaders() + mapOf(
        "Accept" to "application/vnd.github.object+json",
    )

    private fun commonHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "X-GitHub-Api-Version" to "2022-11-28",
        "User-Agent" to "VayanaSync",
    )

    private fun buildFilePutBody(message: String, file: File): GitHubStreamingBody {
        val prefix = """{"message":"${message.escapeJson()}","content":"""".toByteArray(Charsets.UTF_8)
        val suffix = """","branch":"${repository.branch.escapeJson()}","committer":{"name":"${committerName.escapeJson()}","email":"${committerEmail.escapeJson()}"}}""".toByteArray(Charsets.UTF_8)
        val encodedLength = ((file.length() + 2) / 3) * 4
        return GitHubStreamingBody(prefix.size + encodedLength + suffix.size) { output ->
            output.write(prefix)
            // Closing the encoder emits padding, but the JSON suffix still needs the underlying stream.
            val nonClosing = object : FilterOutputStream(output) {
                override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
                override fun close() = flush()
            }
            Base64.getEncoder().wrap(nonClosing).use { encoded ->
                file.inputStream().use { it.copyTo(encoded) }
            }
            output.write(suffix)
        }
    }

    private fun buildPutBody(message: String, bytes: ByteArray, existingSha: String?): String {
        val encodedBytes = Base64.getEncoder().encodeToString(bytes)
        val shaProperty = existingSha?.let { ""","sha":"${it.escapeJson()}"""" }.orEmpty()
        return """
            {
              "message":"${message.escapeJson()}",
              "content":"$encodedBytes",
              "branch":"${repository.branch.escapeJson()}",
              "committer":{
                "name":"${committerName.escapeJson()}",
                "email":"${committerEmail.escapeJson()}"
              }$shaProperty
            }
        """.trimIndent()
    }

    private fun buildDeleteBody(message: String, sha: String): String =
        """
            {
              "message":"${message.escapeJson()}",
              "sha":"${sha.escapeJson()}",
              "branch":"${repository.branch.escapeJson()}",
              "committer":{
                "name":"${committerName.escapeJson()}",
                "email":"${committerEmail.escapeJson()}"
              }
            }
        """.trimIndent()
}

sealed interface GitHubConnectionTestResult {
    data object Connected : GitHubConnectionTestResult
    data object ReadyForInitialSync : GitHubConnectionTestResult
}

data class GitHubSyncDocument(
    val bytes: ByteArray,
    val sha: String,
)

/** The last commit this process made to a repository, and the guard blob it left. */
internal data class LastSyncCommit(val guardPath: String, val guardSha: String?, val commitSha: String, val treeSha: String)

/** By repository scope: stores are created per sync, the branch head outlives them. */
internal val LastSyncCommits = java.util.concurrent.ConcurrentHashMap<String, LastSyncCommit>()

/** A file for [GitHubContentsAssetStore.commitSyncDocuments]: its new content, or null [bytes] to delete it. */
class SyncDocumentChange(val path: String, val bytes: ByteArray?)

data class GitHubContentEntry(
    val name: String,
    val path: String,
    val type: String,
    val sha: String?,
)

data class GitHubHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val maxResponseBytes: Int = MaxHttpResponseBytes,
    val streamingBody: GitHubStreamingBody? = null,
)

class GitHubStreamingBody(val contentLength: Long, val writeTo: (OutputStream) -> Unit)

data class GitHubHttpResponse(
    val statusCode: Int,
    val body: ByteArray,
    /** Response headers keyed by lower-cased name. */
    val headers: Map<String, String> = emptyMap(),
    /** Bytes sent and received on the wire (compressed body and headers, not encryption); null when unknown. */
    val wireUpBytes: Long? = null,
    val wireDownBytes: Long? = null,
) {
    fun header(name: String): String? = headers[name.lowercase()]

    fun bodyText(): String = body.toString(Charsets.UTF_8)

    fun safeBodyText(): String = bodyText()
        .replace(AuthorizationTokenRegex, """"token":"***"""")
        .take(MaxErrorBodyChars)
}

interface GitHubHttpClient {
    fun execute(request: GitHubHttpRequest): GitHubHttpResponse
}

class GitHubAssetStoreException(
    message: String,
    val statusCode: Int,
    val responseBody: String,
) : IOException("$message: HTTP $statusCode")

private class UrlConnectionGitHubHttpClient : GitHubHttpClient {
    override fun execute(request: GitHubHttpRequest): GitHubHttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = NetworkTimeoutMillis
            connection.readTimeout = NetworkTimeoutMillis
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            // Asked for explicitly, the response stays compressed until read here, so its real size can be counted.
            connection.setRequestProperty("Accept-Encoding", "gzip")
            check(request.body == null || request.streamingBody == null) { "Only one request body is allowed" }
            val streamingBody = request.streamingBody
            if (streamingBody != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(streamingBody.contentLength)
                connection.outputStream.use(streamingBody.writeTo)
            } else request.body?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status >= HttpURLConnection.HTTP_BAD_REQUEST) {
                connection.errorStream
            } else {
                connection.inputStream
            }
            val gzip = connection.contentEncoding.equals("gzip", ignoreCase = true)
            val (bytes, bodyWireBytes) = stream?.use { readResponseBody(it, gzip, request.maxResponseBytes) } ?: (ByteArray(0) to 0L)
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapNotNull { (name, values) -> values.firstOrNull()?.let { name.lowercase() to it } }
                .toMap()
            val requestHeaderBytes = request.method.length + request.url.length + RequestLineOverhead +
                request.headers.entries.sumOf { (name, value) -> name.length + value.length + HeaderLineOverhead }
            val responseHeaderBytes = connection.headerFields.entries.sumOf { (name, values) ->
                values.sumOf { (name?.length ?: 0) + it.length + HeaderLineOverhead }
            }
            return GitHubHttpResponse(
                statusCode = status,
                body = bytes,
                headers = headers,
                wireUpBytes = requestHeaderBytes + (request.body?.size?.toLong() ?: streamingBody?.contentLength ?: 0L),
                wireDownBytes = responseHeaderBytes + bodyWireBytes,
            )
        } finally {
            connection.disconnect()
        }
    }
}

/** A deleted asset, or one another device deleted first (404). */
private val AssetDeleteDoneStatusCodes = setOf(
    HttpURLConnection.HTTP_OK,
    HttpURLConnection.HTTP_ACCEPTED,
    HttpURLConnection.HTTP_NO_CONTENT,
    HttpURLConnection.HTTP_NOT_FOUND,
)

private fun validateAssetPath(path: String) {
    require(path.startsWith("vayana/assets/")) { "Invalid cloud asset path" }
    require(path.endsWith(".bin")) { "Invalid cloud asset path" }
    val assetId = path.substringAfterLast('/').removeSuffix(".bin")
    require(CloudAssetLayout.isValidAssetId(assetId)) { "Invalid cloud asset id" }
    require(path == CloudAssetLayout.pathFor(assetId)) { "Invalid cloud asset path" }
}

private fun validateSyncDocumentPath(path: String) {
    require(
        path == "vayana/snapshot-latest.json" ||
            path.startsWith("vayana/snapshots/") ||
            path.startsWith("vayana/snapshot-slices/"),
    ) { "Invalid sync document path" }
    require(
        path.endsWith(".json") || path.matches(CompressedSlicePathRegex),
    ) { "Invalid sync document path" }
    require(".." !in path && "//" !in path) { "Invalid sync document path" }
    require(path.matches(SyncDocumentPathRegex)) { "Invalid sync document path" }
}

private fun validateSyncDocumentDirectoryPath(path: String) {
    require(path == "vayana/snapshot-slices" || path.matches(SnapshotSliceDirectoryPathRegex)) { "Invalid sync document directory path" }
    require(".." !in path && "//" !in path) { "Invalid sync document directory path" }
}

private fun parseContentEntries(jsonText: String): List<GitHubContentEntry> =
    jsonText.topLevelJsonObjects().map { entry ->
        GitHubContentEntry(
            name = entry.extractJsonString("name").orEmpty(),
            path = entry.extractJsonString("path").orEmpty(),
            type = entry.extractJsonString("type").orEmpty(),
            sha = entry.extractJsonString("sha")?.takeIf { it.matches(GitHubObjectShaRegex) },
        )
    }

/**
 * Splits a JSON array of objects into its top-level object texts. GitHub directory entries each carry a
 * nested `"_links": {...}` object, so a brace-free regex would match only those inner objects.
 */
private fun String.topLevelJsonObjects(): List<String> {
    val objects = mutableListOf<String>()
    var depth = 0
    var start = -1
    var inString = false
    var escaped = false
    for (index in indices) {
        val char = this[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> {
                if (depth == 0) start = index
                depth += 1
            }
            '}' -> {
                depth -= 1
                if (depth == 0 && start >= 0) {
                    objects += substring(start, index + 1)
                    start = -1
                }
            }
        }
    }
    return objects
}

/**
 * The read timeout only bounds each read(), so a lossy connection trickling bytes could hold a download (and the
 * whole sync) for many minutes. Past a grace period it must average [MinDownloadBytesPerSecond]; a slower one
 * fails, and reads retry on a fresh connection.
 */
/**
 * Reads a response body, un-gzipping it when [gzip], and returns it with the bytes that came off the network. An
 * empty body is fine even when marked gzip (some 204s and 304s are).
 */
internal fun readResponseBody(stream: java.io.InputStream, gzip: Boolean, maxBytes: Int): Pair<ByteArray, Long> {
    val counted = CountingInputStream(stream)
    if (!gzip) return counted.readBytesLimited(maxBytes) to counted.count
    val peek = java.io.PushbackInputStream(counted, 1)
    val first = peek.read()
    if (first == -1) return ByteArray(0) to counted.count
    peek.unread(first)
    return java.util.zip.GZIPInputStream(peek).readBytesLimited(maxBytes) to counted.count
}

private class CountingInputStream(input: java.io.InputStream) : java.io.FilterInputStream(input) {
    var count = 0L
        private set

    override fun read(): Int = super.read().also { if (it >= 0) count++ }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        super.read(buffer, offset, length).also { if (it > 0) count += it }
}

private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = java.io.ByteArrayOutputStream()
    val startedAt = System.nanoTime()
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        total += read
        if (total > maxBytes) {
            throw IOException("GitHub response exceeded $maxBytes bytes")
        }
        output.write(buffer, 0, read)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        if (elapsedMillis > SlowDownloadGraceMillis && total * 1000L / elapsedMillis < MinDownloadBytesPerSecond) {
            throw java.net.SocketTimeoutException("GitHub download too slow: $total bytes in ${elapsedMillis / 1000}s")
        }
    }
    return output.toByteArray()
}

/**
 * Linear scan for a (possibly ~1 MB) string value. The regex in [extractJsonString] recurses per character in
 * java.util.regex and can overflow the stack on inputs that long. Escapes are dropped rather than decoded,
 * which is right for base64 (GitHub only escapes its line breaks) and is all this is used for.
 */
private fun String.rawJsonStringValue(name: String): String? {
    val keyIndex = indexOf("\"$name\"").takeIf { it >= 0 } ?: return null
    var index = indexOf(':', startIndex = keyIndex + name.length + 2).takeIf { it >= 0 } ?: return null
    index += 1
    while (index < length && this[index].isWhitespace()) index += 1
    if (index >= length || this[index] != '"') return null
    index += 1
    val value = StringBuilder()
    while (index < length) {
        when (val char = this[index]) {
            '\\' -> index += 2
            '"' -> return value.toString()
            else -> {
                value.append(char)
                index += 1
            }
        }
    }
    return null
}

internal data class CachedSyncDocumentMetadata(val etag: String, val shaByPath: Map<String, String>)

/** Last ETag and SHA seen per repository path, for conditional metadata requests. */
internal object GitHubMetadataCache {
    private const val MaxEntries = 256
    private val entries = LinkedHashMap<String, CachedSyncDocumentMetadata>(16, 0.75f, true)

    @Synchronized
    fun get(key: String): CachedSyncDocumentMetadata? = entries[key]

    @Synchronized
    fun put(key: String, metadata: CachedSyncDocumentMetadata) {
        entries[key] = metadata
        if (entries.size > MaxEntries) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun clear() = entries.clear()
}

/** The git object id these bytes get as a file, so a write can seed [GitHubBlobCache] without reading them back. */
internal fun gitBlobSha(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-1").run {
        update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        digest(bytes)
    }.joinToString("") { "%02x".format(it) }

/** Size-bounded LRU of sync-document bytes keyed by git blob SHA (content-addressed, so never stale). */
internal object GitHubBlobCache {
    private const val MaxTotalBytes = 12 * 1024 * 1024
    private const val MaxEntryBytes = 6 * 1024 * 1024
    private val entries = LinkedHashMap<String, ByteArray>(16, 0.75f, true)
    private var totalBytes = 0

    @Synchronized
    fun get(sha: String): ByteArray? = entries[sha]

    @Synchronized
    fun put(sha: String, bytes: ByteArray) {
        if (bytes.size > MaxEntryBytes) return
        entries.put(sha, bytes)?.let { totalBytes -= it.size }
        totalBytes += bytes.size
        val iterator = entries.entries.iterator()
        while (totalBytes > MaxTotalBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest.key == sha) continue
            totalBytes -= eldest.value.size
            iterator.remove()
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        totalBytes = 0
    }
}

private fun String.extractJsonString(name: String): String? {
    val pattern = Regex(""""${Regex.escape(name)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
    return pattern.find(this)?.groupValues?.get(1)?.unescapeJson()
}

private fun String.escapeJson(): String = buildString(length) {
    for (char in this@escapeJson) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
}

private fun String.unescapeJson(): String = buildString(length) {
    var index = 0
    while (index < this@unescapeJson.length) {
        val char = this@unescapeJson[index++]
        if (char != '\\' || index >= this@unescapeJson.length) {
            append(char)
            continue
        }
        when (val escaped = this@unescapeJson[index++]) {
            '\\', '"', '/' -> append(escaped)
            'b' -> append('\b')
            'f' -> append('\u000C')
            'n' -> append('\n')
            'r' -> append('\r')
            't' -> append('\t')
            'u' -> {
                val hex = this@unescapeJson.substring(index, index + 4)
                append(hex.toInt(16).toChar())
                index += 4
            }
            else -> append(escaped)
        }
    }
}

private fun GitHubHttpResponse.isEmptyRepository(): Boolean =
    statusCode == HttpURLConnection.HTTP_CONFLICT &&
        bodyText().contains("Git Repository is empty", ignoreCase = true)

private fun GitHubHttpResponse.retryDelayMillis(attempt: Int): Long =
    header("Retry-After")
        ?.trim()
        ?.toLongOrNull()
        ?.coerceIn(0L, MaxRetryAfterMillis / 1_000L)
        ?.times(1_000L)
        ?: readRetryDelayMillis(attempt)

private fun readRetryDelayMillis(attempt: Int): Long = InitialReadRetryDelayMillis * (1L shl attempt)

private val GitHubNameRegex = Regex("^[A-Za-z0-9_.-]{1,100}$")
private val GitHubBranchRegex = Regex("^[A-Za-z0-9._/-]{1,255}$")
private val GitHubObjectShaRegex = Regex("^[a-f0-9]{40,64}$")
// "GET <url> HTTP/1.1\r\n" around the method and URL, and ": " plus "\r\n" on every header line.
private const val RequestLineOverhead = 12
private const val HeaderLineOverhead = 4
private val RepositoryPrivateRegex = Regex("""^\s*\{.*?"private"\s*:\s*(true|false)""", RegexOption.DOT_MATCHES_ALL)
private val SyncDocumentPathRegex = Regex("^[A-Za-z0-9._/-]{1,240}$")
// Any slice may be zipped (the compact snapshot format); which names are slices is checked when a manifest is read.
private val CompressedSlicePathRegex = Regex("^vayana/snapshot-slices/[1-9][0-9]*/[a-z-]+\\.zip$")
private val BranchTreeShaRegex = Regex(""""tree"\s*:\s*\{\s*"sha"\s*:\s*"([a-f0-9]{40,64})"""")
private val SnapshotSliceDirectoryPathRegex = Regex("^vayana/snapshot-slices/[1-9][0-9]*$")
private val AuthorizationTokenRegex = Regex(""""token"\s*:\s*"[^"]+"""")
private val RetryableReadStatusCodes = setOf(408, 425, 429, 500, 502, 503, 504)
private const val NetworkTimeoutMillis = 30_000
// ponytail: fixed floor; 8 KiB/s still lets a 2.7 MB slice take ~6 min. Tune if real links trip it.
private const val SlowDownloadGraceMillis = 20_000L
private const val MinDownloadBytesPerSecond = 8 * 1024L
private const val MaxPutAttempts = 3
private const val MaxReadAttempts = 3
/** GraphQL answers these when a query took too long; retrying the same query rarely helps. */
val GraphQlTimeoutStatusCodes = setOf(502, 504)
private const val InitialReadRetryDelayMillis = 200L
private const val MaxRetryAfterMillis = 2_000L
private const val MaxEncryptedAssetBytes = 80 * 1024 * 1024
private const val MaxSyncDocumentBytes = 16 * 1024 * 1024
private const val MaxSyncDocumentJsonBytes = MaxSyncDocumentBytes * 2
private const val MaxHttpResponseBytes = MaxEncryptedAssetBytes + 1024
private const val MaxErrorBodyChars = 4_096
