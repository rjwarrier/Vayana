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
    /** The app build that published this snapshot (the device is [deviceLabel]); null in older snapshots. */
    val appBuild: PortableAppBuild? = null,
    /**
     * Slices left out of this export because nothing in them changed since this device published them: they hold
     * no records here, get no document, and the publish keeps pointing at their published files.
     */
    val unchangedSliceKeys: Set<String> = emptySet(),
)

data class PortableAppBuild(
    val versionName: String,
    val versionCode: Long,
    /** Version code + build date and time, e.g. 3.20261013.2245. */
    val buildNumber: String,
    val builtAt: Long,
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
    val gutenbergId: Long? = null,
    val readNextPinned: Boolean = false,
    val readingDisposition: String = "ACTIVE",
    val dispositionReason: String? = null,
    val dispositionUpdatedAt: Long? = null,
    /** Home Library mirror provenance: Home Library owns these books' catalog fields (see BookSource). */
    val source: String? = null,
    val syncUuid: String? = null,
    val sourceUpdatedAt: Long? = null,
    val sourceHasCover: Boolean = false,
    val sourceMetadata: String? = null,
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
    val reviewQuestion: String? = null,
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
    val startPage: Int? = null,
    val endPage: Int? = null,
    val activeIntervals: String? = null,
    val bookFileHash: String? = null,
    /** Sync name of the device that recorded it; absent for sessions from before devices were recorded. */
    val deviceName: String? = null,
    /** Seconds of [durationSeconds] spent listening to read aloud; absent (0) in older snapshots. */
    val listenedSeconds: Long = 0L,
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
