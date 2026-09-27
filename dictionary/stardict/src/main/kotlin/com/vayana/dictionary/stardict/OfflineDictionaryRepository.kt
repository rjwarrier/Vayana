package com.vayana.dictionary.stardict

import com.vayana.core.resources.failUnless
import android.content.Context
import android.net.Uri
import com.vayana.core.resources.LocalizedException
import com.vayana.core.resources.R
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionaryPackState
import com.vayana.dictionary.api.DictionaryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class OfflineDictionaryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : DictionaryRepository {
    private val dictionariesDirectory = File(context.filesDir, "dictionaries")
    private val packDirectory = File(dictionariesDirectory, "oewn-2025")
    private val stagingDirectory = File(dictionariesDirectory, "oewn-2025.installing")
    private val backupDirectory = File(dictionariesDirectory, "oewn-2025.backup")

    init {
        recoverInterruptedInstall()
    }

    private val _englishPackState = MutableStateFlow<DictionaryPackState>(
        if (isValidPack(packDirectory)) DictionaryPackState.Installed else DictionaryPackState.NotInstalled,
    )
    override val englishPackState: StateFlow<DictionaryPackState> = _englishPackState

    @Volatile
    private var dictionary: WordNetDictionary? = null
    private val installMutex = Mutex()

    override suspend fun lookupEnglish(word: String): DictionaryEntry? = withContext(Dispatchers.IO) {
        // englishPackState is already kept accurate by install/init; avoid re-stat'ing 8 files
        // from disk on every single word lookup, which fires on every text selection.
        if (_englishPackState.value !is DictionaryPackState.Installed) return@withContext null
        (dictionary ?: synchronized(this@OfflineDictionaryRepository) {
            dictionary ?: WordNetDictionary(packDirectory).also { dictionary = it }
        }).lookup(word)
    }

    override suspend fun installEnglish(sourceUri: String) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            _englishPackState.value = DictionaryPackState.Installing
            try {
                stagingDirectory.deleteRecursively()
                failUnless(stagingDirectory.mkdirs(), R.string.dictionary_error_prepare_storage)
                val input = context.contentResolver.openInputStream(Uri.parse(sourceUri))
                    ?: throw LocalizedException(R.string.dictionary_error_open_file)
                input.use { stream -> extractPack(ZipInputStream(stream.buffered()), stagingDirectory) }
                failUnless(isValidPack(stagingDirectory), R.string.dictionary_error_not_wordnet)

                backupDirectory.deleteRecursively()
                if (packDirectory.exists()) {
                    failUnless(packDirectory.renameTo(backupDirectory), R.string.dictionary_error_preserve_existing)
                }
                if (!stagingDirectory.renameTo(packDirectory)) {
                    val restored = backupDirectory.exists() && backupDirectory.renameTo(packDirectory)
                    throw LocalizedException(
                        if (restored) {
                            R.string.dictionary_error_finish_install
                        } else {
                            R.string.dictionary_error_finish_install_no_restore
                        },
                    )
                }
                backupDirectory.deleteRecursively()
                dictionary = null
                _englishPackState.value = DictionaryPackState.Installed
            } catch (throwable: Throwable) {
                stagingDirectory.deleteRecursively()
                if (!isValidPack(packDirectory) && isValidPack(backupDirectory)) {
                    packDirectory.deleteRecursively()
                    backupDirectory.renameTo(packDirectory)
                }
                _englishPackState.value = if (isValidPack(packDirectory)) {
                    DictionaryPackState.Installed
                } else {
                    DictionaryPackState.Failed
                }
                throw throwable
            }
        }
    }

    private fun recoverInterruptedInstall() {
        stagingDirectory.deleteRecursively()
        if (!isValidPack(packDirectory) && isValidPack(backupDirectory)) {
            packDirectory.deleteRecursively()
            backupDirectory.renameTo(packDirectory)
        }
        if (isValidPack(packDirectory)) backupDirectory.deleteRecursively()
    }

    private fun extractPack(zip: ZipInputStream, destination: File) {
        var totalBytes = 0L
        var entryCount = 0
        val extractedFiles = mutableSetOf<String>()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        zip.use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                failUnless(++entryCount <= MaxArchiveEntries, R.string.dictionary_error_too_many_entries)
                val name = entry.name.substringAfterLast('/')
                // Real-world WordNet distributions bundle a LICENSE/README/citation file alongside
                // the dict data; only extract the files we actually use and ignore the rest.
                if (!entry.isDirectory && name in AllowedFiles) {
                    failUnless(extractedFiles.add(name), R.string.dictionary_error_duplicate_files)
                    failUnless(entry.size <= MaxExtractedBytes, R.string.dictionary_error_file_too_large)
                    val outputFile = File(destination, name)
                    FileOutputStream(outputFile).buffered().use { output ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            failUnless(totalBytes <= MaxExtractedBytes, R.string.dictionary_error_archive_too_large)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                input.closeEntry()
            }
        }
    }

    private fun isValidPack(directory: File): Boolean =
        RequiredFiles.all { name -> File(directory, name).let { it.isFile && it.length() > 0L } }

    private companion object {
        val RequiredFiles = setOf(
            "index.noun", "data.noun", "index.verb", "data.verb",
            "index.adj", "data.adj", "index.adv", "data.adv",
        )
        val AllowedFiles = RequiredFiles + setOf("noun.exc", "verb.exc", "adj.exc", "adv.exc")
        const val MaxArchiveEntries = 32
        const val MaxExtractedBytes = 64L * 1024L * 1024L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DictionaryModule {
    @Binds
    abstract fun bindDictionaryRepository(implementation: OfflineDictionaryRepository): DictionaryRepository
}
