package com.vayana.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import kotlin.math.roundToInt

/**
 * Paper books, audiobooks and ebooks read outside the app. They are ordinary library rows (so they count towards the yearly
 * goal, sync, and open in Book details) but stay off the Books screen, which lists what can be read here.
 */
@Composable
fun OfflineBooksRoute(
    onBack: () -> Unit,
    onBookClick: (Long) -> Unit,
    /** Opens the new book's details straight into the Goodreads picker. */
    onFetchGoodreads: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val books by viewModel.offlineBooks.collectAsStateWithLifecycle()
    OfflineBooksScreen(
        modifier = modifier,
        books = books,
        onBack = onBack,
        onBookClick = onBookClick,
        onAddBook = { draft ->
            viewModel.addOfflineBook(draft) { bookId -> if (draft.fetchGoodreads) onFetchGoodreads(bookId) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineBooksScreen(
    books: List<Book>,
    onBack: () -> Unit,
    onBookClick: (Long) -> Unit,
    onAddBook: (OfflineBookDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline_books_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.notes_back_content_description),
                        )
                    }
                },
                // The app shell already pads for the status bar, as on Settings and Help.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.offline_books_add)) },
                modifier = Modifier.padding(bottom = LocalFloatingNavigationInset.current),
            )
        },
    ) { innerPadding ->
        if (books.isEmpty()) {
            OfflineBooksEmptyState(contentPadding = innerPadding)
        } else {
            PagedLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Paddings.screenHorizontal,
                    end = Paddings.screenHorizontal,
                    top = innerPadding.calculateTopPadding() + Spacing.sm,
                    // Clear the extended FAB as well as the floating bar.
                    bottom = innerPadding.calculateBottomPadding() + Sizes.fab + Spacing.xl +
                        LocalFloatingNavigationInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(books, key = { it.id }) { book ->
                    OfflineBookRow(book = book, onClick = { onBookClick(book.id) })
                }
            }
        }
    }

    if (showAddDialog) {
        AddOfflineBookDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { draft ->
                showAddDialog = false
                onAddBook(draft)
            },
        )
    }
}

@Composable
private fun OfflineBookRow(book: Book, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.largeIncreased))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radii.largeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Paddings.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .width(Sizes.coverWidthMin)
                    .aspectRatio(Sizes.coverAspectRatio),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OfflineFormatChip(book.format)
                    Text(
                        text = book.offlineStatusText(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineFormatChip(format: BookFormat) {
    Surface(
        shape = RoundedCornerShape(Radii.full),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Paddings.badgeHorizontal, vertical = Paddings.badgeVertical),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(format.offlineIcon(), contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
            Text(text = format.displayLabel(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Book.offlineStatusText(): String {
    finishedReadingAt?.let { return stringResource(R.string.offline_book_finished_on, it.formatDate()) }
    val page = currentPage()
    val total = pageCount
    if (page != null && total != null && page > 0) return stringResource(R.string.offline_book_page_of, page, total)
    startedReadingAt?.let { return stringResource(R.string.offline_book_started_on, it.formatDate()) }
    return stringResource(R.string.library_status_not_started)
}

/** The page an offline book is on, from its progress through [Book.pageCount]; null while the page count is unknown. */
internal fun Book.currentPage(): Int? = pageCount?.let { (readingPercent * it).roundToInt() }

@Composable
private fun OfflineBooksEmptyState(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(Radii.extraLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Row(modifier = Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(Sizes.iconLarge))
                Icon(Icons.Outlined.Headphones, contentDescription = null, modifier = Modifier.size(Sizes.iconLarge))
                Icon(Icons.Outlined.TabletAndroid, contentDescription = null, modifier = Modifier.size(Sizes.iconLarge))
            }
        }
        Text(
            text = stringResource(R.string.offline_books_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = stringResource(R.string.offline_books_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** How a book's format reads in the UI: the file type for books read here, Physical or Audiobook otherwise. */
@Composable
internal fun BookFormat.displayLabel(): String = when (this) {
    BookFormat.PHYSICAL -> stringResource(R.string.offline_book_type_physical)
    BookFormat.AUDIOBOOK -> stringResource(R.string.offline_book_type_audiobook)
    BookFormat.OTHER_EBOOK -> stringResource(R.string.offline_book_type_other_ebook)
    else -> name
}

internal fun BookFormat.offlineIcon(): ImageVector = when (this) {
    BookFormat.AUDIOBOOK -> Icons.Outlined.Headphones
    BookFormat.OTHER_EBOOK -> Icons.Outlined.TabletAndroid
    else -> Icons.AutoMirrored.Outlined.MenuBook
}
