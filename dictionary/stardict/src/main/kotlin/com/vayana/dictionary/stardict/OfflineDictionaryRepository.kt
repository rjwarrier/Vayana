package com.vayana.dictionary.stardict

import android.content.Context
import android.net.Uri
import com.vayana.core.resources.LocalizedException
import com.vayana.core.resources.R
import com.vayana.core.resources.failUnless
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionaryKind
import com.vayana.dictionary.api.DictionaryPackState
import com.vayana.dictionary.api.DictionaryRepository
import com.vayana.dictionary.api.DictionaryStatus
import com.vayana.dictionary.api.FreeDictOffer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Every offline dictionary: WordNet (its own files), and SQLite lookup files built on the phone for GCIDE, Olam,
 * FreeDict and imported StarDict dictionaries. Lookups go by the book's language (see [lookup]); [order] is the
 * reader's preference among dictionaries for the same languages.
 */
@Singleton
internal class OfflineDictionaryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : DictionaryRepository {
    private val dictionariesDirectory = File(context.filesDir, "dictionaries")
    private val packDirectory = File(dictionariesDirectory, "oewn-2025")
    private val stagingDirectory = File(dictionariesDirectory, "oewn-2025.installing")
    private val backupDirectory = File(dictionariesDirectory, "oewn-2025.backup")
    private val packsDirectory = File(dictionariesDirectory, "packs")
    private val orderFile = File(dictionariesDirectory, "order")

    /** One dictionary's file and current state; built-ins exist whether or not they're installed. */
    private data class Slot(
        val id: String,
        val kind: DictionaryKind,
        val file: File,
        val state: DictionaryPackState,
        val name: String? = null,
        val wordLanguage: String = English,
        val meaningLanguage: String = English,
        val progress: Float? = null,
    ) {
        val installed: Boolean get() = state == DictionaryPackState.Installed
        val monolingual: Boolean get() = wordLanguage == meaningLanguage
    }

    private val installMutex = Mutex()
    private val openDictionaries = HashMap<String, SqliteDictionary>()
    @Volatile
    private var wordNet: WordNetDictionary? = null

    @Volatile
    private var slots: List<Slot>

    init {
        recoverInterruptedInstall()
        // Staging files of an interrupted install, and the empty journals SQLite leaves beside them.
        dictionariesDirectory.listFiles { file -> StagingSuffix in file.name }?.forEach { it.deleteRecursively() }
        packsDirectory.listFiles { file -> StagingSuffix in file.name }?.forEach { it.deleteRecursively() }
        context.cacheDir.listFiles { file -> file.name.startsWith(DownloadPrefix) }?.forEach { it.deleteRecursively() }
        slots = discover()
    }

    private val _englishPackState = MutableStateFlow(anyInstalledState())
    override val englishPackState: StateFlow<DictionaryPackState> = _englishPackState
    private val _dictionaries = MutableStateFlow(statuses())
    override val dictionaries: StateFlow<List<DictionaryStatus>> = _dictionaries

    // region Lookup

    override suspend fun lookup(word: String, bookLanguage: String?): DictionaryEntry? = withContext(Dispatchers.IO) {
        if (_englishPackState.value != DictionaryPackState.Installed) return@withContext null
        val installed = slots.filter { it.installed }
        val language = bookLanguage?.let(::iso3)
        val wordLanguage = language ?: English
        // Defined in the book's own language first, then explained in English; the reader's order within each.
        val primary = installed
            .filter { it.wordLanguage == wordLanguage && (it.monolingual || it.meaningLanguage == English) }
            .sortedBy { if (it.monolingual) 0 else 1 }
        var mainSlot: Slot? = null
        var main = primary.firstNotNullOfOrNull { slot -> lookIn(slot, word)?.also { mainSlot = slot } }
        val extras = installed
            .filter { it.wordLanguage == wordLanguage && !it.monolingual && it != mainSlot }
            .mapNotNull { slot -> lookIn(slot, word)?.let { slot to it } }
            .distinctBy { (slot, _) -> slot.meaningLanguage }
            .filter { (slot, _) -> slot.meaningLanguage != main?.meaningLanguage }
            .map { it.second }
            .take(MaxExtraLanguages)
            .toMutableList()
        if (main == null && extras.isNotEmpty()) main = extras.removeAt(0)
        if (main == null && language == null) main = installed.filter { it.wordLanguage != English }.firstNotNullOfOrNull { lookIn(it, word) }
        main?.copy(alsoIn = extras)
    }

