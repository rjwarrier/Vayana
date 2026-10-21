package com.vayana.feature.reader

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.core.database.repository.AnnotationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A scanned book read in its text view (an EPUB built from the recognised text) has marks of its own: the text's
 * locations aren't the scan's. They are stored with [TextViewLocatorPrefix] in front of the locator, and this keeps
 * each view to its own: the reader sees, and writes, only the marks of the view on screen, with locators as its
 * engine knows them. Notes with no locator (about the book as a whole) belong to both.
 */
internal class ViewAnnotations(
    private val stored: AnnotationRepository,
    private val textView: () -> Boolean,
) : AnnotationRepository by stored {

    override fun observeForBook(bookId: Long): Flow<List<Annotation>> =
        stored.observeForBook(bookId).map { annotations -> annotations.mapNotNull { it.inView() } }

    override suspend fun create(
        bookId: Long,
        type: AnnotationType,
        colorKey: String,
        locator: String,
        chapterTitle: String?,
        chapterHref: String?,
        selectedText: String,
        readerNote: String?,
    ): Annotation = stored.create(bookId, type, colorKey, toStored(locator), chapterTitle, chapterHref, selectedText, readerNote)
        .let { it.inView() ?: it }

    override suspend fun update(annotation: Annotation) = stored.update(annotation.copy(locator = toStored(annotation.locator)))

    private fun Annotation.inView(): Annotation? = when {
        locator.isBlank() -> this
        locator.startsWith(TextViewLocatorPrefix) -> if (textView()) copy(locator = locator.removePrefix(TextViewLocatorPrefix)) else null
        else -> if (textView()) null else this
    }

    private fun toStored(locator: String) =
        if (textView() && locator.isNotBlank() && !locator.startsWith(TextViewLocatorPrefix)) TextViewLocatorPrefix + locator else locator
}

/** Marks a locator in a scanned book's text view; see [ViewAnnotations]. */
internal const val TextViewLocatorPrefix = "scan-text:"
