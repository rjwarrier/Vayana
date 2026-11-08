package com.vayana.format.ocr

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONObject

data class OcrSyncArchive(val bytes: ByteArray, val sha256: String, val updatedAt: Long, val revision: String)

/** Completed OCR travels with its boxes, joined words, text EPUB and printed-page map; never the models. */
object OcrSyncArchives {
    fun capture(store: OcrStore, book: String, checkCancelled: () -> Unit = {}): OcrSyncArchive? {
        require(BookHash.matches(book))
        if (!store.hasLineJoins(book) || store.textEpub(book) == null) return null
        val pages = store.savedPages(book)
        val count = store.textPageMap(book).size
        if (count == 0 || count > MaxPages || store.pageCount(book) != count || pages.size != count || (0 until count).any { it !in pages }) return null
        val directory = File(store.root, book)
        val files = (pages.sorted().map { File(directory, "$it.json") } +
            listOf(LineJoinsMarker, TextEpubName, TextPageMapName).map { File(directory, it) }).sortedBy { it.name }
        require(files.sumOf { it.length() } <= MaxExpanded) { "Recognized text is too large to sync" }
        val fingerprint = fingerprint(files)
        val cache = File(directory, ".sync-v1.zip")
        val cacheMetadata = File(directory, ".sync-v1.json")
        try {
            val metadata = JSONObject(cacheMetadata.readText())
            if (metadata.getString("fingerprint") == fingerprint && cache.length() in 1..MaxExpanded) {
                val cached = cache.readBytes()
                if (hash(cached) == metadata.getString("sha256")) {
                    checkCancelled()
                    return OcrSyncArchive(cached, metadata.getString("sha256"), metadata.getLong("updatedAt"), metadata.getString("revision"))
                }
            }
        } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: Exception) { /* A disposable archive cache; rebuild missing or corrupt cache entries. */ }
        validateTextView(store, book)
        val revision = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                fun write(name: String, data: ByteArray) {
                    zip.putNextEntry(ZipEntry(name).apply { time = 0 })
                    zip.write(data); zip.closeEntry()
                }
                write("book.json", JSONObject().put("version", 1).put("book", book).put("pages", count).toString().toByteArray())
                for (file in files) {
                    checkCancelled()
                    if (PageName.matches(file.name)) require(store.read(book, file.name.removeSuffix(".json").toInt()) != null)
                    val data = file.readBytes()
                    revision.update("${file.name}:${data.size}:".toByteArray())
                    revision.update(data)
                    write(file.name, data)
                }
            }
        }.toByteArray()
        require(bytes.size <= MaxExpanded) { "Recognized text archive is too large to sync" }
        check(fingerprint(files) == fingerprint) { "Recognized text changed during archive creation" }
        val result = OcrSyncArchive(bytes, hash(bytes), files.maxOf { it.lastModified() }.coerceAtLeast(1),
            revision.digest().joinToString("") { "%02x".format(it) })
        cacheArchive(directory, fingerprint, result)
        return result
    }

    private fun cacheArchive(directory: File, fingerprint: String, result: OcrSyncArchive) {
        // Cache errors must not stop sync; metadata is committed last and cached bytes are always verified.
        try {
            atomicWrite(File(directory, ".sync-v1.zip"), result.bytes)
            atomicWrite(File(directory, ".sync-v1.json"), JSONObject().put("fingerprint", fingerprint).put("sha256", result.sha256)
                .put("updatedAt", result.updatedAt).put("revision", result.revision).toString().toByteArray())
        } catch (_: java.io.IOException) { }
    }

    /** Call under RecognitionAccess.book. A bad download never replaces existing recognition. */
    fun restore(store: OcrStore, book: String, archive: OcrSyncArchive, checkCancelled: () -> Unit = {}) {
        require(BookHash.matches(book))
        require(archive.bytes.size <= MaxExpanded && hash(archive.bytes) == archive.sha256)
        // Siblings are on the same filesystem, outside the OCR orphan cleaner's directory scan.
        val stagingRoot = File(store.root.absoluteFile.parentFile, ".${store.root.name}-sync-${UUID.randomUUID()}")
        val staged = File(stagingRoot, book).apply { mkdirs() }
        val destination = File(store.root, book)
        val previous = File(stagingRoot, "previous")
        val journal = File(store.root, ".restore-$book.json")
        try {
            var expanded = 0L
            val names = mutableSetOf<String>()
            ZipInputStream(ByteArrayInputStream(archive.bytes)).use { zip ->
                while (true) {
                    checkCancelled()
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && allowed(entry.name) && names.add(entry.name)) { "Invalid recognized text archive entry" }
                    require(names.size <= MaxPages + 4)
                    File(staged, entry.name).outputStream().use { output ->
                        val chunk = ByteArray(8192)
                        while (true) {
                            checkCancelled()
                            val read = zip.read(chunk)
                            if (read < 0) break
                            expanded += read
                            require(expanded <= MaxExpanded) { "Recognized text archive is too large" }
                            output.write(chunk, 0, read)
                        }
                    }
                    File(staged, entry.name).setLastModified(archive.updatedAt)
                }
            }
            val metadata = JSONObject(File(staged, "book.json").readText())
            require(metadata.getInt("version") == 1 && metadata.getString("book") == book)
            val count = metadata.getInt("pages")
            require(count in 1..MaxPages && names.size == count + 4)
            val incoming = OcrStore(stagingRoot)
            incoming.recordPageCount(book, count)
            require(incoming.hasLineJoins(book) && incoming.textEpub(book) != null && incoming.textPageMap(book).size == count)
            validateTextView(incoming, book)
            for (page in 0 until count) {
                checkCancelled()
                require(incoming.read(book, page) != null) { "Recognized text archive has a missing or invalid page" }
            }
            // Verify the downloaded files directly: rebuilding a second ZIP here doubles restore CPU and memory.
            val files = names.filter { it != "book.json" }.sorted().map { File(staged, it) }
            val digest = MessageDigest.getInstance("SHA-256")
            for (file in files) {
                checkCancelled()
                digest.update("${file.name}:${file.length()}:".toByteArray())
                file.inputStream().use { input ->
                    val chunk = ByteArray(8192)
                    while (true) {
                        checkCancelled()
                        val countRead = input.read(chunk)
                        if (countRead < 0) break
                        digest.update(chunk, 0, countRead)
                    }
                }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) } == archive.revision) { "Recognized text revision does not match" }
            cacheArchive(staged, fingerprint(files), archive)
            File(staged, "book.json").delete()
            checkCancelled()
            synchronized(InstallLock) {
                store.root.mkdirs()
                atomicWrite(journal, JSONObject().put("staging", stagingRoot.name).toString().toByteArray())
                if (destination.exists()) Files.move(destination.toPath(), previous.toPath())
                Files.move(staged.toPath(), destination.toPath())
                check(journal.delete()) { "Could not complete recognized text installation" }
            }
            OcrStore.changed()
        } finally {
            if (journal.exists()) recover(store.root)
            if (!previous.exists() || destination.exists()) stagingRoot.deleteRecursively()
        }
    }

    /** Recover a process death between directory moves before any reader accesses the OCR store. */
    internal fun recover(root: File) = synchronized(InstallLock) {
        for (journal in root.listFiles().orEmpty().filter { RestoreJournal.matches(it.name) }) {
            val book = RestoreJournal.matchEntire(journal.name)!!.groupValues[1]
            val name = try { JSONObject(journal.readText()).getString("staging") } catch (_: Exception) { continue }
            val prefix = ".${root.name}-sync-"
            if (!name.startsWith(prefix) || runCatching { UUID.fromString(name.removePrefix(prefix)) }.isFailure) continue
            val staging = File(root.absoluteFile.parentFile, name)
            val previous = File(staging, "previous")
            val destination = File(root, book)
            if (!destination.exists()) {
                when {
                    previous.isDirectory -> Files.move(previous.toPath(), destination.toPath())
                    File(staging, book).isDirectory -> Files.move(File(staging, book).toPath(), destination.toPath())
                    else -> continue
                }
            }
            if (journal.delete()) staging.deleteRecursively()
        }
    }

    private fun fingerprint(files: List<File>) = hash(files.joinToString("\n") { "${it.name}:${it.length()}:${it.lastModified()}" }
        .plus(":${files.first { it.name == LineJoinsMarker }.readText()}").toByteArray())

    private fun validateTextView(store: OcrStore, book: String) {
        ZipFile(requireNotNull(store.textEpub(book))).use { zip ->
            val entries = zip.entries().asSequence().toList()
            require(entries.size <= MaxPages + 16 && entries.all { it.size in 0..MaxExpanded } && entries.sumOf { it.size } <= MaxExpanded)
            require(entries.map { it.name }.distinct().size == entries.size && zip.getEntry("OEBPS/content.opf") != null)
            require(store.textPageMap(book).all { path ->
                path != null && path.startsWith("OEBPS/c") && path.endsWith(".xhtml") && ".." !in path && zip.getEntry(path)?.isDirectory == false
            }) { "Recognized text has an invalid printed-page map" }
        }
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        val partial = File(file.parentFile, "${file.name}.part")
        try {
            partial.writeBytes(bytes)
            Files.move(partial.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { partial.delete() }
    }

    private fun allowed(name: String) = name in setOf("book.json", LineJoinsMarker, TextEpubName, TextPageMapName) ||
        PageName.matches(name) && name.removeSuffix(".json").toIntOrNull()?.let { it in 0 until MaxPages } == true
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val BookHash = Regex("[0-9a-f]{64}")
    private val PageName = Regex("(?:0|[1-9][0-9]{0,4})\\.json")
    private val RestoreJournal = Regex("\\.restore-([0-9a-f]{64})\\.json")
    private val InstallLock = Any()
    private const val MaxPages = 50_000
    private const val MaxExpanded = 32L * 1024 * 1024
}
