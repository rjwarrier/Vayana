package com.vayana.reader.web

import java.io.File
import kotlin.math.abs

/**
 * PDF pages as pdf.js drew them, one WebP per page, size, colours and rotation, in a folder per book (the
 * [PageCountStore.bookKey]). Reopening a book or jumping to a page read before then shows the saved image instead of
 * decoding the page again, which for a scanned page means a full-resolution image. It is a cache: the least recently
 * used files go once the whole store passes [maxBytes].
 */
internal class PdfPageImageCache(private val dir: File, private val maxBytes: Long = DefaultMaxBytes) {
    private val lock = Any()
    private var writesSinceTrim = 0

    /**
     * The saved image of [name]'s page, rotation and colours, marked as just used, or null. Another size serves too,
     * scaled to fit, the closest first: any larger one (page previews shrink a full page), or one at most
     * [SizeTolerance] smaller, since a page's size on screen drifts a little as the crop of a book's margins grows.
     */
    fun read(book: String, name: String): File? {
        val requested = file(book, name) ?: return null
        val file = requested.takeIf { it.isFile } ?: closestSize(requested) ?: return null
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    private fun closestSize(requested: File): File? {
        val (page, width, variant) = PageNameParts.matchEntire(requested.name)?.destructured ?: return null
        val wanted = width.toInt()
        return requested.parentFile?.listFiles().orEmpty()
            .mapNotNull { candidate ->
                val parts = PageNameParts.matchEntire(candidate.name)?.destructured ?: return@mapNotNull null
                val (candidatePage, candidateWidth, candidateVariant) = parts
                val difference = candidateWidth.toInt() - wanted
                if (candidatePage != page || candidateVariant != variant || -difference > wanted * SizeTolerance) null
                else candidate to abs(difference)
            }
            .minByOrNull { it.second }?.first
    }

    fun save(book: String, name: String, bytes: ByteArray) {
        val file = file(book, name) ?: return
        if (bytes.isEmpty()) return
        synchronized(lock) {
            file.parentFile?.mkdirs()
            val partial = File(file.parentFile, "${file.name}.part")
            partial.writeBytes(bytes)
            if (!partial.renameTo(file)) partial.delete()
            if (++writesSinceTrim >= TrimEveryWrites) {
                writesSinceTrim = 0
                trim()
            }
        }
    }

    /** Deletes the least recently used images until the store fits [maxBytes]. */
    fun trim() = synchronized(lock) {
        val files = dir.listFiles().orEmpty().flatMap { it.listFiles().orEmpty().asList() }.filter { it.isFile }
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return@synchronized
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
        dir.listFiles().orEmpty().filter { it.isDirectory && it.list().isNullOrEmpty() }.forEach { it.delete() }
    }

    private fun file(book: String, name: String): File? =
        if (BookKeyPattern.matches(book) && PageNamePattern.matches(name)) File(File(dir, book), name) else null
}

private val BookKeyPattern = Regex("[0-9a-f]{64}")
private val PageNamePattern = Regex("[0-9]{1,6}-[0-9]{1,5}x[0-9]{1,5}-[0-9]{1,3}-[0-9A-Za-z-]{1,40}\\.webp")
// page, width, then rotation and colours
private val PageNameParts = Regex("([0-9]+)-([0-9]+)x[0-9]+-(.+)\\.webp")
private const val SizeTolerance = 0.15

// A page of a scanned book at phone size is ~100-250 KB, so ~1000 pages across books.
private const val DefaultMaxBytes = 200L * 1024 * 1024
private const val TrimEveryWrites = 20
