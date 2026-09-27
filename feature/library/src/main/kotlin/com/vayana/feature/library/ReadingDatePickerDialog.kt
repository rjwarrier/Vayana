package com.vayana.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

/**
 * Material date picker for when reading started or finished. Future days, a start after [finishedAt] and a finish
 * before [startedAt] can't be picked; the chosen day keeps [current]'s time of day. [onConfirm] gets the new instant
 * for [field] only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingDatePickerDialog(
    field: ReadingDateField,
    current: Long,
    startedAt: Long?,
    finishedAt: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = ReadingDates.toPickerMillis(current),
        yearRange = ReadingDatesMinYear..ReadingDates.pickerDate(ReadingDates.toPickerMillis(now)).year,
        selectableDates = remember(field, startedAt, finishedAt, now) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    ReadingDates.isSelectable(field, utcTimeMillis, startedAt, finishedAt, now)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis ?: return@TextButton onDismiss()
                    onConfirm(ReadingDates.resolve(field, picked, current, startedAt, finishedAt, now))
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.library_reading_date_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_edit_metadata_cancel)) }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(
                        when (field) {
                            ReadingDateField.STARTED -> R.string.library_reading_date_started_title
                            ReadingDateField.FINISHED -> R.string.library_reading_date_finished_title
                        },
                    ),
                    modifier = Modifier.padding(start = Spacing.lg, end = Spacing.md, top = Spacing.md),
                )
            },
        )
    }
}

/** Earliest year the reading-date picker offers. */
private const val ReadingDatesMinYear = 1900
