package com.vayana.feature.library

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.vayana.core.designsystem.sharecard.BookCardStyle
import com.vayana.core.designsystem.sharecard.BookShareCardLayout
import com.vayana.core.designsystem.sharecard.MaxTitleScale
import com.vayana.core.designsystem.sharecard.MinTitleScale
import com.vayana.core.designsystem.sharecard.ShareCardChoiceRow
import com.vayana.core.designsystem.sharecard.ShareCardFontRow
import com.vayana.core.designsystem.sharecard.ShareCardOptionChip
import com.vayana.core.designsystem.sharecard.ShareCardOptionsLabel
import com.vayana.core.designsystem.sharecard.ShareCardPalettePicker
import com.vayana.core.designsystem.sharecard.ShareCardSealControls
import com.vayana.core.designsystem.sharecard.ShareCardShapeRow
import com.vayana.core.designsystem.sharecard.ShareCardSliderRow
import com.vayana.core.designsystem.sharecard.ShareCardWeightRow
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

/** The book card editor's tabs, in order; [BookShareTab] draws each. */
internal val BookShareTabs = listOf(
    R.string.share_quote_tab_text,
    R.string.share_quote_tab_layout,
    R.string.share_quote_tab_colors,
    R.string.share_quote_tab_show,
)

/** One tab of the book card editor; each change redraws the card above immediately. */
@Composable
internal fun BookShareTab(
    tab: Int,
    options: BookShareImageOptions,
    bookTitle: String,
    onBookTitleChange: (String) -> Unit,
    hasRating: Boolean,
    hasSeries: Boolean,
    hasTags: Boolean,
    hasYearlyGoal: Boolean,
    onOptionsChange: (BookShareImageOptions) -> Unit,
    /** Names the stat and date chips after what this book's card shows in those places. */
    stats: BookShareStats,
    originalScriptTitle: String? = null,
    catalogTitle: String? = null,
) {
    val style = options.style
    val restyle: (BookCardStyle) -> Unit = { onOptionsChange(options.copy(style = it)) }
    when (tab) {
        0 -> {
            OutlinedTextField(
                value = bookTitle,
                onValueChange = onBookTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.share_card_image_options_book_title)) },
                supportingText = { Text(stringResource(R.string.share_card_image_options_book_title_hint)) },
                singleLine = true,
            )
            if (originalScriptTitle != null && catalogTitle != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ShareCardOptionChip(
                        selected = bookTitle == originalScriptTitle,
                        label = stringResource(R.string.home_library_original_script_title),
                        onClick = { onBookTitleChange(originalScriptTitle) },
                    )
                    ShareCardOptionChip(
                        selected = bookTitle == catalogTitle,
                        label = stringResource(R.string.home_library_catalog_title),
                        onClick = { onBookTitleChange(catalogTitle) },
                    )
                }
            }
            ShareCardOptionsLabel(stringResource(R.string.share_quote_title_font))
            ShareCardFontRow(selected = style.titleFont, onSelect = { restyle(style.copy(titleFont = it)) }) {
                ShareCardOptionChip(
                    selected = style.titleItalic,
                    label = stringResource(R.string.share_quote_italic),
                    onClick = { restyle(style.copy(titleItalic = !style.titleItalic)) },
                )
            }
            ShareCardOptionsLabel(stringResource(R.string.share_quote_weight))
            ShareCardWeightRow(selected = style.titleWeight, onSelect = { restyle(style.copy(titleWeight = it)) })
            ShareCardSliderRow(
                label = stringResource(R.string.share_book_title_size),
                value = style.titleScale,
                range = MinTitleScale..MaxTitleScale,
                steps = TitleScaleSteps,
                onChange = { restyle(style.copy(titleScale = it)) },
            )
            ShareCardOptionsLabel(stringResource(R.string.share_book_details_font))
            ShareCardFontRow(selected = style.detailsFont, onSelect = { restyle(style.copy(detailsFont = it)) })
        }
        1 -> {
            ShareCardShapeRow(selected = style.shape, onSelect = { restyle(style.copy(shape = it)) })
            ShareCardOptionsLabel(stringResource(R.string.share_card_image_options_layout))
            ShareCardChoiceRow(
                choices = BookShareCardLayout.entries,
                selected = options.layout,
                label = { stringResource(it.label) },
                // Backdrop is nothing without the cover, so picking it turns the cover on.
                onSelect = { onOptionsChange(options.copy(layout = it, showCover = options.showCover || it == BookShareCardLayout.BACKDROP)) },
            )
            if (options.layout == BookShareCardLayout.BACKDROP) {
                ShareCardSliderRow(
                    label = stringResource(R.string.share_card_image_options_cover_opacity),
                    value = options.backdropCoverAlpha,
                    range = 0f..1f,
                    steps = 9,
                    onChange = { onOptionsChange(options.copy(backdropCoverAlpha = it)) },
                )
            }
        }
        2 -> ShareCardPalettePicker(
            palette = style.palette,
            theme = options.theme,
            onPaletteChange = { restyle(style.copy(palette = it)) },
            onThemeChange = { onOptionsChange(options.copy(theme = it)) },
        )
        else -> {
            ShareCardOptionsLabel(stringResource(R.string.share_card_image_options_include))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (hasYearlyGoal) {
                    ShareCardOptionChip(options.showYearlyGoal, stringResource(R.string.share_card_image_options_yearly_goal)) {
                        onOptionsChange(options.copy(showYearlyGoal = !options.showYearlyGoal))
                    }
                }
                // Chips a layout never draws are hidden rather than left as toggles that change nothing.
                if (options.layout != BookShareCardLayout.MINIMAL) {
                    ShareCardOptionChip(options.showCover, stringResource(R.string.share_card_image_options_cover)) {
                        onOptionsChange(options.copy(showCover = !options.showCover))
                    }
                }
                ShareCardOptionChip(options.showAuthor, stringResource(R.string.share_card_image_options_author)) {
                    onOptionsChange(options.copy(showAuthor = !options.showAuthor))
                }
                // A book outside any series has nothing to show here.
                if (hasSeries) {
                    ShareCardOptionChip(options.showSeries, stringResource(R.string.share_card_image_options_series)) {
                        onOptionsChange(options.copy(showSeries = !options.showSeries))
                    }
                }
                ShareCardOptionChip(options.showStatus, stringResource(R.string.share_card_image_options_status)) {
                    onOptionsChange(options.copy(showStatus = !options.showStatus))
                }
                ShareCardOptionChip(options.showProgress, stringResource(stats.stat1OptionLabel)) {
                    onOptionsChange(options.copy(showProgress = !options.showProgress))
                }
                ShareCardOptionChip(options.showReadTime, stringResource(stats.stat2OptionLabel)) {
                    onOptionsChange(options.copy(showReadTime = !options.showReadTime))
                }
                // An unrated book has nothing to show here, so the toggle would change nothing in the preview.
                if (hasRating) {
                    ShareCardOptionChip(options.showRating, stringResource(R.string.share_card_image_options_rating)) {
                        onOptionsChange(options.copy(showRating = !options.showRating))
                    }
                }
                // Same as rating: a book without tags would get a toggle that changes nothing.
                if (hasTags) {
                    // Tags and the imported date compete for the same space, so showing one hides the other.
                    ShareCardOptionChip(options.showTags, stringResource(R.string.share_card_image_options_tags)) {
                        val showTags = !options.showTags
                        onOptionsChange(options.copy(showTags = showTags, showImportedDate = options.showImportedDate && !showTags))
                    }
                }
                ShareCardOptionChip(options.showImportedDate, stringResource(stats.dateOptionLabel)) {
                    val showImportedDate = !options.showImportedDate
                    onOptionsChange(options.copy(showImportedDate = showImportedDate, showTags = options.showTags && !showImportedDate))
                }
                ShareCardOptionChip(options.showTagline, stringResource(R.string.share_card_image_options_tagline)) {
                    onOptionsChange(options.copy(showTagline = !options.showTagline))
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            ShareCardSealControls(
                show = style.showSeal,
                scale = style.sealScale,
                onShowChange = { restyle(style.copy(showSeal = it)) },
                onScaleChange = { restyle(style.copy(sealScale = it)) },
            )
        }
    }
}

