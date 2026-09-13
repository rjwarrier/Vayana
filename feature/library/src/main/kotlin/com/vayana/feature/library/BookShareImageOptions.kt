package com.vayana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.vayana.core.designsystem.sharecard.BookShareCardLayout
import com.vayana.core.designsystem.sharecard.ShareCardTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

/** Inline controls under the share-card preview; each change redraws the card above immediately. */
@Composable
internal fun BookShareImageOptionsPanel(
    options: BookShareImageOptions,
    hasRating: Boolean,
    hasTags: Boolean,
    onOptionsChange: (BookShareImageOptions) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ShareImageOptionsLabel(stringResource(R.string.share_card_image_options_layout))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BookShareCardLayout.entries.forEachIndexed { index, layout ->
                SegmentedButton(
                    selected = options.layout == layout,
                    onClick = { onOptionsChange(options.copy(layout = layout)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = BookShareCardLayout.entries.size),
                    label = {
                        Text(
                            stringResource(
                                when (layout) {
                                    BookShareCardLayout.CLASSIC -> R.string.share_card_image_options_layout_classic
                                    BookShareCardLayout.SPOTLIGHT -> R.string.share_card_image_options_layout_spotlight
                                    BookShareCardLayout.MINIMAL -> R.string.share_card_image_options_layout_minimal
                                    BookShareCardLayout.BACKDROP -> R.string.share_card_image_options_layout_backdrop
                                },
                            ),
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        ShareImageOptionsLabel(stringResource(R.string.share_card_image_options_theme))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ShareCardTheme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = options.theme == theme,
                    onClick = { onOptionsChange(options.copy(theme = theme)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ShareCardTheme.entries.size),
                    label = {
                        Text(
                            stringResource(
                                when (theme) {
                                    ShareCardTheme.LIGHT -> R.string.share_card_image_options_light
                                    ShareCardTheme.DARK -> R.string.share_card_image_options_dark
                                },
                            ),
                        )
                    },
                )
            }
        }
        ShareImageOptionsLabel(stringResource(R.string.share_card_image_options_include))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // Chips a layout never draws are hidden rather than left as toggles that change nothing.
            if (options.layout != BookShareCardLayout.MINIMAL) {
                ShareImageOptionChip(
                    selected = options.showCover,
                    label = stringResource(R.string.share_card_image_options_cover),
                    onClick = { onOptionsChange(options.copy(showCover = !options.showCover)) },
                )
            }
            ShareImageOptionChip(
                selected = options.showAuthor,
                label = stringResource(R.string.share_card_image_options_author),
                onClick = { onOptionsChange(options.copy(showAuthor = !options.showAuthor)) },
            )
            ShareImageOptionChip(
                selected = options.showStatus,
                label = stringResource(R.string.share_card_image_options_status),
                onClick = { onOptionsChange(options.copy(showStatus = !options.showStatus)) },
            )
            ShareImageOptionChip(
                selected = options.showProgress,
                label = stringResource(R.string.share_card_image_options_progress),
                onClick = { onOptionsChange(options.copy(showProgress = !options.showProgress)) },
            )
            ShareImageOptionChip(
                selected = options.showReadTime,
                label = stringResource(R.string.share_card_image_options_read_time),
                onClick = { onOptionsChange(options.copy(showReadTime = !options.showReadTime)) },
            )
            // An unrated book has nothing to show here, so the toggle would change nothing in the preview.
            if (hasRating) {
                ShareImageOptionChip(
                    selected = options.showRating,
                    label = stringResource(R.string.share_card_image_options_rating),
                    onClick = { onOptionsChange(options.copy(showRating = !options.showRating)) },
                )
            }
            // Same as rating: a book without tags would get a toggle that changes nothing.
            if (hasTags) {
                ShareImageOptionChip(
                    selected = options.showTags,
                    label = stringResource(R.string.share_card_image_options_tags),
                    // Tags and the imported date compete for the same space, so showing one hides the other.
                    onClick = {
                        val showTags = !options.showTags
                        onOptionsChange(options.copy(showTags = showTags, showImportedDate = options.showImportedDate && !showTags))
                    },
                )
            }
            ShareImageOptionChip(
                selected = options.showImportedDate,
                label = stringResource(R.string.share_card_image_options_imported_date),
                onClick = {
                    val showImportedDate = !options.showImportedDate
                    onOptionsChange(options.copy(showImportedDate = showImportedDate, showTags = options.showTags && !showImportedDate))
                },
            )
            ShareImageOptionChip(
                selected = options.showTagline,
                label = stringResource(R.string.share_card_image_options_tagline),
                onClick = { onOptionsChange(options.copy(showTagline = !options.showTagline)) },
            )
        }
    }
}

@Composable
private fun ShareImageOptionsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

@Composable
private fun ShareImageOptionChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}
