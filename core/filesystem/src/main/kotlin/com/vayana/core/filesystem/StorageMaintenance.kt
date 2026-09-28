package com.vayana.core.filesystem

import android.content.Context
import android.webkit.WebView
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.DatabaseMaintenance
import com.vayana.core.database.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Space the app would otherwise keep for nothing: share copies left in the cache, covers no book uses any more,
 * covers stored far larger than shown, web-browser leftovers. Runs at most daily, in the background after launch.
 * Anything a task might still be working on (a cover being imported, a file just shared) is younger than a day and
 * so left alone.
 */
@Singleton
class StorageMaintenance @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageRoots: StorageRoots,
    private val bookRepository: BookRepository,
    private val databaseMaintenance: DatabaseMaintenance,
    private val dispatchers: DispatcherProvider,
) {
    private val preferences by lazy { context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE) }

    suspend fun runIfDue(now: Long = System.currentTimeMillis()) = withContext(dispatchers.io) {
        if (now - preferences.getLong(LastRunKey, 0L) < DayMillis) return@withContext
        // Each task on its own: one failing (a file in use, a full disk) mustn't stop the others.
        runCatchingCancellable { deleteStaleShareCopies(now) }
        runCatchingCancellable { deleteUnusedCovers(now) }
        runCatchingCancellable { compactCovers() }
        runCatchingCancellable { deleteWebViewMetrics() }
        if (!preferences.getBoolean(BrowserCacheClearedKey, false)) {
            runCatchingCancellable { clearBrowserCache() }.onSuccess { preferences.edit().putBoolean(BrowserCacheClearedKey, true).apply() }
        }
        if (now - preferences.getLong(LastDatabaseOptimizeKey, 0L) >= WeekMillis) {
            runCatchingCancellable { databaseMaintenance.optimize() }
                .onSuccess { preferences.edit().putLong(LastDatabaseOptimizeKey, now).apply() }
        }
        preferences.edit().putLong(LastRunKey, now).apply()
    }

    /** Copies made to hand a book, quote card or image to another app; the other app has long since read them. */
    private fun deleteStaleShareCopies(now: Long) {
        ShareCacheDirectories.forEach { name ->
            File(context.cacheDir, name).listFiles()?.forEach { file ->
                if (file.isFile && now - file.lastModified() > DayMillis) file.delete()
            }
        }
    }

    /** Cover files no book points to any more (replaced covers, leftovers of an interrupted import). */
    private suspend fun deleteUnusedCovers(now: Long) {
        val directory = File(storageRoots.rootDir, CoversDirectoryName)
        val files = directory.listFiles()?.filter { it.isFile } ?: return
        val referenced = bookRepository.referencedCoverPaths().mapTo(HashSet()) { it.normalizedPath() }
        files.forEach { file ->
            val unused = storageRoots.relativize(file).normalizedPath() !in referenced
            if (unused && now - file.lastModified() > DayMillis) file.delete()
        }
    }

    /** Covers stored far larger than they are ever shown; see [CoverImages]. Once shrunk, a cover is skipped. */
    private fun compactCovers() {
        File(storageRoots.rootDir, CoversDirectoryName).listFiles()
            ?.filter { it.isFile && it.length() >= CoverImages.CompactThresholdBytes }
            ?.forEach(CoverImages::compactFile)
    }

    /** Usage metrics the web engine wrote before the app opted out of them (see the manifest). */
    private fun deleteWebViewMetrics() {
        File(context.noBackupFilesDir, WebViewMetricsDirectory).takeIf { it.isDirectory }?.deleteRecursively()
    }

    /**
     * The Goodreads and cover-search browsers used to leave their pages (ads included) in the web cache; they now
     * clear it when closed, and this clears what an earlier version left. Needs a web view, so it runs on the main
     * thread, once.
     */
    private suspend fun clearBrowserCache() = withContext(dispatchers.main) {
        WebView(context).apply {
            clearCache(true)
            destroy()
        }
    }

    private fun String.normalizedPath(): String = replace('\\', '/').trimStart('/')

    private companion object {
        const val PreferencesName = "storage_maintenance"
        const val LastRunKey = "last_run"
        const val LastDatabaseOptimizeKey = "last_database_optimize"
        const val BrowserCacheClearedKey = "browser_cache_cleared_v1"
        const val CoversDirectoryName = "covers"
        const val WebViewMetricsDirectory = ".webview/BrowserMetrics"
        val ShareCacheDirectories = listOf("shared_books", "shared_files", "shared_images")
        val DayMillis = TimeUnit.DAYS.toMillis(1)
        val WeekMillis = TimeUnit.DAYS.toMillis(7)
    }
}