    private fun lookIn(slot: Slot, word: String): DictionaryEntry? = runCatching {
        when (slot.kind) {
            DictionaryKind.WORDNET -> wordNetDictionary().lookup(word)
            else -> sqlite(slot).lookup(word)
        }
    }.getOrNull()

    private fun wordNetDictionary(): WordNetDictionary = wordNet ?: synchronized(this) {
        wordNet ?: WordNetDictionary(packDirectory).also { wordNet = it }
    }

    private fun sqlite(slot: Slot): SqliteDictionary = synchronized(openDictionaries) {
        openDictionaries.getOrPut(slot.id) { SqliteDictionary(slot.file, fallbackAttribution(slot)) }
    }

    private fun fallbackAttribution(slot: Slot): String? = when (slot.kind) {
        DictionaryKind.GCIDE -> "GCIDE · GPL"
        DictionaryKind.OLAM -> "Olam · ODbL"
        else -> slot.name
    }

    private fun closeDictionary(id: String) = synchronized(openDictionaries) { openDictionaries.remove(id)?.close() }

    // endregion

    // region Installs

    override suspend fun installGcide() = installPack(GcideId, DictionaryKind.GCIDE) { staging, progress ->
        val download = downloadFile(GcideDownloadUrl, GcideHost, MaxGcideBytes, sha512 = null) { progress(it * DownloadShare) }
        try {
            buildOrFail(R.string.dictionary_error_not_gcide) {
                GcideParser.build(download, staging) { progress(DownloadShare + it * (1f - DownloadShare)) }
            }
        } finally {
            download.delete()
        }
    }

    override suspend fun installOlam() = installPack(OlamId, DictionaryKind.OLAM, meaningLanguage = "mal") { staging, progress ->
        val download = downloadFile(OlamDownloadUrl, OlamHost, MaxOlamBytes, sha512 = null) { progress(it * DownloadShare) }
        try {
            buildOrFail(R.string.dictionary_error_not_dictionary) {
                OlamPack.build(download, staging) { progress(DownloadShare + it * (1f - DownloadShare)) }
            }
        } finally {
            download.delete()
        }
    }

