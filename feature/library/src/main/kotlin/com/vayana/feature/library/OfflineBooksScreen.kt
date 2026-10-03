package com.vayana.feature.library

import androidx.compose.foundation.clickable
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.remember
import com.vayana.core.database.model.PhysicalBookOwnership
import com.vayana.core.designsystem.theme.PagedLazyVerticalGrid
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
import androidx.compose.runtime.LaunchedEffect
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
import com.vayana.core.homelibrary.HomeLibraryStatus
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
    /** The floating bar's add button was pressed; the screen opens its add dialog, then calls [onAddBookRequestHandled]. */
    addBookRequested: Boolean,
    onAddBookRequestHandled: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val books by viewModel.offlineBooks.collectAsStateWithLifecycle()
    val homeLibraryStatus by viewModel.homeLibraryStatus.collectAsStateWithLifecycle()
    OfflineBooksScreen(
        modifier = modifier,
        books = books,
        // Only worth a word once Home Library books are here: someone who never installed it is not told about it.
        addBookRequested = addBookRequested,
        onAddBookRequestHandled = onAddBookRequestHandled,
        homeLibraryNotConnected = homeLibraryStatus == HomeLibraryStatus.NotConnected && books.any { it.isHomeLibrary },
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
    homeLibraryNotConnected: Boolean,
    addBookRequested: Boolean,
    onAddBookRequestHandled: () -> Unit,
    onBack: () -> Unit,
    onBookClick: (Long) -> Unit,
    onAddBook: (OfflineBookDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(LibraryViewMode.LIST) }
    var category by rememberSaveable { mutableStateOf(OfflineCategory.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var fromShelves by rememberSaveable { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf<String?>(null) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    val entries = remember(books) { homeLibraryBrowseEntries(books) }
    val hasHomeLibrary = remember(books) { books.any { it.isHomeLibrary } }
    val shelfEntries = remember(entries) {
        val emptyQuery = HomeLibraryBrowseQuery("")
        entries.filter { it.matches(emptyQuery, true, null, null) }
    }
    val languages = remember(shelfEntries) { shelfEntries.mapNotNull { it.language }.distinct().sorted() }
    val genres = remember(shelfEntries) { shelfEntries.flatMap { it.genres }.distinct().sorted() }
    val activeLanguage = validHomeLibraryLanguageSelection(language, languages)
    val activeGenre = genre?.takeIf { it in genres }
    LaunchedEffect(activeLanguage, activeGenre, books.isNotEmpty()) {
        // Ignore the initial empty emission while the catalog is loading after state restoration.
        if (books.isNotEmpty()) {
            language = activeLanguage
            genre = activeGenre
        }
    }
    val preparedQuery = remember(query, hasHomeLibrary) { HomeLibraryBrowseQuery(query.takeIf { hasHomeLibrary }.orEmpty()) }
    val shownBooks = remember(entries, category, preparedQuery, fromShelves, activeLanguage, activeGenre, hasHomeLibrary) {
        entries.filter {
            category.includes(it.book) && it.matches(preparedQuery, fromShelves && hasHomeLibrary,
                activeLanguage.takeIf { fromShelves && hasHomeLibrary }, activeGenre.takeIf { fromShelves && hasHomeLibrary })
        }.map { it.book }
    }
    // With the floating bar, its attached add button asks for the dialog; the extended button below is for the other
    // navigation styles.
    val usesFloatingBar = LocalFloatingNavigationInset.current > Elevations.none
    LaunchedEffect(addBookRequested) {
        if (addBookRequested) {
            showAddDialog = true
            onAddBookRequestHandled()
        }
    }

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
                actions = {
                    IconButton(onClick = { viewMode = viewMode.toggled() }) {
                        Icon(
                            imageVector = viewMode.toggleIcon(),
                            contentDescription = stringResource(viewMode.toggleLabelRes()),
                        )
                    }
                },
                // The app shell already pads for the status bar, as on Settings and Help.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        floatingActionButton = {
            if (!usesFloatingBar) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.offline_books_add)) },
                )
            }
        },
    ) { innerPadding ->
        if (books.isEmpty()) {
            OfflineBooksEmptyState(contentPadding = innerPadding)
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                if (hasHomeLibrary) {
                    HomeLibraryBrowseControls(
                        query = query,
                        onQueryChange = { query = it },
                        fromShelves = fromShelves,
                        onFromShelvesChange = {
                            fromShelves = it
                            language = null
                            genre = null
                            if (it) category = OfflineCategory.OWN
                        },
                        languages = languages,
                        language = activeLanguage,
                        onLanguageChange = { language = it },
                        genres = genres,
                        genre = activeGenre,
                        onGenreChange = { genre = it },
                        modifier = Modifier.padding(horizontal = Paddings.screenHorizontal),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Paddings.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OfflineCategory.entries.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = {
                                category = option
                                if (option == OfflineCategory.BORROWED) {
                                    fromShelves = false
                                    language = null
                                    genre = null
                                }
                            },
                            label = { Text(stringResource(option.labelRes)) },
                        )
                    }
                }
                val bottomPadding = innerPadding.calculateBottomPadding() + Sizes.fab + Spacing.xl +
                    LocalFloatingNavigationInset.current
                when (viewMode) {
                    LibraryViewMode.LIST -> PagedLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Paddings.screenHorizontal,
                            end = Paddings.screenHorizontal,
                            top = Spacing.sm,
                            // Clear the extended FAB as well as the floating bar.
                            bottom = bottomPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        if (homeLibraryNotConnected) {
                            item(key = "home-library-not-connected") { HomeLibraryNotConnectedNote() }
                        }
                        if (shownBooks.isEmpty()) {
                            item(key = "no-matches") { Text(stringResource(R.string.home_library_browse_empty)) }
                        }
                        items(shownBooks, key = { it.id }) { book ->
                            OfflineBookRow(book = book, onClick = { onBookClick(book.id) })
                        }
                    }
                    LibraryViewMode.THUMBNAILS -> PagedLazyVerticalGrid(
                        showPageButtons = false,
                        columns = GridCells.Adaptive(minSize = Sizes.libraryGridCoverWidthMin),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Paddings.screenHorizontal,
                            end = Paddings.screenHorizontal,
                            top = Spacing.sm,
                            bottom = bottomPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        if (homeLibraryNotConnected) {
                            item(key = "home-library-not-connected", span = { GridItemSpan(maxLineSpan) }) {
                                HomeLibraryNotConnectedNote()
                            }
                        }
                        if (shownBooks.isEmpty()) {
                            item(key = "no-matches", span = { GridItemSpan(maxLineSpan) }) {
                                Text(stringResource(R.string.home_library_browse_empty))
                            }
                        }
                        gridItems(shownBooks, key = { it.id }) { book ->
                            OfflineBookCover(book = book, onClick = { onBookClick(book.id) })
                        }
                    }
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

/** The two kinds of offline book: ones you own (every audiobook and ebook, and owned paper books) and loans. */
private enum class OfflineCategory(@StringRes val labelRes: Int) {
    ALL(R.string.library_filter_all),
    OWN(R.string.offline_book_owned),
    BORROWED(R.string.offline_book_borrowed);

    fun includes(book: Book): Boolean = when (this) {
        ALL -> true
        OWN -> !book.isBorrowed()
        BORROWED -> book.isBorrowed()
    }
}

private fun Book.isBorrowed(): Boolean =
    format == BookFormat.PHYSICAL && physicalOwnership == PhysicalBookOwnership.BORROWED

/** A cover with its title and author underneath, for the thumbnail view. */
@Composable
private fun OfflineBookCover(book: Book, onClick: () -> Unit) {
    val displayTitle = remember(book.title, book.source, book.sourceMetadata) { book.homeLibraryDisplayTitle }
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        BookCover(book = book, modifier = Modifier.fillMaxWidth())
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.labelLarge,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        HomeLibraryCatalogTitle(book)
        HomeLibraryShelfLocation(book)
        book.author?.takeIf { it.isNotBlank() }?.let { author ->
            Text(
                text = author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One quiet line: the mirrored books stay as they were, and syncing resumes by itself once Home Library is back. */
@Composable
private fun HomeLibraryNotConnectedNote() {
    Text(
        text = stringResource(R.string.home_library_status_not_connected),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.xs),
    )
}

@Composable
private fun OfflineBookRow(book: Book, onClick: () -> Unit) {
    val displayTitle = remember(book.title, book.source, book.sourceMetadata) { book.homeLibraryDisplayTitle }
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
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                HomeLibraryCatalogTitle(book)
                HomeLibraryShelfLocation(book)
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
