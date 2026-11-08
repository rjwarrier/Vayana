package com.vayana.format.epub

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

data class EpubCleanupOptions(
    val gutenberg: Boolean = false,
    val images: Boolean = false,
    val fonts: Boolean = false,
    val textArtifacts: Boolean = false,
    val media: Boolean = false,
) {
    val hasSelection: Boolean get() = gutenberg || images || fonts || textArtifacts || media
}

/** Compressed resource sizes are estimates; rewriting chapter markup also changes the final size. */
data class EpubCleanupInventory(
    val images: Int,
    val imageBytes: Long,
    val fonts: Int,
    val fontBytes: Long,
    val media: Int,
    val mediaBytes: Long,
    val gutenberg: Boolean,
    val textArtifacts: Boolean,
    val inlineImages: Boolean = false,
    val inlineFonts: Boolean = false,
    val inlineMedia: Boolean = false,
)

/** Rewrites a separate copy, including package references, without ever altering the source. */
object EpubCleanup {
    fun inspect(source: File, checkCancelled: () -> Unit = {}): EpubCleanupInventory = ZipFile(source).use { zip ->
        val archive = Archive(zip, checkCancelled)
        var gutenberg = false
        var textArtifacts = false
        var inlineImages = false
        var inlineFonts = false
        var inlineMedia = false
        for (entry in archive.entries.filter { it.name.extension() in Chapters }) {
            checkCancelled()
            val bytes = archive.read(entry)
            gutenberg = gutenberg || GutenbergCleanup.cleanChapter(bytes, textArtifacts = false).second > 0
            textArtifacts = textArtifacts || GutenbergCleanup.cleanChapter(bytes, gutenberg = false).second > 0
            val document = parse(bytes)
            for (element in document.elements()) {
                inlineImages = inlineImages || element.localName in setOf("img", "picture", "svg")
                inlineMedia = inlineMedia || element.localName in setOf("audio", "video")
            }
            val text = bytes.toString(Charsets.UTF_8)
            inlineFonts = inlineFonts || FontFace.containsMatchIn(text)
            inlineImages = inlineImages || text.contains("data:image/", true)
        }
        for (entry in archive.entries.filter { it.name.extension() == "css" }) {
            val text = archive.read(entry).toString(Charsets.UTF_8)
            inlineFonts = inlineFonts || FontFace.containsMatchIn(text)
            inlineImages = inlineImages || text.contains("data:image/", true)
        }
        fun count(kind: Kind) = archive.kinds.count { it.value == kind }
        fun size(kind: Kind) = archive.entries.filter { archive.kinds[it.name] == kind }.sumOf { it.compressedSize.coerceAtLeast(0) }
        EpubCleanupInventory(count(Kind.IMAGE), size(Kind.IMAGE), count(Kind.FONT), size(Kind.FONT),
            count(Kind.MEDIA), size(Kind.MEDIA), gutenberg, textArtifacts, inlineImages, inlineFonts, inlineMedia)
    }

