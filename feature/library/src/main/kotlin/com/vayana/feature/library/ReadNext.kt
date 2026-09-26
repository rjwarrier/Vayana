package com.vayana.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

internal data class ReadNextSeriesBreakWarning(
    val currentBook: Book,
    val nextBook: Book,
    val queuedBook: Book,
)

/** The Read Next shelf's books: the queue, or suggestions while it is empty and [showSuggestions]. */
@Composable
internal fun rememberReadNextShelfBooks(
    queue: List<Book>,
    showSuggestions: Boolean,
    books: List<Book>,
    currentBook: Book?,
): List<Book> {
    val suggestions = if (showSuggestions) remember(books, currentBook) { suggestedReadNext(books, currentBook) } else emptyList()
    return queue.ifEmpty { suggestions }
}

/** The next unread book in the series being read, else the author's next unread book; empty when nothing fits. */
internal fun suggestedReadNext(books: List<Book>, currentBook: Book?): List<Book> {
    val current = currentBook
        ?.takeIf { it.hasStartedReading() && it.finishedReadingAt == null }
        ?: return emptyList()
    val currentSeries = current.series?.metadataKey()?.takeIf { it.isNotBlank() } ?: return emptyList()
    val currentSeriesNumber = current.seriesNumber?.toDoubleOrNull()
    val nextInSeries = books.nextInSeries(current, currentSeries, afterNumber = currentSeriesNumber)
    val fallbackByAuthor = if (nextInSeries == null) {
        val author = current.author?.metadataKey()?.takeIf { it.isNotBlank() }
        books
            .asSequence()
            .filter { author != null && it.author?.metadataKey() == author }
            .filter { it.id != current.id }
            .filter { it.series?.metadataKey() != currentSeries || it.seriesNumber?.toDoubleOrNull()?.let { number -> currentSeriesNumber != null && number > currentSeriesNumber } == true }
            .filter { it.isReadNextCandidate() }
            .minWithOrNull(
                compareBy<Book> { it.series?.metadataKey().orEmpty() }
                    .thenBy { it.seriesNumber?.toDoubleOrNull() ?: Double.MAX_VALUE }
                    .thenBy { it.title.metadataKey() },
            )
    } else {
        null
    }
    return listOfNotNull(nextInSeries ?: fallbackByAuthor)
}

@Composable
internal fun ReadNextShelf(
    books: List<Book>,
    isSuggestion: Boolean,
    downloadingBookId: Long?,
    downloadProgress: Float?,
    onBookClick: (Book) -> Unit,
    onRemove: (Book) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (isSuggestion) R.string.library_read_next_suggestions_title else R.string.library_read_next_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(if (isSuggestion) R.string.library_read_next_suggestions_subtitle else R.string.library_read_next_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isSuggestion) {
                TextButton(onClick = onViewAll) {
                    Text(stringResource(R.string.library_read_next_view_all))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            books.forEach { book ->
                ReadNextBookCard(
                    book = book,
                    isSuggestion = isSuggestion,
                    isDownloading = book.id == downloadingBookId,
                    downloadProgress = if (book.id == downloadingBookId) downloadProgress else null,
                    onClick = { onBookClick(book) },
                    onRemove = { onRemove(book) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReadNextBookCard(
    book: Book,
    isSuggestion: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Surface(
        modifier = modifier
            .clickable(enabled = !isDownloading, onClick = onClick),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.none,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                BookCover(
                    book = book,
                    modifier = Modifier
                        .bookSharedElement(book.id, BookOpenTransitionSource.READ_NEXT_COVER)
                        .width(Sizes.coverWidthMin * 0.48f)
                        .aspectRatio(Sizes.coverAspectRatio)
                        .clip(RoundedCornerShape(Radii.small)),
                )
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress ?: 0f },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(Sizes.iconLarge),
                        strokeWidth = Spacing.xs,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author.orEmpty().ifBlank { stringResource(R.string.library_group_unknown_author) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                book.seriesDisplayOrNone().takeIf { it.isNotBlank() }?.let { series ->
                    Text(
                        text = series,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!isSuggestion) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.library_read_next_remove),
                        modifier = Modifier.size(Sizes.iconSmall),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReadNextSeriesBreakDialog(
    warning: ReadNextSeriesBreakWarning,
    onDismissRequest: () -> Unit,
    onFollowCurrentSeries: () -> Unit,
    onConfirm: () -> Unit,
) {
    ExpressiveDialogSurface(onDismissRequest = onDismissRequest, animateContentSize = false) {
        Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = Elevations.level1,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                contentDescription = null,
                modifier = Modifier
                    .padding(Spacing.md)
                    .size(Sizes.iconLarge),
            )
        }
        Text(
            text = stringResource(R.string.library_read_next_series_break_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.library_read_next_series_break_body,
                warning.currentBook.title,
                warning.currentBook.seriesNumber.orEmpty(),
                warning.currentBook.series.orEmpty(),
                warning.nextBook.title,
                warning.queuedBook.title,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onFollowCurrentSeries,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            shape = Radii.buttonShape,
        ) {
            Text(stringResource(R.string.library_read_next_series_break_follow), maxLines = 1)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f),
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.library_edit_metadata_cancel), maxLines = 1)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                shape = Radii.buttonShape,
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text(stringResource(R.string.library_read_next_series_break_confirm), maxLines = 1)
            }
        }
    }
}

private fun Book.isReadNextCandidate(): Boolean = format != BookFormat.PHYSICAL && !isFinished()

/** The lowest-numbered unread book of [seriesKey] after [afterNumber] (any number when null), excluding [current]. */
private fun List<Book>.nextInSeries(current: Book, seriesKey: String, afterNumber: Double?): Book? = asSequence()
    .filter { it.id != current.id }
    .filter { it.series?.metadataKey() == seriesKey }
    .filter { it.isReadNextCandidate() }
    .mapNotNull { book -> book.seriesNumber?.toDoubleOrNull()?.let { number -> number to book } }
    .filter { (number, _) -> afterNumber == null || number > afterNumber }
    .minWithOrNull(compareBy<Pair<Double, Book>> { it.first }.thenBy { it.second.title.metadataKey() })
    ?.second

internal fun List<Book>.readNextSeriesBreakWarningFor(queuedBook: Book): ReadNextSeriesBreakWarning? {
    val current = asSequence()
        .filter { it.id != queuedBook.id }
        .filter { it.hasStartedReading() && it.finishedReadingAt == null }
        .filter { !it.series.isNullOrBlank() && !it.seriesNumber.isNullOrBlank() }
        .maxByOrNull { it.lastReadAt ?: it.startedReadingAt ?: 0L }
        ?: return null
    val currentSeriesKey = current.series.orEmpty().metadataKey()
    if (queuedBook.series?.metadataKey() == currentSeriesKey) return null
    val currentNumber = current.seriesNumber?.toDoubleOrNull() ?: return null
    val nextBook = nextInSeries(current, currentSeriesKey, afterNumber = currentNumber) ?: return null
    return ReadNextSeriesBreakWarning(
        currentBook = current,
        nextBook = nextBook,
        queuedBook = queuedBook,
    )
}
