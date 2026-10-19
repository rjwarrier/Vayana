package com.vayana.dictionary.stardict

import com.vayana.core.resources.failUnless
import android.content.Context
import android.net.Uri
import com.vayana.core.resources.LocalizedException
import com.vayana.core.resources.R
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionaryPack
import com.vayana.dictionary.api.DictionaryPackStatus
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
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

    private val gcideFile = File(dictionariesDirectory, "gcide.db")
    private val gcideStagingFile = File(dictionariesDirectory, "gcide.installing.db")
    private val orderFile = File(dictionariesDirectory, "order")

    init {
        recoverInterruptedInstall()
        gcideStagingFile.delete()
        File(context.cacheDir, GcideDownloadName).delete()
    }

    private var wordNetState: DictionaryPackState =
        if (isValidPack(packDirectory)) DictionaryPackState.Installed else DictionaryPackState.NotInstalled
    private var gcideState: DictionaryPackState =
        if (gcideFile.isFile && gcideFile.length() > 0L) DictionaryPackState.Installed else DictionaryPackState.NotInstalled
    private var gcideProgress: Float? = null
    private var order: List<DictionaryPack> = readOrder()

    private val _englishPackState = MutableStateFlow(anyInstalledState())
    override val englishPackState: StateFlow<DictionaryPackState> = _englishPackState
    private val _packs = MutableStateFlow(statuses())
    override val packs: StateFlow<List<DictionaryPackStatus>> = _packs

    @Volatile
    private var dictionary: WordNetDictionary? = null
    @Volatile
    private var gcide: GcideDictionary? = null
    private val installMutex = Mutex()

    override suspend fun lookupEnglish(word: String): DictionaryEntry? = withContext(Dispatchers.IO) {
        // States are kept accurate by install/init; no files are stat'ed per lookup (one per text selection).
        if (_englishPackState.value !is DictionaryPackState.Installed) return@withContext null
        order.firstNotNullOfOrNull { pack ->
            when (pack) {
                DictionaryPack.WORDNET -> if (wordNetState is DictionaryPackState.Installed) wordNet().lookup(word) else null
                DictionaryPack.GCIDE -> if (gcideState is DictionaryPackState.Installed) gcide().lookup(word) else null
            }
        }
    }

    private fun wordNet(): WordNetDictionary = dictionary ?: synchronized(this) {
        dictionary ?: WordNetDictionary(packDirectory).also { dictionary = it }
    }

    private fun gcide(): GcideDictionary = gcide ?: synchronized(this) {
        gcide ?: GcideDictionary(gcideFile).also { gcide = it }
    }

    override suspend fun installGcide() = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val download = File(context.cacheDir, GcideDownloadName)
            setGcide(DictionaryPackState.Installing, progress = 0f)
            try {
                failUnless(dictionariesDirectory.isDirectory || dictionariesDirectory.mkdirs(), R.string.dictionary_error_prepare_storage)
                downloadGcide(download) { fraction -> setGcide(DictionaryPackState.Installing, progress = fraction * DownloadShare) }
                val job = currentCoroutineContext().job
                val built = runCatching {
                    GcideDictionary.build(download, gcideStagingFile) { fraction ->
                        job.ensureActive()
                        setGcide(DictionaryPackState.Installing, progress = DownloadShare + fraction * (1f - DownloadShare))
                    }
                }
                built.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
                built.exceptionOrNull()?.let { failure ->
                    if (failure is IllegalArgumentException) throw LocalizedException(R.string.dictionary_error_not_gcide)
                    throw failure
                }
                synchronized(this@OfflineDictionaryRepository) {
                    gcide?.close()
                    gcide = null
                    gcideFile.delete()
                    failUnless(gcideStagingFile.renameTo(gcideFile), R.string.dictionary_error_finish_install)
                }
                setGcide(DictionaryPackState.Installed)
            } catch (throwable: Throwable) {
                gcideStagingFile.delete()
                setGcide(if (gcideFile.isFile && gcideFile.length() > 0L) DictionaryPackState.Installed else DictionaryPackState.Failed)
                throw throwable
            } finally {
                download.delete()
            }
        }
    }

    /** GNU's own release ZIP over HTTPS, only from ftp.gnu.org, capped in size; progress 0..1 by bytes. */
    private suspend fun downloadGcide(destination: File, onProgress: (Float) -> Unit) {
        val connection = URL(GcideDownloadUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = NetworkTimeoutMillis
            connection.readTimeout = NetworkTimeoutMillis
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Vayana")
            // Uncompressed on the wire, so the length is known and progress can be shown (the ZIP is compressed already).
            connection.setRequestProperty("Accept-Encoding", "identity")
            failUnless(connection.responseCode == HttpURLConnection.HTTP_OK, R.string.dictionary_error_download_failed)
            val total = connection.contentLengthLong
            failUnless(total <= MaxGcideDownloadBytes, R.string.dictionary_error_archive_too_large)
            var read = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        read += count
                        failUnless(read <= MaxGcideDownloadBytes, R.string.dictionary_error_archive_too_large)
                        output.write(buffer, 0, count)
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } catch (failure: java.io.IOException) {
            throw LocalizedException(R.string.dictionary_error_download_failed)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun remove(pack: DictionaryPack) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            synchronized(this@OfflineDictionaryRepository) {
                when (pack) {
                    DictionaryPack.WORDNET -> {
                        dictionary = null
                        packDirectory.deleteRecursively()
                        wordNetState = DictionaryPackState.NotInstalled
                    }
                    DictionaryPack.GCIDE -> {
                        gcide?.close()
                        gcide = null
                        gcideFile.delete()
                        gcideState = DictionaryPackState.NotInstalled
                    }
                }
            }
            publish()
        }
    }

    override fun moveUp(pack: DictionaryPack) {
        val index = order.indexOf(pack)
        if (index <= 0) return
        order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
        runCatching {
            dictionariesDirectory.mkdirs()
            orderFile.writeText(order.joinToString(",") { it.name })
        }
        publish()
    }

    private fun readOrder(): List<DictionaryPack> {
        val saved = runCatching { orderFile.readText() }.getOrNull().orEmpty().split(',')
            .mapNotNull { name -> DictionaryPack.entries.firstOrNull { it.name == name.trim() } }
        return (saved + DictionaryPack.entries).distinct()
    }

    private fun setWordNet(state: DictionaryPackState) {
        wordNetState = state
        publish()
    }

    private fun setGcide(state: DictionaryPackState, progress: Float? = null) {
        gcideState = state
        gcideProgress = progress
        publish()
    }

    private fun anyInstalledState(): DictionaryPackState = when {
        wordNetState is DictionaryPackState.Installed || gcideState is DictionaryPackState.Installed -> DictionaryPackState.Installed
        wordNetState is DictionaryPackState.Installing || gcideState is DictionaryPackState.Installing -> DictionaryPackState.Installing
        wordNetState is DictionaryPackState.Failed || gcideState is DictionaryPackState.Failed -> DictionaryPackState.Failed
        else -> DictionaryPackState.NotInstalled
    }

    private fun statuses(): List<DictionaryPackStatus> = order.map { pack ->
        when (pack) {
            DictionaryPack.WORDNET -> DictionaryPackStatus(pack, wordNetState,
                packDirectory.listFiles()?.sumOf { it.length() } ?: 0L)
            DictionaryPack.GCIDE -> DictionaryPackStatus(pack, gcideState, gcideFile.length(), gcideProgress)
        }
    }

    private fun publish() {
        _englishPackState.value = anyInstalledState()
        _packs.value = statuses()
    }

    override suspend fun installEnglish(sourceUri: String) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            setWordNet(DictionaryPackState.Installing)
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
                setWordNet(DictionaryPackState.Installed)
            } catch (throwable: Throwable) {
                stagingDirectory.deleteRecursively()
                if (!isValidPack(packDirectory) && isValidPack(backupDirectory)) {
                    packDirectory.deleteRecursively()
                    backupDirectory.renameTo(packDirectory)
                }
                setWordNet(if (isValidPack(packDirectory)) DictionaryPackState.Installed else DictionaryPackState.Failed)
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
        const val GcideDownloadUrl = "https://ftp.gnu.org/gnu/gcide/gcide-latest.zip"
        const val GcideDownloadName = "gcide-download.zip"
        const val MaxGcideDownloadBytes = 64L * 1024L * 1024L
        const val NetworkTimeoutMillis = 30_000
        /** The download is about half the wait; building the lookup file the rest. */
        const val DownloadShare = 0.5f
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DictionaryModule {
    @Binds
    abstract fun bindDictionaryRepository(implementation: OfflineDictionaryRepository): DictionaryRepository
}
