package com.vayana.feature.library

import com.vayana.core.database.model.BookFormat
import com.vayana.format.epub.EpubParser
import com.vayana.format.pdf.PdfParser
import com.vayana.format.pdf.PdfPasswordProtectedException
import java.io.File

/** Formats the reader can open. Anything else is reported as unsupported on import. */
internal val ReadableBookFormats = setOf(BookFormat.EPUB, BookFormat.PDF)

private const val EpubMimeType = "application/epub+zip"
private const val PdfMimeType = "application/pdf"

/** The extension for a file whose name has none (common from browsers and mail apps), from its MIME type. */
internal fun readableExtensionForMimeType(mimeType: String?): String = when (mimeType) {
    EpubMimeType -> "epub"
    PdfMimeType -> "pdf"
    else -> ""
}

/** Uses the picked file's extension when present, otherwise its MIME type. */
internal fun readableExtension(displayName: String, mimeType: String?): String =
    displayName.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .ifEmpty { readableExtensionForMimeType(mimeType) }

/** What an imported book file says about itself, whatever its format. */
internal class BookFileMetadata(
    val title: String,
    val author: String?,
    val series: String? = null,
    val seriesNumber: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val coverBytes: ByteArray?,
)

/**
 * Reads [file] as [format]. [displayName] is the name the file was picked under; a PDF without a title of its own is
 * named after it.
 *
 * Password-protected PDFs are imported with filename metadata; the reader asks for the password when opened.
 */
internal fun readBookFileMetadata(file: File, format: BookFormat, displayName: String): BookFileMetadata = when (format) {
    BookFormat.PDF -> try {
        PdfParser.parse(file, fallbackTitle = titleFromFileName(displayName)).let { pdf ->
            BookFileMetadata(title = pdf.title, author = pdf.author, coverBytes = pdf.coverBytes)
        }
    } catch (_: PdfPasswordProtectedException) {
        BookFileMetadata(title = titleFromFileName(displayName), author = null, coverBytes = null)
    }
    else -> EpubParser.parse(file).let { epub ->
        BookFileMetadata(
            title = epub.title,
            author = epub.author,
            series = epub.series,
            seriesNumber = epub.seriesNumber,
            description = epub.description,
            tags = epub.tags,
            coverBytes = epub.coverBytes,
        )
    }
}

/** "The_Art-of Reading.pdf" -> "The Art-of Reading". */
internal fun titleFromFileName(displayName: String): String =
    displayName.substringBeforeLast('.')
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifEmpty { "Untitled" }
