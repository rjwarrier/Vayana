package com.vayana.core.homelibrary

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import com.vayana.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** One row of `/books`: a live book, or (when [deleted]) a tombstone that carries only [syncUuid] and [updatedAt]. */
data class HomeBookRow(
    val syncUuid: String,
    val deleted: Boolean,
    val updatedAt: Long,
    val title: String? = null,
    val subtitle: String? = null,
    val originalScriptTitle: String? = null,
    /** Names separated by "; ". */
    val authors: String? = null,
    val languageCode: String? = null,
    val isbn13: String? = null,
    val isbn10: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val pageCount: Int? = null,
    val formatCode: String? = null,
    val seriesName: String? = null,
    val mainGenre: String? = null,
    val subGenres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val readStatusCode: String? = null,
    val rating: Float? = null,
    val room: String? = null,
    val bookcase: String? = null,
    val shelf: String? = null,
    val positionNote: String? = null,
    val hasCover: Boolean = false,
)

/** The `/info` row: cheap to read, so it decides whether a full query is needed. */
data class HomeLibraryInfo(
    val schemaVersion: Int,
    val bookCount: Int,
    val maxUpdatedAt: Long?,
    val appVersion: String?,
)

/** Home Library is not installed, or refused the query (`SecurityException`, unknown provider). */
class HomeLibraryUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Read-only view of Home Library's catalog. */
interface HomeLibrarySource {
    /** The `/info` row, or null when the provider returned nothing (the user turned sharing off). */
    suspend fun info(): HomeLibraryInfo?

    /** Rows with `updated_at > updatedSince` (all when null), tombstones included, oldest first. */
    suspend fun books(updatedSince: Long?, limit: Int): List<HomeBookRow>

    /** The cover image of [syncUuid], or null when it has none. The caller closes the stream. */
    suspend fun openCover(syncUuid: String): InputStream?
}

@Singleton
class ContentResolverHomeLibrarySource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : HomeLibrarySource {

    override suspend fun info(): HomeLibraryInfo? = withContext(dispatchers.io) {
        queryOrThrow(HomeLibraryContract.infoUri).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            HomeLibraryInfo(
                schemaVersion = cursor.int(HomeLibraryContract.Info.SCHEMA_VERSION) ?: 1,
                bookCount = cursor.int(HomeLibraryContract.Info.BOOK_COUNT) ?: 0,
                maxUpdatedAt = cursor.long(HomeLibraryContract.Info.MAX_UPDATED_AT),
                appVersion = cursor.text(HomeLibraryContract.Info.APP_VERSION),
            )
        }
    }

    override suspend fun books(updatedSince: Long?, limit: Int): List<HomeBookRow> = withContext(dispatchers.io) {
        queryOrThrow(HomeLibraryContract.booksUri(updatedSince, limit)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) cursor.toBookRow()?.let(::add)
            }
        }
    }

    override suspend fun openCover(syncUuid: String): InputStream? = withContext(dispatchers.io) {
        try {
            context.contentResolver.openInputStream(HomeLibraryContract.coverUri(syncUuid))
        } catch (_: FileNotFoundException) {
            null
        } catch (error: SecurityException) {
            throw HomeLibraryUnavailableException("Home Library refused the cover request", error)
        } catch (error: IllegalArgumentException) {
            throw HomeLibraryUnavailableException("Home Library catalog provider is unknown", error)
        }
    }

    private fun queryOrThrow(uri: Uri): Cursor {
        if (!isInstalled()) throw HomeLibraryUnavailableException("Home Library is not installed")
        return try {
            // A null cursor means no provider answered: the same as not connected, unlike an empty cursor.
            context.contentResolver.query(uri, null, null, null, null)
                ?: throw HomeLibraryUnavailableException("Home Library catalog provider did not answer")
        } catch (error: SecurityException) {
            throw HomeLibraryUnavailableException("Home Library refused the query", error)
        } catch (error: IllegalArgumentException) {
            throw HomeLibraryUnavailableException("Home Library catalog provider is unknown", error)
        }
    }

    private fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(HomeLibraryContract.PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

private fun Cursor.index(column: String): Int = getColumnIndex(column)

private fun Cursor.text(column: String): String? =
    index(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)?.takeIf { it.isNotBlank() }

private fun Cursor.long(column: String): Long? =
    index(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

private fun Cursor.int(column: String): Int? = long(column)?.toInt()

private fun Cursor.float(column: String): Float? =
    index(column).takeIf { it >= 0 && !isNull(it) }?.let(::getFloat)

/** Parses the columns it knows by name, so an unknown extra column (or a missing optional one) is harmless. */
internal fun Cursor.toBookRow(): HomeBookRow? {
    val uuid = text(HomeLibraryContract.Books.SYNC_UUID) ?: return null
    val books = HomeLibraryContract.Books
    return HomeBookRow(
        syncUuid = uuid,
        deleted = (long(books.DELETED) ?: 0L) != 0L,
        updatedAt = long(books.UPDATED_AT) ?: 0L,
        title = text(books.TITLE),
        subtitle = text(books.SUBTITLE),
        originalScriptTitle = text(books.ORIGINAL_SCRIPT_TITLE),
        authors = text(books.AUTHORS),
        languageCode = text(books.LANGUAGE_CODE),
        isbn13 = text(books.ISBN13),
        isbn10 = text(books.ISBN10),
        publisher = text(books.PUBLISHER),
        publishedYear = int(books.PUBLISHED_YEAR),
        pageCount = int(books.PAGE_COUNT),
        formatCode = text(books.FORMAT_CODE),
        seriesName = text(books.SERIES_NAME),
        mainGenre = text(books.MAIN_GENRE),
        subGenres = text(books.SUB_GENRES).jsonStrings(),
        tags = text(books.TAGS).jsonStrings(),
        readStatusCode = text(books.READ_STATUS_CODE),
        rating = float(books.RATING),
        room = text(books.ROOM),
        bookcase = text(books.BOOKCASE),
        shelf = text(books.SHELF),
        positionNote = text(books.POSITION_NOTE),
        hasCover = (long(books.HAS_COVER) ?: 0L) != 0L,
    )
}

/** A JSON array of strings; anything else (null, malformed) is an empty list. */
internal fun String?.jsonStrings(): List<String> {
    if (isNullOrBlank()) return emptyList()
    val array = runCatching { JSONArray(this) }.getOrNull() ?: return emptyList()
    return List(array.length()) { array.optString(it, "") }.map { it.trim() }.filter { it.isNotEmpty() }
}
