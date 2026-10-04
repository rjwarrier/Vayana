package com.vayana.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.designsystem.theme.vayanaPressScale
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R

internal fun physicalReadingHomeBooks(books: List<Book>, activeBookId: Long?): List<Book> {
    val order = compareByDescending<Book> { it.id == activeBookId }
        .thenByDescending { it.lastReadAt ?: it.startedReadingAt ?: 0L }.thenBy { it.id }
    val recent = ArrayList<Book>(4)
    for (book in books) {
        if (book.readingDisposition != "ACTIVE" || book.format != BookFormat.PHYSICAL || (book.id != activeBookId && book.readingState() != BookReadingState.READING)) continue
        val index = recent.indexOfFirst { order.compare(book, it) < 0 }.let { if (it < 0) recent.size else it }
        if (index >= 3) continue
        recent.add(index, book)
        if (recent.size > 3) recent.removeAt(3)
    }
    return recent
}

@Composable
internal fun PhysicalReadingHomeShelf(books: List<Book>, timer: PhysicalTimerSession?,
    onBookClick: (Book) -> Unit, onViewAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.physical_home_reading), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onViewAll) { Text(stringResource(R.string.library_read_next_view_all)) }
        }
        books.forEach { book ->
            val active = timer?.takeIf { it.bookId == book.id }
            val interaction = remember(book.id) { MutableInteractionSource() }
            Surface(onClick = { onBookClick(book) }, interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().vayanaPressScale(interaction),
                shape = RoundedCornerShape(Radii.extraLargeIncreased),
                color = if (active != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (active != null) BorderStroke(Strokes.emphasis, MaterialTheme.colorScheme.primary) else null) {
                Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically) {
                    BookCover(book, Modifier.width(Sizes.coverWidthMin * 0.6f).bookSharedElement(book.id, BookOpenTransitionSource.COVER))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(book.homeLibraryDisplayTitle, style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        book.currentPage()?.let { page ->
                            Text(stringResource(R.string.physical_home_page, page, book.pageCount!!),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (active != null) {
                            Text(stringResource(when (active.phase) {
                                PhysicalTimerPhase.RUNNING -> R.string.physical_timer_running
                                PhysicalTimerPhase.PAUSED -> R.string.physical_timer_paused
                                PhysicalTimerPhase.STOPPED -> R.string.physical_timer_stopped
                            }), style = MaterialTheme.typography.labelMedium)
                            PhysicalTimerClock(active, style = MaterialTheme.typography.titleLarge)
                        } else {
                            book.lastReadAt?.let {
                                Text(stringResource(R.string.library_last_read_on, it.formatDate()),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(stringResource(R.string.physical_home_open), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
