package com.vayana.core.filesystem

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-owned storage root (PROMPT2appbuild.md §2). The DB stores paths relative to [rootDir]
 * (via [relativize]/[resolve]) — never absolute — so relocating the whole library later is one
 * migration job, not a data-model change. `books/`/`covers` are created lazily on first use.
 */
@Singleton
class StorageRoots @Inject constructor(@ApplicationContext context: Context) {

    val rootDir: File = context.getExternalFilesDir(null) ?: context.filesDir

    val booksDir: File get() = File(rootDir, "books").apply { mkdirs() }
    val coversDir: File get() = File(rootDir, "covers").apply { mkdirs() }

    fun resolve(rootRelativePath: String): File = File(rootDir, rootRelativePath)

    fun relativize(file: File): String = file.relativeTo(rootDir).path
}