    override suspend fun freeDictCatalog(): List<FreeDictOffer> = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "freedict-database.json")
        val fresh = cache.isFile && System.currentTimeMillis() - cache.lastModified() < CatalogMaxAgeMillis
        if (!fresh) {
            val download = downloadFile(FreeDictCatalogUrl, FreeDictHost, MaxCatalogBytes, sha512 = null) {}
            if (!download.renameTo(cache)) {
                download.copyTo(cache, overwrite = true)
                download.delete()
            }
        }
        runCatching { FreeDictCatalog.parse(cache.readText()) }.getOrElse {
            cache.delete()
            throw LocalizedException(R.string.dictionary_error_download_failed)
        }
    }

    override suspend fun installFreeDict(offer: FreeDictOffer) {
        failUnless(offer.url.startsWith(FreeDictCatalog.DownloadPrefix), R.string.dictionary_error_download_failed)
        val id = "$FreeDictIdPrefix${offer.name}"
        installPack(id, DictionaryKind.FREEDICT, name = offer.name, wordLanguage = offer.wordLanguage, meaningLanguage = offer.meaningLanguage) { staging, progress ->
            val download = downloadFile(offer.url, FreeDictDownloadHost, MaxStarDictBytes, offer.sha512.takeIf { it.length == 128 }) { progress(it * DownloadShare) }
            try {
                buildOrFail(R.string.dictionary_error_not_dictionary) {
                    StarDictPack.build(download, File(context.cacheDir, "$DownloadPrefix$id.unpacked"), staging, offer.wordLanguage, offer.meaningLanguage) {
                        progress(DownloadShare + it * (1f - DownloadShare))
                    }
                }
            } finally {
                download.delete()
            }
        }
    }

    override suspend fun importStarDict(sourceUri: String, wordLanguage: String, meaningLanguage: String) {
        val id = "$StarDictIdPrefix${System.currentTimeMillis()}"
        installPack(id, DictionaryKind.STARDICT, wordLanguage = wordLanguage, meaningLanguage = meaningLanguage) { staging, progress ->
            val copy = File(context.cacheDir, "$DownloadPrefix$id.archive")
            try {
                val input = context.contentResolver.openInputStream(Uri.parse(sourceUri)) ?: throw LocalizedException(R.string.dictionary_error_open_file)
                input.use { stream -> copyCapped(stream, copy, MaxStarDictBytes) }
                buildOrFail(R.string.dictionary_error_not_dictionary) {
                    StarDictPack.build(copy, File(context.cacheDir, "$DownloadPrefix$id.unpacked"), staging, wordLanguage, meaningLanguage, progress)
                }
            } finally {
                copy.delete()
            }
        }
    }

    /**
     * Runs one install: the slot shows Installing with progress, [build] writes the lookup file to a staging path,
     * which then replaces any previous one. Failures leave the previous dictionary (if any) as it was.
     */
    private suspend fun installPack(
        id: String,
        kind: DictionaryKind,
        name: String? = null,
        wordLanguage: String = English,
        meaningLanguage: String = English,
        build: suspend (staging: File, progress: (Float) -> Unit) -> Unit,
    ) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val file = fileFor(id, kind)
            val staging = File(file.path + StagingSuffix)
            val previous = slots.firstOrNull { it.id == id }
            val job = currentCoroutineContext().job
            update(Slot(id, kind, file, DictionaryPackState.Installing, name, wordLanguage, meaningLanguage, progress = 0f))
            try {
                failUnless(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true, R.string.dictionary_error_prepare_storage)
                build(staging) { fraction ->
                    job.ensureActive()
                    update(Slot(id, kind, file, DictionaryPackState.Installing, name, wordLanguage, meaningLanguage, progress = fraction.coerceIn(0f, 1f)))
                }
                closeDictionary(id)
                file.delete()
                failUnless(staging.renameTo(file), R.string.dictionary_error_finish_install)
                File(staging.path + "-journal").delete()
                val built = runCatching { SqliteDictionary(file).also { it.close() } }.getOrNull()
                update(Slot(id, kind, file, DictionaryPackState.Installed, built?.name ?: name, wordLanguage, meaningLanguage))
            } catch (throwable: Throwable) {
                staging.delete()
                File(staging.path + "-journal").delete()
                when {
                    file.isFile && file.length() > 0L -> update(previous?.copy(state = DictionaryPackState.Installed, progress = null)
                        ?: Slot(id, kind, file, DictionaryPackState.Installed, name, wordLanguage, meaningLanguage))
                    kind in BuiltInKinds -> update(Slot(id, kind, file, if (throwable is CancellationException) DictionaryPackState.NotInstalled else DictionaryPackState.Failed))
                    else -> removeSlot(id)
                }
                throw throwable
            }
        }
    }

    private inline fun buildOrFail(message: Int, block: () -> Unit) {
        try {
            block()
        } catch (failure: IllegalArgumentException) {
            throw LocalizedException(message)
        }
    }

    /**
     * Downloads [url] (HTTPS, only from [host], no redirects) to a cache file within [maxBytes], checking [sha512]
     * when the catalog gives one; progress 0..1 by bytes.
     */
    private suspend fun downloadFile(url: String, host: String, maxBytes: Long, sha512: String?, onProgress: (Float) -> Unit): File {
        val parsed = URL(url)
        failUnless(parsed.protocol == "https" && parsed.host == host, R.string.dictionary_error_download_failed)
        val destination = File.createTempFile(DownloadPrefix, ".part", context.cacheDir)
        val connection = parsed.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = NetworkTimeoutMillis
            connection.readTimeout = NetworkTimeoutMillis
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Vayana")
            // Uncompressed on the wire, so the length is known and progress can be shown (archives are compressed already).
            connection.setRequestProperty("Accept-Encoding", "identity")
            failUnless(connection.responseCode == HttpURLConnection.HTTP_OK, R.string.dictionary_error_download_failed)
            val total = connection.contentLengthLong
            failUnless(total <= maxBytes, R.string.dictionary_error_archive_too_large)
            val digest = sha512?.let { MessageDigest.getInstance("SHA-512") }
            var read = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        read += count
                        failUnless(read <= maxBytes, R.string.dictionary_error_archive_too_large)
                        output.write(buffer, 0, count)
                        digest?.update(buffer, 0, count)
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (digest != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                failUnless(actual == sha512, R.string.dictionary_error_checksum)
            }
            return destination
        } catch (failure: IOException) {
            destination.delete()
            throw LocalizedException(R.string.dictionary_error_download_failed)
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        } finally {
            connection.disconnect()
        }
    }

    private fun copyCapped(input: java.io.InputStream, file: File, maxBytes: Long) {
        var total = 0L
        FileOutputStream(file).buffered().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                failUnless(total <= maxBytes, R.string.dictionary_error_archive_too_large)
                output.write(buffer, 0, count)
            }
        }
    }

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val slot = slots.firstOrNull { it.id == id } ?: return@withLock
            when (slot.kind) {
                DictionaryKind.WORDNET -> synchronized(this@OfflineDictionaryRepository) {
                    wordNet = null
                    packDirectory.deleteRecursively()
                }
                else -> {
                    closeDictionary(id)
                    slot.file.delete()
                }
            }
            if (slot.kind in BuiltInKinds) update(slot.copy(state = DictionaryPackState.NotInstalled, progress = null)) else removeSlot(id)
        }
    }

    override fun moveUp(id: String) {
        val index = slots.indexOfFirst { it.id == id }
        if (index <= 0) return
        slots = slots.toMutableList().apply { add(index - 1, removeAt(index)) }
        saveOrder()
        publish()
    }

    // endregion

    // region State

    private fun discover(): List<Slot> {
        val builtIns = listOf(
            Slot(WordNetId, DictionaryKind.WORDNET, packDirectory,
                if (isValidPack(packDirectory)) DictionaryPackState.Installed else DictionaryPackState.NotInstalled),
            sqliteSlot(GcideId, DictionaryKind.GCIDE, File(dictionariesDirectory, "gcide.db")),
            sqliteSlot(OlamId, DictionaryKind.OLAM, File(dictionariesDirectory, "olam.db")),
        )
        val packs = packsDirectory.listFiles { file -> file.name.endsWith(".db") }.orEmpty().sortedBy { it.name }.mapNotNull { file ->
            val id = file.name.removeSuffix(".db")
            val kind = if (id.startsWith(FreeDictIdPrefix)) DictionaryKind.FREEDICT else DictionaryKind.STARDICT
            sqliteSlot(id, kind, file).takeIf { it.installed }
        }
        val saved = runCatching { orderFile.readText() }.getOrNull().orEmpty().split(',').map { it.trim() }
        return (builtIns + packs).sortedBy { slot -> saved.indexOf(slot.id).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    private fun sqliteSlot(id: String, kind: DictionaryKind, file: File): Slot {
        if (!file.isFile || file.length() == 0L) return Slot(id, kind, file, DictionaryPackState.NotInstalled,
            meaningLanguage = if (kind == DictionaryKind.OLAM) "mal" else English)
        val dictionary = runCatching { SqliteDictionary(file) }.getOrNull()
            ?: return Slot(id, kind, file, DictionaryPackState.Failed)
        return try {
            val meaning = if (kind == DictionaryKind.OLAM) "mal" else dictionary.meaningLanguage
            Slot(id, kind, file, DictionaryPackState.Installed, dictionary.name, dictionary.wordLanguage, meaning)
        } finally {
            dictionary.close()
        }
    }

    private fun fileFor(id: String, kind: DictionaryKind): File = when (kind) {
        DictionaryKind.WORDNET -> packDirectory
        DictionaryKind.GCIDE -> File(dictionariesDirectory, "gcide.db")
        DictionaryKind.OLAM -> File(dictionariesDirectory, "olam.db")
        DictionaryKind.FREEDICT, DictionaryKind.STARDICT -> File(packsDirectory, "$id.db")
    }

    private fun update(slot: Slot) {
        synchronized(this) {
            val index = slots.indexOfFirst { it.id == slot.id }
            slots = if (index < 0) slots + slot else slots.toMutableList().apply { set(index, slot) }
        }
        publish()
    }

    private fun removeSlot(id: String) {
        synchronized(this) { slots = slots.filterNot { it.id == id } }
        saveOrder()
        publish()
    }

    private fun saveOrder() = runCatching {
        dictionariesDirectory.mkdirs()
        orderFile.writeText(slots.joinToString(",") { it.id })
    }

    private fun anyInstalledState(): DictionaryPackState = when {
        slots.any { it.installed } -> DictionaryPackState.Installed
        slots.any { it.state == DictionaryPackState.Installing } -> DictionaryPackState.Installing
        slots.any { it.state == DictionaryPackState.Failed } -> DictionaryPackState.Failed
        else -> DictionaryPackState.NotInstalled
    }

    private fun statuses(): List<DictionaryStatus> = slots.map { slot ->
        DictionaryStatus(
            id = slot.id,
            kind = slot.kind,
            name = slot.name,
            wordLanguage = slot.wordLanguage,
            meaningLanguage = slot.meaningLanguage,
            state = slot.state,
            sizeBytes = if (slot.kind == DictionaryKind.WORDNET) packDirectory.listFiles()?.sumOf { it.length() } ?: 0L else slot.file.length(),
            progress = slot.progress,
        )
    }

    private fun publish() {
        _englishPackState.value = anyInstalledState()
        _dictionaries.value = statuses()
    }

    // endregion

    // region WordNet (installed from the official ZIP the reader picks)

    override suspend fun installEnglish(sourceUri: String) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val wordNetSlot = slots.first { it.id == WordNetId }
            update(wordNetSlot.copy(state = DictionaryPackState.Installing, progress = null))
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
                        if (restored) R.string.dictionary_error_finish_install else R.string.dictionary_error_finish_install_no_restore,
                    )
                }
                backupDirectory.deleteRecursively()
                synchronized(this@OfflineDictionaryRepository) { wordNet = null }
                update(wordNetSlot.copy(state = DictionaryPackState.Installed, progress = null))
            } catch (throwable: Throwable) {
                stagingDirectory.deleteRecursively()
                if (!isValidPack(packDirectory) && isValidPack(backupDirectory)) {
                    packDirectory.deleteRecursively()
                    backupDirectory.renameTo(packDirectory)
                }
                update(wordNetSlot.copy(state = if (isValidPack(packDirectory)) DictionaryPackState.Installed else DictionaryPackState.Failed, progress = null))
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

    // endregion

    private companion object {
        const val English = "eng"
        const val WordNetId = "wordnet"
        const val GcideId = "gcide"
        const val OlamId = "olam"
        const val FreeDictIdPrefix = "freedict-"
        const val StarDictIdPrefix = "stardict-"
        const val StagingSuffix = ".installing"
        const val DownloadPrefix = "dictionary-download-"
        val BuiltInKinds = setOf(DictionaryKind.WORDNET, DictionaryKind.GCIDE, DictionaryKind.OLAM)
        const val MaxExtraLanguages = 2

        val RequiredFiles = setOf(
            "index.noun", "data.noun", "index.verb", "data.verb",
            "index.adj", "data.adj", "index.adv", "data.adv",
        )
        val AllowedFiles = RequiredFiles + setOf("noun.exc", "verb.exc", "adj.exc", "adv.exc")
        const val MaxArchiveEntries = 32
        const val MaxExtractedBytes = 64L * 1024L * 1024L

        const val GcideHost = "ftp.gnu.org"
        const val GcideDownloadUrl = "https://ftp.gnu.org/gnu/gcide/gcide-latest.zip"
        const val OlamHost = "olam.in"
        const val OlamDownloadUrl = "https://olam.in/files/enml.tar.gz"
        const val FreeDictHost = "freedict.org"
        const val FreeDictCatalogUrl = "https://freedict.org/freedict-database.json"
        const val FreeDictDownloadHost = "download.freedict.org"
        const val MaxGcideBytes = 64L * 1024L * 1024L
        const val MaxOlamBytes = 16L * 1024L * 1024L
        const val MaxCatalogBytes = 8L * 1024L * 1024L
        const val MaxStarDictBytes = 256L * 1024L * 1024L
        const val CatalogMaxAgeMillis = 24L * 60 * 60 * 1000
        const val NetworkTimeoutMillis = 30_000
        /** The download is about half the wait; building the lookup file the rest. */
        const val DownloadShare = 0.5f

        /** "en" or "eng" → "eng": books report ISO 639-1, dictionaries ISO 639-3 (FreeDict's codes). */
        fun iso3(language: String): String? {
            val code = language.trim().substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
            if (code.isEmpty()) return null
            return if (code.length == 3) code else runCatching { Locale(code).isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DictionaryModule {
    @Binds
    abstract fun bindDictionaryRepository(implementation: OfflineDictionaryRepository): DictionaryRepository
}
