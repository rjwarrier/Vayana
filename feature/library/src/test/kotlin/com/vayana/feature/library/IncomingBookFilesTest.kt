package com.vayana.feature.library

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IncomingBookFilesTest {
    private val book = Uri.parse("content://downloads/all_downloads/7")
    private val other = Uri.parse("content://mail/attachments/9")

    @Test
    fun openWithCarriesTheViewedFile() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(book, "application/epub+zip")

        assertEquals(listOf(book), intent.incomingBookUris())
    }

    @Test
    fun shareCarriesTheStream() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, book)

        assertEquals(listOf(book), intent.incomingBookUris())
    }

    @Test
    fun shareMultipleCarriesEveryStream() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(book, other))

        assertEquals(listOf(book, other), intent.incomingBookUris())
    }

    @Test
    fun shareFallsBackToTheClip() {
        val intent = Intent(Intent.ACTION_SEND).apply { clipData = ClipData.newRawUri("book", book) }

        assertEquals(listOf(book), intent.incomingBookUris())
    }

    @Test
    fun fileUrisAndOtherActionsAreIgnored() {
        val fileUri = Uri.parse("file:///sdcard/book.epub")

        assertEquals(emptyList(), Intent(Intent.ACTION_VIEW).setData(fileUri).incomingBookUris())
        assertEquals(emptyList(), Intent(Intent.ACTION_MAIN).setData(book).incomingBookUris())
    }

    @Test
    fun pendingFilesAreHandedOverOnceAndWithoutRepeats() {
        val incoming = IncomingBookFiles()

        incoming.offer(listOf(book))
        incoming.offer(listOf(book, other))

        assertEquals(listOf(book, other), incoming.pending.value)
        assertEquals(listOf(book, other), incoming.drain())
        assertEquals(emptyList(), incoming.drain())
    }
}
