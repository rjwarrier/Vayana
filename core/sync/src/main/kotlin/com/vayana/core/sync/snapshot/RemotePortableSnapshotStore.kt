package com.vayana.core.sync.snapshot

import com.vayana.core.backup.PortableReadingProgressPatch
import com.vayana.core.backup.PortableReadingSession
import com.vayana.core.backup.PortableSnapshot
import com.vayana.core.backup.PortableSnapshotLatestPath
import com.vayana.core.backup.PortableTombstone
import com.vayana.core.backup.PortableWordLookupCounter
import com.vayana.core.backup.patchPortableReadingProgressOnly
import com.vayana.core.backup.patchSlicedPortableReadingProgress
import com.vayana.core.backup.portableSnapshotSliceDataEquals
import com.vayana.core.backup.portableSnapshotSlicePaths
import com.vayana.core.backup.repointPortableSnapshotSlices
import com.vayana.core.backup.toSlicedJsonDocuments
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticsLogStore
import com.vayana.core.sync.asset.GitHubAssetStoreException
import com.vayana.core.sync.asset.GitHubContentsAssetStore
import java.util.concurrent.ConcurrentHashMap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The latest snapshot pointer plus lazy access to its slices. A slice is downloaded the first time someone
 * asks for it, so a caller that only needs positions (the manifest's "books") never pays for annotations or
 * sessions. A missing (404) slice falls back to the manifest text, as before.
 */
class RemotePortableSnapshotDocument internal constructor(
    val jsonText: String,
    val sha: String,
    val sliced: Boolean,
    sliceJsonByKey: Map<String, String> = emptyMap(),
    private val slicePaths: Map<String, String> = emptyMap(),
    private val loadSlice: (suspend (String) -> String)? = null,
) {
    constructor(jsonText: String, sha: String, sliced: Boolean) : this(jsonText, sha, sliced, emptyMap())

    private val loadedSlices = ConcurrentHashMap(sliceJsonByKey)
    private val missingSlices: MutableSet<String> = ConcurrentHashMap.newKeySet()

    suspend fun jsonFor(slice: RemotePortableSnapshotSlice): String = sliceJsonOrNull(slice.key) ?: jsonText

    /** Downloads the given slices concurrently, for callers about to read most of them. */
    suspend fun prefetch(slices: Collection<RemotePortableSnapshotSlice> = RemotePortableSnapshotSlice.entries) {
        coroutineScope {
            slices.filter { it.key in slicePaths && !loadedSlices.containsKey(it.key) }
                .map { slice -> async { sliceJsonOrNull(slice.key) } }
                .awaitAll()
        }
    }

    /** The slice's own document, or null when this snapshot has no such slice or it is gone (404). */
    internal suspend fun sliceJsonOrNull(key: String): String? {
        loadedSlices[key]?.let { return it }
        if (key in missingSlices) return null
        val loader = loadSlice ?: return null
        val path = slicePaths[key] ?: return null
        val json = loadSnapshotSliceOrNull(path, loader)
        if (json == null) missingSlices += key else loadedSlices[key] = json
        return json
    }

    /** A slice already in memory, without triggering a download. */
    internal fun loadedSliceJsonOrNull(key: String): String? = loadedSlices[key]

    internal fun slicePathOrNull(key: String): String? = slicePaths[key]
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
    val latest = getSyncDocumentWithShaUnless(PortableSnapshotLatestPath, skipSha) ?: return null
    val latestJson = latest.bytes.toString(Charsets.UTF_8)
    return remotePortableSnapshotDocumentFrom(
        latestJson = latestJson,
        sha = latest.sha,
        slicePaths = portableSnapshotSlicePaths(latestJson),
        loadSlice = { path -> getSnapshotSliceCached(path) },
    )
}

internal fun remotePortableSnapshotDocumentFrom(
    latestJson: String,
    sha: String,
    slicePaths: Map<String, String>,
    loadSlice: suspend (String) -> String,
): RemotePortableSnapshotDocument =
    if (slicePaths.isEmpty()) {
        RemotePortableSnapshotDocument(jsonText = latestJson, sha = sha, sliced = false)
    } else {
        RemotePortableSnapshotDocument(
            jsonText = latestJson,
            sha = sha,
            sliced = true,
            slicePaths = slicePaths,
            loadSlice = loadSlice,
        )
    }

/**
 * Slice files are written once to a timestamped path and never rewritten (see [putNewSyncDocument]), so a
 * slice read by path stays valid for the life of the process and repeat syncs skip the download entirely.
 */
private suspend fun GitHubContentsAssetStore.getSnapshotSliceCached(path: String): String {
    val key = "$cacheScope|$path"
    SnapshotSliceCache.get(key)?.let { return it }
    val bytes = getSyncDocument(path)
    val json = if (path.endsWith(".zip")) unzipSnapshotJson(bytes) else bytes.toString(Charsets.UTF_8)
    SnapshotSliceCache.put(key, json)
    return json
}

internal fun zipSnapshotJson(json: String): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry(AnnotationsZipEntryName))
        zip.write(json.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    return output.toByteArray()
}