private val BookShareCardLayout.label: Int get() = when (this) {
    BookShareCardLayout.CLASSIC -> R.string.share_card_image_options_layout_classic
    BookShareCardLayout.SPOTLIGHT -> R.string.share_card_image_options_layout_spotlight
    BookShareCardLayout.MINIMAL -> R.string.share_card_image_options_layout_minimal
    BookShareCardLayout.BACKDROP -> R.string.share_card_image_options_layout_backdrop
}

private const val TitleScaleSteps = 6

/**
 * The book card's last look, kept on this device so the next book opens the way the reader left the last one.
 * The per-book title override isn't kept, and progress/reading time still follow each book's state
 * ([BookShareImageOptions.forState]). Unknown or missing values fall back to the defaults.
 */
internal object BookShareOptionsStore {
    fun load(context: Context): BookShareImageOptions {
        val p = prefs(context)
        val d = BookShareImageOptions()
        val ds = d.style
        return BookShareImageOptions(
            layout = p.enum("layout", d.layout),
            theme = p.enum("theme", d.theme),
            backdropCoverAlpha = p.getFloat("backdropCoverAlpha", d.backdropCoverAlpha),
            showCover = p.getBoolean("showCover", d.showCover),
            showAuthor = p.getBoolean("showAuthor", d.showAuthor),
            showSeries = p.getBoolean("showSeries", d.showSeries),
            showStatus = p.getBoolean("showStatus", d.showStatus),
            showProgress = p.getBoolean("showProgress", d.showProgress),
            showReadTime = p.getBoolean("showReadTime", d.showReadTime),
            showRating = p.getBoolean("showRating", d.showRating),
            showTags = p.getBoolean("showTags", d.showTags),
            showImportedDate = p.getBoolean("showImportedDate", d.showImportedDate),
            showTagline = p.getBoolean("showTagline", d.showTagline),
            showYearlyGoal = p.getBoolean("showYearlyGoal", d.showYearlyGoal),
            style = BookCardStyle(
                palette = p.enum("palette", ds.palette),
                shape = p.enum("shape", ds.shape),
                titleFont = p.enum("titleFont", ds.titleFont),
                titleWeight = p.enum("titleWeight", ds.titleWeight),
                titleItalic = p.getBoolean("titleItalic", ds.titleItalic),
                titleScale = p.getFloat("titleScale", ds.titleScale),
                detailsFont = p.enum("detailsFont", ds.detailsFont),
                showSeal = p.getBoolean("showSeal", ds.showSeal),
                sealScale = p.getFloat("sealScale", ds.sealScale),
            ),
        )
    }