    fun clean(
        source: File,
        destination: File,
        options: EpubCleanupOptions,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
        checkCancelled: () -> Unit = {},
    ): Int {
        require(source.canonicalFile != destination.canonicalFile)
        require(options.hasSelection)
        try {
            ZipFile(source).use { zip ->
                val archive = Archive(zip, checkCancelled)
                val removedPaths = archive.kinds.filterValues {
                    when (it) {
                        Kind.IMAGE -> options.images
                        Kind.FONT -> options.fonts
                        Kind.MEDIA -> options.media
                    }
                }.keys
                val removedIds = archive.packages.values.flatMap { document ->
                    document.elements().filter { it.localName == "item" &&
                        resolve(archive.packagePaths.getValue(document), it.getAttribute("href")) in removedPaths }
                        .map { it.getAttribute("id") }
                }.toSet()
                var changes = removedPaths.size
                val mime = zip.getEntry("mimetype") ?: error("Missing EPUB mimetype")
                val ordered = listOf(mime) + archive.entries.filter { it.name != "mimetype" }
                ZipOutputStream(destination.outputStream().buffered()).use { output ->
                    onProgress(0, ordered.size)
                    for ((index, entry) in ordered.withIndex()) {
                        checkCancelled()
                        if (entry.name !in removedPaths) {
                            val extension = entry.name.extension()
                            val rewrite = extension in Chapters || extension in setOf("opf", "css", "ncx", "smil", "svg") ||
                                entry.name == "META-INF/encryption.xml" || entry.name == "mimetype"
                            var bytes = if (rewrite) archive.read(entry) else null
                            if (bytes != null && extension in Chapters) {
                                val cleaned = GutenbergCleanup.cleanChapter(bytes, options.gutenberg, options.textArtifacts)
                                bytes = cleaned.first
                                changes += cleaned.second
                            }
                            if (bytes != null && extension == "css") {
                                val before = bytes.toString(Charsets.UTF_8)
                                val after = cleanCss(before, entry.name, removedPaths, options)
                                if (before != after) { bytes = after.toByteArray(); changes++ }
                            } else if (bytes != null && entry.name != "mimetype" &&
                                (removedPaths.isNotEmpty() || options.images || options.fonts || options.media)) {
                                val document = archive.packages[entry.name] ?: parse(bytes)
                                if (cleanDocument(document, entry.name, removedPaths, removedIds, options)) {
                                    bytes = serialize(document)
                                    changes++
                                }
                            }
                            val target = ZipEntry(entry.name)
                            if (entry.name == "mimetype") {
                                require(bytes?.toString(Charsets.UTF_8) == "application/epub+zip")
                                target.method = ZipEntry.STORED
                                target.size = bytes.size.toLong()
                                target.crc = CRC32().apply { update(bytes) }.value
                            }
                            output.putNextEntry(target)
                            if (bytes != null) output.write(bytes) else archive.transfer(entry, output)
                            output.closeEntry()
                        }
                        onProgress(index + 1, ordered.size)
                    }
                }
                return changes
            }
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    private enum class Kind { IMAGE, FONT, MEDIA }

    private class Archive(val zip: ZipFile, val checkCancelled: () -> Unit) {
        val entries = zip.entries().asSequence().toList()
        val packages = linkedMapOf<String, Document>()
        val packagePaths = mutableMapOf<Document, String>()
        val kinds = mutableMapOf<String, Kind>()
        private var expanded = 0L
        init {
            require(entries.map { it.name }.distinct().size == entries.size) { "Duplicate EPUB entries" }
            require(entries.size <= 20_000) { "Too many EPUB entries" }
            require(entries.all { it.size >= 0 } && entries.sumOf { it.size } <= MaxExpanded) { "EPUB is too large to clean" }
            for (entry in entries) {
                checkCancelled()
                kind(entry.name, "")?.let { kinds[entry.name] = it }
                if (entry.name.extension() == "opf") {
                    val document = parse(read(entry))
                    packages[entry.name] = document
                    packagePaths[document] = entry.name
                    for (item in document.elements().filter { it.localName == "item" }) {
                        val path = resolve(entry.name, item.getAttribute("href")) ?: continue
                        if (zip.getEntry(path) != null) kind(path, item.getAttribute("media-type"))?.let { kinds[path] = it }
                    }
                }
            }
        }
        fun read(entry: ZipEntry): ByteArray = ByteArrayOutputStream().also { transfer(entry, it) }.toByteArray()
        fun transfer(entry: ZipEntry, output: java.io.OutputStream) {
            zip.getInputStream(entry).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    expanded += count
                    // OPF is read once during inspection and once while writing.
                    require(expanded <= MaxExpanded + 4 * 1024 * 1024) { "EPUB is too large to clean" }
                    output.write(buffer, 0, count)
                }
            }
        }
    }

