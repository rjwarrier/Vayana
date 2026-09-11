package com.vayana.core.sync.snapshot

import com.vayana.core.backup.PortableSnapshot
import com.vayana.core.backup.PortableSnapshotLatestPath
import com.vayana.core.backup.portableSnapshotSlicePaths
import com.vayana.core.backup.toSlicedJsonDocuments
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticsLogStore
import com.vayana.core.sync.asset.GitHubContentsAssetStore
import kotlinx.coroutines.CancellationException

data class RemotePortableSnapshotDocument(
    val jsonText: String,
    val sha: String,
    val sliced: Boolean,
    internal val sliceJsonByKey: Map<String, String> = emptyMap(),
) {
    fun jsonFor(slice: RemotePortableSnapshotSlice): String =
        sliceJsonByKey[slice.key] ?: jsonText
}

enum class RemotePortableSnapshotSlice(val key: String) {
    Books("books"),
    Annotations("annotations"),
    Shelves("shelves"),
    ShelfMemberships("shelfMemberships"),
    ReadingSessions("readingSessions"),
    VocabularyCards("vocabularyCards"),
    WordLookupCounters("wordLookupCounters"),
    BookAliases("bookAliases"),
    Tombstones("tombstones"),
}

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
        jsonText = latestJson,
        sha = latest.sha,
        sliced = true,
        sliceJsonByKey = slices,
    )
}

suspend fun GitHubContentsAssetStore.putPortableSnapshotDocuments(
    snapshot: PortableSnapshot,
    deviceSnapshotPath: String,
    expectedLatestSha: String?,
    diagnosticsLogStore: DiagnosticsLogStore? = null,
) {
    val documents = snapshot.toSlicedJsonDocuments()
    val latestDocument = documents.last { it.path == PortableSnapshotLatestPath }
    val sliceDocuments = documents.filter { it.path != PortableSnapshotLatestPath }
    val latestBytes = latestDocument.jsonText.toByteArray(Charsets.UTF_8)
    val sliceBytes = sliceDocuments.map { document -> document.path to document.jsonText.toByteArray(Charsets.UTF_8) }
    putSyncDocument(deviceSnapshotPath, latestBytes)
    sliceBytes.forEach { (path, bytes) ->
        putSyncDocument(path, bytes)
    }
    putSyncDocumentIfUnchanged(
        path = PortableSnapshotLatestPath,
        bytes = latestBytes,
        expectedSha = expectedLatestSha,
    )
    var pruneSummary = SnapshotSlicePruneSummary()
    try {
        pruneSummary = pruneOlderPortableSnapshotSlices(currentExportedAt = snapshot.exportedAt)
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        pruneSummary = SnapshotSlicePruneSummary(failed = true)
    }
    diagnosticsLogStore?.record(
        category = DiagnosticCategory.SYNC,
        source = "RemotePortableSnapshotStore.putPortableSnapshotDocuments",
        message = "Published sliced snapshot",
        detail = buildString {
            append("exportedAt=").append(snapshot.exportedAt)
            append(", books=").append(snapshot.books.size)
            append(", slices=").append(sliceDocuments.size)
            append(", latestBytes=").append(latestBytes.size)
            append(", sliceBytes=").append(sliceBytes.sumOf { it.second.size })
            append(", prunedDirs=").append(pruneSummary.directoriesPruned)
            append(", prunedFiles=").append(pruneSummary.filesPruned)
            append(", pruneFailed=").append(pruneSummary.failed)
        },
    )
}

internal suspend fun GitHubContentsAssetStore.pruneOlderPortableSnapshotSlices(currentExportedAt: Long): SnapshotSlicePruneSummary {
    val sliceRootEntries = listSyncDocumentDirectory(SnapshotSlicesRoot)
    val retainedExportTimes = sliceRootEntries
        .asSequence()
        .filter { it.type == "dir" }
        .mapNotNull { it.name.toLongOrNull() }
        .filter { it > 0L }
        .plus(currentExportedAt)
        .distinct()
        .sortedDescending()
        .take(RetainedSnapshotSliceSets)
        .toSet()
    val staleDirectories = sliceRootEntries
        .asSequence()
        .filter { it.type == "dir" }
        .mapNotNull { entry -> entry.name.toLongOrNull()?.takeIf { it > 0L } }
        .filterNot { it in retainedExportTimes }
        .toList()
    var filesPruned = 0
    staleDirectories.forEach { exportedAt ->
        val allowedPaths = SnapshotSliceFileNames.mapTo(mutableSetOf()) { fileName -> "$SnapshotSlicesRoot/$exportedAt/$fileName" }
        listSyncDocumentDirectory("$SnapshotSlicesRoot/$exportedAt")
            .forEach { entry ->
                val sha = entry.sha ?: return@forEach
                if (entry.type == "file" && entry.path in allowedPaths) {
                    deleteSyncDocument(entry.path, sha)
                    filesPruned += 1
                }
            }
    }
    return SnapshotSlicePruneSummary(
        directoriesPruned = staleDirectories.size,
        filesPruned = filesPruned,
    )
}

internal data class SnapshotSlicePruneSummary(
    val directoriesPruned: Int = 0,
    val filesPruned: Int = 0,
    val failed: Boolean = false,
)

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
