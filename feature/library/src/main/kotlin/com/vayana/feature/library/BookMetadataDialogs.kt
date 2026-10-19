package com.vayana.feature.library

import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import com.vayana.core.designsystem.tokens.Paddings
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vayana.core.database.model.MaxBookTagChars
import com.vayana.core.database.model.MaxBookTags
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import com.vayana.core.database.model.normalizedBookTag

@Composable
internal fun CoverPreviewDialog(book: Book, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.medium),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Elevations.shadowMedium,
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            )
        }
    }
}

@Composable
internal fun EditMetadataDialog(
    book: Book,
    libraryBooks: List<Book>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author.orEmpty()) }
    var series by remember(book.id) { mutableStateOf(book.series.orEmpty()) }
    var seriesNumber by remember(book.id) { mutableStateOf(book.seriesNumber.orEmpty()) }
    var tags by remember(book.id) { mutableStateOf(book.tags()) }
    var tagInput by remember(book.id) { mutableStateOf("") }
    val authorSuggestions = remember(libraryBooks) { libraryBooks.metadataSuggestions { it.author } }
    val seriesSuggestions = remember(libraryBooks) { libraryBooks.metadataSuggestions { it.series } }
    val libraryTags = remember(libraryBooks) { libraryBooks.tagsByUse() }
    val duplicateSeriesNumber = remember(book.id, libraryBooks, series, seriesNumber) {
        libraryBooks.hasSeriesNumberCollision(
            currentBookId = book.id,
            series = series,
            seriesNumber = seriesNumber,
        )
    }
    val canSave = title.isNotBlank() && !duplicateSeriesNumber
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )

    val save = { onSave(title, author, series, seriesNumber, tags.withTypedTags(tagInput).toTagsCsv()) }

    // A full-screen editor (Material's full-screen dialog): the form gets the whole screen and the keyboard, with
    // close and Save in the top bar, instead of a card squeezed between the system bars.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.library_edit_metadata_cancel))
                        }
                    },
                    title = {
                        Column {
                            Text(stringResource(R.string.library_edit_metadata_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = save, enabled = canSave) {
                            Text(stringResource(R.string.library_edit_metadata_save))
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.md)
                    .widthIn(max = Sizes.contentMaxWidth)
                    .vayanaAnimateContentSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.library_edit_metadata_title_label)) },
                    singleLine = true,
                    isError = !canSave,
                    shape = RoundedCornerShape(Radii.medium),
                    colors = textFieldColors,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )

                MetadataSuggestionField(
                    value = author,
                    onValueChange = { author = it },
                    suggestions = authorSuggestions,
                    label = stringResource(R.string.library_edit_metadata_author_label),
                    leadingIcon = Icons.Outlined.EditNote,
                    textFieldColors = textFieldColors,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.large),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = Elevations.none,
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        MetadataSuggestionField(
                            value = series,
                            onValueChange = { series = it },
                            suggestions = seriesSuggestions,
                            label = stringResource(R.string.library_edit_metadata_series_label),
                            leadingIcon = Icons.Outlined.Category,
                            textFieldColors = textFieldColors,
                        )

                        OutlinedTextField(
                            value = seriesNumber,
                            onValueChange = { seriesNumber = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.library_edit_metadata_series_number_label)) },
                            singleLine = true,
                            isError = duplicateSeriesNumber,
                            shape = RoundedCornerShape(Radii.medium),
                            colors = textFieldColors,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            supportingText = {
                                if (duplicateSeriesNumber) {
                                    Text(
                                        text = stringResource(R.string.library_edit_metadata_series_number_taken),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                }

                TagEditor(
                    tags = tags,
                    onTagsChange = { tags = it },
                    input = tagInput,
                    onInputChange = { tagInput = it },
                    libraryTags = libraryTags,
                    textFieldColors = textFieldColors,
                )
            }
        }
    }
}

