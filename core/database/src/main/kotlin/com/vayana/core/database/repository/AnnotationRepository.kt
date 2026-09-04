package com.vayana.core.database.repository

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import kotlinx.coroutines.flow.Flow

interface AnnotationRepository {
    fun observeAll(): Flow<List<Annotation>>
    fun observeForBook(bookId: Long): Flow<List<Annotation>>
    suspend fun getById(id: Long): Annotation?
    suspend fun create(
        bookId: Long,
        type: AnnotationType,
        colorKey: String,
        locator: String,
        chapterTitle: String?,
        chapterHref: String?,
        selectedText: String,
        readerNote: String?,
    ): Annotation
    suspend fun createAll(items: List<Annotation>): List<Annotation>
    suspend fun update(annotation: Annotation)

    /** Immediately hides the annotation from every query. Durable - safe even if the app is
     * killed a moment later, unlike a timer-based delete. */
    suspend fun softDelete(id: Long)

    /** Un-hides a soft-deleted annotation (the "Undo" action after [softDelete]). */
    suspend fun restore(id: Long)

    /** Permanently removes a row. Only meaningful after [softDelete] - never call this to
     * "delete" something the user just did, or a cancelled/killed caller loses it for good. */
    suspend fun purge(id: Long)
}
