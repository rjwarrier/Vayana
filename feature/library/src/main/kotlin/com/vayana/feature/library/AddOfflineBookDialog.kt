package com.vayana.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.vayana.core.database.model.BookFormat
import com.vayana.core.database.model.OfflinePages
import com.vayana.core.database.model.PhysicalBookOwnership
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

/** What the add dialog collects; the book is created from it as-is. */
internal data class OfflineBookDraft(
    val title: String,
    val author: String?,
    val format: BookFormat,
    val startedAt: Long?,
    val finishedAt: Long?,
    val pageCount: Int?,
    val currentPage: Int?,
    val physicalOwnership: PhysicalBookOwnership,
    val borrowReturnAt: Long?,
    val fetchGoodreads: Boolean,
)

/**
 * Collects a paper book, audiobook or ebook read outside the app: title and author, its type, when it was started and
 * finished (both optional), and whether to pick it on Goodreads next to fill in the rest.
 */
@Composable
internal fun AddOfflineBookDialog(
    onDismiss: () -> Unit,
    onAdd: (OfflineBookDraft) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(BookFormat.PHYSICAL) }
    var startedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var finishedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var totalPagesText by rememberSaveable { mutableStateOf("") }
    var currentPageText by rememberSaveable { mutableStateOf("") }
    var physicalOwnership by rememberSaveable { mutableStateOf(PhysicalBookOwnership.OWNED) }
    var borrowReturnAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val pages = offlinePagesInput(totalPagesText, currentPageText)
    var fetchGoodreads by rememberSaveable { mutableStateOf(true) }
    var editingDate by rememberSaveable { mutableStateOf<ReadingDateField?>(null) }
    var editingReturnDate by rememberSaveable { mutableStateOf(false) }
    val textFieldColors = expressiveTextFieldColors()

    ExpressiveDialogSurface(onDismissRequest = onDismiss, scrollable = true) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.LibraryAdd,
            title = stringResource(R.string.offline_book_add_title),
            supportingText = stringResource(R.string.offline_book_add_subtitle),
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.library_edit_metadata_title_label)) },
            singleLine = true,
            shape = RoundedCornerShape(Radii.medium),
            colors = textFieldColors,
            leadingIcon = { Icon(Icons.Outlined.AutoStories, contentDescription = null) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = author,
            onValueChange = { author = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.library_edit_metadata_author_label)) },
            singleLine = true,
            shape = RoundedCornerShape(Radii.medium),
            colors = textFieldColors,
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )

        Text(
            text = stringResource(R.string.offline_book_type_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BookFormat.Offline.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = format == option,
                    onClick = { format = option },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = BookFormat.Offline.size),
                    icon = { Icon(option.offlineIcon(), contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) },
                ) {
                    Text(option.displayLabel())
                }
            }
        }

        if (format == BookFormat.PHYSICAL) {
            Text(
                text = stringResource(R.string.offline_book_ownership_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PhysicalBookOwnership.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = physicalOwnership == option,
                        onClick = {
                            physicalOwnership = option
                            if (option == PhysicalBookOwnership.OWNED) borrowReturnAt = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, PhysicalBookOwnership.entries.size),
                    ) {
                        Text(option.displayLabel())
                    }
                }
            }
            if (physicalOwnership == PhysicalBookOwnership.BORROWED) {
                OfflineDateField(
                    label = stringResource(R.string.offline_book_return_date_label),
                    value = borrowReturnAt,
                    onClick = { editingReturnDate = true },
                    onClear = { borrowReturnAt = null },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OfflineDateField(
                label = stringResource(R.string.offline_book_started_label),
                value = startedAt,
                onClick = { editingDate = ReadingDateField.STARTED },
                onClear = { startedAt = null },
                modifier = Modifier.weight(1f),
            )
            OfflineDateField(
                label = stringResource(R.string.offline_book_finished_label),
                value = finishedAt,
                onClick = { editingDate = ReadingDateField.FINISHED },
                onClear = { finishedAt = null },
                modifier = Modifier.weight(1f),
            )
        }

        if (format.tracksPages) {
            OfflinePageFields(
                totalPages = totalPagesText,
                currentPage = currentPageText,
                onTotalPagesChange = { totalPagesText = it },
                onCurrentPageChange = { currentPageText = it },
                currentPageTooHigh = pages.currentTooHigh,
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { fetchGoodreads = !fetchGoodreads },
            shape = RoundedCornerShape(Radii.medium),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(Paddings.card),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.offline_book_goodreads_label), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.offline_book_goodreads_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The whole row toggles, so the switch itself only reflects the state.
                Switch(checked = fetchGoodreads, onCheckedChange = null)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        ) {
            FilledTonalButton(onClick = onDismiss, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_edit_metadata_cancel))
            }
            Button(
                onClick = {
                    onAdd(
                        OfflineBookDraft(
                            title = title.trim(),
                            author = author.trim().takeIf { it.isNotEmpty() },
                            format = format,
                            startedAt = startedAt,
                            finishedAt = finishedAt,
                            pageCount = pages.total.takeIf { format.tracksPages },
                            currentPage = pages.current.takeIf { format.tracksPages },
                            physicalOwnership = physicalOwnership,
                            borrowReturnAt = borrowReturnAt.takeIf {
                                format == BookFormat.PHYSICAL && physicalOwnership == PhysicalBookOwnership.BORROWED
                            },
                            fetchGoodreads = fetchGoodreads,
                        ),
                    )
                },
                enabled = title.isNotBlank() && !(format.tracksPages && pages.currentTooHigh),
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.offline_book_save))
            }
        }
    }

    editingDate?.let { field ->
        ReadingDatePickerDialog(
            field = field,
            current = when (field) {
                ReadingDateField.STARTED -> startedAt
                ReadingDateField.FINISHED -> finishedAt
            } ?: System.currentTimeMillis(),
            startedAt = startedAt,
            finishedAt = finishedAt,
            onConfirm = { chosen ->
                when (field) {
                    ReadingDateField.STARTED -> startedAt = chosen
                    ReadingDateField.FINISHED -> finishedAt = chosen
                }
                editingDate = null
            },
            onDismiss = { editingDate = null },
        )
    }
    if (editingReturnDate) {
        BorrowReturnDatePickerDialog(
            current = borrowReturnAt,
            onConfirm = { chosen ->
                borrowReturnAt = chosen
                editingReturnDate = false
            },
            onDismiss = { editingReturnDate = false },
        )
    }
}

