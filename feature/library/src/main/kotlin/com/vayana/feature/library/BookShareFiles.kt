package com.vayana.feature.library

import android.content.Intent
import androidx.core.content.FileProvider
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.resources.R
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun Book.toShareText(context: android.content.Context): String {
    val progress = (readingPercent * 100).toInt()
    return buildString {
        appendLine(homeLibraryDisplayTitle)
        if (homeLibraryOriginalTitle != null) appendLine(title)
        author?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        appendLine(context.getString(R.string.library_share_book_progress, progress))
    }.trim()
}

/** Copies the book file into the share cache off the main thread (books can be hundreds of MB), then shares it. */
internal suspend fun android.content.Context.shareBookFile(book: Book) {
    val uri = withContext(Dispatchers.IO) {
        val source = File(book.filePath)
        val sharedFile = source.copyToSharedBookFile(this@shareBookFile, book.shareFileName(source))
        FileProvider.getUriForFile(this@shareBookFile, "$packageName.fileprovider", sharedFile)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = book.format.shareMimeType()
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.library_share_file)))
}

private fun File.copyToSharedBookFile(context: android.content.Context, fileName: String): File {
    val dir = File(context.cacheDir, "shared_books").apply { mkdirs() }
    return copyTo(File(dir, fileName), overwrite = true)
}

private fun Book.shareFileName(source: File): String {
    val extension = source.extension.toShareFileExtension().ifBlank { format.defaultExtension() }
    val suffix = extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    return "${shareFileBaseName()}$suffix"
}

internal fun Book.shareFileBaseName(): String =
    "${title.toShareFileSegment(fallback = "Book")}_${author.orEmpty().toShareFileSegment(fallback = "UnknownAuthor")}"

private fun String.toShareFileSegment(fallback: String): String {
    val segment = split(Regex("[^\\p{L}\\p{N}]+"))
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
            }
        }
        .take(MaxSharedBookFileSegmentChars)
    return segment.ifBlank { fallback }
}

private fun String.toShareFileExtension(): String =
    lowercase(Locale.ROOT)
        .filter { it in 'a'..'z' || it in '0'..'9' }
        .take(MaxSharedBookFileExtensionChars)

private fun BookFormat.shareMimeType(): String = when (this) {
    BookFormat.EPUB -> "application/epub+zip"
    BookFormat.PDF -> "application/pdf"
    BookFormat.TXT -> "text/plain"
    BookFormat.MOBI,
    BookFormat.AZW3,
    BookFormat.FB2,
    BookFormat.PHYSICAL,
    BookFormat.AUDIOBOOK,
    BookFormat.OTHER_EBOOK,
    -> "application/octet-stream"
}

private fun BookFormat.defaultExtension(): String = when (this) {
    BookFormat.EPUB -> "epub"
    BookFormat.PDF -> "pdf"
    BookFormat.TXT -> "txt"
    BookFormat.MOBI -> "mobi"
    BookFormat.AZW3 -> "azw3"
    BookFormat.FB2 -> "fb2"
    BookFormat.PHYSICAL, BookFormat.AUDIOBOOK, BookFormat.OTHER_EBOOK -> ""
}
