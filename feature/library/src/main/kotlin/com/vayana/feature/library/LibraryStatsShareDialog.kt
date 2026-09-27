package com.vayana.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.vayana.core.common.ShareImageFormat
import com.vayana.core.common.shareBitmap
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.sharecard.ShareCardChoiceRow
import com.vayana.core.designsystem.sharecard.ShareCardOptionChip
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LibraryStatsShareDialog(books: List<Book>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot = remember(books) { libraryStatsSnapshot(books) }
    var format by remember { mutableStateOf(LibraryShareFormat.STORY) }
    var theme by remember { mutableStateOf(LibraryShareTheme.NIGHT) }
    var spineStyle by remember { mutableStateOf(LibrarySpineStyle.COLORFUL) }
    var sharing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val shareChooser = stringResource(R.string.library_stats_share_chooser)
    val material = MaterialTheme.colorScheme
    val materialPalette = remember(material) {
        LibrarySharePalette(
            background = material.surface.toArgb(),
            ink = material.onSurface.toArgb(),
            muted = material.onSurfaceVariant.toArgb(),
            accent = material.primary.toArgb(),
            track = material.surfaceContainerHighest.toArgb(),
            spines = listOf(
                material.primary,
                material.secondary,
                material.tertiary,
                material.onSurface,
                material.primaryContainer,
                material.secondaryContainer,
            ).map { it.toArgb() },
        )
    }
    val palette = remember(theme, materialPalette) { paletteFor(theme, materialPalette) }
    val fonts = remember(context) {
        LibraryShareFonts(
            serif = runCatching { ResourcesCompat.getFont(context, com.vayana.feature.library.R.font.literata_share) }.getOrNull()
                ?: Typeface.create("serif", Typeface.BOLD),
            sans = runCatching { ResourcesCompat.getFont(context, com.vayana.feature.library.R.font.manrope_share) }.getOrNull()
                ?: Typeface.create("sans-serif", Typeface.NORMAL),
        )
    }
    val date = remember { LocalDate.now() }
    val copy = LibraryStatsShareCopy(
        title = stringResource(R.string.library_stats_share_my_library),
        everySpine = stringResource(R.string.library_stats_share_every_spine),
        sampledSpines = stringResource(R.string.library_stats_share_sampled_spines),
        books = stringResource(R.string.library_stats_share_books),
        authors = stringResource(R.string.library_stats_share_authors),
        read = stringResource(R.string.library_stats_share_read),
        percentRead = stringResource(R.string.library_stats_share_percent_read, snapshot.readPercent),
        readLegend = stringResource(R.string.library_stats_share_read_legend),
        footer = stringResource(R.string.library_stats_share_footer),
        datePattern = stringResource(R.string.library_stats_share_date_pattern),
    )
    val bitmap by produceState<Bitmap?>(null, snapshot, format, palette, date, copy, spineStyle, fonts) {
        value = withContext(Dispatchers.Default) {
            renderLibraryStatsCard(context, snapshot, format, palette, date, copy, spineStyle, fonts)
        }
    }
    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }
    val preview = remember(bitmap) { bitmap?.asImageBitmap() }
    val heightLimit = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = heightLimit),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(stringResource(R.string.library_stats_share_title), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth())
                if (preview == null) {
                    CircularProgressIndicator(modifier = Modifier.padding(Spacing.xl))
                } else {
                    Image(
                        bitmap = preview,
                        contentDescription = stringResource(R.string.library_stats_share_preview_description,
                            snapshot.bookCount, snapshot.authorCount, snapshot.readCount),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.widthIn(max = if (format == LibraryShareFormat.STORY) 218.dp else 280.dp)
                            .fillMaxWidth()
                            .aspectRatio(format.designWidth.toFloat() / format.designHeight),
                    )
                }
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(stringResource(R.string.library_stats_share_format), style = MaterialTheme.typography.labelLarge)
                    ShareCardChoiceRow(
                        choices = LibraryShareFormat.entries,
                        selected = format,
                        label = { stringResource(if (it == LibraryShareFormat.STORY) R.string.library_stats_share_story else R.string.library_stats_share_square) },
                        onSelect = { format = it },
                    )
                    Text(stringResource(R.string.library_stats_share_theme), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        LibraryShareTheme.entries.forEach { choice ->
                            ShareCardOptionChip(selected = theme == choice,
                                label = stringResource(when (choice) {
                                LibraryShareTheme.NIGHT -> R.string.library_stats_share_night
                                LibraryShareTheme.PAPER -> R.string.library_stats_share_paper
                                LibraryShareTheme.MARIGOLD -> R.string.library_stats_share_marigold
                                LibraryShareTheme.MATERIAL -> R.string.library_stats_share_material
                                }), onClick = { theme = choice })
                        }
                    }
                    Text(stringResource(R.string.library_stats_share_book_style), style = MaterialTheme.typography.labelLarge)
                    ShareCardChoiceRow(
                        choices = LibrarySpineStyle.entries,
                        selected = spineStyle,
                        label = { stringResource(if (it == LibrarySpineStyle.COLORFUL)
                            R.string.library_stats_share_colorful else R.string.library_stats_share_outline) },
                        onSelect = { spineStyle = it },
                    )
                }
                if (failed) Text(stringResource(R.string.library_stats_share_failed), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_reset_all_cancel))
                    }
                    Button(
                        onClick = {
                            val image = bitmap ?: return@Button
                            sharing = true
                            failed = false
                            scope.launch {
                                try {
                                    context.shareBitmap(image, shareChooser,
                                        "vayana_library_${format.name.lowercase(Locale.ROOT)}", ShareImageFormat.PNG)
                                    onDismiss()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    failed = true
                                } finally {
                                    sharing = false
                                }
                            }
                        },
                        enabled = bitmap != null && !sharing,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.share_card_share_image)) }
                }
            }
        }
    }
}

