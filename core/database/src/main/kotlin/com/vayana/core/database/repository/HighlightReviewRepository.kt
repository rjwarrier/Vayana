package com.vayana.core.database.repository

import androidx.room.withTransaction
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.HighlightReviewDao
import com.vayana.core.database.entity.HighlightReviewEntity
import com.vayana.core.database.model.Annotation
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A highlight's spaced-review schedule. A highlight never reviewed has none and counts as new. */
data class HighlightReview(
    val annotationSyncId: String,
    val dueAt: Long,
    val intervalDays: Int,
    val easeFactor: Float,
    val repetitions: Int,
    val lastReviewedAt: Long,
)

data class HighlightReviewSessionData(val annotations: List<Annotation>, val reviewableCount: Int)

interface HighlightReviewRepository {
    fun observeDueCount(now: Long): Flow<Int>
    suspend fun reset(syncId: String)

    /** Every schedule by the annotation's sync id. */
    fun observeAll(): Flow<Map<String, HighlightReview>>

    /** At most [limit] due or new annotations, with the total eligible count from one database snapshot. */
    suspend fun session(now: Long, limit: Int = 10): HighlightReviewSessionData

    /** The same fixed oldest-first rotation as dailyHighlights, without materializing the whole library. */
    suspend fun practice(epochDay: Long, count: Int = 5): List<Annotation>

    fun observeDue(now: Long, limit: Int = 10): Flow<List<Annotation>>

    /** Records how the highlight went and returns its new schedule. */
    suspend fun grade(annotationSyncId: String, grade: ReviewGrade, now: Long = System.currentTimeMillis()): HighlightReview

    /** Drops schedules whose highlight is gone for good. */
    suspend fun deleteOrphans()
}

class HighlightReviewRepositoryImpl @Inject constructor(
    private val dao: HighlightReviewDao,
    private val database: VayanaDatabase,
) : HighlightReviewRepository {
    override fun observeDueCount(now: Long): Flow<Int> = dao.observeDueCount(now, ReviewWhitespace)
    override suspend fun reset(syncId: String) = dao.reset(syncId)

    override suspend fun session(now: Long, limit: Int): HighlightReviewSessionData = database.withTransaction {
        HighlightReviewSessionData(
            annotations = dao.due(now, limit, ReviewWhitespace).map { it.toDomain() },
            reviewableCount = dao.reviewableCount(ReviewWhitespace),
        )
    }

    override suspend fun practice(epochDay: Long, count: Int): List<Annotation> = database.withTransaction {
        val total = dao.reviewableCount(ReviewWhitespace)
        if (total == 0 || count <= 0) return@withTransaction emptyList()
        val size = minOf(count, total)
        val start = if (total <= count) 0 else Math.floorMod(epochDay * count, total.toLong()).toInt()
        val first = dao.practice(size, start, ReviewWhitespace)
        val wrapped = if (first.size < size) dao.practice(size - first.size, 0, ReviewWhitespace) else emptyList()
        (first + wrapped).map { it.toDomain() }
    }

    override fun observeDue(now: Long, limit: Int): Flow<List<Annotation>> =
        dao.observeDue(now, limit, ReviewWhitespace).map { rows -> rows.map { it.toDomain() } }

    override fun observeAll(): Flow<Map<String, HighlightReview>> =
        dao.observeAll().map { rows -> rows.associate { it.annotationSyncId to it.toDomain() } }

    override suspend fun grade(annotationSyncId: String, grade: ReviewGrade, now: Long): HighlightReview {
        val existing = dao.getByAnnotationSyncId(annotationSyncId)
        val next = VocabularySchedule.next(
            repetitions = existing?.repetitions ?: 0,
            intervalDays = existing?.intervalDays ?: 0,
            easeFactor = existing?.easeFactor ?: VocabularySchedule.DefaultEase,
            grade = grade,
            now = now,
        )
        val review = HighlightReviewEntity(
            annotationSyncId = annotationSyncId,
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
    annotationSyncId = annotationSyncId,
    dueAt = dueAt,
    intervalDays = intervalDays,
    easeFactor = easeFactor,
    repetitions = repetitions,
    lastReviewedAt = lastReviewedAt,
)

/** Matches Kotlin's Char.isWhitespace for the stored selected text's leading and trailing characters. */
private val ReviewWhitespace: String = buildString {
    for (code in 0..0xffff) if (code.toChar().isWhitespace()) append(code.toChar())
}
