package com.vayana.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import com.vayana.app.MainActivity
import com.vayana.app.R
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.filesystem.StorageRoots
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vayana.core.resources.R as Res

/** The home-screen widget: the book being read, its progress, and one tap back into it. */
@AndroidEntryPoint
class ContinueReadingWidget : AppWidgetProvider() {
    @Inject lateinit var updater: ContinueReadingWidgetUpdater

    // Added, resized or restored after a reboot: the app may not be running, so draw from the database now.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        updater.refresh { pending.finish() }
    }
}

/** What the widget shows: the most recently read book whose file is on this device, as the library's hero card does. */
data class WidgetBook(val id: Long, val title: String, val author: String?, val coverPath: String?, val percent: Int)

internal fun Book.toWidgetBook(): WidgetBook =
    WidgetBook(id, title, author, coverPath, (readingPercent * PercentScale).roundToInt())

/**
 * Redraws the widget when the book being read changes or moves on - while the app runs, which is when reading
 * happens - and on request when the launcher asks. A widget nobody has placed costs a single id lookup.
 */
@Singleton
class ContinueReadingWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val storageRoots: StorageRoots,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            bookRepository.observeContinueReading()
                .map { it?.toWidgetBook() }
                .distinctUntilChanged()
                .collect { render(it) }
        }
    }

    fun refresh(onDone: () -> Unit) {
        scope.launch {
            try {
                render(bookRepository.observeContinueReading().first()?.toWidgetBook())
            } finally {
                onDone()
            }
        }
    }

    private suspend fun render(book: WidgetBook?) = withContext(dispatchers.io) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ContinueReadingWidget::class.java))
        if (ids.isEmpty()) return@withContext
        manager.updateAppWidget(ids, views(book))
    }

    private fun views(book: WidgetBook?): RemoteViews = RemoteViews(context.packageName, R.layout.widget_continue_reading).apply {
        val hasBook = book != null
        setViewVisibility(R.id.widget_book, if (hasBook) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_empty, if (hasBook) View.GONE else View.VISIBLE)
        setOnClickPendingIntent(R.id.widget_root, openIntent(book?.id))
        if (book == null) return@apply
        setTextViewText(R.id.widget_title, book.title)
        setTextViewText(R.id.widget_author, book.author.orEmpty())
        setViewVisibility(R.id.widget_author, if (book.author.isNullOrBlank()) View.GONE else View.VISIBLE)
        setTextViewText(R.id.widget_progress_text, context.getString(Res.string.library_progress_value, book.percent))
        setProgressBar(R.id.widget_progress, PercentScale, book.percent, false)
        val cover = book.coverPath?.let(::loadCover)
        if (cover != null) setImageViewBitmap(R.id.widget_cover, cover) else setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
    }

    /** Opens the book in the reader; with no book yet, just the app. */
    private fun openIntent(bookId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ActionOpenBook)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        bookId?.let { intent.putExtra(ExtraBookId, it) }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The cover scaled down to the widget's size: RemoteViews bitmaps travel through a size-limited parcel. */
    private fun loadCover(relativePath: String): Bitmap? = runCatching {
        val file = storageRoots.resolve(relativePath).takeIf { it.isFile } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= CoverHeightPx) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    companion object {
        const val ActionOpenBook = "com.vayana.app.OPEN_BOOK"
        const val ExtraBookId = "com.vayana.app.extra.BOOK_ID"
        private const val CoverHeightPx = 320
    }
}

private const val PercentScale = 100