internal data class LibrarySharePalette(
    val background: Int,
    val ink: Int,
    val muted: Int,
    val accent: Int,
    val track: Int,
    val spines: List<Int>,
)

internal data class LibraryStatsShareCopy(
    val title: String,
    val everySpine: String,
    val sampledSpines: String,
    val books: String,
    val authors: String,
    val read: String,
    val percentRead: String,
    val readLegend: String,
    val footer: String,
    /** `DateTimeFormatter` pattern for the card's date, so each language orders it its own way. */
    val datePattern: String,
)

internal data class LibraryShareFonts(val serif: Typeface, val sans: Typeface)

internal fun paletteFor(theme: LibraryShareTheme, material: LibrarySharePalette): LibrarySharePalette = when (theme) {
    LibraryShareTheme.NIGHT -> NightPalette
    LibraryShareTheme.PAPER -> PaperPalette
    LibraryShareTheme.MARIGOLD -> MarigoldPalette
    LibraryShareTheme.MATERIAL -> material
}

private val NightPalette = LibrarySharePalette(0xFF1D3B53.toInt(), 0xFFF6EFE2.toInt(), 0xFFB9C4CF.toInt(),
    0xFFE8A33D.toInt(), 0xFF4A6070.toInt(), listOf(0xFFE8A33D, 0xFFF6EFE2, 0xFFC75B4A, 0xFF6FA79A, 0xFF8E9CC9, 0xFFD9C49A).map(Long::toInt))
private val PaperPalette = LibrarySharePalette(0xFFF2E8D5.toInt(), 0xFF1C1B22.toInt(), 0xFF5E5967.toInt(),
    0xFFB8741A.toInt(), 0xFFD9CFBF.toInt(), listOf(0xFF7A2E2E, 0xFF2F5D50, 0xFF3B3A6B, 0xFF8A5A1E, 0xFF1C1B22, 0xFFC9803A).map(Long::toInt))
private val MarigoldPalette = LibrarySharePalette(0xFFE8A33D.toInt(), 0xFF1C1B22.toInt(), 0xFF4A3A1C.toInt(),
    0xFF1C1B22.toInt(), 0xFFC88D37.toInt(), listOf(0xFF1C1B22, 0xFF7A2E2E, 0xFFF6EFE2, 0xFF2F5D50, 0xFF1D3B53, 0xFFF2E8D5).map(Long::toInt))

