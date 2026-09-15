package com.vayana.feature.library

import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.PurgedBook
import com.vayana.core.filesystem.BookFileCleaner
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Deletes books permanently, everywhere: on request, or once they've been in Recently deleted longer than allowed. */
class RecentlyDeletedAutoPurge @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookFileCleaner: BookFileCleaner,
) {
    /** Deletes one book permanently, everywhere, its files on this device included; null when the book is gone already. */
    suspend fun purgeBook(id: Long): PurgedBook? =
        bookRepository.purgeEverywhere(id)?.also { purged -> bookFileCleaner.delete(purged.localFilePaths) }

    /** Purges books deleted more than [retentionDays] days before [now]; 0 or less keeps them forever. Returns how many went. */
    suspend fun purgeExpired(retentionDays: Int, now: Long = System.currentTimeMillis()): Int {
        if (retentionDays <= 0) return 0
        val cutoff = now - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        return bookRepository.deletedBookIdsBefore(cutoff).count { id -> purgeBook(id) != null }
    }
}
