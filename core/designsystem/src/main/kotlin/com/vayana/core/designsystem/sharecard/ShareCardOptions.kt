package com.vayana.core.designsystem.sharecard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

/** Controls shared by the share-card option panels, so book and quote cards customise the same way. */

@Composable
fun ShareCardOptionsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

@Composable
fun ShareCardOptionChip(selected: Boolean, label: String, onClick: () -> Unit) {
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

/** One segmented row picking among [choices], each labelled by [label]. */
@Composable
fun <T> ShareCardChoiceRow(choices: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
                label = { Text(label(choice), maxLines = 1) },
            )
        }
    }
}

@Composable
fun ShareCardThemeRow(selected: ShareCardTheme, onSelect: (ShareCardTheme) -> Unit) {
    ShareCardOptionsLabel(stringResource(R.string.share_card_image_options_theme))
    ShareCardChoiceRow(
        choices = ShareCardTheme.entries,
        selected = selected,
        label = { theme ->
            stringResource(
                when (theme) {
                    ShareCardTheme.LIGHT -> R.string.share_card_image_options_light
                    ShareCardTheme.DARK -> R.string.share_card_image_options_dark
                },
            )
        },
        onSelect = onSelect,
    )
}
