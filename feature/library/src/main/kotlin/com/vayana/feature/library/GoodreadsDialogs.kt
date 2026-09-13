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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import java.text.NumberFormat
import kotlinx.coroutines.launch

/** Goodreads rating and original year under the series line; tapping it opens the book on Goodreads. */
@Composable
internal fun GoodreadsInfoLine(book: Book, modifier: Modifier = Modifier) {
    val rating = book.goodreadsRating?.let { value ->
        val count = book.goodreadsRatingsCount
        if (count != null) {
            stringResource(R.string.library_goodreads_rating_with_count, value, NumberFormat.getIntegerInstance().format(count))
        } else {
            stringResource(R.string.library_goodreads_rating, value)
        }
    }
    val year = book.originalPublicationYear?.let { stringResource(R.string.library_goodreads_first_published, it) }
    val text = listOfNotNull(rating, year).joinToString("  ·  ")
    if (text.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    val url = book.goodreadsUrl
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.then(if (url != null) Modifier.clickable { uriHandler.openUri(url) } else Modifier),
    )
}

/**
 * Paste a Goodreads link and read it into a preview. Applying the preview fills in details, cover and popular quotes.
 */
@Composable
internal fun GoodreadsImportDialog(
    state: GoodreadsImportState,
    initialLink: String,
    browseFallbackQuery: String,
    onImport: (String) -> Unit,
    onBrowse: (url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var link by rememberSaveable { mutableStateOf(initialLink) }
    // The pasted book's page if there is one, otherwise a Goodreads search for this book.
    val browseUrl = goodreadsBookIdOf(link)?.let(::goodreadsBookUrl) ?: goodreadsSearchUrl(browseFallbackQuery)
    val working = state as? GoodreadsImportState.Working
    val canImport = link.isNotBlank() && working == null
    ExpressiveDialogSurface(
        // An import in flight can't be abandoned halfway; the dialog only closes once it's done or failed.
        onDismissRequest = { if (working == null) onDismiss() },
    ) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.Link,
            title = stringResource(R.string.library_goodreads_import),
            supportingText = stringResource(R.string.library_goodreads_import_detail),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text(stringResource(R.string.library_goodreads_link_label)) },
            placeholder = { Text(stringResource(R.string.library_goodreads_link_hint)) },
            singleLine = true,
            enabled = working == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (canImport) onImport(link) }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.medium),
            colors = expressiveTextFieldColors(),
        )
        OutlinedButton(
            onClick = { onBrowse(browseUrl) },
            enabled = working == null,
            modifier = Modifier.fillMaxWidth(),
            shape = Radii.buttonShape,
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.library_goodreads_browse))
        }
        if (working != null) {
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
                    Text(
                        text = stringResource(
                            when (working.step) {
                                GoodreadsImportStep.FETCHING_BOOK -> R.string.library_goodreads_step_book
                                GoodreadsImportStep.FETCHING_COVER_AND_QUOTES -> R.string.library_goodreads_step_extras
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        (state as? GoodreadsImportState.Failed)?.let { failed ->
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
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onDismiss,
                enabled = working == null,
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.library_edit_metadata_cancel))
            }
            Button(
                onClick = { onImport(link) },
                enabled = canImport,
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.library_goodreads_import_action))
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
