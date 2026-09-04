package com.vayana.core.database.repository

import com.vayana.core.database.dao.AnnotationDao
import com.vayana.core.database.entity.AnnotationEntity
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AnnotationRepositoryImpl @Inject constructor(
    private val annotationDao: AnnotationDao,
) : AnnotationRepository {

    override fun observeAll(): Flow<List<Annotation>> =
        annotationDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeForBook(bookId: Long): Flow<List<Annotation>> =
        annotationDao.observeForBook(bookId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: Long): Annotation? = annotationDao.getById(id)?.toDomain()

    override suspend fun create(
        bookId: Long,
        type: AnnotationType,
        colorKey: String,
        locator: String,
        chapterTitle: String?,
        chapterHref: String?,
        selectedText: String,
        readerNote: String?,
    ): Annotation {
        val now = System.currentTimeMillis()
        val entity = AnnotationEntity(
            bookId = bookId,
            type = type.name,
            colorKey = colorKey,
            locator = locator,
            chapterTitle = chapterTitle,
            chapterHref = chapterHref,
            selectedText = selectedText,
            readerNote = readerNote,
            createdAt = now,
            updatedAt = now,
        )
        val id = annotationDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun createAll(items: List<Annotation>): List<Annotation> {
        if (items.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val entities = items.map { item ->
            item.toEntity(updatedAt = now).copy(
                createdAt = if (item.createdAt > 0L) item.createdAt else now,
                updatedAt = now,
            )
        }
        val ids = annotationDao.insertAll(entities)
        return entities.zip(ids) { entity, id -> entity.copy(id = id).toDomain() }
    }

    override suspend fun update(annotation: Annotation) {
        annotationDao.update(annotation.toEntity(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun softDelete(id: Long) {
        annotationDao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun restore(id: Long) {
        annotationDao.restore(id, System.currentTimeMillis())
    }

    override suspend fun purge(id: Long) {
        val annotation = annotationDao.getById(id) ?: return
        annotationDao.delete(annotation)
    }
}

private fun AnnotationEntity.toDomain(): Annotation = Annotation(
    id = id,
    bookId = bookId,
    type = AnnotationType.valueOf(type),
    colorKey = colorKey,
    locator = locator,
    chapterTitle = chapterTitle,
    chapterHref = chapterHref,
    selectedText = selectedText,
    readerNote = readerNote,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)

private fun Annotation.toEntity(updatedAt: Long): AnnotationEntity = AnnotationEntity(
    id = id,
    bookId = bookId,
    type = type.name,
    colorKey = colorKey,
    locator = locator,
    chapterTitle = chapterTitle,
    chapterHref = chapterHref,
    selectedText = selectedText,
    readerNote = readerNote,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)
