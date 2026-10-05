package com.vayana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.PhysicalReadingSessionSummary
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PhysicalSessionHistoryDialog(
    book: Book,
    sessions: List<ReadingSession>,
    summary: PhysicalReadingSessionSummary,
    pace: PhysicalReadingPace,
    onUpdatePages: suspend (ReadingSession, Int, Int) -> Unit,
    onDeleteSession: suspend (ReadingSession) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale) }
    val zone = ZoneId.systemDefault()
    var editingSession by remember { mutableStateOf<ReadingSession?>(null) }
    var deletingSession by remember { mutableStateOf<ReadingSession?>(null) }
    ExpressiveDialogSurface(onDismissRequest = onDismiss) {
        ExpressiveDialogHeader(Icons.Outlined.History, stringResource(R.string.physical_timer_reading_log),
            supportingText = book.homeLibraryDisplayTitle)
        Surface(
            shape = RoundedCornerShape(Radii.large),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (book.totalReadingSeconds > 0) {
                    Text(
                        stringResource(R.string.physical_timer_total_time, formatTimerClock(book.totalReadingSeconds)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(stringResource(R.string.physical_timer_session_count, summary.sessionCount))
                    Text(stringResource(
                        R.string.physical_timer_average_session,
                        formatReadingDuration(summary.averageSeconds, context),
                    ))
                }
                pace.pagesPerHour?.let {
                    Text(
                        stringResource(R.string.physical_timer_speed, String.format(locale, "%.1f", it)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.physical_timer_pace_basis, pace.timedPages, pace.sampleCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
                pace.remainingSeconds?.let {
                    Text(stringResource(R.string.physical_timer_remaining, formatReadingDuration(it, context)))
                } ?: Text(
                    stringResource(R.string.physical_timer_estimate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
        Text(stringResource(R.string.physical_timer_history), style = MaterialTheme.typography.titleMedium)
        if (sessions.isEmpty()) Text(stringResource(R.string.physical_timer_history_empty))
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(sessions, key = { it.syncId }) { log ->
                Surface(shape = RoundedCornerShape(Radii.medium), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        Modifier.fillMaxWidth().padding(Spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(remember(log.startedAt, zone, formatter) { formatter.format(Instant.ofEpochMilli(log.startedAt).atZone(zone)) },
                                style = MaterialTheme.typography.labelLarge)
                            Text(formatTimerClock(log.durationSeconds), style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.physical_timer_history_pages, log.startPage!!, log.endPage!!),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            OutlinedButton(onClick = { editingSession = log }, shape = Radii.buttonShape) {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                                Text(stringResource(R.string.physical_timer_edit_pages), modifier = Modifier.padding(start = Spacing.xs))
                            }
                            TextButton(onClick = { deletingSession = log }) {
                                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(stringResource(R.string.library_delete_confirm), color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = Spacing.xs))
                            }
                        }
                    }
                }
            }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.tools_close)) }
    }
    editingSession?.let { session ->
        PhysicalSessionPageEditDialog(
            session = session,
            pageCount = book.pageCount,
            onDismiss = { editingSession = null },
            onSave = { startPage, endPage -> onUpdatePages(session, startPage, endPage) },
        )
    }
    deletingSession?.let { session ->
        PhysicalSessionDeleteDialog(session, formatter.format(Instant.ofEpochMilli(session.startedAt).atZone(zone)),
            onDismiss = { deletingSession = null }, onDelete = { onDeleteSession(session) })
    }

}

@Composable
private fun PhysicalSessionDeleteDialog(session: ReadingSession, dateLabel: String,
    onDismiss: () -> Unit, onDelete: suspend () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ExpressiveDialogSurface(onDismissRequest = { if (!busy) onDismiss() }) {
        ExpressiveDialogHeader(Icons.Outlined.Delete, stringResource(R.string.physical_timer_delete_session),
            supportingText = dateLabel)
        Text(stringResource(R.string.physical_timer_history_pages, session.startPage!!, session.endPage!!))
        Text(stringResource(R.string.physical_timer_delete_session_body, formatTimerClock(session.durationSeconds)))
        if (failed) Text(stringResource(R.string.physical_timer_delete_session_failed), color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
            FilledTonalButton(onClick = onDismiss, enabled = !busy, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_edit_metadata_cancel))
            }
            Button(onClick = {
                scope.launch {
                    busy = true; failed = false
                    try { onDelete(); onDismiss() }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { failed = true }
                    finally { busy = false }
                }
            }, enabled = !busy, shape = Radii.buttonShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError)) {
                Text(stringResource(R.string.library_delete_confirm))
            }
        }
    }
}

internal data class PhysicalSessionPageEdit(val startPage: Int, val endPage: Int)

internal fun resolvePhysicalSessionPageEdit(
    startPage: String,
    endPage: String,
    pageCount: Int?,
): PhysicalSessionPageEdit? {
    val start = startPage.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val end = endPage.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    if (pageCount != null && (pageCount <= 0 || start > pageCount || end > pageCount)) return null
    return PhysicalSessionPageEdit(start, end)
}

@Composable
private fun PhysicalSessionPageEditDialog(
    session: ReadingSession,
    pageCount: Int?,
    onDismiss: () -> Unit,
    onSave: suspend (Int, Int) -> Unit,
) {
    var startPage by rememberSaveable(session.syncId) { mutableStateOf(session.startPage?.toString().orEmpty()) }
    var endPage by rememberSaveable(session.syncId) { mutableStateOf(session.endPage?.toString().orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val candidate = remember(startPage, endPage, pageCount) {
        resolvePhysicalSessionPageEdit(startPage, endPage, pageCount)
    }
    ExpressiveDialogSurface(
        onDismissRequest = { if (!busy) onDismiss() },
        scrollable = true,
    ) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.Edit,
            title = stringResource(R.string.physical_timer_edit_pages),
            supportingText = stringResource(R.string.physical_timer_edit_pages_hint),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ReadingLogPageField(
                value = startPage,
                onValueChange = { startPage = it; failed = false },
                label = stringResource(R.string.physical_manual_start_page),
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            ReadingLogPageField(
                value = endPage,
                onValueChange = { endPage = it; failed = false },
                label = stringResource(R.string.physical_timer_end_page),
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
        }
        if (candidate == null) {
            Text(
                text = pageCount?.let {
                    stringResource(R.string.physical_timer_edit_pages_invalid_with_total, it)
                } ?: stringResource(R.string.physical_timer_edit_pages_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (failed) {
            Text(
                stringResource(R.string.physical_timer_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        ) {
            FilledTonalButton(
                onClick = onDismiss,
                enabled = !busy,
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.library_edit_metadata_cancel))
            }
            Button(
                onClick = {
                    val edit = candidate ?: return@Button
                    scope.launch {
                        busy = true
                        failed = false
                        try {
                            onSave(edit.startPage, edit.endPage)
                            onDismiss()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = candidate != null && !busy,
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.library_reading_date_save))
            }
        }
    }
}

@Composable
private fun ReadingLogPageField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(7)) },
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