internal fun renderLibraryStatsCard(
    context: Context,
    snapshot: LibraryStatsSnapshot,
    format: LibraryShareFormat,
    colors: LibrarySharePalette,
    date: LocalDate,
    copy: LibraryStatsShareCopy,
    spineStyle: LibrarySpineStyle,
    fonts: LibraryShareFonts,
): Bitmap {
    val bitmap = Bitmap.createBitmap(format.exportWidth, format.exportHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(colors.background)
    canvas.scale(format.exportWidth / format.designWidth.toFloat(), format.exportHeight / format.designHeight.toFloat())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val serifBold = Typeface.create(fonts.serif, Typeface.BOLD)
    val sansBold = Typeface.create(fonts.sans, Typeface.BOLD)
    fun text(value: String, x: Float, y: Float, size: Float, color: Int, typeface: Typeface, right: Boolean = false) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        paint.textSize = size
        paint.typeface = typeface
        paint.textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        canvas.drawText(value, x, y, paint)
    }
    val shortDate = date.format(DateTimeFormatter.ofPattern(copy.datePattern, Locale.getDefault()))
        .uppercase(Locale.getDefault())
    runCatching { ContextCompat.getDrawable(context, R.drawable.ic_vayana_mark) }.getOrNull()?.mutate()?.let { glyph ->
        glyph.setTint(colors.ink)
        glyph.setBounds(27, 24, 72, 69)
        glyph.draw(canvas)
    }
    text("V A Y A N A", 76f, 55f, 13f, colors.ink, sansBold)
    text(shortDate, format.designWidth - 36f, 54f, 12f, colors.muted, sansBold, right = true)
    text(copy.title, 36f,
        if (format == LibraryShareFormat.STORY) 136f else 121f, 36f, colors.ink, serifBold)

    val spines = librarySpines(snapshot, format, colors.spines.size)
    val subtitle = if (spines.representsEveryBook) copy.everySpine else copy.sampledSpines
    text(subtitle, 36f, if (format == LibraryShareFormat.STORY) 167f else 149f,
        if (format == LibraryShareFormat.STORY) 14f else 11f, colors.muted, sansBold)
    val shelfBottoms = if (format == LibraryShareFormat.STORY) listOf(289f, 399f, 509f) else listOf(254f)
    shelfBottoms.forEachIndexed { index, bottom ->
        spines.spines.filter { it.shelf == index }.forEach { spine ->
            paint.reset()
            paint.isAntiAlias = true
            paint.color = if (spineStyle == LibrarySpineStyle.COLORFUL) colors.spines[spine.colorIndex] else colors.ink
            paint.style = if (spineStyle == LibrarySpineStyle.COLORFUL) Paint.Style.FILL else Paint.Style.STROKE
            paint.strokeWidth = 2f
            val bounds = RectF(36f + spine.x, bottom - spine.height, 36f + spine.x + spine.width, bottom)
            canvas.drawRoundRect(bounds, 2f, 2f, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = colors.accent
        canvas.drawRoundRect(RectF(36f, bottom, format.designWidth - 36f, bottom + 5f), 2.5f, 2.5f, paint)
    }

    val formatter = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN"))
    val values = listOf(snapshot.bookCount, snapshot.authorCount, snapshot.readCount)
    val labels = listOf(copy.books, copy.authors, copy.read)
    val statsTop = if (format == LibraryShareFormat.STORY) 580f else 322f
    val columnWidth = (format.designWidth - 72f) / 3f
    values.forEachIndexed { index, count ->
        val x = 36f + index * columnWidth
        text(formatter.format(count), x, statsTop, if (format == LibraryShareFormat.STORY) 46f else 40f,
            colors.ink, serifBold)
        text(labels[index].uppercase(Locale.getDefault()), x, statsTop + 19f,
            11f, colors.muted, sansBold)
    }
    val barY = if (format == LibraryShareFormat.STORY) 634f else 372f
    val bar = RectF(36f, barY, format.designWidth - 36f, barY + 8f)
    paint.reset()
    paint.isAntiAlias = true
    paint.color = colors.track
    canvas.drawRoundRect(bar, 4f, 4f, paint)
    if (snapshot.readFraction > 0f) {
        paint.color = colors.accent
        canvas.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * snapshot.readFraction, bar.bottom), 4f, 4f, paint)
    }
    val progressY = barY + 27f
    text(copy.percentRead, 36f, progressY,
        12f, colors.muted, sansBold)
    text(copy.readLegend, format.designWidth - 36f, progressY,
        12f, colors.muted, sansBold, right = true)
    val footer = copy.footer
    paint.textSize = 12f
    paint.typeface = sansBold
    val footerX = (format.designWidth - paint.measureText(footer)) / 2f
    text(footer, footerX, if (format == LibraryShareFormat.STORY) 744f else 458f,
        12f, colors.muted, sansBold)
    return bitmap
}
