package com.vayana.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@Composable
fun LibraryRoute(onBookClick: (Long) -> Unit, onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val books by viewModel.books.collectAsState()
    val importSummary by viewModel.importSummary.collectAsState()

    LibraryScreen(
        modifier = modifier,
        books = books,
        importSummary = importSummary,
        onImportSummaryShown = viewModel::onImportSummaryShown,
        onImportFiles = viewModel::importFiles,
        onImportFolder = viewModel::importFolder,
        onBookClick = onBookClick,
        onSettingsClick = onSettingsClick,
    )
}

@Composable
private fun LibraryScreen(
    modifier: Modifier = Modifier,
    books: List<Book>,
    importSummary: ImportSummary?,
    onImportSummaryShown: () -> Unit,
    onImportFiles: (android.content.ContentResolver, List<Uri>) -> Unit,
    onImportFolder: (android.content.ContentResolver, Uri) -> Unit,
    onBookClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importSummaryMessage = importSummary?.let { summary ->
        stringResource(
            R.string.library_import_summary,
            summary.imported,
            summary.duplicates,
            summary.unsupported,
            summary.failed,
        )
    }

    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImportFiles(context.contentResolver, uris)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onImportFolder(context.contentResolver, uri)
    }

    LaunchedEffect(importSummaryMessage) {
        val message = importSummaryMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onImportSummaryShown()
    }

    Scaffold(
        modifier = modifier,
        topBar = { LibraryTopBar(onSettingsClick = onSettingsClick) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            LibraryAddFab(
                // "*/*" (not just epub+zip): the picker should also show .mobi/.azw3/etc. so the
                // user can select them and get an "unsupported yet" result, rather than not
                // seeing them at all — SAF mime filtering can't distinguish by file extension.
                onImportFiles = { filesPicker.launch(arrayOf("*/*")) },
                onImportFolder = { folderPicker.launch(null) },
            )
        },
    ) { innerPadding ->
        if (books.isEmpty()) {
            LibraryEmptyState(contentPadding = innerPadding)
        } else {
            LibraryGrid(books = books, contentPadding = innerPadding, onBookClick = onBookClick)
        }
    }
}

@Composable
private fun LibraryAddFab(onImportFiles: () -> Unit, onImportFolder: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column {
        FloatingActionButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Outlined.LibraryAdd,
                contentDescription = stringResource(R.string.library_add_content_description),
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_import_files)) },
                leadingIcon = { Icon(Icons.Outlined.AutoStories, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onImportFiles()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_import_folder)) },
                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onImportFolder()
                },
            )
        }
    }
}

@Composable
private fun LibraryTopBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { /* search — wired in a later milestone */ }) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.library_search_content_description),
                    modifier = Modifier.size(Sizes.icon),
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.library_settings_content_description),
                    modifier = Modifier.size(Sizes.icon),
                )
            }
        }
    }
}

@Composable
private fun LibraryGrid(books: List<Book>, contentPadding: PaddingValues, onBookClick: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = Sizes.coverWidthMin),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            end = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            bottom = contentPadding.calculateBottomPadding() + Spacing.md,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        items(books, key = { it.id }) { book -> BookCoverCell(book, onClick = { onBookClick(book.id) }) }
    }
}

@Composable
private fun BookCoverCell(book: Book, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        if (book.coverPath != null) {
            AsyncImage(
                model = java.io.File(book.coverPath),
                contentDescription = stringResource(R.string.library_book_cover_content_description, book.title),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(Sizes.coverAspectRatio)
                    .clip(RoundedCornerShape(Radii.small)),
                contentScale = ContentScale.Crop,
            )
        } else {
            GeneratedCover(title = book.title, author = book.author)
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** Deterministic color hashed from the title, per PROMPT2appbuild.md §4.1's generated-cover spec. */
@Composable
private fun GeneratedCover(title: String, author: String?) {
    val hue = title.hashCode().mod(360).toFloat()
    val background = androidx.compose.ui.graphics.Color.hsv(hue, GeneratedCoverSaturation, GeneratedCoverValue)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(Sizes.coverAspectRatio)
            .background(background, RoundedCornerShape(Radii.small))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (author != null) {
            Text(
                text = author,
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = GeneratedCoverAuthorAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val GeneratedCoverSaturation = 0.45f
private const val GeneratedCoverValue = 0.55f
private const val GeneratedCoverAuthorAlpha = 0.8f

@Composable
private fun LibraryEmptyState(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoStories,
            contentDescription = null,
            modifier = Modifier.size(Sizes.iconLarge),
        )
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}
