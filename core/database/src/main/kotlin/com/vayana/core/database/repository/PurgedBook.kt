package com.vayana.core.database.repository

/** What a permanent deletion removed, for the caller to finish off (local files) and report. */
data class PurgedBook(
    val syncId: String,
    val title: String,
    /** The book file and cover files, root-relative. Still on disk until the caller deletes them. */
    val localFilePaths: List<String>,
    /** Cloud assets now queued in `pending_cloud_deletions`. */
    val queuedCloudAssetIds: List<String>,
)

enum class CloudAssetKind(val value: String) {
    BOOK_FILE("book_file"),
    COVER("cover"),
}
