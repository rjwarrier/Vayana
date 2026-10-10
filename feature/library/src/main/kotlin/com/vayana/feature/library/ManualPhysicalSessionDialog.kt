package com.vayana.feature.library

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import com.vayana.core.designsystem.theme.asAppDate
import java.util.UUID
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualPhysicalSessionDialog(book: Book, currentPage: Int, busy: Boolean, failed: Boolean,
    onDismiss: () -> Unit, onSave: (String, ManualPhysicalReadingSession, Boolean) -> Unit) {
    val initial = remember { Instant.now().minusSeconds(1800).atZone(ZoneId.systemDefault()) }
    val today = ReadingDates.toPickerMillis(System.currentTimeMillis())
    var dateMillis by rememberSaveable(book.id) { mutableStateOf(ReadingDates.toPickerMillis(initial.toInstant().toEpochMilli())) }
    var startHour by rememberSaveable(book.id) { mutableStateOf(initial.hour) }
    var startMinute by rememberSaveable(book.id) { mutableStateOf(initial.minute) }
    var hours by rememberSaveable(book.id) { mutableStateOf("0") }
    var minutes by rememberSaveable(book.id) { mutableStateOf("30") }
    var startPage by rememberSaveable(book.id) { mutableStateOf(currentPage.toString()) }
    var endPage by rememberSaveable(book.id) { mutableStateOf(currentPage.toString()) }
    var total by rememberSaveable(book.id) { mutableStateOf(book.pageCount?.toString().orEmpty()) }
    var updateProgress by rememberSaveable(book.id) { mutableStateOf(dateMillis == today) }
    var progressChoiceExplicit by rememberSaveable(book.id) { mutableStateOf(false) }
    val syncId = rememberSaveable(book.id) { "session-${UUID.randomUUID()}" }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    // A blank optional total keeps an already-known total, as the repository does when saving.
    val effectiveTotal = total.ifBlank { book.pageCount?.toString().orEmpty() }
    val draft = ManualPhysicalSessionDraft(dateMillis, startHour, startMinute, hours, minutes, startPage, endPage, effectiveTotal)
    val zone = ZoneId.systemDefault()
    val candidate = remember(draft, zone) { draft.prepare(zone) }
    var resolved by remember(candidate) { mutableStateOf(candidate?.completedBy(System.currentTimeMillis())) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(candidate, lifecycle, resolved == null) {
        if (candidate == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Wake only while the proposed session is in the future; also recheck on resume.
            do {
                val now = System.currentTimeMillis()
                resolved = candidate.completedBy(now)
                if (resolved != null) break
                delay((candidate.endedAt - now).coerceIn(1L, 60_000L))
            } while (true)
        }
    }
    val locale = LocalLocale.current.platformLocale
    val dateLabel = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate().asAppDate()
    val timeLabel = remember(startHour, startMinute, locale) {
        String.format(locale, "%02d:%02d", startHour, startMinute)
    }
    ExpressiveDialogSurface(onDismissRequest = { if (!busy) onDismiss() }, scrollable = true) {
        ExpressiveDialogHeader(Icons.Outlined.Timer, stringResource(R.string.physical_manual_add),
            supportingText = stringResource(R.string.physical_manual_hint))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilledTonalButton(onClick = { showDate = true }, enabled = !busy, modifier = Modifier.weight(1f), shape = Radii.buttonShape) {
                Text(dateLabel)
            }
            FilledTonalButton(onClick = { showTime = true }, enabled = !busy, modifier = Modifier.weight(1f), shape = Radii.buttonShape) {
                Text(stringResource(R.string.physical_manual_start_time,
                    timeLabel))
            }
        }
        Text(stringResource(R.string.physical_manual_duration), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ManualNumberField(hours, { hours = it }, stringResource(R.string.physical_manual_hours), 2, Modifier.weight(1f), !busy)
            ManualNumberField(minutes, { minutes = it }, stringResource(R.string.physical_manual_minutes), 2, Modifier.weight(1f), !busy)
        }
        ManualNumberField(startPage, { startPage = it }, stringResource(R.string.physical_manual_start_page), 5, enabled = !busy)
        OfflinePageFields(totalPages = total, currentPage = endPage, onTotalPagesChange = { total = it },
            onCurrentPageChange = { endPage = it }, currentPageTooHigh =
                endPage.toIntOrNull()?.let { end -> effectiveTotal.toIntOrNull()?.let { end > it } } == true, enabled = !busy)
        Row(
            Modifier.fillMaxWidth().toggleable(value = updateProgress, enabled = !busy, role = Role.Switch,
                onValueChange = { updateProgress = it; progressChoiceExplicit = true }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.physical_manual_update_progress), modifier = Modifier.weight(1f))
            Switch(checked = updateProgress, onCheckedChange = null, enabled = !busy)
        }
        val progressPreview = when {
            !updateProgress -> stringResource(R.string.physical_manual_progress_kept, currentPage)
            effectiveTotal.toIntOrNull() == null -> stringResource(R.string.physical_manual_progress_needs_total)
            resolved != null -> stringResource(R.string.physical_manual_progress_updated, resolved!!.endPage)
            else -> null
        }
        progressPreview?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (resolved == null) Text(stringResource(R.string.physical_manual_invalid),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (failed) Text(stringResource(R.string.physical_timer_failed), color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
            FilledTonalButton(onClick = onDismiss, enabled = !busy, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_edit_metadata_cancel))
            }
            Button(onClick = {
                val log = candidate?.completedBy(System.currentTimeMillis())
                resolved = log
                if (log != null) onSave(syncId, log, updateProgress)
            }, enabled = resolved != null && !busy, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_reading_date_save))
            }
        }
    }
    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis, selectableDates = remember(today) {
            object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today }
        })
        DatePickerDialog(onDismissRequest = { showDate = false }, confirmButton = {
            TextButton(onClick = {
                // Changing the date must not silently override the reader's explicit progress choice.
                state.selectedDateMillis?.let {
                    dateMillis = it
                    if (!progressChoiceExplicit) updateProgress = it == today
                }
                showDate = false
            }, enabled = state.selectedDateMillis != null) { Text(stringResource(R.string.library_reading_date_save)) }
        }, dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.library_edit_metadata_cancel)) } }) {
            DatePicker(state)
        }
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute, is24Hour = true)
        ExpressiveDialogSurface(onDismissRequest = { showTime = false }) {
            ExpressiveDialogHeader(Icons.Outlined.Timer, stringResource(R.string.physical_manual_time_title))
            TimeInput(state = state)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
                TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.library_edit_metadata_cancel)) }
                Button(onClick = { startHour = state.hour; startMinute = state.minute; showTime = false }, shape = Radii.buttonShape) {
                    Text(stringResource(R.string.library_reading_date_save))
                }
            }
        }
    }
}

@Composable
private fun ManualNumberField(value: String, onChange: (String) -> Unit, label: String, digits: Int,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = { onChange(it.filter(Char::isDigit).take(digits)) },
        enabled = enabled,
        label = { Text(label) }, singleLine = true, shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier.fillMaxWidth())
}
