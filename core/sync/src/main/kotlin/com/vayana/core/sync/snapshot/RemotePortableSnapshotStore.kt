package com.vayana.core.sync.snapshot

import com.vayana.core.backup.PortableReadingProgressPatch
import com.vayana.core.backup.PortableReadingSession
import com.vayana.core.backup.PortableSnapshot
import com.vayana.core.backup.PortableSnapshotLatestPath
import com.vayana.core.backup.PortableTombstone
import com.vayana.core.backup.PortableWordLookupCounter
import com.vayana.core.backup.patchPortableReadingProgressOnly
import com.vayana.core.backup.patchSlicedPortableReadingProgress
import com.vayana.core.backup.portableSnapshotSlicePaths
import com.vayana.core.backup.toSlicedJsonDocuments
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticsLogStore
import com.vayana.core.sync.asset.GitHubAssetStoreException
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

enum class PortableSnapshotPublishStage {
    DEVICE_SNAPSHOT,
    SNAPSHOT_SLICE,
    LATEST_POINTER,
    PRUNING,
}

data class PortableSnapshotPublishProgress(
    val stage: PortableSnapshotPublishStage,
    val completedDocuments: Int,
    val totalDocuments: Int,
    val completedBytes: Int,
    val totalBytes: Int,
    val currentPath: String? = null,
) {
    val fraction: Float = when {
        totalBytes > 0 -> (completedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        totalDocuments > 0 -> (completedDocuments.toFloat() / totalDocuments.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
}

suspend fun GitHubContentsAssetStore.getLatestPortableSnapshotDocument(): RemotePortableSnapshotDocument {
    return getLatestPortableSnapshotDocumentUnlessSha(skipSha = null) ?: error("Latest snapshot unexpectedly skipped")
}

suspend fun GitHubContentsAssetStore.getLatestPortableSnapshotDocumentUnlessSha(
    skipSha: String?,
): RemotePortableSnapshotDocument? {
    val latest = getSyncDocumentWithSha(PortableSnapshotLatestPath)
    if (skipSha != null && latest.sha == skipSha) return null
    val latestJson = latest.bytes.toString(Charsets.UTF_8)
    return remotePortableSnapshotDocumentFrom(
        latestJson = latestJson,
        sha = latest.sha,
        slicePaths = portableSnapshotSlicePaths(latestJson),
        loadSlice = { path -> getSyncDocument(path).toString(Charsets.UTF_8) },
    )
}

internal suspend fun remotePortableSnapshotDocumentFrom(
    latestJson: String,
    sha: String,
    slicePaths: Map<String, String>,
    loadSlice: suspend (String) -> String,
): RemotePortableSnapshotDocument {
    if (slicePaths.isEmpty()) {
        return RemotePortableSnapshotDocument(jsonText = latestJson, sha = sha, sliced = false)
    }
    val slices = slicePaths.mapNotNull { (key, path) ->
        val jsonText = loadSnapshotSliceOrNull(path, loadSlice) ?: return@mapNotNull null
        key to jsonText
    }.toMap()
    return RemotePortableSnapshotDocument(
        jsonText = latestJson,
        sha = sha,
        sliced = true,
        sliceJsonByKey = slices,
    )
}

private suspend fun loadSnapshotSliceOrNull(path: String, loadSlice: suspend (String) -> String): String? =
    try {
        loadSlice(path)
    } catch (throwable: GitHubAssetStoreException) {
        if (throwable.statusCode == 404) null else throw throwable
    }

data class PortableReadingProgressPushResult(
    val patched: Int = 0,
    val sessionsAdded: Int = 0,
    val wordLookupCountersMerged: Int = 0,
    val tombstonesMerged: Int = 0,
) {
    val pushed: Int
        get() = patched + sessionsAdded + wordLookupCountersMerged + tombstonesMerged
}

/**
 * Pushes local reading positions, sessions, word-lookup counters and tombstones on top of [remote], for both
 * single-document and sliced snapshots. The latest pointer is written with [RemotePortableSnapshotDocument.sha]
 * as the expected SHA, so a concurrent writer surfaces as a 409 [GitHubAssetStoreException] for the caller to
 * re-fetch and retry; slice files written before a lost race are harmless orphans that pruning removes.
 */
suspend fun GitHubContentsAssetStore.pushPortableReadingProgress(
    remote: RemotePortableSnapshotDocument,
    patches: List<PortableReadingProgressPatch>,
    exportedAt: Long,
    readingSessions: List<PortableReadingSession> = emptyList(),
    wordLookupCounters: List<PortableWordLookupCounter> = emptyList(),
    tombstones: List<PortableTombstone> = emptyList(),
): PortableReadingProgressPushResult {
    if (!remote.sliced) {
        val result = patchPortableReadingProgressOnly(
            jsonText = remote.jsonText,
            patches = patches,
            exportedAt = exportedAt,
            readingSessions = readingSessions,
            wordLookupCounters = wordLookupCounters,
            tombstones = tombstones,
        )
        val pushResult = PortableReadingProgressPushResult(
            patched = result.patched,
            sessionsAdded = result.sessionsAdded,
            wordLookupCountersMerged = result.wordLookupCountersMerged,
            tombstonesMerged = result.tombstonesMerged,
        )
        if (pushResult.pushed > 0) {
            putSyncDocumentIfUnchanged(PortableSnapshotLatestPath, result.jsonText.toByteArray(Charsets.UTF_8), remote.sha)
        }
        return pushResult
    }
    val result = patchSlicedPortableReadingProgress(
        manifestJson = remote.jsonText,
        sliceJsonByKey = remote.sliceJsonByKey,
        patches = patches,
        exportedAt = exportedAt,
        readingSessions = readingSessions,
        wordLookupCounters = wordLookupCounters,
        tombstones = tombstones,
    )
    if (result.changed > 0) {
        result.sliceWrites.forEach { (path, jsonText) -> putSyncDocument(path, jsonText.toByteArray(Charsets.UTF_8)) }
        putSyncDocumentIfUnchanged(PortableSnapshotLatestPath, result.manifestJson.toByteArray(Charsets.UTF_8), remote.sha)
    }
    return PortableReadingProgressPushResult(
        patched = result.patched,
        sessionsAdded = result.sessionsAdded,
        wordLookupCountersMerged = result.wordLookupCountersMerged,
        tombstonesMerged = result.tombstonesMerged,
    )
}

suspend fun GitHubContentsAssetStore.putPortableSnapshotDocuments(
    snapshot: PortableSnapshot,
    deviceSnapshotPath: String,
    expectedLatestSha: String?,
    diagnosticsLogStore: DiagnosticsLogStore? = null,
    onProgress: ((PortableSnapshotPublishProgress) -> Unit)? = null,
) {
    val documents = snapshot.toSlicedJsonDocuments()
    val latestDocument = documents.last { it.path == PortableSnapshotLatestPath }
    val sliceDocuments = documents.filter { it.path != PortableSnapshotLatestPath }
    val latestBytes = latestDocument.jsonText.toByteArray(Charsets.UTF_8)
    val sliceBytes = sliceDocuments.map { document -> document.path to document.jsonText.toByteArray(Charsets.UTF_8) }
    val totalDocuments = sliceBytes.size + 2
    val totalBytes = sliceBytes.sumOf { it.second.size } + latestBytes.size + latestBytes.size
    var completedDocuments = 0
    var completedBytes = 0
    fun report(stage: PortableSnapshotPublishStage, path: String? = null) {
        onProgress?.invoke(
            PortableSnapshotPublishProgress(
                stage = stage,
                completedDocuments = completedDocuments,
                totalDocuments = totalDocuments,
                completedBytes = completedBytes,
                totalBytes = totalBytes,
                currentPath = path,
            ),
        )
    }
    putSyncDocument(deviceSnapshotPath, latestBytes)
    completedDocuments += 1
    completedBytes += latestBytes.size
    report(PortableSnapshotPublishStage.DEVICE_SNAPSHOT, deviceSnapshotPath)
    sliceBytes.forEach { (path, bytes) ->
        putSyncDocument(path, bytes)
        completedDocuments += 1
        completedBytes += bytes.size
        report(PortableSnapshotPublishStage.SNAPSHOT_SLICE, path)
    }
    putSyncDocumentIfUnchanged(
        path = PortableSnapshotLatestPath,
        bytes = latestBytes,
        expectedSha = expectedLatestSha,
    )
    completedDocuments += 1
    completedBytes += latestBytes.size
    report(PortableSnapshotPublishStage.LATEST_POINTER, PortableSnapshotLatestPath)
    report(PortableSnapshotPublishStage.PRUNING)
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
    // Whatever the live latest pointer references must survive, whatever its timestamp: a device with a
    // lagging clock (or a progress-only push reusing older slice files) can publish slice dirs that sort
    // below other devices' dirs. Read the pointer after our own publish so a newer one from elsewhere wins.
    val referencedExportTimes = portableSnapshotSlicePaths(getSyncDocument(PortableSnapshotLatestPath).toString(Charsets.UTF_8))
        .values
        .mapNotNullTo(mutableSetOf()) { path -> path.removePrefix("$SnapshotSlicesRoot/").substringBefore('/').toLongOrNull() }
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
        .toSet() + currentExportedAt + referencedExportTimes
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
