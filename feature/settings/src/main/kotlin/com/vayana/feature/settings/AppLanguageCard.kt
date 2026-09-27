package com.vayana.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.common.AppLanguage
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R

/**
 * The app's language: the phone's, or one Vayana is translated into. Each language is named in itself ("Español"),
 * so someone who can't read the current language can still find theirs. Choosing one redraws the app in it.
 */
@Composable
internal fun AppLanguageCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Read once: choosing a language recreates the screen, which reads it again.
    var selected by remember { mutableStateOf(AppLanguage.current(context)) }
    val options = remember {
        listOf<Pair<String?, String?>>(null to null) + AppLanguage.Supported.map { it to AppLanguage.displayName(it) }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevations.none,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Paddings.card)) {
                Text(
                    text = stringResource(R.string.settings_app_language_title),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_app_language_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = Paddings.card, end = Paddings.card, bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                options.forEach { (tag, name) ->
                    val isSelected = tag == selected
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                selected = tag
                                AppLanguage.set(context, tag)
                            }
                        },
                        label = {
                            Text(
                                text = name ?: stringResource(R.string.settings_app_language_system),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                ),
                            )
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(if (isSelected) Radii.full else Radii.small),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = Strokes.outline,
                            selectedBorderWidth = Strokes.none,
                        ),
                    )
                }
            }
        }
    }
}
