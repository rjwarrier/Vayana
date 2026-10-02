package com.vayana.core.homelibrary

import android.net.Uri

/**
 * Home Library's published, read-only catalog contract (`com.mj.homelibrary`, same developer and signing key). Home
 * Library owns the data: Vayana only ever queries it.
 */
object HomeLibraryContract {
    const val PACKAGE = "com.mj.homelibrary"
    const val AUTHORITY = "com.mj.homelibrary.catalog"

    /** Auto-granted signature permission, declared in the manifest and never requested at runtime. */
    const val READ_PERMISSION = "com.mj.homelibrary.permission.READ_CATALOG"
    const val ACTION_SHOW_BOOK = "com.mj.homelibrary.action.SHOW_BOOK"
    const val EXTRA_SYNC_UUID = "sync_uuid"

    /** The highest `/info.schema_version` this build understands. */
    const val SUPPORTED_SCHEMA_VERSION = 1

    /** Deletion tombstones are kept this long; a longer gap needs a full resync. */
    const val TOMBSTONE_RETENTION_DAYS = 90

    const val PAGE_LIMIT = 500

    private val base: Uri = Uri.parse("content://$AUTHORITY")
    val booksUri: Uri = base.buildUpon().appendPath("books").build()
    val infoUri: Uri = base.buildUpon().appendPath("info").build()

    fun booksUri(updatedSince: Long?, limit: Int): Uri = booksUri.buildUpon().apply {
        if (updatedSince != null) appendQueryParameter("updated_since", updatedSince.toString())
        appendQueryParameter("limit", limit.toString())
    }.build()

    fun coverUri(syncUuid: String): Uri =
        booksUri.buildUpon().appendPath(syncUuid).appendPath("cover").build()

    object Books {
        const val SYNC_UUID = "sync_uuid"
        const val DELETED = "deleted"
        const val UPDATED_AT = "updated_at"
        const val TITLE = "title"
        const val SUBTITLE = "subtitle"
        const val ORIGINAL_SCRIPT_TITLE = "original_script_title"
        const val AUTHORS = "authors"
        const val LANGUAGE_CODE = "language_code"
        const val ISBN13 = "isbn13"
        const val ISBN10 = "isbn10"
        const val PUBLISHER = "publisher"
        const val PUBLISHED_YEAR = "published_year"
        const val PAGE_COUNT = "page_count"
        const val FORMAT_CODE = "format_code"
        const val SERIES_NAME = "series_name"
        const val MAIN_GENRE = "main_genre"
        const val SUB_GENRES = "sub_genres"
        const val TAGS = "tags"
        const val READ_STATUS_CODE = "read_status_code"
        const val RATING = "rating"
        const val ROOM = "room"
        const val BOOKCASE = "bookcase"
        const val SHELF = "shelf"
        const val POSITION_NOTE = "position_note"
        const val HAS_COVER = "has_cover"
    }

    object Info {
        const val SCHEMA_VERSION = "schema_version"
        const val BOOK_COUNT = "book_count"
        const val MAX_UPDATED_AT = "max_updated_at"
        const val APP_VERSION = "app_version"
    }
}
