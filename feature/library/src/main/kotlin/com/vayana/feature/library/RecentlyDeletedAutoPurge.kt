package com.vayana.feature.library

import com.vayana.core.database.repository.BookRepository
import com.vayana.core.filesystem.BookFileCleaner
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Deletes books permanently, everywhere, once they've been in Recently deleted longer than the user allows. */
class RecentlyDeletedAutoPurge @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookFileCleaner: BookFileCleaner,
) {
    /** Purges books deleted more than [retentionDays] days before [now]; 0 or less keeps them forever. Returns how many went. */
    suspend fun purgeExpired(retentionDays: Int, now: Long = System.currentTimeMillis()): Int {
        if (retentionDays <= 0) return 0
        val cutoff = now - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        return bookRepository.deletedBookIdsBefore(cutoff).count { id ->
            val purged = bookRepository.purgeEverywhere(id) ?: return@count false
            bookFileCleaner.delete(purged.localFilePaths)
            true
        }
    }
}
