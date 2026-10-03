package com.vayana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vayana.core.database.model.Book
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
import java.util.Locale

@Composable
internal fun PhysicalSessionHistoryDialog(book: Book, sessions: List<ReadingSession>, onDismiss: () -> Unit) {
    val locale = Locale.getDefault()
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale) }
    val zone = ZoneId.systemDefault()
    ExpressiveDialogSurface(onDismissRequest = onDismiss) {
        ExpressiveDialogHeader(Icons.Outlined.Timer, stringResource(R.string.physical_timer_history),
            supportingText = book.homeLibraryDisplayTitle)
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(sessions, key = { it.syncId }) { log ->
                Surface(shape = RoundedCornerShape(Radii.medium), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(remember(log.startedAt, zone, formatter) { formatter.format(Instant.ofEpochMilli(log.startedAt).atZone(zone)) },
                            style = MaterialTheme.typography.labelLarge)
                        Text(formatTimerClock(log.durationSeconds), style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.physical_timer_history_pages, log.startPage!!, log.endPage!!),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_edit_metadata_cancel)) }
    }
}