    private fun kind(path: String, mime: String): Kind? = when {
        mime.startsWith("image/") || path.extension() in setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "svg", "bmp") -> Kind.IMAGE
        mime.startsWith("font/") || mime.contains("font") || path.extension() in setOf("otf", "ttf", "woff", "woff2") -> Kind.FONT
        mime.startsWith("audio/") || mime.startsWith("video/") || mime == "application/smil+xml" ||
            path.extension() in setOf("mp3", "m4a", "aac", "ogg", "oga", "wav", "mp4", "m4v", "webm", "smil") -> Kind.MEDIA
        else -> null
    }

    private fun cleanDocument(
        document: Document, path: String, removed: Set<String>, removedIds: Set<String>, options: EpubCleanupOptions,
    ): Boolean {
        var changed = false
        for (element in document.elements()) {
            val name = element.localName ?: element.tagName
            val target = resolve(path, element.getAttribute("href"))
            val delete = (options.images && name in setOf("img", "picture", "svg")) ||
                (options.media && name in setOf("audio", "video")) ||
                (name == "item" && target in removed) ||
                (name == "itemref" && element.getAttribute("idref") in removedIds) ||
                (name == "meta" && element.getAttribute("name") == "cover" && element.getAttribute("content") in removedIds) ||
                (name == "meta" && element.getAttribute("refines").removePrefix("#") in removedIds) ||
                (options.media && name == "meta" && element.getAttribute("property").startsWith("media:")) ||
                (name == "reference" && target in removed) ||
                (name in setOf("object", "embed") && resolve(path, element.getAttribute(if (name == "object") "data" else "src")) in removed)
            if (delete) { element.parentNode?.removeChild(element); changed = true; continue }
            if (element.getAttribute("media-overlay") in removedIds) {
                element.removeAttribute("media-overlay"); changed = true
            }
            if (element.getAttribute("fallback") in removedIds) {
                element.removeAttribute("fallback"); changed = true
            }
            if (name == "EncryptedData") {
                val references = element.getElementsByTagNameNS("*", "CipherReference")
                if ((0 until references.length).any { resolve(path, (references.item(it) as Element).getAttribute("URI"), root = true) in removed }) {
                    element.parentNode?.removeChild(element); changed = true; continue
                }
            }
            // A library cover is stored separately; only the copy's embedded cover is removed.
            for (attribute in listOf("src", "href", "poster", "data", "xlink:href")) {
                if (resolve(path, element.getAttribute(attribute)) in removed) {
                    element.removeAttribute(attribute); changed = true
                }
            }
            if (options.images && element.hasAttribute("srcset")) { element.removeAttribute("srcset"); changed = true }
            if (element.hasAttribute("style")) {
                val before = element.getAttribute("style")
                val after = cleanCss(before, path, removed, options)
                if (before != after) { element.setAttribute("style", after); changed = true }
            }
            if (name == "style") {
                val before = element.textContent
                val after = cleanCss(before, path, removed, options)
                if (before != after) { element.textContent = after; changed = true }
            }
        }
        return changed
    }

    private fun cleanCss(text: String, path: String, removed: Set<String>, options: EpubCleanupOptions): String {
        var cleaned = if (options.fonts) FontFace.replace(text, "") else text
        cleaned = CssUrl.replace(cleaned) { match ->
            val value = match.groupValues[1].trim().trim('\'', '"')
            if (resolve(path, value) in removed || (options.images && value.startsWith("data:image/", true)) ||
                (options.fonts && (value.startsWith("data:font/", true) || value.startsWith("data:application/font", true)))) "none" else match.value
        }
        return cleaned
    }

    private fun parse(bytes: ByteArray): Document = EpubXml.parse(bytes)

    private fun serialize(document: Document): ByteArray = ByteArrayOutputStream().also { output ->
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(output))
    }.toByteArray()

    private fun Document.elements(): List<Element> = getElementsByTagNameNS("*", "*").let { nodes ->
        (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun resolve(path: String, reference: String, root: Boolean = false): String? = runCatching {
        if (reference.isBlank()) return null
        val uri = URI(reference.replace(" ", "%20"))
        if (uri.isAbsolute || uri.rawAuthority != null) return null
        val base = if (root) URI("/") else URI("/" + path.replace(" ", "%20"))
        base.resolve(uri).normalize().path.removePrefix("/")
    }.getOrNull()

    private fun String.extension() = substringAfterLast('.', "").lowercase()
    private val Chapters = setOf("xhtml", "html", "htm")
    private val FontFace = Regex("@font-face\\s*\\{[^}]*\\}", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val CssUrl = Regex("url\\(\\s*([^)]*)\\)", RegexOption.IGNORE_CASE)
    private const val MaxExpanded = 128L * 1024 * 1024
}
