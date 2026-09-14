package com.vayana.core.sync

import com.vayana.core.backup.PortableTombstone
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.TombstoneEntityType
import com.vayana.core.database.repository.bookSyncIdOfPurge
import com.vayana.core.filesystem.BookFileCleaner
import javax.inject.Inject

/** Applies whole-book deletes synced from another device; shared by the full sync and the reading-progress sync. */
class SyncedBookDeletionApplier @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookFileCleaner: BookFileCleaner,
) {
    /**
     * Applies a [TombstoneEntityType.BOOK] (to Recently deleted, unless restored here after it) or
     * [TombstoneEntityType.BOOK_PURGE] (permanently, files included) tombstone. Returns the removed book's title, or
     * null when nothing changed or [tombstone] isn't a book deletion.
     */
    suspend fun apply(tombstone: PortableTombstone): String? = when (tombstone.entityType) {
        TombstoneEntityType.BOOK.value -> bookRepository.applyBookTombstone(tombstone.syncId, tombstone.deletedAt)
        TombstoneEntityType.BOOK_PURGE.value -> {
            val purged = bookSyncIdOfPurge(tombstone.syncId)
                ?.let { bookRepository.applyPurgeTombstone(it, tombstone.deletedAt) }
            purged?.let {
                bookFileCleaner.delete(it.localFilePaths)
                it.title
            }
        }
        else -> null
    }
}
