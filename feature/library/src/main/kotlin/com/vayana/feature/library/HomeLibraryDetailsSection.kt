package com.vayana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.HomeLibraryDetails
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

/**
 * What Home Library says about a mirrored book beyond the fields Vayana has columns for, plus the way back to it. Home
 * Library owns all of it, so nothing here is editable.
 */
@Composable
internal fun HomeLibraryDetailsSection(
    book: Book,
    onViewInHomeLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = remember(book.sourceMetadata) { HomeLibraryDetails.fromJson(book.sourceMetadata) }
    BookDetailSection(
        icon = Icons.Outlined.LocalLibrary,
        title = stringResource(R.string.home_library_card_title),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.home_library_from_banner),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        details.subtitle?.let { subtitle ->
            Text(text = subtitle, style = MaterialTheme.typography.bodyLarge)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            details.location.takeIf { it.isNotEmpty() }?.let { location ->
                DetailRow(stringResource(R.string.home_library_detail_location), location.joinToString(" · "))
            }
            details.publisher?.let { publisher ->
                val year = details.publishedYear?.let { " ($it)" }.orEmpty()
                DetailRow(stringResource(R.string.home_library_detail_publisher), publisher + year)
            }
            (details.isbn13 ?: details.isbn10)?.let { isbn ->
                DetailRow(stringResource(R.string.home_library_detail_isbn), isbn)
            }
        }
        TextButton(onClick = onViewInHomeLibrary) {
            Text(stringResource(R.string.home_library_view_book))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(2f))
    }
}
