package com.vayana.core.sync.asset

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

    override suspend fun put(path: String, bytes: ByteArray): Unit = withContext(dispatcher) {
        validateAssetPath(path)
        putContents(path, bytes, "Sync Vayana asset $path")
    }

    suspend fun putSyncDocument(path: String, bytes: ByteArray): Unit = withContext(dispatcher) {
        validateSyncDocumentPath(path)
        putContents(path, bytes, "Sync Vayana metadata $path")
    }

    suspend fun getSyncDocument(path: String): ByteArray = withContext(dispatcher) {
        validateSyncDocumentPath(path)
        val response = client.execute(
            GitHubHttpRequest(
                method = "GET",
                url = contentsUrl(path),
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

    private fun putContents(path: String, bytes: ByteArray, message: String) {
        require(bytes.isNotEmpty()) { "Cloud asset upload is empty" }
        val maxBytes = if (path.endsWith(".json")) MaxSyncDocumentBytes else MaxEncryptedAssetBytes
        require(bytes.size <= maxBytes) { "Cloud upload is too large" }
        var lastResponse: GitHubHttpResponse? = null
        repeat(MaxPutAttempts) { attempt ->
            val existingSha = findExistingSha(path)
            val body = buildPutBody(message, bytes, existingSha)
            val response = client.execute(
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

    override suspend fun get(path: String): ByteArray = withContext(dispatcher) {
        validateAssetPath(path)
        val response = client.execute(
            GitHubHttpRequest(
                method = "GET",
                url = contentsUrl(path),
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

    private fun findExistingSha(path: String): String? {
        val response = client.execute(
            GitHubHttpRequest(
                method = "GET",
                url = contentsUrl(path),
                headers = jsonHeaders(),
                maxResponseBytes = MaxGitHubMetadataBytes,
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

    private fun contentsUrl(path: String): String {
        val encodedPath = path.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
        }
        return "${repository.apiBaseUrl.trimEnd('/')}/repos/${repository.owner}/${repository.name}/contents/$encodedPath"
    }

    private fun jsonHeaders(): Map<String, String> = commonHeaders() + mapOf(
        "Accept" to "application/vnd.github+json",
        "Content-Type" to "application/json; charset=utf-8",
    )

    private fun rawHeaders(): Map<String, String> = commonHeaders() + mapOf(
        "Accept" to "application/vnd.github.raw",
    )

    private fun commonHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "X-GitHub-Api-Version" to "2022-11-28",
        "User-Agent" to "VayanaSync",
    )

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
}

data class GitHubHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val maxResponseBytes: Int = MaxHttpResponseBytes,
)

data class GitHubHttpResponse(
    val statusCode: Int,
    val body: ByteArray,
) {
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
            request.body?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status >= HttpURLConnection.HTTP_BAD_REQUEST) {
                connection.errorStream
            } else {
                connection.inputStream
            }
            val bytes = stream?.use { it.readBytesLimited(request.maxResponseBytes) } ?: ByteArray(0)
            return GitHubHttpResponse(status, bytes)
        } finally {
            connection.disconnect()
        }
    }
}

private fun validateAssetPath(path: String) {
    require(path.startsWith("vayana/assets/")) { "Invalid cloud asset path" }
    require(path.endsWith(".bin")) { "Invalid cloud asset path" }
    val assetId = path.substringAfterLast('/').removeSuffix(".bin")
    require(CloudAssetLayout.isValidAssetId(assetId)) { "Invalid cloud asset id" }
    require(path == CloudAssetLayout.pathFor(assetId)) { "Invalid cloud asset path" }
}

private fun validateSyncDocumentPath(path: String) {
    require(path == "vayana/snapshot-latest.json" || path.startsWith("vayana/snapshots/")) { "Invalid sync document path" }
    require(path.endsWith(".json")) { "Invalid sync document path" }
    require(".." !in path && "//" !in path) { "Invalid sync document path" }
    require(path.matches(SyncDocumentPathRegex)) { "Invalid sync document path" }
}

private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = java.io.ByteArrayOutputStream()
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        total += read
        if (total > maxBytes) {
            throw IOException("GitHub response exceeded $maxBytes bytes")
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
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

private val GitHubNameRegex = Regex("^[A-Za-z0-9_.-]{1,100}$")
private val GitHubBranchRegex = Regex("^[A-Za-z0-9._/-]{1,255}$")
private val GitHubObjectShaRegex = Regex("^[a-f0-9]{40,64}$")
private val SyncDocumentPathRegex = Regex("^[A-Za-z0-9._/-]{1,240}$")
private val AuthorizationTokenRegex = Regex(""""token"\s*:\s*"[^"]+"""")
private const val NetworkTimeoutMillis = 30_000
private const val MaxPutAttempts = 3
private const val MaxEncryptedAssetBytes = 80 * 1024 * 1024
private const val MaxSyncDocumentBytes = 8 * 1024 * 1024
private const val MaxHttpResponseBytes = MaxEncryptedAssetBytes + 1024
private const val MaxGitHubMetadataBytes = 256 * 1024
private const val MaxErrorBodyChars = 4_096
