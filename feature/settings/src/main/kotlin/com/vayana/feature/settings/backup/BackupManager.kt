package com.vayana.feature.settings.backup

import com.vayana.core.resources.failUnless
import com.vayana.core.resources.uiText
import com.vayana.core.resources.LocalizedException
import com.vayana.core.resources.UiText
import com.vayana.core.resources.R
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.DATABASE_VERSION
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.filesystem.StorageRoots
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Everything that makes a backup restorable on a fresh install: the database (books,
 * annotations, word-lookup stats), settings, and the actual book/cover files - all as one zip,
 * so restoring is "pick the file" with no server, account, or network involved (the product specification §4.9).
 *
 * Restore is stash-then-commit: every live path it's about to overwrite is first moved aside
 * (same-directory rename, so it's cheap and atomic on any filesystem) rather than deleted, and
 * only discarded once every step has succeeded. Anything that throws after the database is
 * closed rolls the stashed files back before restarting - a failed restore should never be
 * worse than not restoring.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: VayanaDatabase,
    private val settingsRepository: SettingsRepository,
    private val storageRoots: StorageRoots,
    private val dispatchers: DispatcherProvider,
) {
    private val operationMutex = Mutex()

    suspend fun createBackup(destination: Uri): BackupOutcome = withContext(dispatchers.io) {
        operationMutex.withLock {
            try {
                checkpointDatabase()
                failUnless(
                    context.getDatabasePath(DatabaseFileName).isFile,
                    R.string.settings_error_could_not_find_app_database,
                )
                val output = context.contentResolver.openOutputStream(destination)
                    ?: return@withContext BackupOutcome.Failed(UiText.Res(R.string.settings_error_could_not_open_selected_location))
                output.use {
                    ZipOutputStream(BufferedOutputStream(it)).use { zip ->
                        writeManifest(zip)
                        writeSettings(zip)
                        writeFile(zip, context.getDatabasePath(DatabaseFileName), "database/$DatabaseFileName")
                        writeDirectory(zip, storageRoots.booksDir, "books")
                        writeDirectory(zip, storageRoots.coversDir, "covers")
                    }
                }
                BackupOutcome.Success
            } catch (cancellation: CancellationException) {
                runCatching { context.contentResolver.delete(destination, null, null) }
                throw cancellation
            } catch (throwable: Throwable) {
                runCatchingCancellable { context.contentResolver.delete(destination, null, null) }
                BackupOutcome.Failed(throwable.uiText(R.string.settings_error_backup_failed))
            }
        }
    }

    /** Read-only pass over the zip - no extraction, no writes - so picking a file is fast and free to cancel. */
    suspend fun inspectBackup(source: Uri): InspectOutcome = withContext(dispatchers.io) {
        operationMutex.withLock {
            val tempDb = File(context.cacheDir, "inspect-db.tmp")
            try {
                tempDb.delete()
                var manifest: BackupManifest? = null
                var settingsCount = 0
                var fileBookCount = 0
                var sawDatabase = false
                var totalBytes = 0L
                val seenEntries = HashSet<String>()
                val input = context.contentResolver.openInputStream(source)
                    ?: return@withContext InspectOutcome.Failed(UiText.Res(R.string.settings_error_could_not_open_selected_file))
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var entryCount = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = zip.nextEntry ?: break
                        failUnless(
                            ++entryCount <= MaxBackupEntries,
                            R.string.settings_error_backup_archive_contains_too_many_entries,
                        )
                        validateEntryName(entry.name, seenEntries)
                        if (!entry.isDirectory) {
                            when (entry.name) {
                                "manifest.json" -> manifest = parseManifestJson(readBounded(zip, MaxMetadataBytes))
                                "settings.json" -> settingsCount = JSONObject(readBounded(zip, MaxMetadataBytes)).length()
                                "database/$DatabaseFileName" -> {
                                    sawDatabase = true
                                    tempDb.parentFile?.mkdirs()
                                    FileOutputStream(tempDb).use { output -> totalBytes = consumeEntry(zip, buffer, totalBytes, output) }
                                }
                                else -> {
                                    if (entry.name.startsWith("books/")) fileBookCount++
                                    totalBytes = consumeEntry(zip, buffer, totalBytes)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
                val finalManifest = manifest
                    ?: return@withContext InspectOutcome.Failed(UiText.Res(R.string.settings_error_not_a_vayana_backup))
                if (!sawDatabase) {
                    return@withContext InspectOutcome.Failed(UiText.Res(R.string.settings_error_backup_has_no_database))
                }
                val (bookCount, annotationCount) = countRows(tempDb, fileBookCount)
                InspectOutcome.Success(
                    BackupInspection(
                        manifest = finalManifest,
                        bookCount = bookCount,
                        annotationCount = annotationCount,
                        settingsCount = settingsCount,
                        totalBytes = totalBytes,
                        isNewerFormat = finalManifest.backupFormatVersion > CurrentBackupFormatVersion,
                        isNewerDatabase = finalManifest.databaseVersion > DATABASE_VERSION,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                InspectOutcome.Failed(throwable.uiText(R.string.settings_error_could_not_read_backup))
            } finally {
                tempDb.delete()
            }
        }
    }

    private fun countRows(dbFile: File, fallbackBookCount: Int): Pair<Int, Int> {
        if (!dbFile.isFile) return fallbackBookCount to 0
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                failUnless(
                    cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext(),
                    R.string.settings_error_backup_database_damaged,
                )
            }
            val books = countTable(db, "SELECT COUNT(*) FROM books WHERE isDeleted = 0") ?: fallbackBookCount
            val annotations = countTable(db, "SELECT COUNT(*) FROM annotations") ?: 0
            books to annotations
        }
    }

    private fun countTable(db: SQLiteDatabase, query: String): Int? = runCatching {
        db.rawQuery(query, null).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else null }
    }.getOrNull()

    private suspend fun readBounded(zip: ZipInputStream, limit: Int): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = zip.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            failUnless(output.size() <= limit, R.string.settings_error_backup_metadata_unexpectedly_large)
        }
        return output.toString("UTF-8")
    }

    private suspend fun consumeEntry(zip: ZipInputStream, buffer: ByteArray, initialBytes: Long, output: OutputStream? = null): Long {
        var total = initialBytes
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = zip.read(buffer)
            if (count < 0) break
            failUnless(total <= MaxRestoreTotalBytes - count, R.string.settings_error_backup_unexpectedly_large)
            total += count
            output?.write(buffer, 0, count)
        }
        return total
    }

    suspend fun restoreBackup(source: Uri): RestoreOutcome = withContext(dispatchers.io) {
        operationMutex.withLock {
            val stagingDir = File(context.cacheDir, "restore-staging")
            try {
                stagingDir.deleteRecursively()
                stagingDir.mkdirs()
                val input = context.contentResolver.openInputStream(source)
                    ?: return@withContext RestoreOutcome.Failed(UiText.Res(R.string.settings_error_could_not_open_selected_file))
                ZipInputStream(BufferedInputStream(input)).use { zip -> extractAll(zip, stagingDir) }

                val manifest = readManifest(File(stagingDir, "manifest.json"))
                    ?: return@withContext RestoreOutcome.Failed(UiText.Res(R.string.settings_error_not_a_vayana_backup))
                if (manifest.backupFormatVersion > CurrentBackupFormatVersion) {
                    return@withContext RestoreOutcome.Incompatible(UiText.Res(R.string.settings_error_backup_format_too_new))
                }
                if (manifest.databaseVersion > DATABASE_VERSION) {
                    return@withContext RestoreOutcome.Incompatible(UiText.Res(R.string.settings_error_backup_data_too_new))
                }

                val stagedDb = File(stagingDir, "database/$DatabaseFileName")
                if (!stagedDb.isFile) {
                    return@withContext RestoreOutcome.Failed(UiText.Res(R.string.settings_error_backup_has_no_database_unchanged))
                }

                val settingsFile = File(stagingDir, "settings.json")
                val settingsMap = if (settingsFile.isFile) readSettingsMap(settingsFile) else emptyMap()

                validateStagedBackup(context, stagingDir, manifest.databaseVersion)

                // Point of no return: once the live database is closed the process can no
                // longer serve requests, so every path from here ends in a restart.
                database.close()
                val dbFile = context.getDatabasePath(DatabaseFileName)
                val dbWal = File(dbFile.path + "-wal")
                val dbShm = File(dbFile.path + "-shm")
                val stashed = listOf(dbFile, dbWal, dbShm, storageRoots.booksDir, storageRoots.coversDir)

                try {
                    stashed.forEach { stashAside(it) }

                    dbFile.parentFile?.mkdirs()
                    stagedDb.copyTo(dbFile, overwrite = true)
                    File(stagingDir, "books").takeIf { it.isDirectory }
                        ?.copyRecursively(storageRoots.booksDir, overwrite = true)
                    File(stagingDir, "covers").takeIf { it.isDirectory }
                        ?.copyRecursively(storageRoots.coversDir, overwrite = true)
                    if (settingsMap.isNotEmpty()) settingsRepository.importFromMap(settingsMap)

                    stashed.forEach { discardStash(it) }
                    stagingDir.deleteRecursively()
                    restartApp()
                    RestoreOutcome.Success
                } catch (throwable: Throwable) {
                    // Deliberately catches everything, cancellation included: the live database
                    // is already closed at this point, so the process must restart no matter
                    // what interrupted the swap - there is no "let the caller decide" option.
                    runCatchingCancellable { stashed.forEach { unstash(it) } }
                    stagingDir.deleteRecursively()
                    restartApp()
                    RestoreOutcome.Failed(
                        UiText.Res(
                            R.string.settings_error_restore_rolled_back,
                            throwable.uiText(R.string.settings_error_restore_failed),
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                stagingDir.deleteRecursively()
                throw cancellation
            } catch (throwable: Throwable) {
                stagingDir.deleteRecursively()
                RestoreOutcome.Failed(throwable.uiText(R.string.settings_error_restore_failed))
            }
        }
    }

    /** Moves [file] to a same-directory sibling so it's recoverable via [unstash] until [discardStash]. */
    private fun stashAside(file: File) {
        if (!file.exists()) return
        val backup = file.restoreBackupSibling()
        backup.deleteRecursively()
        if (!file.renameTo(backup)) {
            file.copyRecursively(backup, overwrite = true)
            file.deleteRecursively()
        }
    }

    private fun unstash(file: File) {
        val backup = file.restoreBackupSibling()
        if (!backup.exists()) return
        file.deleteRecursively()
        if (!backup.renameTo(file)) {
            backup.copyRecursively(file, overwrite = true)
            backup.deleteRecursively()
        }
    }

    private fun discardStash(file: File) {
        file.restoreBackupSibling().deleteRecursively()
    }

    private fun File.restoreBackupSibling(): File = File(parentFile, "$name.restore-bak")

    private fun checkpointDatabase() {
        val dbFile = context.getDatabasePath(DatabaseFileName)
        if (!dbFile.isFile) return
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                failUnless(
                    cursor.moveToFirst() && cursor.getInt(0) == 0,
                    R.string.settings_error_could_not_save_pending_database_changes,
                )
            }
        }
    }

    private fun writeManifest(zip: ZipOutputStream) {
        val json = JSONObject().apply {
            put("appVersion", appVersionName())
            put("backupFormatVersion", CurrentBackupFormatVersion)
            put("databaseVersion", DATABASE_VERSION)
            put("createdAt", System.currentTimeMillis())
        }
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(json.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private suspend fun writeSettings(zip: ZipOutputStream) {
        val json = JSONObject()
        settingsRepository.exportToMap().forEach { (key, value) -> json.put(key, value) }
        zip.putNextEntry(ZipEntry("settings.json"))
        zip.write(json.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeFile(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.isFile) return
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun writeDirectory(zip: ZipOutputStream, directory: File, entryPrefix: String) {
        if (!directory.isDirectory) return
        directory.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(directory).path.replace(File.separatorChar, '/')
            writeFile(zip, file, "$entryPrefix/$relative")
        }
    }

    private fun extractAll(zip: ZipInputStream, destination: File) {
        val destinationRoot = destination.canonicalPath + File.separator
        var entryCount = 0
        var cumulativeBytes = 0L
        val seenEntries = HashSet<String>()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val entry = zip.nextEntry ?: break
            failUnless(
                ++entryCount <= MaxBackupEntries,
                R.string.settings_error_backup_archive_contains_too_many_entries,
            )
            validateEntryName(entry.name, seenEntries)
            if (!entry.isDirectory) {
                val outFile = File(destination, entry.name).canonicalFile
                failUnless(
                    outFile.path.startsWith(destinationRoot),
                    R.string.settings_error_backup_archive_contains_invalid_entry,
                )
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { output ->
                    var entryBytes = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        failUnless(
                            cumulativeBytes <= MaxRestoreTotalBytes - count,
                            R.string.settings_error_backup_unexpectedly_large,
                        )
                        cumulativeBytes += count
                        entryBytes += count
                        if (entry.name == "manifest.json" || entry.name == "settings.json") {
                            failUnless(
                                entryBytes <= MaxMetadataBytes,
                                R.string.settings_error_backup_metadata_unexpectedly_large,
                            )
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            zip.closeEntry()
        }
    }

    private fun readManifest(file: File): BackupManifest? {
        if (!file.isFile) return null
        return parseManifestJson(file.readText(Charsets.UTF_8))
    }

    private fun parseManifestJson(text: String): BackupManifest {
        val json = JSONObject(text)
        failUnless(
            json.optInt("backupFormatVersion", 1) > 0 && json.optInt("databaseVersion", 0) > 0,
            R.string.settings_error_backup_manifest_has_invalid_version_information,
        )
        return BackupManifest(
            appVersion = json.optString("appVersion", ""),
            backupFormatVersion = json.optInt("backupFormatVersion", 1),
            databaseVersion = json.optInt("databaseVersion", 0),
            createdAt = json.optLong("createdAt", 0L),
        )
    }

    private fun readSettingsMap(file: File): Map<String, String> {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        return json.keys().asSequence().associateWith { key -> json.getString(key) }
    }

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private companion object {
        const val DatabaseFileName = "vayana.db"
        const val CurrentBackupFormatVersion = 1
        const val MaxBackupEntries = 200_000
        const val MaxRestoreTotalBytes = 20L * 1024L * 1024L * 1024L
        const val MaxMetadataBytes = 1024 * 1024
    }
}

internal fun validateEntryName(name: String, seenEntries: MutableSet<String>) {
    val path = name.removeSuffix("/")
    failUnless(
        path.isNotEmpty() && '\\' !in path && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." },
        R.string.settings_error_backup_archive_contains_invalid_entry,
    )
    failUnless(seenEntries.add(path), R.string.settings_error_backup_archive_contains_duplicate_entries)
}
