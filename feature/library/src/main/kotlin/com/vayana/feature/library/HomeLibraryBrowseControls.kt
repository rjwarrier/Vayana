package com.vayana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@Composable
internal fun HomeLibraryBrowseControls(
    query: String,
    onQueryChange: (String) -> Unit,
    fromShelves: Boolean,
    onFromShelvesChange: (Boolean) -> Unit,
    languages: List<String>,
    language: String?,
    onLanguageChange: (String?) -> Unit,
    genres: List<String>,
    genre: String?,
    onGenreChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.home_library_browse_search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(
                selected = fromShelves,
                onClick = { onFromShelvesChange(!fromShelves) },
                label = { Text(stringResource(R.string.home_library_browse_shelves)) },
            )
            if (fromShelves) {
                BrowseChoice(languages, language, onLanguageChange,
                    stringResource(R.string.home_library_browse_all_languages), ::homeLibraryLanguageLabel)
                BrowseChoice(genres, genre, onGenreChange,
                    stringResource(R.string.home_library_browse_all_genres)) { it }
            }
        }
        if (fromShelves) Text(
            stringResource(R.string.home_library_browse_shelves_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BrowseChoice(
    choices: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    allLabel: String,
    label: (String) -> String,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected != null, onClick = { expanded = true },
            label = { Text(selected?.let(label) ?: allLabel) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelect(null); expanded = false })
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(label(choice)) }, onClick = { onSelect(choice); expanded = false })
            }
        }
    }
}
