package com.vayana.core.homelibrary

import androidx.room.withTransaction
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.BookSource
import com.vayana.core.database.model.HomeLibraryDetails
import com.vayana.core.database.model.PhysicalBookOwnership
import com.vayana.core.database.model.normalizedBookTagsCsv
import javax.inject.Inject
import javax.inject.Singleton

/** What one sync run changes locally, applied as a single unit. */
data class HomeLibraryChanges(
    /** Live rows to create, or update when their [HomeBookRow.syncUuid] is already mirrored. */
    val upserts: List<HomeBookRow>,
    /** Mirrored books Home Library deleted (tombstones). */
    val deletes: Set<String>,
    /** On a full resync: every other mirrored book is stale and goes. Null on an incremental sync. */
    val retainOnly: Set<String>?,
)

data class HomeLibraryApplied(val created: Int, val updated: Int, val deleted: Int)

/** Vayana's side of the mirror: the local books that came from Home Library. */
interface HomeLibraryStore {
    /** Applies [changes] atomically: either every create, update and delete lands, or none does. */
    suspend fun apply(changes: HomeLibraryChanges): HomeLibraryApplied
}

@Singleton
class RoomHomeLibraryStore @Inject constructor(
    private val database: VayanaDatabase,
    private val bookDao: BookDao,
) : HomeLibraryStore {

    override suspend fun apply(changes: HomeLibraryChanges): HomeLibraryApplied = database.withTransaction {
        // A full resync compares against the whole mirror; an incremental one only against the books it mentions.
        val mirrored = if (changes.retainOnly != null) {
            bookDao.getHomeLibraryBooks()
        } else {
            (changes.upserts.map { it.syncUuid } + changes.deletes).distinct()
                .chunked(QueryChunk)
                .flatMap { bookDao.getHomeLibraryBooksByUuids(it) }
        }.associateBy { it.syncUuid }
        var created = 0
        var updated = 0
        changes.upserts.forEach { row ->
            val existing = mirrored[row.syncUuid]
            if (existing == null) {
                bookDao.insert(row.toNewEntity())
                created++
            } else {
                // Home Library bumps updated_at on every change, so an identical stamp means nothing to rewrite.
                val merged = if (existing.sourceUpdatedAt == row.updatedAt && existing.sourceHasCover == row.hasCover) {
                    existing
                } else {
                    existing.mergedWith(row)
                }
                if (merged != existing) {
                    bookDao.update(merged)
                    updated++
                }
            }
        }
        val upserted = changes.upserts.mapTo(HashSet()) { it.syncUuid }
        val staleIds = mirrored.values
            .filter { book ->
                val uuid = book.syncUuid ?: return@filter false
                // A tombstone ahead of a later re-creation in the same batch must not delete the new row.
                (uuid in changes.deletes && uuid !in upserted) ||
                    (changes.retainOnly != null && uuid !in changes.retainOnly)
            }
            .map { it.id }
        staleIds.chunked(DeleteChunk).forEach { bookDao.deleteHomeLibraryBooks(it) }
        HomeLibraryApplied(created = created, updated = updated, deleted = staleIds.size)
    }

    private companion object {
        /** Stays well under SQLite's bound-variable limit. */
        const val DeleteChunk = 500
        const val QueryChunk = 500
    }
}

/** Home Library's format codes aren't fixed here, so match loosely; an unknown code is a paper book. */
internal fun homeLibraryFormat(formatCode: String?): BookFormat {
    val code = formatCode?.lowercase().orEmpty()
    return when {
        "audio" in code -> BookFormat.AUDIOBOOK
        listOf("ebook", "e-book", "epub", "kindle", "digital", "pdf", "mobi").any { it in code } -> BookFormat.OTHER_EBOOK
        else -> BookFormat.PHYSICAL
    }
}

/** A name for a row with no title, in nothing a reader would need translated. */
private fun HomeBookRow.displayTitle(): String =
    title ?: originalScriptTitle ?: isbn13 ?: isbn10 ?: syncUuid.take(8)

private fun HomeBookRow.displayAuthors(): String? =
    authors?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(", ")?.takeIf { it.isNotEmpty() }

private fun HomeBookRow.details(): HomeLibraryDetails = HomeLibraryDetails(
    subtitle = subtitle,
    originalScriptTitle = originalScriptTitle,
    languageCode = languageCode,
    isbn13 = isbn13,
    isbn10 = isbn10,
    publisher = publisher,
    publishedYear = publishedYear,
    formatCode = formatCode,
    mainGenre = mainGenre,
    subGenres = subGenres,
    readStatusCode = readStatusCode,
    room = room,
    bookcase = bookcase,
    shelf = shelf,
    positionNote = positionNote,
)

private fun HomeBookRow.tagsCsv(): String? =
    (listOfNotNull(mainGenre) + subGenres + tags).joinToString(",").normalizedBookTagsCsv()

private fun fileHashFor(format: BookFormat, syncUuid: String) = "${format.name.lowercase()}:$syncUuid"

private fun HomeBookRow.toNewEntity(): BookEntity {
    val format = homeLibraryFormat(formatCode)
    val now = System.currentTimeMillis()
    return BookEntity(
        syncId = "homelibrary-$syncUuid",
        title = displayTitle(),
        author = displayAuthors(),
        series = seriesName,
        seriesNumber = null,
        description = null,
        tagsCsv = tagsCsv(),
        coverPath = null,
        filePath = "",
        fileAvailability = BookFileAvailability.LOCAL.name,
        format = format.name,
        fileHash = fileHashFor(format, syncUuid),
        lastLocator = null,
        readingPercent = 0f,
        rating = (rating ?: 0f).coerceIn(0f, 5f),
        groupId = null,
        isDeleted = false,
        wordCount = null,
        pageEstimate = pageCount?.takeIf { it > 0 },
        createdAt = now,
        updatedAt = updatedAt,
        lastReadAt = null,
        originalPublicationYear = publishedYear,
        physicalOwnership = PhysicalBookOwnership.OWNED.name.takeIf { format == BookFormat.PHYSICAL },
        syncUuid = syncUuid,
        source = BookSource.HOME_LIBRARY,
        sourceUpdatedAt = updatedAt,
        sourceHasCover = hasCover,
        sourceMetadata = details().toJson(),
    )
}

/**
 * Takes Home Library's catalog fields and keeps everything Vayana tracks itself (reading dates, progress, loan
 * details, the cover file). The cover is fetched separately once the row is committed.
 */
private fun BookEntity.mergedWith(row: HomeBookRow): BookEntity {
    val format = homeLibraryFormat(row.formatCode)
    return copy(
        title = row.displayTitle(),
        author = row.displayAuthors(),
        series = row.seriesName,
        tagsCsv = row.tagsCsv(),
        format = format.name,
        fileHash = fileHashFor(format, row.syncUuid),
        rating = (row.rating ?: 0f).coerceIn(0f, 5f),
        pageEstimate = row.pageCount?.takeIf { it > 0 },
        originalPublicationYear = row.publishedYear,
        physicalOwnership = if (format == BookFormat.PHYSICAL) physicalOwnership ?: PhysicalBookOwnership.OWNED.name else null,
        borrowReturnAt = borrowReturnAt.takeIf { format == BookFormat.PHYSICAL },
        updatedAt = row.updatedAt,
        sourceUpdatedAt = row.updatedAt,
        sourceHasCover = row.hasCover,
        sourceMetadata = row.details().toJson(),
    )
}
