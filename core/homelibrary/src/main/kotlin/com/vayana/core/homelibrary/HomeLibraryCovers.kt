package com.vayana.core.homelibrary

import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticsLogStore
import com.vayana.core.filesystem.CoverImages
import com.vayana.core.filesystem.StorageRoots
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Keeps a cover file for each mirrored book that has one. Covers are fetched only after a sync has committed (never
 * inside it), one at a time, and a book's file is named after its Home Library `updated_at`, so a changed record
 * fetches a fresh cover while an unchanged one costs nothing.
 *
 * The files live in Vayana's own covers folder, so every screen that shows a cover works unchanged and the daily
 * storage clean-up removes the superseded ones.
 */
@Singleton
class HomeLibraryCovers @Inject constructor(
    private val source: HomeLibrarySource,
    private val bookDao: BookDao,
    private val storageRoots: StorageRoots,
    private val dispatchers: DispatcherProvider,
    private val logStore: DiagnosticsLogStore,
) {
    /** Books whose cover failed at this `updated_at`: not asked again until Home Library changes the book. */
    private val failedAt = ConcurrentHashMap<String, Long?>()
    private val gate = Semaphore(Parallelism)

    /**
     * Fetches every missing or outdated cover, a few at a time. A failure on one book never stops the others, and a
     * book that failed is skipped until its record changes (or the app restarts), so a cover Home Library cannot serve
     * costs one attempt, not one per sync.
     */
    suspend fun refresh() = withContext(dispatchers.io) {
        val failures = AtomicInteger()
        val pending = mutableListOf<Pair<BookEntity, String>>()
        for (book in bookDao.getHomeLibraryBooks()) {
            val uuid = book.syncUuid ?: continue
            if (!book.sourceHasCover) {
                if (!book.coverPath.isNullOrBlank()) clearCover(book)
                continue
            }
            if (book.hasCurrentCover(uuid)) continue
            if (failedAt.containsKey(uuid) && failedAt[uuid] == book.sourceUpdatedAt) continue
            pending += book to uuid
        }
        coroutineScope {
            pending.forEach { (book, uuid) ->
                launch {
                    gate.withPermit {
                        try {
                            fetch(book, uuid)
                            failedAt.remove(uuid)
                        } catch (error: Exception) {
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            failedAt[uuid] = book.sourceUpdatedAt
                            if (failures.getAndIncrement() < MaxLoggedFailures) {
                                logStore.record(DiagnosticCategory.SYNC, "HomeLibraryCovers", "Cover fetch failed for $uuid", error.stackTraceToString())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun BookEntity.hasCurrentCover(uuid: String): Boolean {
        val path = coverPath?.takeIf { it.isNotBlank() } ?: return false
        return File(path).name.startsWith(coverFilePrefix(uuid, sourceUpdatedAt)) && storageRoots.resolve(path).isFile
    }

    private suspend fun fetch(book: BookEntity, uuid: String) {
        val bytes = source.openCover(uuid)?.use { it.readBytes() }?.takeIf { it.isNotEmpty() } ?: return
        val extension = bytes.imageExtension()
        val file = File(storageRoots.coversDir, "${coverFilePrefix(uuid, book.sourceUpdatedAt)}.$extension")
        val temporary = File(file.parentFile, "${file.name}.part")
        temporary.writeBytes(CoverImages.compact(bytes, extension, maxEdgePx = MaxEdgePx, thresholdBytes = ShrinkAboveBytes))
        if (!temporary.renameTo(file)) {
            file.delete()
            if (!temporary.renameTo(file)) {
                temporary.delete()
                return
            }
        }
        val previous = book.coverPath
        bookDao.setHomeLibraryCover(book.id, storageRoots.relativize(file))
        if (!previous.isNullOrBlank() && storageRoots.resolve(previous) != file) {
            runCatchingCancellable { storageRoots.resolve(previous).delete() }
        }
    }

    private suspend fun clearCover(book: BookEntity) {
        val previous = book.coverPath ?: return
        bookDao.setHomeLibraryCover(book.id, null)
        runCatchingCancellable { storageRoots.resolve(previous).delete() }
    }

    private companion object {
        const val MaxLoggedFailures = 5
        const val Parallelism = 4

        /** Offline-book covers are shown small, so they are stored smaller than the library-wide limit. */
        const val MaxEdgePx = 640
        const val ShrinkAboveBytes = 100 * 1024
    }

    private fun coverFilePrefix(uuid: String, updatedAt: Long?): String = "homelibrary-$uuid-${updatedAt ?: 0L}"
}

/** The file extension for what the bytes really are; a wrong one only matters to the cover compactor. */
internal fun ByteArray.imageExtension(): String = when {
    size >= 4 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte() -> "png"
    size >= 12 && this[0] == 'R'.code.toByte() && this[8] == 'W'.code.toByte() && this[9] == 'E'.code.toByte() -> "webp"
    else -> "jpg"
}
