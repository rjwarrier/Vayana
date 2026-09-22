package com.vayana.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.theme.PagedLazyVerticalGrid
import com.vayana.core.designsystem.theme.vayanaPressScale
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@Composable
internal fun SeriesFolderCell(folder: SeriesLibraryItem.Folder, onClick: () -> Unit) {
    val description = stringResource(R.string.library_series_folder_description, folder.title, folder.books.size)
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = Modifier.vayanaPressScale(interactionSource).clickable(interactionSource = interactionSource, indication = null, onClick = onClick).semantics { contentDescription = description }) {
        SeriesCoverStack(folder.books, Modifier.fillMaxWidth())
        Text(
            text = folder.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.library_series_folder_count, folder.books.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SeriesFolderListRow(folder: SeriesLibraryItem.Folder, onClick: () -> Unit) {
    val description = stringResource(R.string.library_series_folder_description, folder.title, folder.books.size)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth().vayanaPressScale(interactionSource).clickable(interactionSource = interactionSource, indication = null, onClick = onClick).semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SeriesCoverStack(folder.books, Modifier.size(Sizes.coverWidthMin))
        Column {
            Text(folder.title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.library_series_folder_count, folder.books.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeriesCoverStack(books: List<Book>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.aspectRatio(Sizes.coverAspectRatio)) {
        books.take(3).asReversed().forEachIndexed { index, book ->
            val inset = (2 - index) * 3
            Surface(
                modifier = Modifier.fillMaxWidth().padding(start = inset.dp, top = inset.dp, end = (6 - inset).dp, bottom = (6 - inset).dp),
                shadowElevation = (index + 1).dp,
            ) {
                BookCover(book, modifier = Modifier.fillMaxWidth())
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xs),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.inverseSurface,
        ) {
            Text(
                text = books.size.toString(),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
fun SeriesFolderScreenRoute(
    seriesKey: String,
    onBack: () -> Unit,
    onBookClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val libraryBooks by viewModel.libraryBooks.collectAsState()
    val books = libraryBooks.filter { it.series?.metadataKey() == seriesKey }.sortedForSeries()
    val title = books.firstOrNull()?.series?.trim().orEmpty()

    SeriesFolderScreen(title, books, onBack, onBookClick, modifier)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SeriesFolderScreen(
    title: String,
    books: List<Book>,
    onBack: () -> Unit,
    onBookClick: (Long) -> Unit,
    modifier: Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                // The app root already applies the status-bar inset to this destination.
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.notes_back_content_description))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (books.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.library_series_folder_empty))
            }
        } else {
            PagedLazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = Sizes.coverWidthMin),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Paddings.screenHorizontal,
                    end = Paddings.screenHorizontal,
                    top = innerPadding.calculateTopPadding() + Spacing.sm,
                    bottom = innerPadding.calculateBottomPadding() + Spacing.xl + LocalFloatingNavigationInset.current,
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.library_series_folder_count, books.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(books, key = { it.id }) { book ->
                    val interactionSource = remember { MutableInteractionSource() }
                    Column(modifier = Modifier.vayanaPressScale(interactionSource).clickable(interactionSource = interactionSource, indication = null) { onBookClick(book.id) }) {
                        BookCover(book, modifier = Modifier.fillMaxWidth())
                        book.seriesNumber?.takeIf { it.isNotBlank() }?.let { number ->
                            Text(
                                text = stringResource(R.string.library_series_number_value, number),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                        Text(
                            book.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}
