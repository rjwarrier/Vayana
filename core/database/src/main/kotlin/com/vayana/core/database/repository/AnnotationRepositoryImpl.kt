package com.vayana.core.database.repository

import androidx.room.withTransaction
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.AnnotationDao
import com.vayana.core.database.dao.BookAliasDao
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.database.entity.AnnotationEntity
import com.vayana.core.database.entity.TombstoneEntity
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AnnotationRepositoryImpl @Inject constructor(
    private val database: VayanaDatabase,
    private val annotationDao: AnnotationDao,
    private val bookDao: BookDao,
    private val bookAliasDao: BookAliasDao,
    private val tombstoneDao: TombstoneDao,
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
        val now = System.currentTimeMillis()
        database.withTransaction {
            annotationDao.getById(id)?.let { annotation ->
                tombstoneDao.upsert(TombstoneEntity(syncId = annotation.syncId, entityType = TombstoneEntityType.ANNOTATION.value, deletedAt = now))
            }
            annotationDao.softDelete(id, now)
        }
    }

    override suspend fun restore(id: Long) {
        database.withTransaction {
            annotationDao.getById(id)?.let { annotation -> tombstoneDao.deleteBySyncId(annotation.syncId) }
            annotationDao.restore(id, System.currentTimeMillis())
        }
    }

    override suspend fun purge(id: Long) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            annotationDao.getById(id)?.let { annotation ->
                tombstoneDao.upsert(TombstoneEntity(syncId = annotation.syncId, entityType = TombstoneEntityType.ANNOTATION.value, deletedAt = now))
            }
            annotationDao.purge(id)
        }
    }

    override suspend fun mergeCloudAnnotation(record: AnnotationRecord): AnnotationMergeResult = database.withTransaction {
        val tombstone = tombstoneDao.findBySyncId(record.syncId)
        val existing = annotationDao.findBySyncId(record.syncId)
        if (tombstone != null && !record.isDeleted &&
            tombstoneDao.supersedes(tombstone, record.updatedAt, existing?.updatedAt)
        ) {
            return@withTransaction AnnotationMergeResult.NO_CHANGE
        }
        if (existing == null) {
            if (record.isDeleted) return@withTransaction AnnotationMergeResult.NO_CHANGE
            val bookId = bookDao.findActiveBySyncIdOrAlias(record.bookSyncId, bookAliasDao)?.id
                ?: return@withTransaction AnnotationMergeResult.NO_LOCAL_BOOK
            annotationDao.insert(
                AnnotationEntity(
                    syncId = record.syncId,
                    bookId = bookId,
                    type = record.type.name,
                    colorKey = record.colorKey,
                    locator = record.locator,
                    chapterTitle = record.chapterTitle,
                    chapterHref = record.chapterHref,
                    selectedText = record.selectedText,
                    readerNote = record.readerNote,
                    createdAt = record.createdAt,
                    updatedAt = record.updatedAt,
                    isDeleted = false,
                ),
            )
            return@withTransaction AnnotationMergeResult.CREATED
        }
        // Never let sync silently delete or resurrect data on its own judgement: if the two sides
        // disagree on whether this annotation is deleted, whichever state is already showing here
        // keeps winning, and the disagreement is just reported rather than acted on.
        if (existing.isDeleted != record.isDeleted) {
            return@withTransaction AnnotationMergeResult.KEPT_LOCAL_OVER_CONFLICT
        }
        if (existing.isDeleted) return@withTransaction AnnotationMergeResult.NO_CHANGE
        if (record.updatedAt <= existing.updatedAt) return@withTransaction AnnotationMergeResult.NO_CHANGE
        annotationDao.update(
            existing.copy(
                type = record.type.name,
                colorKey = record.colorKey,
                locator = record.locator,
                chapterTitle = record.chapterTitle,
                chapterHref = record.chapterHref,
                selectedText = record.selectedText,
                readerNote = record.readerNote,
                updatedAt = record.updatedAt,
            ),
        )
        AnnotationMergeResult.UPDATED
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