internal fun unzipSnapshotJson(bytes: ByteArray): String {
    val output = ByteArrayOutputStream()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        val entry = zip.nextEntry ?: error("Compressed snapshot slice is empty")
        require(!entry.isDirectory && entry.name == AnnotationsZipEntryName) { "Invalid compressed snapshot slice" }
        val buffer = ByteArray(ZipBufferBytes)
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            require(output.size() <= MaxUncompressedSliceBytes) { "Compressed snapshot slice is too large" }
        }
        zip.closeEntry()
        require(zip.nextEntry == null) { "Compressed snapshot slice has unexpected entries" }
    }
    return output.toByteArray().toString(Charsets.UTF_8)
}

/** Process-wide, size-bounded LRU of slice texts keyed by repository scope and path. */
internal object SnapshotSliceCache {
    private const val MaxTotalChars = 6 * 1024 * 1024
    private const val MaxEntryChars = 3 * 1024 * 1024
    private val entries = LinkedHashMap<String, String>(16, 0.75f, true)
    private var totalChars = 0

    @Synchronized
    fun get(key: String): String? = entries[key]

    @Synchronized
    fun put(key: String, json: String) {
        if (json.length > MaxEntryChars) return
        entries.put(key, json)?.let { totalChars -= it.length }
        totalChars += json.length
        val iterator = entries.entries.iterator()
        while (totalChars > MaxTotalChars && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest.key == key) continue
            totalChars -= eldest.value.length
            iterator.remove()
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        totalChars = 0
    }
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
    // Only slices with local data to merge are worth downloading; the rest can't change.
    val neededSliceKeys = buildList {
        if (readingSessions.isNotEmpty()) add(RemotePortableSnapshotSlice.ReadingSessions.key)
        if (wordLookupCounters.isNotEmpty()) add(RemotePortableSnapshotSlice.WordLookupCounters.key)
        if (tombstones.isNotEmpty()) add(RemotePortableSnapshotSlice.Tombstones.key)
    }
    val sliceJsonByKey = neededSliceKeys.mapNotNull { key -> remote.sliceJsonOrNull(key)?.let { key to it } }.toMap()
    val result = patchSlicedPortableReadingProgress(
        manifestJson = remote.jsonText,
        sliceJsonByKey = sliceJsonByKey,
        patches = patches,
        exportedAt = exportedAt,
        readingSessions = readingSessions,
        wordLookupCounters = wordLookupCounters,
        tombstones = tombstones,
    )
    if (result.changed > 0) {
        result.sliceWrites.forEach { (path, jsonText) ->
            putNewSyncDocument(path, jsonText.toByteArray(Charsets.UTF_8))
            SnapshotSliceCache.put("$cacheScope|$path", jsonText)
        }
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
    /**
     * The snapshot this publish replaces. A slice whose records match one of its slices that is already in
     * memory keeps pointing at that published file instead of being uploaded (and committed) again.
     */
    previous: RemotePortableSnapshotDocument? = null,
) {
    val documents = snapshot.toSlicedJsonDocuments()
    val latestDocument = documents.last { it.path == PortableSnapshotLatestPath }
    val allSliceDocuments = documents.filter { it.path != PortableSnapshotLatestPath }
    val compressAnnotations = snapshot.hasLargeGoodreadsQuoteSet()
    val reusedPathsByKey = allSliceDocuments.mapNotNull { document ->
        val key = document.sliceKey ?: return@mapNotNull null
        val previousJson = previous?.loadedSliceJsonOrNull(key) ?: return@mapNotNull null
        val previousPath = previous.slicePathOrNull(key) ?: return@mapNotNull null
        if (key == RemotePortableSnapshotSlice.Annotations.key && previousPath.endsWith(".zip") != compressAnnotations) {
            return@mapNotNull null
        }
        if (portableSnapshotSliceDataEquals(key, document.jsonText, previousJson)) key to previousPath else null
    }.toMap()
    val sliceDocuments = allSliceDocuments.filter { it.sliceKey !in reusedPathsByKey }
    val sliceUploads = sliceDocuments.map { document ->
        val zipped = compressAnnotations && document.sliceKey == RemotePortableSnapshotSlice.Annotations.key
        SnapshotSliceUpload(
            sliceKey = requireNotNull(document.sliceKey),
            path = if (zipped) "$SnapshotSlicesRoot/${snapshot.exportedAt}/annotations.zip" else document.path,
            bytes = if (zipped) zipSnapshotJson(document.jsonText) else document.jsonText.toByteArray(Charsets.UTF_8),
            jsonText = document.jsonText,
        )
    }
    val publishedPathsByKey = reusedPathsByKey + sliceUploads.associate { it.sliceKey to it.path }
    val latestBytes = repointPortableSnapshotSlices(latestDocument.jsonText, publishedPathsByKey).toByteArray(Charsets.UTF_8)
    val totalDocuments = sliceUploads.size + 2
    val totalBytes = sliceUploads.sumOf { it.bytes.size } + latestBytes.size + latestBytes.size
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
    sliceUploads.forEach { upload ->
        putNewSyncDocument(upload.path, upload.bytes)
        SnapshotSliceCache.put("$cacheScope|${upload.path}", upload.jsonText)
        completedDocuments += 1
        completedBytes += upload.bytes.size
        report(PortableSnapshotPublishStage.SNAPSHOT_SLICE, upload.path)
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
            append(", reusedSlices=").append(reusedPathsByKey.size)
            append(", latestBytes=").append(latestBytes.size)
            append(", sliceBytes=").append(sliceUploads.sumOf { it.bytes.size })
            append(", compressedAnnotations=").append(compressAnnotations)
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

private data class SnapshotSliceUpload(
    val sliceKey: String,
    val path: String,
    val bytes: ByteArray,
    val jsonText: String,
)

internal fun PortableSnapshot.hasLargeGoodreadsQuoteSet(): Boolean {
    val countsByBook = HashMap<String, Int>()
    annotations.forEach { annotation ->
        if (
            !annotation.isDeleted &&
            (annotation.locator.startsWith("goodreads-quote:") || annotation.locator.startsWith("quote:"))
        ) {
            val count = (countsByBook[annotation.bookSyncId] ?: 0) + 1
            if (count > CompressedAnnotationsQuoteThreshold) return true
            countsByBook[annotation.bookSyncId] = count
        }
    }
    return false
}

private const val SnapshotSlicesRoot = "vayana/snapshot-slices"
private const val RetainedSnapshotSliceSets = 3
private const val CompressedAnnotationsQuoteThreshold = 50
private const val AnnotationsZipEntryName = "annotations.json"
private const val MaxUncompressedSliceBytes = 16 * 1024 * 1024
private const val ZipBufferBytes = 16 * 1024
private val SnapshotSliceFileNames = setOf(
    "annotations.json",
    "annotations.zip",
    "shelves.json",
    "shelf-memberships.json",
    "reading-sessions.json",
    "vocabulary-cards.json",
    "word-lookup-counters.json",
    "book-aliases.json",
    "tombstones.json",
)