    fun save(context: Context, o: BookShareImageOptions) {
        val s = o.style
        prefs(context).edit {
            putString("layout", o.layout.name)
            putString("theme", o.theme.name)
            putFloat("backdropCoverAlpha", o.backdropCoverAlpha)
            putBoolean("showCover", o.showCover)
            putBoolean("showAuthor", o.showAuthor)
            putBoolean("showSeries", o.showSeries)
            putBoolean("showStatus", o.showStatus)
            putBoolean("showProgress", o.showProgress)
            putBoolean("showReadTime", o.showReadTime)
            putBoolean("showRating", o.showRating)
            putBoolean("showTags", o.showTags)
            putBoolean("showImportedDate", o.showImportedDate)
            putBoolean("showTagline", o.showTagline)
            putBoolean("showYearlyGoal", o.showYearlyGoal)
            putString("palette", s.palette.name)
            putString("shape", s.shape.name)
            putString("titleFont", s.titleFont.name)
            putString("titleWeight", s.titleWeight.name)
            putBoolean("titleItalic", s.titleItalic)
            putFloat("titleScale", s.titleScale)
            putString("detailsFont", s.detailsFont.name)
            putBoolean("showSeal", s.showSeal)
            putFloat("sealScale", s.sealScale)
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("book_share_options", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, default: E): E =
        getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
