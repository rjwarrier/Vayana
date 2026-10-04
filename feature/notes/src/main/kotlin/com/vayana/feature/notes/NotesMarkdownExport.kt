package com.vayana.feature.notes

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.vayana.core.common.HighlightTags
import com.vayana.core.common.passageLink
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.model.Book
import java.io.File
import java.io.FilterOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A portable notebook; notes retain their Markdown and hashtags. */
fun highlightsMarkdown(book: Book, annotations: List<Annotation>): String = buildString {
    val visible = annotations.filterNot {
        it.isDeleted || (it.type == AnnotationType.BOOKMARK && it.selectedText.isBlank())
    }
    val tags = (book.tagsCsv.orEmpty().split(',').map(String::trim).filter(String::isNotBlank) +
        visible.flatMap { HighlightTags.parse(it.readerNote) }).distinct()
    appendLine("---")
    appendLine("title: ${yamlString(book.title)}")
    book.author?.takeIf(String::isNotBlank)?.let { appendLine("author: ${yamlString(it)}") }
    appendLine("tags: [${tags.joinToString(", ") { yamlString(it) }}]")
    appendLine("---")
    appendLine()
    appendLine("# ${headingText(book.title)}")
    book.author?.takeIf(String::isNotBlank)?.let { appendLine("*${headingText(it)}*") }
    appendLine()
    visible.groupBy { it.chapterTitle?.takeIf(String::isNotBlank) }.forEach { (chapter, entries) ->
        chapter?.let {
            appendLine("## ${headingText(it)}")
            appendLine()
        }
        entries.forEach { annotation ->
            annotation.reviewQuestion?.takeIf(String::isNotBlank)?.let { appendLine("**$it**"); appendLine() }
            if (annotation.selectedText.isNotBlank()) {
                appendLine("> ${annotation.selectedText.replace("\r\n", "\n").replace("\n", "\n> ")}")
                appendLine()
            }
            if (annotation.syncId.isNotBlank()) { appendLine("[↗](${passageLink(book.syncId, annotation.syncId)})"); appendLine() }
            annotation.readerNote?.takeIf(String::isNotBlank)?.let {
                appendLine(it)
                appendLine()
            }
        }
    }
}.trim()

private fun headingText(value: String): String = value.replace(Regex("[\\r\\n]+"), " ")

private fun yamlString(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 32) append("\\u${character.code.toString(16).padStart(4, '0')}") else append(character)
        }
    }
    append('"')
}

/** IDs make equal/sanitized titles distinct; entry names never include a directory component. */
internal fun notebookFileName(book: Book): String {
    val title = book.title.replace(Regex("[^A-Za-z0-9 _-]"), "")
        .trim().take(100).ifBlank { "notebook" }
    return "$title-${book.id}.md"
}

/** Shares one ZIP containing one Markdown notebook per selected book. */
suspend fun Context.shareMarkdownNotebooks(items: List<BookNotesItem>) {
    require(items.isNotEmpty()) { "Select at least one notebook" }
    val fileName = "vayana-notebooks-${UUID.randomUUID()}.zip"
    val uri = withContext(Dispatchers.IO) {
        val directory = File(cacheDir, "shared_files").apply { check(isDirectory || mkdirs()) }
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.filter { it.name.startsWith("vayana-notebooks-") && it.extension == "zip" && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
        val file = File(directory, fileName)
        try {
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                writeMarkdownNotebooks(zip, items)
            }
            FileProvider.getUriForFile(this@shareMarkdownNotebooks, "$packageName.fileprovider", file)
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(com.vayana.core.resources.R.string.notes_export_markdown_content_description)))
}

/** Format and release one notebook at a time instead of retaining the whole export. */
internal suspend fun writeMarkdownNotebooks(zip: ZipOutputStream, items: List<BookNotesItem>) {
    val seen = HashSet<Long>()
    for (item in items) {
        currentCoroutineContext().ensureActive()
        if (!seen.add(item.book.id)) continue
        val markdown = withContext(Dispatchers.Default) { highlightsMarkdown(item.book, item.annotations) }
        zip.putNextEntry(ZipEntry(notebookFileName(item.book)))
        // Writer avoids a second notebook-sized UTF-8 byte array; don't close the shared ZIP.
        val entryStream = object : FilterOutputStream(zip) {
            override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
            override fun close() = flush()
        }
        entryStream.writer(Charsets.UTF_8).use { it.write(markdown) }
        zip.closeEntry()
    }
}
