package com.vayana.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
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

/** What the widget draws: the book, and its read-aloud while the app is reading it aloud. */
private data class WidgetState(
    val book: WidgetBook?,
    val readAloud: ReadAloudStatus?,
    val appearance: WidgetAppearance,
    val colors: WidgetTheme,
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
        settingsRepository.snapshot.map {
            WidgetAppearance(it.widgetCornerRadius, it.widgetProgressStyle, border = it.widgetBorder) to it.widgetTheme()
        },
    ) { book, readAloud, (appearance, colors) ->
        WidgetState(book, readAloud?.takeIf { it.bookId == book?.id }, appearance, colors)
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
        views(state, coverFor(state.book), ContinueReadingLayout.ROW, state.colors.palette(context))
    }

    private fun coverFor(book: WidgetBook?): Bitmap? = book?.coverPath?.let { path ->
        cover?.takeIf { it.first == path }?.second ?: loadCover(path).also { cover = path to it }
    }

    private suspend fun render(state: WidgetState) = withContext(dispatchers.io) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ContinueReadingWidget::class.java))
        if (ids.isEmpty()) return@withContext
        val cover = coverFor(state.book)
        val palette = state.colors.palette(context)
        // From Android 12 the launcher picks, for the widget's current size, the largest of these that fits.
        val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(ContinueReadingLayout.bySize.mapValues { (_, layout) -> views(state, cover, layout, palette) })
        } else {
            views(state, cover, ContinueReadingLayout.ROW, palette)
        }
        manager.updateAppWidget(ids, views)
    }

    private fun hasWidgets(): Boolean = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, ContinueReadingWidget::class.java))
        .isNotEmpty()

    private fun views(state: WidgetState, cover: Bitmap?, layout: ContinueReadingLayout, palette: WidgetPalette): RemoteViews =
        RemoteViews(context.packageName, layout.res).apply {
            // The reading-time widget's colours, from the app's theme, so the two widgets match.
            applySurface(R.id.widget_bg, R.id.widget_border, state.appearance, palette)
            applyColors(palette)
            val book = state.book
            val hasBook = book != null
            setViewVisibility(R.id.widget_book, if (hasBook) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_empty, if (hasBook) View.GONE else View.VISIBLE)
            // The root is the launcher's @android:id/background, so opening the app animates from the widget.
            setOnClickPendingIntent(android.R.id.background, openIntent(book?.id))
            if (book == null) return@apply
            // Each size's layout says what it has room for; a view a layout leaves out is never touched.
            // The play/pause button only while the app is reading this book aloud (playing or paused), so a pause
            // from the widget can be resumed from it too.
            if (layout.button) {
                setViewVisibility(R.id.widget_resume, if (state.readAloud != null) View.VISIBLE else View.GONE)
            }
            setViewVisibility(R.id.widget_progress_text, if (layout.pill) View.VISIBLE else View.GONE)
            if (layout.titleLines > 0) setInt(R.id.widget_title, "setMaxLines", layout.titleLines)
            // Sized covers (Android 12+ only) stay 2:3 rather than squashing to the row's height.
            // A plain if, not takeIf: lint only recognises the version check in this form.
            val coverWidth = layout.coverWidthDp
            if (coverWidth != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setViewLayoutWidth(R.id.widget_cover, coverWidth, TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.widget_cover, coverWidth * CoverAspect, TypedValue.COMPLEX_UNIT_DIP)
            }
            // A large row's cover takes the widget's full height, as wide as the cover's own shape makes it.
            if (layout.coverFillsHeight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setViewLayoutHeight(R.id.widget_cover, ViewGroup.LayoutParams.MATCH_PARENT.toFloat(), TypedValue.COMPLEX_UNIT_PX)
                setViewLayoutWidth(R.id.widget_cover, ViewGroup.LayoutParams.WRAP_CONTENT.toFloat(), TypedValue.COMPLEX_UNIT_PX)
                setBoolean(R.id.widget_cover, "setAdjustViewBounds", true)
            }
            // The cover-only size names the book for TalkBack, since no title shows.
            if (layout.titleLines == 0) setContentDescription(R.id.widget_cover, book.title)
            // Play/pause read-aloud in place.
            val readAloud = state.readAloud
            if (layout.button && readAloud != null) {
                val speaking = readAloud.playing
                setImageViewResource(R.id.widget_resume, if (speaking) R.drawable.ic_widget_pause else R.drawable.ic_widget_resume)
                setContentDescription(
                    R.id.widget_resume,
                    context.getString(if (speaking) Res.string.widget_pause_read_aloud else Res.string.widget_read_aloud),
                )
                setOnClickPendingIntent(R.id.widget_resume, ReadAloudStatusHolder.playPauseIntent(context, play = !speaking))
            }
            if (layout.titleLines > 0) {
                setTextViewText(R.id.widget_title, book.title)
                setTextViewText(R.id.widget_author, book.author.orEmpty())
                setViewVisibility(R.id.widget_author, if (book.author.isNullOrBlank()) View.GONE else View.VISIBLE)
            }
            if (layout.pill) {
                setTextViewText(R.id.widget_progress_text, context.getString(Res.string.library_progress_value, book.percent))
            }
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

    private fun RemoteViews.applyColors(palette: WidgetPalette) {
        setTextColor(R.id.widget_title, palette.onSurface)
        setTextColor(R.id.widget_author, palette.onSurfaceVariant)
        setTextColor(R.id.widget_progress_text, palette.onPrimaryContainer)
        setTextColor(R.id.widget_empty_text, palette.onSurfaceVariant)
        tintBackground(R.id.widget_progress_text, palette.primaryContainer)
        tintBackground(R.id.widget_cover, palette.secondaryContainer)
        tintBackground(R.id.widget_resume, palette.primary)
        tintBackground(R.id.widget_empty_icon, palette.primaryContainer)
        // The cover-only layout's empty state is the icon itself.
        tintBackground(R.id.widget_empty, palette.primaryContainer)
        setInt(R.id.widget_resume, "setColorFilter", palette.onPrimary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (bar in intArrayOf(R.id.widget_progress, R.id.widget_progress_wavy)) {
                setColorStateList(bar, "setProgressTintList", ColorStateList.valueOf(palette.primary))
                setColorStateList(bar, "setProgressBackgroundTintList", ColorStateList.valueOf(palette.secondaryContainer))
            }
        }
    }

    /** Opens the book in the reader; with no book yet, just the app. */
    private fun openIntent(bookId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ActionOpenBook)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        bookId?.let { intent.putExtra(ExtraBookId, it) }
        return PendingIntent.getActivity(
            context,
            OpenRequestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * The cover at the height the largest layout shows it (a stacked or large-row cover, up to about 300dp at 2x), and
     * no larger: RemoteViews bitmaps travel through a size-limited parcel.
     */
    private fun loadCover(relativePath: String): Bitmap? = loadCoverBitmap(storageRoots, relativePath, CoverHeightPx, CoverOversize)

    companion object {
        const val ActionOpenBook = "com.vayana.app.OPEN_BOOK"
        const val ExtraBookId = "com.vayana.app.extra.BOOK_ID"
        private const val OpenRequestCode = 0
        private const val CoverHeightPx = 640
        private const val CoverOversize = 1.25f
        private const val CoverAspect = 1.5f
    }
}

private const val PercentScale = 100

/**
 * The widget's layout for each size it can take. The launcher (Android 12+) shows the largest size in [bySize] that
 * fits the widget: a small square shows only the cover, narrow or squarish widgets stack the cover over the text, wide
 * ones lay it beside the text, and a large one does so with a bigger cover and longer title.
 */
internal enum class ContinueReadingLayout(
    @LayoutRes val res: Int,
    /** Room for the read-aloud play/pause button, shown while the app reads aloud. */
    val button: Boolean,
    /** The percent pill above the title. */
    val pill: Boolean,
    /** 0 for a layout with no title. */
    val titleLines: Int,
    /** A cover width other than the layout's own, in dp. */
    val coverWidthDp: Float? = null,
    /** The cover fills the widget's height instead. */
    val coverFillsHeight: Boolean = false,
) {
    COVER(R.layout.widget_continue_reading_cover, button = false, pill = false, titleLines = 0),
    TALL(R.layout.widget_continue_reading_tall, button = true, pill = false, titleLines = 2),
    ROW_COMPACT(R.layout.widget_continue_reading, button = false, pill = false, titleLines = 1, coverWidthDp = 54f),
    ROW_SHORT(R.layout.widget_continue_reading, button = true, pill = false, titleLines = 1, coverWidthDp = 54f),
    ROW_NARROW(R.layout.widget_continue_reading, button = false, pill = true, titleLines = 2),
    ROW(R.layout.widget_continue_reading, button = true, pill = true, titleLines = 2),
    LARGE(R.layout.widget_continue_reading, button = true, pill = true, titleLines = 3, coverFillsHeight = true),
    ;

    companion object {
        /** Smallest size, in dp, at which each layout is used. */
        val bySize: Map<SizeF, ContinueReadingLayout> = linkedMapOf(
            SizeF(100f, 100f) to COVER,
            SizeF(100f, 190f) to TALL,
            SizeF(175f, 190f) to TALL,
            SizeF(175f, 95f) to ROW_COMPACT,
            SizeF(250f, 95f) to ROW_SHORT,
            SizeF(175f, 125f) to ROW_NARROW,
            SizeF(250f, 125f) to ROW,
            SizeF(250f, 190f) to LARGE,
            // Big and squarish: a wide row would float in empty space, so the cover heads a stack again.
            SizeF(250f, 300f) to TALL,
        )
    }
}
