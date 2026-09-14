package com.vayana.core.database.repository

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import kotlinx.coroutines.flow.Flow

/** A remote annotation ready to merge in - already validated/typed by the caller (mirrors how
 *  [CloudBookRecord] is the typed counterpart of a parsed portable book). */
data class AnnotationRecord(
    val syncId: String,
    val bookSyncId: String,
    val type: AnnotationType,
    val colorKey: String,
    val locator: String,
    val chapterTitle: String?,
    val chapterHref: String?,
    val selectedText: String,
    val readerNote: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
)

enum class AnnotationMergeResult {
    CREATED,
    UPDATED,
    /** Remote wasn't newer, or both sides already agree - nothing to do. */
    NO_CHANGE,
    /** The annotation's book hasn't been merged onto this device (yet, or ever). */
    NO_LOCAL_BOOK,
    /** One side has this annotation deleted and the other doesn't. Sync never silently deletes or
     *  resurrects data on its own judgement, so whichever state is already showing locally wins. */
    KEPT_LOCAL_OVER_CONFLICT,
}

interface AnnotationRepository {
    fun observeAll(): Flow<List<Annotation>>
    /** Annotations whose text matches every word of [text] (as word prefixes), newest first. */
    fun observeSearch(text: String, limit: Int): Flow<List<Annotation>>
    fun observeForBook(bookId: Long): Flow<List<Annotation>>
    fun observeCountForBook(bookId: Long): Flow<Int>
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

    suspend fun mergeCloudAnnotation(record: AnnotationRecord): AnnotationMergeResult
}
