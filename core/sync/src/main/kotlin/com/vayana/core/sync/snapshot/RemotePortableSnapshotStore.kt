package com.vayana.core.sync.snapshot

import com.vayana.core.backup.PortableSnapshot
import com.vayana.core.backup.PortableSnapshotLatestPath
import com.vayana.core.backup.mergePortableSnapshotSlices
import com.vayana.core.backup.portableSnapshotSlicePaths
import com.vayana.core.backup.toSlicedJsonDocuments
import com.vayana.core.sync.asset.GitHubContentsAssetStore
import kotlinx.coroutines.CancellationException

data class RemotePortableSnapshotDocument(
    val jsonText: String,
    val sha: String,
    val sliced: Boolean,
)

suspend fun GitHubContentsAssetStore.getLatestPortableSnapshotDocument(): RemotePortableSnapshotDocument {
    val latest = getSyncDocumentWithSha(PortableSnapshotLatestPath)
    val latestJson = latest.bytes.toString(Charsets.UTF_8)
    val slicePaths = portableSnapshotSlicePaths(latestJson)
    if (slicePaths.isEmpty()) {
        return RemotePortableSnapshotDocument(jsonText = latestJson, sha = latest.sha, sliced = false)
    }
    val slices = slicePaths.mapValues { (_, path) ->
        getSyncDocument(path).toString(Charsets.UTF_8)
    }
    return RemotePortableSnapshotDocument(
        jsonText = mergePortableSnapshotSlices(latestJson, slices),
        sha = latest.sha,
        sliced = true,
    )
}

suspend fun GitHubContentsAssetStore.putPortableSnapshotDocuments(
    snapshot: PortableSnapshot,
    deviceSnapshotPath: String,
    expectedLatestSha: String?,
) {
    val documents = snapshot.toSlicedJsonDocuments()
    val latestDocument = documents.last { it.path == PortableSnapshotLatestPath }
    putSyncDocument(deviceSnapshotPath, latestDocument.jsonText.toByteArray(Charsets.UTF_8))
    documents
        .asSequence()
        .filter { it.path != PortableSnapshotLatestPath }
        .forEach { document ->
            putSyncDocument(document.path, document.jsonText.toByteArray(Charsets.UTF_8))
        }
    putSyncDocumentIfUnchanged(
        path = PortableSnapshotLatestPath,
        bytes = latestDocument.jsonText.toByteArray(Charsets.UTF_8),
        expectedSha = expectedLatestSha,
    )
    try {
        pruneOlderPortableSnapshotSlices(currentExportedAt = snapshot.exportedAt)
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
    }
}

internal suspend fun GitHubContentsAssetStore.pruneOlderPortableSnapshotSlices(currentExportedAt: Long) {
    val retainedExportTimes = listSyncDocumentDirectory(SnapshotSlicesRoot)
        .asSequence()
        .filter { it.type == "dir" }
        .mapNotNull { it.name.toLongOrNull() }
        .filter { it > 0L }
        .plus(currentExportedAt)
        .distinct()
        .sortedDescending()
        .take(RetainedSnapshotSliceSets)
        .toSet()
    val staleDirectories = listSyncDocumentDirectory(SnapshotSlicesRoot)
        .asSequence()
        .filter { it.type == "dir" }
        .mapNotNull { entry -> entry.name.toLongOrNull()?.takeIf { it > 0L } }
        .filterNot { it in retainedExportTimes }
        .toList()
    staleDirectories.forEach { exportedAt ->
        val allowedPaths = SnapshotSliceFileNames.mapTo(mutableSetOf()) { fileName -> "$SnapshotSlicesRoot/$exportedAt/$fileName" }
        listSyncDocumentDirectory("$SnapshotSlicesRoot/$exportedAt")
            .asSequence()
            .filter { it.type == "file" }
            .map { it.path }
            .filter { path -> path in allowedPaths }
            .forEach { path -> deleteSyncDocumentIfExists(path) }
    }
}

private const val SnapshotSlicesRoot = "vayana/snapshot-slices"
private const val RetainedSnapshotSliceSets = 3
private val SnapshotSliceFileNames = setOf(
    "annotations.json",
    "shelves.json",
    "shelf-memberships.json",
    "reading-sessions.json",
    "vocabulary-cards.json",
    "word-lookup-counters.json",
    "book-aliases.json",
    "tombstones.json",
)
