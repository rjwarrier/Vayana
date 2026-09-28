package com.vayana.baselineprofile

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns

/** Serves the bundled sample EPUB (a small generated book) to Vayana through a granted content URI. */
class SampleBookProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? =
        requireNotNull(context).assets.openFd(requireNotNull(uri.lastPathSegment))

    override fun getType(uri: Uri) = "application/epub+zip"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val name = requireNotNull(uri.lastPathSegment)
        val size = requireNotNull(context).assets.openFd(name).use { it.length }
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>(name, size)) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
