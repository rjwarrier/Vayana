package com.vayana.core.homelibrary

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.vayana.core.common.DispatcherProvider
import java.io.File
import java.io.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The ContentResolver side of the contract, against a fake Home Library provider, and the change observer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class HomeLibraryProviderTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private fun installHomeLibrary() {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = HomeLibraryContract.PACKAGE })
    }

    private fun source() = ContentResolverHomeLibrarySource(context, dispatchers)

    private fun provide(provider: FakeProvider = FakeProvider()): FakeProvider {
        Robolectric.buildContentProvider(FakeProvider::class.java).create(HomeLibraryContract.AUTHORITY)
        FakeProvider.delegate = provider
        return provider
    }

    @Test
    fun readsBooksAndTombstonesIgnoringUnknownColumns() = runBlocking<Unit> {
        installHomeLibrary()
        provide(FakeProvider(booksCursor = {
            MatrixCursor(
                arrayOf("sync_uuid", "deleted", "updated_at", "title", "authors", "rating", "has_cover", "sub_genres", "brand_new_column"),
            ).apply {
                addRow(arrayOf<Any?>("u1", 0, 100L, "Dune", "Frank Herbert", 4.5, 1, """["Sci-fi","Classic"]""", "ignored"))
                addRow(arrayOf<Any?>("u2", 1, 200L, null, null, null, null, null, null))
            }
        }))

        val rows = source().books(updatedSince = null, limit = 500)

        assertEquals(2, rows.size)
        val live = rows[0]
        assertEquals("Dune", live.title)
        assertEquals(4.5f, live.rating)
        assertTrue(live.hasCover)
        assertEquals(listOf("Sci-fi", "Classic"), live.subGenres)
        assertTrue(rows[1].deleted)
        assertNull(rows[1].title)
        assertEquals(200L, rows[1].updatedAt)
    }

    @Test
    fun passesUpdatedSinceAndLimitInTheUri() = runBlocking<Unit> {
        installHomeLibrary()
        val provider = provide(FakeProvider(booksCursor = { MatrixCursor(arrayOf("sync_uuid")) }))

        source().books(updatedSince = 1234L, limit = 500)

        val uri = provider.queriedUris.single()
        assertEquals("1234", uri.getQueryParameter("updated_since"))
        assertEquals("500", uri.getQueryParameter("limit"))
        assertEquals("/books", uri.path)
    }

    @Test
    fun infoRowIsReadAndAnEmptyInfoCursorIsNull() = runBlocking<Unit> {
        installHomeLibrary()
        provide(FakeProvider(infoCursor = {
            MatrixCursor(arrayOf("schema_version", "book_count", "max_updated_at", "app_version")).apply {
                addRow(arrayOf<Any?>(1, 42, 9_000L, "2.3"))
            }
        }))
        assertEquals(HomeLibraryInfo(1, 42, 9_000L, "2.3"), source().info())

        FakeProvider.delegate = FakeProvider(infoCursor = { MatrixCursor(arrayOf("schema_version")) })
        assertNull(source().info())
    }

    @Test
    fun notInstalledIsUnavailable() {
        assertFailsWith<HomeLibraryUnavailableException> { runBlocking { source().info() } }
    }

    @Test
    fun securityExceptionIsUnavailableNotACrash() {
        installHomeLibrary()
        provide(FakeProvider(failWith = SecurityException("no permission")))

        assertFailsWith<HomeLibraryUnavailableException> { runBlocking { source().books(null, 500) } }
    }

    @Test
    fun unknownProviderIllegalArgumentIsUnavailable() {
        installHomeLibrary()
        provide(FakeProvider(failWith = IllegalArgumentException("Unknown URL")))

        assertFailsWith<HomeLibraryUnavailableException> { runBlocking { source().info() } }
    }

    @Test
    fun coverStreamsAndMissingCoverIsNull() = runBlocking<Unit> {
        installHomeLibrary()
        val cover = File.createTempFile("cover", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)); deleteOnExit() }
        provide(FakeProvider(coverFile = { uuid -> cover.takeIf { uuid == "has" } }))

        assertEquals(listOf<Byte>(1, 2, 3), source().openCover("has")?.use { it.readBytes() }?.toList())
        assertNull(source().openCover("none"))
    }

    @Test
    fun changeNotificationsOnAnyBooksUriAreDebouncedIntoOneEmission() = runBlocking<Unit> {
        var emissions = 0
        val job: Job = launch(Dispatchers.Unconfined) { homeLibraryChanges(context, debounceMillis = 150).collect { emissions++ } }

        // A burst, as an import in Home Library would cause, including a descendant URI.
        repeat(5) { context.contentResolver.notifyChange(HomeLibraryContract.booksUri, null) }
        context.contentResolver.notifyChange(HomeLibraryContract.coverUri("u1"), null)
        shadowOf(Looper.getMainLooper()).idle()
        withTimeout(5_000) { while (emissions == 0) kotlinx.coroutines.delay(20) }
        kotlinx.coroutines.delay(400)

        assertEquals(1, emissions)
        job.cancel()
    }

    @Test
    fun observerIsUnregisteredWhenCollectionStops() = runBlocking<Unit> {
        var emissions = 0
        val job = launch(Dispatchers.Unconfined) { homeLibraryChanges(context, debounceMillis = 10).collect { emissions++ } }
        job.cancel()
        job.join()

        context.contentResolver.notifyChange(HomeLibraryContract.booksUri, null)
        shadowOf(Looper.getMainLooper()).idle()
        kotlinx.coroutines.delay(100)

        assertEquals(0, emissions)
    }

    /** Stands in for Home Library's provider; the static delegate lets a test swap behaviour after registration. */
    class FakeProvider(
        val booksCursor: () -> Cursor = { MatrixCursor(arrayOf("sync_uuid")) },
        val infoCursor: () -> Cursor = { MatrixCursor(arrayOf("schema_version")) },
        val coverFile: (String) -> File? = { null },
        val failWith: RuntimeException? = null,
    ) : ContentProvider() {
        val queriedUris = mutableListOf<Uri>()

        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val target = delegate ?: this
            target.failWith?.let { throw it }
            target.queriedUris += uri
            return if (uri.path == "/info") target.infoCursor() else target.booksCursor()
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val target = delegate ?: this
            val uuid = uri.pathSegments[1]
            val file = target.coverFile(uuid) ?: throw FileNotFoundException("no cover")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

        companion object {
            var delegate: FakeProvider? = null
        }
    }
}
