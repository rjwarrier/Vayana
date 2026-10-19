package com.vayana.core.sync.asset

import org.json.JSONArray
import org.json.JSONObject

/** A commit that changed the latest snapshot pointer. */
data class GitHubSnapshotCommit(
    val sha: String,
    val committedAt: String,
    /** The commit author: the publishing device's sync name. */
    val author: String,
    val manifestBlobSha: String?,
    /** Slice folder name (a publish timestamp) to the files in it, by file name to blob id. */
    val sliceFolders: Map<String, Map<String, String>>,
)

data class GitHubSnapshotHistoryPage(val commits: List<GitHubSnapshotCommit>, val nextCursor: String?)

internal const val SnapshotHistoryQuery = """
query(${'$'}owner: String!, ${'$'}name: String!, ${'$'}branch: String!, ${'$'}first: Int!, ${'$'}cursor: String) {
  repository(owner: ${'$'}owner, name: ${'$'}name) {
    ref(qualifiedName: ${'$'}branch) {
      target {
        ... on Commit {
          history(first: ${'$'}first, after: ${'$'}cursor, path: "vayana/snapshot-latest.json") {
            pageInfo { hasNextPage endCursor }
            nodes {
              oid
              committedDate
              author { name }
              committer { name }
              manifest: file(path: "vayana/snapshot-latest.json") { oid }
              slices: file(path: "vayana/snapshot-slices") {
                object { ... on Tree { entries { name object { ... on Tree { entries { name oid } } } } } }
              }
            }
          }
        }
      }
    }
  }
}
"""

internal fun parseSnapshotHistoryPage(jsonText: String): GitHubSnapshotHistoryPage {
    val root = JSONObject(jsonText)
    val history = root.optJSONObject("data")?.optJSONObject("repository")?.optJSONObject("ref")
        ?.optJSONObject("target")?.optJSONObject("history")
        ?: error("GitHub history response had no history: ${root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")}")
    val commits = history.optJSONArray("nodes").objects().mapNotNull { node ->
        val author = node.optJSONObject("author")?.optStringOrNull("name")
            ?: node.optJSONObject("committer")?.optStringOrNull("name")
            ?: return@mapNotNull null
        val folders = node.optJSONObject("slices")?.optJSONObject("object")?.optJSONArray("entries").objects()
            .mapNotNull { folder ->
                val files = folder.optJSONObject("object")?.optJSONArray("entries") ?: return@mapNotNull null
                folder.optString("name") to files.objects().associate { it.optString("name") to it.optString("oid") }
            }
            .toMap()
        GitHubSnapshotCommit(
            sha = node.optString("oid"),
            committedAt = node.optString("committedDate"),
            author = author,
            manifestBlobSha = node.optJSONObject("manifest")?.optStringOrNull("oid"),
            sliceFolders = folders,
        )
    }
    val pageInfo = history.optJSONObject("pageInfo")
    val next = pageInfo?.optStringOrNull("endCursor")?.takeIf { pageInfo.optBoolean("hasNextPage") }
    return GitHubSnapshotHistoryPage(commits, next)
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
