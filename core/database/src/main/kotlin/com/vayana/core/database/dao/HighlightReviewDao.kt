package com.vayana.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vayana.core.database.entity.HighlightReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightReviewDao {
    @Query("SELECT * FROM highlight_reviews")
    fun observeAll(): Flow<List<HighlightReviewEntity>>

    @Query("SELECT * FROM highlight_reviews WHERE annotationSyncId = :annotationSyncId")
    suspend fun getByAnnotationSyncId(annotationSyncId: String): HighlightReviewEntity?

    @Upsert
    suspend fun upsert(review: HighlightReviewEntity)

    /** Schedules of highlights that no longer exist at all (a purged book), not merely soft-deleted ones. */
    @Query("DELETE FROM highlight_reviews WHERE annotationSyncId NOT IN (SELECT syncId FROM annotations)")
    suspend fun deleteOrphans(): Int
}
