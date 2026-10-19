package com.vayana.core.sync.history

import com.vayana.core.sync.asset.GitHubSnapshotCommit

/** Where one published version of the reading sessions lives, and which device published it. */
internal data class SessionsVersion(val blobSha: String, val zipped: Boolean, val device: String)

/**
 * The sessions document each commit published, oldest first, with runs of the same document collapsed. A slice
 * file is never rewritten, so the newest slice folder holding a reading-sessions file is the one the commit's
 * pointer names; before slices, the sessions lived in the pointer itself.
 */
internal fun sessionsVersions(commitsOldestFirst: List<GitHubSnapshotCommit>): List<SessionsVersion> {
    val versions = mutableListOf<SessionsVersion>()
    commitsOldestFirst.forEach { commit ->
        val slice = commit.sliceFolders.entries
            .mapNotNull { (folder, files) -> files.entries.firstOrNull { it.key.startsWith(SessionsSliceFilePrefix) }?.let { folder to it } }
            .maxByOrNull { (folder, _) -> folder.toLongOrNull() ?: Long.MIN_VALUE }
            ?.second
        val version = when {
            slice != null -> SessionsVersion(slice.value, slice.key.endsWith(".zip"), commit.author)
            commit.manifestBlobSha != null -> SessionsVersion(commit.manifestBlobSha, zipped = false, device = commit.author)
            else -> return@forEach
        }
        if (versions.lastOrNull()?.blobSha != version.blobSha) versions += version
    }
    return versions
}

/**
 * Which device recorded each session: the author of the first published version that holds it. A device only
 * publishes sessions it has, and it has another device's sessions only after that device published them, so the
 * first publisher is the recorder.
 *
 * Reading every version would download hundreds of documents, so this bisects: between two read versions, the
 * sessions new in the later one were first published somewhere in between, and when one device published every
 * version in that stretch it is the recorder without reading the middle. Only stretches where devices took turns
 * and sessions were added are split. Sessions only ever accumulate (a removed session leaves a tombstone instead),
 * which is what makes the shortcut sound.
 */
internal suspend fun creditSessionsToDevices(
    versions: List<SessionsVersion>,
    sessionIds: suspend (SessionsVersion) -> Set<String>,
): Map<String, String> {
    if (versions.isEmpty()) return emptyMap()
    val loaded = HashMap<Int, Set<String>>()
    suspend fun ids(index: Int): Set<String> = loaded.getOrPut(index) { sessionIds(versions[index]) }
    val devices = HashMap<String, String>()
    fun credit(ids: Collection<String>, device: String) = ids.forEach { devices.putIfAbsent(it, device) }

    credit(ids(0), versions[0].device)
    // Explicit stack, earlier stretch first, so the earliest publisher wins and deep histories can't overflow.
    val stack = ArrayDeque<Pair<Int, Int>>().apply { addLast(0 to versions.lastIndex) }
    while (stack.isNotEmpty()) {
        val (low, high) = stack.removeLast()
        if (high <= low) continue
        val added = ids(high) - ids(low)
        if (added.isEmpty()) continue
        val publishers = (low + 1..high).mapTo(HashSet()) { versions[it].device }
        if (publishers.size == 1 || high == low + 1) {
            credit(added, versions[high].device)
            continue
        }
        val middle = (low + high) / 2
        stack.addLast(middle to high)
        stack.addLast(low to middle)
    }
    return devices
}

private const val SessionsSliceFilePrefix = "reading-sessions."
