package com.vayana.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vayana.core.common.QuoteParser
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Palette
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import kotlinx.coroutines.launch

/** Goodreads rating, original year, and imported community-quote count under the series line. */
@Composable
internal fun GoodreadsInfoLine(book: Book, communityQuoteCount: Int?, modifier: Modifier = Modifier) {
    val rating = book.goodreadsRating
    val year = book.originalPublicationYear?.let { stringResource(R.string.library_goodreads_first_published, it) }
    if (rating == null && year == null && book.goodreadsUrl == null && (communityQuoteCount ?: 0) == 0) return
    val uriHandler = LocalUriHandler.current
    val url = book.goodreadsUrl
    Column(modifier = modifier) {
        rating?.let { value ->
            Surface(
                shape = RoundedCornerShape(Radii.full),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (url != null) Modifier.clickable { uriHandler.openUri(url) } else Modifier,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Palette.Gold500,
                        modifier = Modifier.size(Sizes.iconSmall),
                    )
                    Text(
                        text = stringResource(R.string.library_goodreads_rating_chip, value),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        year?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = (if (rating == null) Modifier else Modifier.padding(top = Spacing.xs))
                    .then(if (url != null) Modifier.clickable { uriHandler.openUri(url) } else Modifier),
            )
        }
        communityQuoteCount?.let { count ->
            Surface(
                modifier = Modifier.padding(top = Spacing.sm),
                shape = RoundedCornerShape(Radii.full),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.library_community_quotes_count, count, count),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

/** Progress/error surface for refreshing an already-linked book; first-time imports start in the browser. */
@Composable
internal fun GoodreadsImportStatusDialog(
    state: GoodreadsImportState,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val working = state as? GoodreadsImportState.Working
    val failed = state as? GoodreadsImportState.Failed
    val quoteProgress = working?.quoteProgress?.takeIf { it.total > GoodreadsQuoteProgressThreshold }
    ExpressiveDialogSurface(
        onDismissRequest = { if (working == null) onDismiss() },
    ) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.Link,
            title = stringResource(R.string.library_goodreads_import),
            supportingText = stringResource(R.string.library_goodreads_import_detail),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (failed == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.large),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                    Column {
                        Text(
                            text = stringResource(
                                when (working?.step ?: GoodreadsImportStep.FETCHING_BOOK) {
                                    GoodreadsImportStep.FETCHING_BOOK -> R.string.library_goodreads_step_book
                                    GoodreadsImportStep.FETCHING_COVER_AND_QUOTES -> R.string.library_goodreads_step_extras
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        quoteProgress?.let { progress ->
                            Text(
                                text = stringResource(
                                    R.string.library_goodreads_quotes_progress,
                                    progress.processed,
                                    progress.total,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.medium),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    text = stringResource(
                        when (failed.error) {
                            GoodreadsFetchError.INVALID_LINK -> R.string.library_goodreads_invalid_link
                            GoodreadsFetchError.NOT_FOUND -> R.string.library_goodreads_not_found
                            GoodreadsFetchError.BLOCKED -> R.string.library_goodreads_blocked
                            GoodreadsFetchError.FAILED -> R.string.library_goodreads_failed
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            ) {
                FilledTonalButton(onClick = onDismiss, shape = Radii.buttonShape) {
                    Text(stringResource(R.string.library_edit_metadata_cancel))
                }
                Button(onClick = onBrowse, shape = Radii.buttonShape) {
                    Text(stringResource(R.string.library_goodreads_browse))
                }
            }
        }
    }
}

@Composable
internal fun GoodreadsPreviewDialog(
    book: Book,
    metadata: GoodreadsBookMetadata,
    capturedQuoteCount: Int?,
    onApply: (GoodreadsImportOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentGenres = book.tags()
    val proposedGenres = book.tagsCsv.withGoodreadsGenresPreview(metadata.genres)
    val hasQuotes = metadata.workId != null || (capturedQuoteCount ?: 0) > 0
    var includeSeries by remember(metadata) { mutableStateOf(metadata.series != null) }
    var includeDescription by remember(metadata) { mutableStateOf(!metadata.description.isNullOrBlank()) }
    var includeGenres by remember(metadata) { mutableStateOf(metadata.genres.isNotEmpty()) }
    var includeCover by remember(metadata) { mutableStateOf(metadata.coverUrl != null) }
    var includeGoodreadsInfo by remember(metadata) { mutableStateOf(metadata.averageRating != null || metadata.originalPublicationYear != null) }
    var includeQuotes by remember(metadata, capturedQuoteCount) { mutableStateOf(hasQuotes) }
    val selectedOptions = GoodreadsImportOptions(
        series = includeSeries,
        description = includeDescription,
        genres = includeGenres,
        cover = includeCover,
        goodreadsInfo = includeGoodreadsInfo,
        quotes = includeQuotes,
    )
    ExpressiveDialogSurface(onDismissRequest = onDismiss, scrollable = true) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.Link,
            title = stringResource(R.string.library_goodreads_preview_title),
            supportingText = stringResource(R.string.library_goodreads_preview_body),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            metadata.series?.let {
                GoodreadsPreviewRow(
                    label = stringResource(R.string.library_goodreads_preview_series),
                    current = book.seriesDisplayOrNone().ifBlank { stringResource(R.string.library_goodreads_preview_empty) },
                    proposed = listOfNotNull(it, metadata.seriesNumber?.let { number -> "#$number" }).joinToString(" "),
                    selected = includeSeries,
                    onSelectedChange = { includeSeries = it },
                )
            }
            metadata.description?.takeIf { it.isNotBlank() }?.let {
                GoodreadsPreviewRow(
                    label = stringResource(R.string.library_goodreads_preview_description),
                    current = book.description?.cleanHtml()?.take(PreviewTextLimit).orEmpty().ifBlank {
                        stringResource(R.string.library_goodreads_preview_empty)
                    },
                    proposed = it.take(PreviewTextLimit),
                    selected = includeDescription,
                    onSelectedChange = { includeDescription = it },
                )
            }
            if (metadata.genres.isNotEmpty()) {
                GoodreadsPreviewRow(
                    label = stringResource(R.string.library_goodreads_preview_tags),
                    current = currentGenres.joinToString(", ").ifBlank { stringResource(R.string.library_goodreads_preview_empty) },
                    proposed = proposedGenres.joinToString(", "),
                    selected = includeGenres,
                    onSelectedChange = { includeGenres = it },
                )
            }
            metadata.coverUrl?.let {
                GoodreadsPreviewRow(
                    label = stringResource(R.string.library_goodreads_preview_cover),
                    current = if (book.coverPath.isNullOrBlank()) {
                        stringResource(R.string.library_goodreads_preview_empty)
                    } else {
                        stringResource(R.string.library_goodreads_preview_present)
                    },
                    proposed = stringResource(R.string.library_goodreads_preview_goodreads_cover),
                    selected = includeCover,
                    onSelectedChange = { includeCover = it },
                )
            }
            val rating = metadata.averageRating
            if (rating != null || metadata.originalPublicationYear != null) {
                GoodreadsPreviewRow(
                    label = stringResource(R.string.library_goodreads_preview_goodreads_info),
                    current = listOfNotNull(
                        book.goodreadsRating?.let { stringResource(R.string.library_goodreads_rating, it) },
                        book.originalPublicationYear?.let { stringResource(R.string.library_goodreads_first_published, it) },
                    ).joinToString(" · ").ifBlank { stringResource(R.string.library_goodreads_preview_empty) },
                    proposed = listOfNotNull(
                        rating?.let { stringResource(R.string.library_goodreads_rating, it) },
                        metadata.originalPublicationYear?.let { stringResource(R.string.library_goodreads_first_published, it) },
                    ).joinToString(" · "),
                    selected = includeGoodreadsInfo,
                    onSelectedChange = { includeGoodreadsInfo = it },
                )
            }
            if (hasQuotes) {
                GoodreadsPreviewRow(
                    label = stringResource(R.string.library_goodreads_preview_quotes),
                    current = stringResource(R.string.library_goodreads_preview_current_quotes),
                    proposed = capturedQuoteCount?.let { stringResource(R.string.library_goodreads_preview_captured_quotes, it) }
                        ?: stringResource(R.string.library_goodreads_preview_fetch_quotes),
                    selected = includeQuotes,
                    onSelectedChange = { includeQuotes = it },
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilledTonalButton(onClick = onDismiss, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_edit_metadata_cancel))
            }
            Button(
                onClick = { onApply(selectedOptions) },
                enabled = selectedOptions.hasAnySelection,
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.library_goodreads_preview_apply))
            }
        }
    }
}

@Composable
private fun GoodreadsPreviewRow(
    label: String,
    current: String,
    proposed: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectedChange(!selected) },
        shape = Radii.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(Strokes.outline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        tonalElevation = Elevations.level1,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = selected, onCheckedChange = onSelectedChange)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.library_goodreads_preview_current, current),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.library_goodreads_preview_proposed, proposed),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun String?.withGoodreadsGenresPreview(genres: List<String>): List<String> =
    (orEmpty().split(",").map { it.trim() } + genres.take(GoodreadsMaxGenreTags))
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

@Composable
internal fun ImportQuotesDialog(
    onDismiss: () -> Unit,
    onImportText: (String) -> Unit,
    onImportFile: (Uri) -> Unit,
) {
    var rawText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val parsedQuotes = remember(rawText) { QuoteParser.parse(rawText) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onImportFile(uri)
        }
    }

    ExpressiveDialogSurface(onDismissRequest = onDismiss, scrollable = true) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.EditNote,
            title = stringResource(R.string.library_import_quotes_dialog_title),
            supportingText = stringResource(R.string.library_import_quotes_subtitle),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text(stringResource(R.string.library_import_quotes_tab_paste)) },
                shape = Radii.chipShape,
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text(stringResource(R.string.library_import_quotes_tab_file)) },
                shape = Radii.chipShape,
            )
        }

        if (selectedTab == 0) {
            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.coverWidthMin, max = Sizes.coverWidthMax),
                placeholder = {
                    Text(
                        text = stringResource(R.string.library_import_quotes_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(Radii.medium),
                colors = expressiveTextFieldColors(),
            )

            if (parsedQuotes.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.large),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = stringResource(R.string.library_import_quotes_detected, parsedQuotes.size),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        parsedQuotes.take(3).forEach { quote ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "\"${quote.quoteText.take(45)}...\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = RoundedCornerShape(Radii.small),
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    Text(
                                        text = "${quote.highlightsCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                    )
                                }
                            }
                        }
                        if (parsedQuotes.size > 3) {
                            Text(
                                text = "+ ${parsedQuotes.size - 3} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                    }
                }
            }
        } else {
            ElevatedButton(
                onClick = { filePicker.launch(arrayOf("text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = Radii.buttonShape,
            ) {
                Icon(Icons.Outlined.EditNote, contentDescription = null)
                Text(
                    text = stringResource(R.string.library_import_quotes_file_button),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onDismiss, shape = Radii.buttonShape) {
                Text(stringResource(R.string.settings_reset_all_cancel))
            }
            if (selectedTab == 0) {
                Button(
                    onClick = { onImportText(rawText) },
                    enabled = parsedQuotes.isNotEmpty(),
                    shape = Radii.buttonShape,
                ) {
                    Text(stringResource(R.string.library_import_quotes_confirm, parsedQuotes.size))
                }
            }
        }
    }
}