@Composable
internal fun PhysicalBookOwnership.displayLabel(): String = stringResource(
    when (this) {
        PhysicalBookOwnership.OWNED -> R.string.offline_book_owned
        PhysicalBookOwnership.BORROWED -> R.string.offline_book_borrowed
    },
)

@Composable
private fun OfflineDateField(
    label: String,
    value: Long?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = value?.formatDate() ?: stringResource(R.string.offline_book_date_not_set),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (value != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.offline_book_clear_date))
                }
            } else {
                Icon(
                    imageVector = Icons.Outlined.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.md),
                )
            }
        }
    }
}

/** The typed page fields, read by the same rules the book is saved with ([OfflinePages]). */
internal data class OfflinePagesInput(val total: Int?, val current: Int?, val currentTooHigh: Boolean)

internal fun offlinePagesInput(totalText: String, currentText: String): OfflinePagesInput {
    val typedCurrent = currentText.toIntOrNull()
    val pages = OfflinePages.of(totalText.toIntOrNull(), typedCurrent)
    val total = pages.total
    return OfflinePagesInput(
        total = total,
        current = pages.current,
        currentTooHigh = total != null && typedCurrent != null && typedCurrent > total,
    )
}

@Composable
private fun OfflinePageFields(
    totalPages: String,
    currentPage: String,
    onTotalPagesChange: (String) -> Unit,
    onCurrentPageChange: (String) -> Unit,
    currentPageTooHigh: Boolean,
) {
    val colors = expressiveTextFieldColors()
    val digits = { text: String -> text.filter(Char::isDigit).take(MaxPageDigits) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = currentPage,
            onValueChange = { onCurrentPageChange(digits(it)) },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.offline_book_current_page)) },
            singleLine = true,
            isError = currentPageTooHigh,
            supportingText = if (currentPageTooHigh) {
                { Text(stringResource(R.string.offline_book_pages_too_high)) }
            } else {
                null
            },
            shape = RoundedCornerShape(Radii.medium),
            colors = colors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = totalPages,
            onValueChange = { onTotalPagesChange(digits(it)) },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.offline_book_total_pages)) },
            singleLine = true,
            shape = RoundedCornerShape(Radii.medium),
            colors = colors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
    }
}

/** Book details' page editor for a book read outside the app. */
@Composable
internal fun OfflinePagesDialog(
    book: com.vayana.core.database.model.Book,
    onDismiss: () -> Unit,
    onSave: (pageCount: Int?, currentPage: Int?) -> Unit,
) {
    var totalPagesText by rememberSaveable(book.id) { mutableStateOf(book.pageCount?.toString().orEmpty()) }
    var currentPageText by rememberSaveable(book.id) { mutableStateOf(book.currentPage()?.toString().orEmpty()) }
    val pages = offlinePagesInput(totalPagesText, currentPageText)
    ExpressiveDialogSurface(onDismissRequest = onDismiss) {
        ExpressiveDialogHeader(
            icon = book.format.offlineIcon(),
            title = stringResource(R.string.offline_book_pages_title),
            supportingText = stringResource(R.string.offline_book_pages_body),
        )
        OfflinePageFields(
            totalPages = totalPagesText,
            currentPage = currentPageText,
            onTotalPagesChange = { totalPagesText = it },
            onCurrentPageChange = { currentPageText = it },
            currentPageTooHigh = pages.currentTooHigh,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        ) {
            FilledTonalButton(onClick = onDismiss, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_edit_metadata_cancel))
            }
            Button(
                onClick = { onSave(pages.total, pages.current) },
                enabled = !pages.currentTooHigh,
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.library_reading_date_save))
            }
        }
    }
}

private const val MaxPageDigits = 5
