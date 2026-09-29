package com.vayana.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.vayana.app.MainActivity
import com.vayana.app.R
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.filesystem.StorageRoots
import com.vayana.feature.reader.ReadAloudStatus
import com.vayana.feature.reader.ReadAloudStatusHolder
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        updater.start()
        val pending = goAsync()
        updater.refresh { pending.finish() }
    }

    override fun onEnabled(context: Context) = updater.start()

    override fun onDisabled(context: Context) = updater.stop()
}

/** What the widget draws: the book, its read-aloud if that's running, and whether read-aloud is on in Settings. */
private data class WidgetState(
    val book: WidgetBook?,
    val readAloud: ReadAloudStatus?,
    val audioEnabled: Boolean,
    val appearance: WidgetAppearance,
)

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
    private val settingsRepository: SettingsRepository,
    private val readAloudStatusHolder: ReadAloudStatusHolder,
    @ApplicationScope private val scope: CoroutineScope,
) : VayanaWidget {
    override val provider = ContinueReadingWidget::class.java

    // The last cover drawn: play/pause redraws the widget, and needn't decode the same cover again.
    private var cover: Pair<String, Bitmap?>? = null
    private var observer: Job? = null

    private val state: Flow<WidgetState> = combine(
        bookRepository.observeContinueReading().map { it?.toWidgetBook() },
        readAloudStatusHolder.status,
        settingsRepository.snapshot.map { it.readerAudioFeaturesEnabled to WidgetAppearance(it.widgetCornerRadius, it.widgetProgressStyle) },
    ) { book, readAloud, (audioEnabled, appearance) ->
        WidgetState(book, readAloud?.takeIf { it.bookId == book?.id }, audioEnabled, appearance)
    }
        .distinctUntilChanged()

    @Synchronized
    fun start() {
        if (observer?.isActive == true || !hasWidgets()) return
        observer = scope.launch { state.collect { render(it) } }
    }

    @Synchronized
    fun stop() {
        observer?.cancel()
        observer = null
        cover = null
    }

    fun refresh(onDone: () -> Unit) {
        scope.launch {
            try {
                render(state.first())
            } finally {
                onDone()
            }
        }
    }

    override suspend fun preview(appearance: WidgetAppearance): RemoteViews = withContext(dispatchers.io) {
        val state = state.first().copy(appearance = appearance)
        views(state, coverFor(state.book), narrow = false, short = false)
    }

    private fun coverFor(book: WidgetBook?): Bitmap? = book?.coverPath?.let { path ->
        cover?.takeIf { it.first == path }?.second ?: loadCover(path).also { cover = path to it }
    }

    private suspend fun render(state: WidgetState) = withContext(dispatchers.io) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ContinueReadingWidget::class.java))
        if (ids.isEmpty()) return@withContext
        val cover = coverFor(state.book)
        // From Android 12 the launcher picks the layout for the widget's size: narrow ones drop the resume button,
        // short ones the progress pill and the title's second line.
        val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(
                mapOf(
                    SizeF(NarrowWidth, ShortHeight) to views(state, cover, narrow = true, short = true),
                    SizeF(WideWidth, ShortHeight) to views(state, cover, narrow = false, short = true),
                    SizeF(NarrowWidth, TallHeight) to views(state, cover, narrow = true, short = false),
                    SizeF(WideWidth, TallHeight) to views(state, cover, narrow = false, short = false),
                ),
            )
        } else {
            views(state, cover, narrow = false, short = false)
        }
        manager.updateAppWidget(ids, views)
    }

    private fun hasWidgets(): Boolean = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, ContinueReadingWidget::class.java))
        .isNotEmpty()

    private fun views(state: WidgetState, cover: Bitmap?, narrow: Boolean, short: Boolean): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_continue_reading).apply {
            state.appearance.applyTo(this)
            val book = state.book
            val hasBook = book != null
            setViewVisibility(R.id.widget_book, if (hasBook) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_empty, if (hasBook) View.GONE else View.VISIBLE)
            // The root is the launcher's @android:id/background, so opening the app animates from the widget.
            setOnClickPendingIntent(android.R.id.background, openIntent(book?.id))
            if (book == null) return@apply
            setViewVisibility(R.id.widget_resume, if (narrow || !state.audioEnabled) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.widget_progress_text, if (short) View.GONE else View.VISIBLE)
            setInt(R.id.widget_title, "setMaxLines", if (short) 1 else 2)
            // A short widget (Android 12+ only) gets a smaller cover, still 2:3, rather than a squashed one.
            if (short && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setViewLayoutWidth(R.id.widget_cover, ShortCoverWidthDp, TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.widget_cover, ShortCoverWidthDp * CoverAspect, TypedValue.COMPLEX_UNIT_DIP)
            }
            // Play/pause read-aloud: pauses or resumes it in place while it runs; otherwise opens the book and starts it.
            val speaking = state.readAloud?.playing == true
            setImageViewResource(R.id.widget_resume, if (speaking) R.drawable.ic_widget_pause else R.drawable.ic_widget_resume)
            setContentDescription(
                R.id.widget_resume,
                context.getString(if (speaking) Res.string.widget_pause_read_aloud else Res.string.widget_read_aloud),
            )
            setOnClickPendingIntent(
                R.id.widget_resume,
                if (state.readAloud != null) {
                    ReadAloudStatusHolder.playPauseIntent(context, play = !speaking)
                } else {
                    openIntent(book.id, readAloud = true)
                },
            )
            setTextViewText(R.id.widget_title, book.title)
            setTextViewText(R.id.widget_author, book.author.orEmpty())
            setViewVisibility(R.id.widget_author, if (book.author.isNullOrBlank()) View.GONE else View.VISIBLE)
            setTextViewText(R.id.widget_progress_text, context.getString(Res.string.library_progress_value, book.percent))
            state.appearance.applyProgress(this, R.id.widget_progress, R.id.widget_progress_wavy, PercentScale, book.percent)
            if (cover != null) {
                setImageViewBitmap(R.id.widget_cover, cover)
                setViewPadding(R.id.widget_cover, 0, 0, 0, 0)
            } else {
                // No cover: the book glyph, small, on the card's tonal colour.
                setImageViewResource(R.id.widget_cover, R.drawable.ic_widget_book)
                val inset = context.resources.getDimensionPixelSize(R.dimen.widget_placeholder_inset)
                setViewPadding(R.id.widget_cover, inset, inset, inset, inset)
            }
        }

    /** Opens the book in the reader (and, with [readAloud], starts reading it aloud); with no book yet, just the app. */
    private fun openIntent(bookId: Long?, readAloud: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ActionOpenBook)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(ExtraReadAloud, readAloud)
        bookId?.let { intent.putExtra(ExtraBookId, it) }
        // Its own request code: PendingIntents differing only in extras would otherwise overwrite each other.
        return PendingIntent.getActivity(
            context,
            if (readAloud) ReadAloudRequestCode else OpenRequestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
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
        const val ExtraReadAloud = "com.vayana.app.extra.READ_ALOUD"
        private const val OpenRequestCode = 0
        private const val ReadAloudRequestCode = 1
        private const val CoverHeightPx = 320
        private const val NarrowWidth = 180f
        private const val WideWidth = 270f
        private const val ShortHeight = 100f
        private const val TallHeight = 130f
        private const val ShortCoverWidthDp = 54f
        private const val CoverAspect = 1.5f
    }
}

private const val PercentScale = 100
