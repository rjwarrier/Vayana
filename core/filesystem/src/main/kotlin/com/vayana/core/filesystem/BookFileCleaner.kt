package com.vayana.core.filesystem

import com.vayana.core.common.DispatcherProvider
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Deletes the files a removed book left behind. Only files inside the app's storage root are ever touched. */
class BookFileCleaner @Inject constructor(
    private val storageRoots: StorageRoots,
    private val dispatchers: DispatcherProvider,
) {
    /** How many of [rootRelativePaths] were deleted; missing files and paths outside the root are skipped. */
    suspend fun delete(rootRelativePaths: Collection<String>): Int = withContext(dispatchers.io) {
        deleteFilesUnderRoot(storageRoots.rootDir, rootRelativePaths)
    }
}

internal fun deleteFilesUnderRoot(root: File, rootRelativePaths: Collection<String>): Int {
    val canonicalRoot = root.canonicalFile
    return rootRelativePaths.distinct().count { relativePath ->
        val file = File(canonicalRoot, relativePath).canonicalFile
        file.toPath().startsWith(canonicalRoot.toPath()) && file.isFile && file.delete()
    }
}