@Composable
internal fun EditDescriptionDialog(
    description: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(description) }
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = Sizes.contentMaxWidth),
            shape = RoundedCornerShape(Radii.extraLargeIncreased),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Elevations.shadowLarge,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.library_edit_description),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.library_edit_metadata_description_label)) },
                    minLines = 5,
                    shape = RoundedCornerShape(Radii.medium),
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(Radii.full),
                    ) {
                        Text(stringResource(R.string.library_edit_metadata_cancel))
                    }
                    Button(
                        onClick = { onSave(text) },
                        shape = RoundedCornerShape(Radii.full),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(end = Spacing.xs),
                        )
                        Text(stringResource(R.string.library_edit_metadata_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataSuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    textFieldColors: androidx.compose.material3.TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) { suggestions.matchingMetadataSuggestions(value) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    expanded = it.isNotBlank()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(Radii.medium),
                colors = textFieldColors,
                leadingIcon = leadingIcon?.let { icon ->
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange(""); expanded = false }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.input_clear_content_description),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            DropdownMenu(
                expanded = expanded && matches.isNotEmpty(),
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(),
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                matches.forEach { suggestion ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = suggestion,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            onValueChange(suggestion)
                            expanded = false
                        },
                    )
                }
            }
        }

        if (matches.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                matches.take(4).forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            onValueChange(suggestion)
                            expanded = false
                        },
                        label = {
                            Text(
                                text = suggestion,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        shape = RoundedCornerShape(Radii.full),
                    )
                }
            }
        }
    }
}

/**
 * Tags as chips that wrap onto new lines, removed with a tap, plus a field that adds what's typed on Enter or at each
 * comma (so a pasted list adds every tag). Library tags not on the book are offered below it: those matching what's
 * typed, or the most used ones while the field is empty.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    input: String,
    onInputChange: (String) -> Unit,
    libraryTags: List<String>,
    textFieldColors: androidx.compose.material3.TextFieldColors,
    modifier: Modifier = Modifier,
) {
    val suggestions = remember(tags, input, libraryTags) { libraryTags.suggestedTags(tags, input) }
    fun add(typed: String) {
        onTagsChange(tags.withTypedTags(typed))
        onInputChange("")
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.library_edit_metadata_tags_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (tags.size > 1) {
                TextButton(onClick = { onTagsChange(emptyList()) }) {
                    Text(stringResource(R.string.library_edit_metadata_tags_clear))
                }
            }
        }
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                tags.forEach { tag ->
                    val remove = stringResource(R.string.library_edit_metadata_tag_remove, tag)
                    InputChip(
                        selected = false,
                        onClick = { onTagsChange(tags - tag) },
                        label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = remove,
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                        modifier = Modifier.semantics { contentDescription = remove },
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { text ->
                if (',' in text) {
                    onTagsChange(tags.withTypedTags(text.substringBeforeLast(',')))
                    onInputChange(text.substringAfterLast(',').trimStart())
                } else {
                    onInputChange(text.take(MaxBookTagChars))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.library_edit_metadata_tag_add)) },
            supportingText = { Text(stringResource(R.string.library_edit_metadata_tags_supporting)) },
            singleLine = true,
            shape = RoundedCornerShape(Radii.medium),
            colors = textFieldColors,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = { add(input) }) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.library_edit_metadata_tag_add),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) add(input) }),
        )
        if (suggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { add(suggestion) },
                        label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Adds [typed] (one tag, or several separated by commas), skipping ones already there in any case, up to the cap. */
internal fun List<String>.withTypedTags(typed: String): List<String> =
    (this + typed.tags()).distinctBy { it.metadataKey() }.take(MaxBookTags)

/** Tags as stored, kept within the field's length limit by dropping whole tags from the end. */
internal fun List<String>.toTagsCsv(): String {
    val kept = ArrayList<String>(size)
    var length = 0
    for (tag in this) {
        val added = tag.length + if (kept.isEmpty()) 0 else 2
        if (length + added > MaxBookTagsInputChars) break
        kept += tag
        length += added
    }
    return kept.joinToString(", ")
}

