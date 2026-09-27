package com.vayana.core.backup

data class PortableSnapshot(
    val formatVersion: Int,
    val exportedAt: Long,
    val deviceLabel: String,
    val books: List<PortableBook>,
    val annotations: List<PortableAnnotation>,
    val shelves: List<PortableShelf>,
    val shelfMemberships: List<PortableShelfMembership>,
    val readingSessions: List<PortableReadingSession>,
    val vocabularyCards: List<PortableVocabularyCard>,
    val wordLookupCounters: List<PortableWordLookupCounter>,
    val bookAliases: List<PortableBookAlias> = emptyList(),
    val tombstones: List<PortableTombstone> = emptyList(),
    val settings: Map<String, String>,
    val syncConflicts: List<PortableSyncConflict> = emptyList(),
)

data class PortableBook(
    val syncId: String,
    val title: String,
    val author: String?,
    val series: String?,
    val seriesNumber: String?,
    val description: String?,
    val tagsCsv: String?,
    val format: String,
    val fileHash: String,
    val fileAvailability: String,
    val fileAvailableLocally: Boolean,
    val fileAsset: PortableAsset?,
    val coverAvailableLocally: Boolean,
    val coverAsset: PortableAsset?,
    val lastLocator: String?,
    val readingPercent: Float,
    val rating: Float,
    val groupId: Long?,
    val isDeleted: Boolean,
    val wordCount: Int?,
    val pageEstimate: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastReadAt: Long?,
    val startedReadingAt: Long?,
    val finishedReadingAt: Long?,
    val totalReadingSeconds: Long,
    val customFontSizePercent: Int?,
    val customLineHeight: Float?,
    val customFontFamily: String?,
    val customSideMarginPercent: Int?,
    val readNextAddedAt: Long?,
    val readNextUpdatedAt: Long? = null,
    val deletionUpdatedAt: Long? = null,
    val goodreadsUrl: String? = null,
    val goodreadsRating: Float? = null,
    val goodreadsRatingsCount: Int? = null,
    val originalPublicationYear: Int? = null,
    val physicalOwnership: String? = null,
    val borrowReturnAt: Long? = null,
)

data class PortableAsset(
    val id: String,
    val sha256: String,
    val sizeBytes: Long,
    val uploadedAt: Long,
)

data class PortableAnnotation(
    val syncId: String,
    val bookSyncId: String,
    val type: String,
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

data class PortableShelf(
    val syncId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class PortableShelfMembership(
    val bookSyncId: String,
    val shelfSyncId: String,
    val createdAt: Long,
)

data class PortableReadingSession(
    val syncId: String,
    val bookSyncId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
)

data class PortableVocabularyCard(
    val syncId: String,
    val word: String,
    val definition: String,
    val sentence: String?,
    val bookSyncId: String?,
    val bookTitle: String?,
    val createdAt: Long,
    val lastReviewedAt: Long?,
    val known: Boolean,
    /** Spaced-repetition schedule; absent from snapshots made before it existed, which read as a new card. */
    val dueAt: Long? = null,
    val intervalDays: Int = 0,
    val easeFactor: Float = 2.5f,
    val repetitions: Int = 0,
)

data class PortableWordLookupCounter(
    val word: String,
    val writerOrigin: String,
    val count: Int,
    val lastLookedUpAt: Long,
)

data class PortableBookAlias(
    val syncId: String,
    val fileHash: String,
    val createdAt: Long,
)

data class PortableTombstone(
    val syncId: String,
    val entityType: String,
    val deletedAt: Long,
)

data class PortableSyncConflict(
    val type: String,
    val syncId: String,
    val reason: String,
    val detectedAt: Long,
    val localDeviceLabel: String,
    val remoteDeviceLabel: String?,
    val local: PortableReadingPositionAlternative,
    val remote: PortableReadingPositionAlternative,
)

data class PortableReadingPositionAlternative(
    val fileHash: String,
    val locator: String?,
    val readingPercent: Float,
    val lastReadAt: Long?,
    val updatedAt: Long,
)
