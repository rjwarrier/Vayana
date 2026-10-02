package com.vayana.core.homelibrary

import java.util.concurrent.TimeUnit

/** How one sync run ended. */
sealed interface HomeLibrarySyncResult {
    /** Changes were applied locally and the checkpoint advanced. */
    data class Synced(val created: Int, val updated: Int, val deleted: Int) : HomeLibrarySyncResult

    /** Home Library's `/info` matched the last sync, so nothing was queried. */
    data object UpToDate : HomeLibrarySyncResult

    /** Queried, but nothing came back (or nothing could be trusted): local data is untouched. */
    data object NoChanges : HomeLibrarySyncResult

    /** Home Library is not installed, or refused the query. */
    data object NotConnected : HomeLibrarySyncResult

    /** Home Library answered `/info` with no row: the user turned sharing off there. */
    data object SharingOff : HomeLibrarySyncResult

    /** Home Library speaks a newer catalog schema than this build understands. */
    data class UnsupportedSchema(val schemaVersion: Int) : HomeLibrarySyncResult
}

/**
 * Mirrors Home Library's catalog into Vayana's offline books, read-only on Home Library's side.
 *
 * A run reads `/info`, pages through `/books` into memory, applies everything in one local transaction, and only then
 * saves the new checkpoint - so a failed transaction leaves the checkpoint where it was and the next run retries.
 */
class HomeLibrarySyncEngine(
    private val source: HomeLibrarySource,
    private val store: HomeLibraryStore,
    private val checkpoints: HomeLibraryCheckpointStore,
    private val log: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** [force] skips the "nothing changed" shortcut (a manual "Sync now"). */
    suspend fun sync(force: Boolean = false): HomeLibrarySyncResult {
        val info = try {
            source.info() ?: return HomeLibrarySyncResult.SharingOff
        } catch (_: HomeLibraryUnavailableException) {
            return HomeLibrarySyncResult.NotConnected
        }
        if (info.schemaVersion > HomeLibraryContract.SUPPORTED_SCHEMA_VERSION) {
            log("Home Library catalog schema ${info.schemaVersion} is newer than supported ${HomeLibraryContract.SUPPORTED_SCHEMA_VERSION}; not syncing")
            return HomeLibrarySyncResult.UnsupportedSchema(info.schemaVersion)
        }

        val now = clock()
        val checkpoint = checkpoints.read()
        val lastUpdatedAt = checkpoint.lastSyncUpdatedAt
        val lastSyncedAt = checkpoint.lastSyncedAt
        // Tombstones live 90 days: past that (or with no stored sync point) a deletion may have been missed.
        val fullResync = lastUpdatedAt == null || lastSyncedAt == null || now - lastSyncedAt > TombstoneRetentionMillis
        if (!fullResync && !force && checkpoint.matches(info)) {
            checkpoints.save(checkpoint.copy(lastSyncedAt = now))
            return HomeLibrarySyncResult.UpToDate
        }

        val rows = try {
            fetchAll(since = if (fullResync) null else lastUpdatedAt)
        } catch (_: HomeLibraryUnavailableException) {
            return HomeLibrarySyncResult.NotConnected
        }
        val live = rows.values.filterNot { it.deleted }
        when {
            // An empty cursor means "no changes" (sharing may have just been switched off), never "delete everything".
            // The one exception is a full resync against a catalog that says it holds no books.
            rows.isEmpty() && !(fullResync && info.bookCount == 0) -> return HomeLibrarySyncResult.NoChanges
            // /info promised books but none came back live: don't wipe the mirror on a half-answer.
            fullResync && live.isEmpty() && info.bookCount > 0 -> return HomeLibrarySyncResult.NoChanges
        }

        val applied = store.apply(
            HomeLibraryChanges(
                upserts = live,
                deletes = rows.values.filter { it.deleted }.mapTo(HashSet()) { it.syncUuid },
                retainOnly = if (fullResync) live.mapTo(HashSet()) { it.syncUuid } else null,
            ),
        )
        // Only after the transaction committed.
        checkpoints.save(
            HomeLibraryCheckpoint(
                lastSyncUpdatedAt = maxOf(lastUpdatedAt ?: 0L, rows.values.maxOfOrNull { it.updatedAt } ?: 0L),
                lastSyncedAt = now,
                infoBookCount = info.bookCount,
                infoMaxUpdatedAt = info.maxUpdatedAt,
            ),
        )
        return HomeLibrarySyncResult.Synced(applied.created, applied.updated, applied.deleted)
    }

    /**
     * Every row after [since], page by page, oldest first. A later row for the same book replaces an earlier one, so a
     * delete followed by a re-creation (or two edits) collapses to its final state.
     */
    private suspend fun fetchAll(since: Long?): Map<String, HomeBookRow> {
        val rows = LinkedHashMap<String, HomeBookRow>()
        var cursor = since
        repeat(MaxPages) {
            val page = source.books(cursor, HomeLibraryContract.PAGE_LIMIT)
            page.forEach { row ->
                rows.remove(row.syncUuid)
                rows[row.syncUuid] = row
            }
            if (page.size < HomeLibraryContract.PAGE_LIMIT) return rows
            // Resume one millisecond early so rows sharing the last timestamp aren't skipped; repeats collapse above.
            val pageMax = page.maxOf { it.updatedAt }
            val next = pageMax - 1
            cursor = if (cursor != null && next <= cursor) pageMax else next
        }
        return rows
    }

    private fun HomeLibraryCheckpoint.matches(info: HomeLibraryInfo): Boolean =
        infoBookCount == info.bookCount && infoMaxUpdatedAt == info.maxUpdatedAt

    private companion object {
        val TombstoneRetentionMillis = TimeUnit.DAYS.toMillis(HomeLibraryContract.TOMBSTONE_RETENTION_DAYS.toLong())

        /** A safety stop (5 million rows); a real catalog ends long before. */
        const val MaxPages = 10_000
    }
}
