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
    suspend fun update(annotation: Annotation)
    suspend fun delete(id: Long)
}
