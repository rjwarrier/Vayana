package com.vayana.core.sync.asset

import java.io.IOException
import java.net.HttpURLConnection
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
        require(branch.isNotBlank()) { "GitHub branch is required" }
        require(apiBaseUrl.startsWith("https://")) { "GitHub API base URL must use HTTPS" }
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
        val existingSha = findExistingSha(path)
        val body = buildPutBody(path, bytes, existingSha)
        val response = client.execute(
            GitHubHttpRequest(
                method = "PUT",
                url = contentsUrl(path),
                headers = jsonHeaders(),
                body = body.toByteArray(Charsets.UTF_8),
            ),
        )
        if (response.statusCode !in setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED)) {
            throw GitHubAssetStoreException("GitHub asset upload failed", response.statusCode, response.bodyText())
        }
    }

    override suspend fun get(path: String): ByteArray = withContext(dispatcher) {
        validateAssetPath(path)
        val response = client.execute(
            GitHubHttpRequest(
                method = "GET",
                url = contentsUrl(path),
                headers = rawHeaders(),
            ),
        )
        if (response.statusCode != HttpURLConnection.HTTP_OK) {
            throw GitHubAssetStoreException("GitHub asset download failed", response.statusCode, response.bodyText())
        }
        response.body
    }

    private fun findExistingSha(path: String): String? {
        val response = client.execute(
            GitHubHttpRequest(
                method = "GET",
                url = contentsUrl(path),
                headers = jsonHeaders(),
            ),
        )
        return when (response.statusCode) {
            HttpURLConnection.HTTP_OK -> response.bodyText().extractJsonString("sha")
            HttpURLConnection.HTTP_NOT_FOUND -> null
            else -> throw GitHubAssetStoreException(
                message = "GitHub asset metadata lookup failed",
                statusCode = response.statusCode,
                responseBody = response.bodyText(),
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

    private fun buildPutBody(path: String, bytes: ByteArray, existingSha: String?): String {
        val encodedBytes = Base64.getEncoder().encodeToString(bytes)
        val shaProperty = existingSha?.let { ""","sha":"${it.escapeJson()}"""" }.orEmpty()
        return """
            {
              "message":"${"Sync Vayana asset $path".escapeJson()}",
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
)

data class GitHubHttpResponse(
    val statusCode: Int,
    val body: ByteArray,
) {
    fun bodyText(): String = body.toString(Charsets.UTF_8)
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
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        connection.disconnect()
        return GitHubHttpResponse(status, bytes)
    }
}

private fun validateAssetPath(path: String) {
    require(path == CloudAssetLayout.pathFor(path.substringAfterLast('/').removeSuffix(".bin"))) {
        "Invalid cloud asset path"
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

private val GitHubNameRegex = Regex("^[A-Za-z0-9_.-]{1,100}$")
private const val NetworkTimeoutMillis = 30_000
