package com.vayana.core.database.repository

import com.vayana.core.database.dao.HighlightReviewDao
import com.vayana.core.database.entity.HighlightReviewEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A highlight's spaced-review schedule. A highlight never reviewed has none and counts as new. */
data class HighlightReview(
    val annotationId: Long,
    val dueAt: Long,
    val intervalDays: Int,
    val easeFactor: Float,
    val repetitions: Int,
    val lastReviewedAt: Long,
)

interface HighlightReviewRepository {
    /** Every schedule by annotation id. */
    fun observeAll(): Flow<Map<Long, HighlightReview>>

    /** Records how the highlight went and returns its new schedule. */
    suspend fun grade(annotationId: Long, grade: ReviewGrade, now: Long = System.currentTimeMillis()): HighlightReview

    /** Drops schedules whose highlight is gone for good. */
    suspend fun deleteOrphans()
}

class HighlightReviewRepositoryImpl @Inject constructor(
    private val dao: HighlightReviewDao,
) : HighlightReviewRepository {
    override fun observeAll(): Flow<Map<Long, HighlightReview>> =
        dao.observeAll().map { rows -> rows.associate { it.annotationId to it.toDomain() } }

    override suspend fun grade(annotationId: Long, grade: ReviewGrade, now: Long): HighlightReview {
        val existing = dao.getByAnnotationId(annotationId)
        val next = VocabularySchedule.next(
            repetitions = existing?.repetitions ?: 0,
            intervalDays = existing?.intervalDays ?: 0,
            easeFactor = existing?.easeFactor ?: VocabularySchedule.DefaultEase,
            grade = grade,
            now = now,
        )
        val review = HighlightReviewEntity(
            annotationId = annotationId,
            dueAt = next.dueAt,
            intervalDays = next.intervalDays,
            easeFactor = next.easeFactor,
            repetitions = next.repetitions,
            lastReviewedAt = now,
        )
        dao.upsert(review)
        return review.toDomain()
    }

    override suspend fun deleteOrphans() {
        dao.deleteOrphans()
    }
}

private fun HighlightReviewEntity.toDomain() = HighlightReview(
    annotationId = annotationId,
    dueAt = dueAt,
    intervalDays = intervalDays,
    easeFactor = easeFactor,
    repetitions = repetitions,
    lastReviewedAt = lastReviewedAt,
)
