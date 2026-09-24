package com.vayana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vayana.core.designsystem.sharecard.BookShareCardLayout
import com.vayana.core.designsystem.sharecard.ShareCardChoiceRow
import com.vayana.core.designsystem.sharecard.ShareCardOptionChip
import com.vayana.core.designsystem.sharecard.ShareCardOptionsLabel
import com.vayana.core.designsystem.sharecard.ShareCardThemeRow
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import kotlin.math.roundToInt

/** Inline controls under the share-card preview; each change redraws the card above immediately. */
@Composable
internal fun BookShareImageOptionsPanel(
    options: BookShareImageOptions,
    hasRating: Boolean,
    hasSeries: Boolean,
    hasTags: Boolean,
    hasYearlyGoal: Boolean,
    onOptionsChange: (BookShareImageOptions) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ShareCardOptionsLabel(stringResource(R.string.share_card_image_options_layout))
        ShareCardChoiceRow(
            choices = BookShareCardLayout.entries,
            selected = options.layout,
            label = { layout ->
                stringResource(
                    when (layout) {
                        BookShareCardLayout.CLASSIC -> R.string.share_card_image_options_layout_classic
                        BookShareCardLayout.SPOTLIGHT -> R.string.share_card_image_options_layout_spotlight
                        BookShareCardLayout.MINIMAL -> R.string.share_card_image_options_layout_minimal
                        BookShareCardLayout.BACKDROP -> R.string.share_card_image_options_layout_backdrop
                    },
                )
            },
            onSelect = { layout ->
                onOptionsChange(
                    options.copy(
                        layout = layout,
                        showCover = options.showCover || layout == BookShareCardLayout.BACKDROP,
                    ),
                )
            },
        )
        if (options.layout == BookShareCardLayout.BACKDROP) {
            val coverOpacityLabel = stringResource(R.string.share_card_image_options_cover_opacity)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShareCardOptionsLabel(coverOpacityLabel)
                Text(
                    text = stringResource(
                        R.string.share_card_image_options_cover_opacity_value,
                        (options.backdropCoverAlpha * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = options.backdropCoverAlpha,
                onValueChange = { onOptionsChange(options.copy(backdropCoverAlpha = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = coverOpacityLabel },
                enabled = options.showCover,
                valueRange = 0f..1f,
                steps = 9,
            )
        }
        ShareCardThemeRow(selected = options.theme, onSelect = { onOptionsChange(options.copy(theme = it)) })
        ShareCardOptionsLabel(stringResource(R.string.share_card_image_options_include))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (hasYearlyGoal) {
                ShareCardOptionChip(
                    selected = options.showYearlyGoal,
                    label = stringResource(R.string.share_card_image_options_yearly_goal),
                    onClick = { onOptionsChange(options.copy(showYearlyGoal = !options.showYearlyGoal)) },
                )
            }
            // Chips a layout never draws are hidden rather than left as toggles that change nothing.
            if (options.layout != BookShareCardLayout.MINIMAL) {
                ShareCardOptionChip(
                    selected = options.showCover,
                    label = stringResource(R.string.share_card_image_options_cover),
                    onClick = { onOptionsChange(options.copy(showCover = !options.showCover)) },
                )
            }
            ShareCardOptionChip(
                selected = options.showAuthor,
                label = stringResource(R.string.share_card_image_options_author),
                onClick = { onOptionsChange(options.copy(showAuthor = !options.showAuthor)) },
            )
            // A book outside any series has nothing to show here.
            if (hasSeries) {
                ShareCardOptionChip(
                    selected = options.showSeries,
                    label = stringResource(R.string.share_card_image_options_series),
                    onClick = { onOptionsChange(options.copy(showSeries = !options.showSeries)) },
                )
            }
            ShareCardOptionChip(
                selected = options.showStatus,
                label = stringResource(R.string.share_card_image_options_status),
                onClick = { onOptionsChange(options.copy(showStatus = !options.showStatus)) },
            )
            ShareCardOptionChip(
                selected = options.showProgress,
                label = stringResource(R.string.share_card_image_options_progress),
                onClick = { onOptionsChange(options.copy(showProgress = !options.showProgress)) },
            )
            ShareCardOptionChip(
                selected = options.showReadTime,
                label = stringResource(R.string.share_card_image_options_read_time),
                onClick = { onOptionsChange(options.copy(showReadTime = !options.showReadTime)) },
            )
            // An unrated book has nothing to show here, so the toggle would change nothing in the preview.
            if (hasRating) {
                ShareCardOptionChip(
                    selected = options.showRating,
                    label = stringResource(R.string.share_card_image_options_rating),
                    onClick = { onOptionsChange(options.copy(showRating = !options.showRating)) },
                )
            }
            // Same as rating: a book without tags would get a toggle that changes nothing.
            if (hasTags) {
                ShareCardOptionChip(
                    selected = options.showTags,
                    label = stringResource(R.string.share_card_image_options_tags),
                    // Tags and the imported date compete for the same space, so showing one hides the other.
                    onClick = {
                        val showTags = !options.showTags
                        onOptionsChange(options.copy(showTags = showTags, showImportedDate = options.showImportedDate && !showTags))
                    },
                )
            }
            ShareCardOptionChip(
                selected = options.showImportedDate,
                label = stringResource(R.string.share_card_image_options_imported_date),
                onClick = {
                    val showImportedDate = !options.showImportedDate
                    onOptionsChange(options.copy(showImportedDate = showImportedDate, showTags = options.showTags && !showImportedDate))
                },
            )
            ShareCardOptionChip(
                selected = options.showTagline,
                label = stringResource(R.string.share_card_image_options_tagline),
                onClick = { onOptionsChange(options.copy(showTagline = !options.showTagline)) },
            )
        }
    }
}
