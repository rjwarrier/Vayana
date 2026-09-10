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