/** Every tag in the library, most used first (then alphabetical), each once whatever its case. */
internal fun List<Book>.tagsByUse(): List<String> =
    flatMap { it.tags() }
        .groupBy { it.metadataKey() }
        .values
        .sortedWith(compareByDescending<List<String>> { it.size }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first() })
        .map { it.first() }

/** Library tags not on the book: ones containing [input], or the most used ones while nothing is typed. */
internal fun List<String>.suggestedTags(tags: List<String>, input: String): List<String> {
    val taken = tags.mapTo(HashSet()) { it.metadataKey() }
    val query = input.normalizedBookTag()
    return asSequence()
        .filter { it.metadataKey() !in taken }
        .filter { query.isEmpty() || (it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true)) }
        .take(TagSuggestionLimit)
        .toList()
}

/**
 * Everything cover-related in one place: a live preview, the Your cover / Goodreads switch (when both exist),
 * and change/remove. Changes apply straight away, so the preview always shows what the library will.
 */
@Composable
internal fun EditCoverDialog(
    book: Book,
    onChangeCover: () -> Unit,
    onSearchCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onUseCover: (CoverSource) -> Unit,
    onDismiss: () -> Unit,
) {
    ExpressiveDialogSurface(onDismissRequest = onDismiss) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.Image,
            title = stringResource(R.string.library_edit_cover),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(Radii.large),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(Strokes.outline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .padding(Spacing.md)
                    .size(width = Sizes.coverWidthMax, height = Sizes.coverWidthMax / Sizes.coverAspectRatio),
            )
        }
        if (book.customCoverPath != null && book.goodreadsCoverPath != null) {
            CoverSourceSwitch(
                selected = if (book.coverPath == book.goodreadsCoverPath) CoverSource.GOODREADS else CoverSource.CUSTOM,
                onSelect = onUseCover,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedButton(
                onClick = onRemoveCover,
                enabled = book.coverPath != null,
                modifier = Modifier.weight(1f),
                shape = Radii.buttonShape,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.library_remove_cover), maxLines = 1)
            }
            Button(
                onClick = onChangeCover,
                modifier = Modifier.weight(1f),
                shape = Radii.buttonShape,
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.library_change_cover), maxLines = 1)
            }
        }
        OutlinedButton(
            onClick = onSearchCover,
            modifier = Modifier.fillMaxWidth(),
            shape = Radii.buttonShape,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.library_search_cover_images))
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = Radii.buttonShape,
        ) {
            Text(stringResource(R.string.library_edit_cover_done))
        }
    }
}

@Composable
private fun CoverSourceSwitch(selected: CoverSource, onSelect: (CoverSource) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        CoverSource.entries.forEachIndexed { index, source ->
            SegmentedButton(
                selected = selected == source,
                onClick = { if (selected != source) onSelect(source) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = CoverSource.entries.size),
                label = {
                    Text(
                        text = stringResource(
                            when (source) {
                                CoverSource.CUSTOM -> R.string.library_cover_source_custom
                                CoverSource.GOODREADS -> R.string.library_cover_source_goodreads
                            },
                        ),
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

private fun List<Book>.metadataSuggestions(selector: (Book) -> String?): List<String> =
    mapNotNull { book -> selector(book)?.trim()?.takeIf { it.isNotEmpty() } }
        .distinctBy { it.metadataKey() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

private fun List<String>.matchingMetadataSuggestions(value: String): List<String> {
    val query = value.trim()
    if (query.isEmpty()) return emptyList()
    return filter { suggestion ->
        suggestion.contains(query, ignoreCase = true)
    }.filterNot { suggestion ->
        suggestion.equals(query, ignoreCase = true)
    }.take(MetadataSuggestionLimit)
}

private fun List<Book>.hasSeriesNumberCollision(currentBookId: Long, series: String, seriesNumber: String): Boolean {
    val normalizedSeries = series.metadataKey()
    val normalizedNumber = seriesNumber.metadataKey()
    if (normalizedNumber.isEmpty()) return false

    return any { book ->
        book.id != currentBookId &&
            book.series.orEmpty().metadataKey() == normalizedSeries &&
            book.seriesNumber.orEmpty().metadataKey() == normalizedNumber
    }
}
