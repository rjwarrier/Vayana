package com.vayana.feature.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.theme.asAppDate
import com.vayana.core.designsystem.theme.rememberCoverColorFilter
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.io.File

@Composable
internal fun ReadingRecordsCard(books: List<Book>, onOpenBook: (Long) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(Radii.medium), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(Paddings.card), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.statistics_reading_records), style = MaterialTheme.typography.titleMedium)
            books.take(3).forEach { ReadingRecordRow(it, onOpenBook) }
            if (books.size > 3) TextButton(onClick = { showAll = true }) {
                Text(stringResource(R.string.statistics_reading_records_all))
            }
        }
    }
    if (showAll) AlertDialog(
        onDismissRequest = { showAll = false },
        title = { Text(stringResource(R.string.statistics_reading_records)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                items(books, key = { it.id }) { book ->
                    ReadingRecordRow(book) { showAll = false; onOpenBook(it) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showAll = false }) { Text(stringResource(android.R.string.ok)) } },
    )
}

@Composable
private fun ReadingRecordRow(book: Book, onOpenBook: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = { onOpenBook(book.id) }).padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        val coverModifier = Modifier.width(48.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(Radii.small))
        if (book.coverPath != null) AsyncImage(
            model = File(book.coverPath), contentDescription = null, modifier = coverModifier,
            contentScale = ContentScale.Crop, colorFilter = rememberCoverColorFilter(),
        ) else Surface(modifier = coverModifier, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) }
        }
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            book.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            val unknown = stringResource(R.string.statistics_record_missing_date)
            Text(stringResource(R.string.statistics_record_dates, book.startedReadingAt?.asAppDate() ?: unknown,
                book.finishedReadingAt?.asAppDate() ?: unknown), style = MaterialTheme.typography.bodySmall)
            val minutes = book.totalReadingSeconds / 60
            val time = if (minutes == 0L) stringResource(R.string.statistics_record_seconds, book.totalReadingSeconds)
                else {
                    val duration = if (minutes >= 60) stringResource(R.string.reader_duration_hours_minutes, minutes / 60, minutes % 60)
                        else stringResource(R.string.reader_duration_minutes, minutes)
                    stringResource(R.string.statistics_record_time, duration)
                }
            Text(time, style = MaterialTheme.typography.bodySmall)
        }
    }
}
