package com.vayana.core.database.repository

/**
 * [value] is the wire/storage form (Room column, portable-snapshot JSON) and must stay stable across app
 * versions and devices; it intentionally does not track the Kotlin enum name.
 */
enum class TombstoneEntityType(val value: String) {
    BOOK("book"),
    SHELF("shelf"),
    VOCABULARY_CARD("vocabulary_card"),
    ANNOTATION("annotation"),
    SHELF_MEMBERSHIP("shelf_membership"),
    READING_SESSION("reading_session"),

    /**
     * A book deleted permanently, on every device, keyed by [bookPurgeTombstoneId]. Unlike [BOOK] (moved to Recently
     * deleted), it applies even over newer local edits, and each device also removes the book's cloud files.
     */
    BOOK_PURGE("book_purge"),

    /**
     * Not a deletion: marks when a book's reading stats were reset, keyed by [readingProgressResetTombstoneId].
     * Progress merges treat it as local progress at that moment, so older synced progress can't restore the stats.
     */
    READING_PROGRESS_RESET("reading_progress_reset"),
    ;

    companion object {
        fun fromValue(value: String): TombstoneEntityType? = entries.find { it.value == value }
    }
}

private const val ReadingProgressResetPrefix = "reset:"

fun readingProgressResetTombstoneId(bookSyncId: String): String = "$ReadingProgressResetPrefix$bookSyncId"

/** The book a [TombstoneEntityType.READING_PROGRESS_RESET] tombstone is about, or null if [tombstoneSyncId] isn't one. */
fun bookSyncIdOfReadingProgressReset(tombstoneSyncId: String): String? =
    tombstoneSyncId.takeIf { it.startsWith(ReadingProgressResetPrefix) }
        ?.removePrefix(ReadingProgressResetPrefix)
        ?.takeIf { it.isNotBlank() }

private const val BookPurgePrefix = "purge:"

fun bookPurgeTombstoneId(bookSyncId: String): String = "$BookPurgePrefix$bookSyncId"

/** The book a [TombstoneEntityType.BOOK_PURGE] tombstone is about, or null if [tombstoneSyncId] isn't one. */
fun bookSyncIdOfPurge(tombstoneSyncId: String): String? =
    tombstoneSyncId.takeIf { it.startsWith(BookPurgePrefix) }
        ?.removePrefix(BookPurgePrefix)
        ?.takeIf { it.isNotBlank() }

/** Tombstones the lightweight reading-progress sync also publishes and applies: reading data and book deletions. */
fun isSyncedWithReadingProgress(entityType: String): Boolean =
    TombstoneEntityType.fromValue(entityType) in ReadingProgressSyncTombstoneTypes

/** Deletions of whole books (to Recently deleted, or permanent): the only deletions the silent launch sync applies. */
fun isBookDeletion(entityType: String): Boolean = TombstoneEntityType.fromValue(entityType) in BookDeletionTombstoneTypes

private val BookDeletionTombstoneTypes = setOf(TombstoneEntityType.BOOK, TombstoneEntityType.BOOK_PURGE)

private val ReadingProgressSyncTombstoneTypes =
    setOf(TombstoneEntityType.READING_SESSION, TombstoneEntityType.READING_PROGRESS_RESET) + BookDeletionTombstoneTypes
