package com.vayana.core.filesystem

import android.content.Context
import android.net.Uri
import com.vayana.core.common.Hashing
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ImportedFile(val file: File, val sha256: String)

/** Copies a SAF-picked book file into [StorageRoots.booksDir] and hashes it (import-time dedupe input). */
class BookFileImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageRoots: StorageRoots,
) {
    fun import(sourceUri: Uri, extension: String): ImportedFile {
        val destination = File(storageRoots.booksDir, "${UUID.randomUUID()}.$extension")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open $sourceUri")

        val sha256 = destination.inputStream().use { Hashing.sha256(it) }
        return ImportedFile(destination, sha256)
    }
}
