package com.vayana.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.vayana.core.common.AppLanguage
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

/**
 * The app's language: the phone's, or one Vayana is translated into, picked from a dropdown so the list can grow.
 * Each language is named in itself ("Español"), so someone who can't read the current language can still find
 * theirs. Choosing one redraws the app in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppLanguageCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Read once: choosing a language recreates the screen, which reads it again.
    var selected by remember { mutableStateOf(AppLanguage.current(context)) }
    var expanded by remember { mutableStateOf(false) }
    val systemLabel = stringResource(R.string.settings_app_language_system)
    val options: List<Pair<String?, String>> = remember(systemLabel) {
        listOf<Pair<String?, String>>(null to systemLabel) + AppLanguage.SupportedDisplayNames
    }
    val selectedLabel = remember(options, selected) {
        options.firstOrNull { it.first == selected }?.second ?: systemLabel
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevations.none,
    ) {
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
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.padding(top = Spacing.md),
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_app_language_title)) },
                    leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    shape = RoundedCornerShape(Radii.medium),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.exposedDropdownSize(),
                ) {
                    options.forEach { (tag, name) ->
                        val isSelected = tag == selected
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            trailingIcon = if (isSelected) {
                                { Icon(Icons.Outlined.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                expanded = false
                                if (!isSelected) {
                                    selected = tag
                                    AppLanguage.set(context, tag)
                                }
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
        }
    }
}
