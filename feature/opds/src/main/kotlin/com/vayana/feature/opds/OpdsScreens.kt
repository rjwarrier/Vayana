package com.vayana.feature.opds

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.core.designsystem.component.VayanaLoadingIndicator
import com.vayana.core.designsystem.component.morphingButtonShapes
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import kotlinx.coroutines.launch

/** The catalogues the reader added: tap one to browse it, add, edit or remove them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsCatalogsRoute(
    onBack: () -> Unit,
    onOpenCatalog: (String) -> Unit,
    viewModel: OpdsCatalogsViewModel = hiltViewModel(),
) {
    val catalogs by viewModel.catalogs.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EditorTarget?>(null) }
    var removing by remember { mutableStateOf<OpdsCatalog?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.opds_title)) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.opds_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = EditorTarget.New },
                modifier = Modifier.padding(bottom = LocalFloatingNavigationInset.current),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.opds_add))
            }
        },
    ) { padding ->
        if (catalogs.isEmpty()) {
            EmptyCatalogs(contentPadding = padding)
        } else {
            PagedLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Paddings.screenHorizontal,
                    end = Paddings.screenHorizontal,
                    top = padding.calculateTopPadding() + Spacing.sm,
                    bottom = padding.calculateBottomPadding() + Spacing.xl + LocalFloatingNavigationInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(catalogs, key = { it.id }) { catalog ->
                    CatalogRow(
                        catalog = catalog,
                        onOpen = { onOpenCatalog(catalog.id) },
                        onEdit = { editing = EditorTarget.Existing(catalog) },
                        onDelete = { removing = catalog },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
    editing?.let { target ->
        val existingId = (target as? EditorTarget.Existing)?.catalog?.id
        CatalogEditor(
            existing = (target as? EditorTarget.Existing)?.catalog,
            onDismiss = { editing = null },
            onCheck = { url, username, password -> viewModel.check(existingId, url, username, password) },
            onSave = { name, url, username, password ->
                val saved = viewModel.save(existingId, name, url, username, password)
                if (saved) editing = null
                saved
            },
        )
    }
    removing?.let { catalog ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.opds_remove_title, catalog.name)) },
            text = { Text(stringResource(R.string.opds_remove_body)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(catalog.id)
                    removing = null
                }) { Text(stringResource(R.string.opds_remove)) }
            },
            dismissButton = {
                FilledTonalButton(onClick = { removing = null }) { Text(stringResource(R.string.opds_cancel)) }
            },
        )
    }
}

private sealed interface EditorTarget {
    data object New : EditorTarget
    data class Existing(val catalog: OpdsCatalog) : EditorTarget
}

@Composable
private fun CatalogRow(
    catalog: OpdsCatalog,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.largeIncreased))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(Radii.largeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Paddings.card, top = Spacing.md, bottom = Spacing.md, end = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Outlined.CloudDownload)
            Column(modifier = Modifier.weight(1f)) {
                Text(catalog.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    catalog.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (catalog.hasLogin) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.opds_has_login),
                    modifier = Modifier.padding(horizontal = Spacing.xs).size(Sizes.iconSmall),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.opds_edit)) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.opds_remove)) }
        }
    }
}

/** The rounded primary-container tile that leads a row, as on the shelves list. */
@Composable
private fun IconTile(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(Spacing.md))
    }
}

@Composable
private fun EmptyCatalogs(contentPadding: PaddingValues) {
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
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                modifier = Modifier.padding(Spacing.lg).size(Sizes.iconLarge),
            )
        }
        Text(
            text = stringResource(R.string.opds_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = stringResource(R.string.opds_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun CatalogEditor(
    existing: OpdsCatalog?,
    onDismiss: () -> Unit,
    onCheck: suspend (url: String, username: String, password: String) -> OpdsCheck,
    onSave: (name: String, url: String, username: String, password: String) -> Boolean,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    val initial = remember { existing?.url?.let(::parseAddress) ?: OpdsAddress() }
    var host by rememberSaveable { mutableStateOf(initial.host) }
    var port by rememberSaveable { mutableStateOf(initial.port) }
    var secure by rememberSaveable { mutableStateOf(initial.secure) }
    var path by rememberSaveable { mutableStateOf(initial.path) }
    // A local address (192.168.x.x, a .local name) is nearly always plain HTTP: follow it until the reader chooses.
    var secureChosen by rememberSaveable { mutableStateOf(existing != null) }
    val url = buildAddress(OpdsAddress(host, port, secure, path)).orEmpty()
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    // The server is asked before saving, so a typo or a wrong login shows while the editor is still open.
    var checking by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<OpdsFailure?>(null) }
    val scope = rememberCoroutineScope()
    val clearLogin = username.isNotBlank() && !secure
    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        title = { Text(stringResource(if (existing == null) R.string.opds_add else R.string.opds_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !checking,
                    label = { Text(stringResource(R.string.opds_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { typed ->
                            invalid = false
                            failure = null
                            if ("/" in typed || ":" in typed) {
                                // A whole address pasted in: spread it over the fields.
                                val pasted = parseAddress(typed)
                                host = pasted.host
                                if ("://" in typed || pasted.port.isNotEmpty()) port = pasted.port
                                if ("://" in typed) {
                                    secure = pasted.secure
                                    secureChosen = true
                                }
                                if (pasted.path.isNotEmpty()) path = pasted.path
                            } else {
                                host = typed
                                if (!secureChosen) secure = !looksLikeLocalServer(typed)
                            }
                        },
                        enabled = !checking,
                        label = { Text(stringResource(R.string.opds_host_label)) },
                        placeholder = { Text(stringResource(R.string.opds_host_hint)) },
                        isError = invalid && buildAddress(OpdsAddress(host, "", secure, "")) == null,
                        supportingText = {
                            if (invalid && buildAddress(OpdsAddress(host, "", secure, "")) == null) {
                                Text(stringResource(R.string.opds_host_invalid))
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(HostWeight),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = {
                            port = it.filter(Char::isDigit).take(MaxPortDigits)
                            invalid = false
                            failure = null
                        },
                        enabled = !checking,
                        label = { Text(stringResource(R.string.opds_port_label)) },
                        placeholder = { Text(stringResource(if (secure) R.string.opds_port_hint_secure else R.string.opds_port_hint)) },
                        isError = invalid && port.isNotEmpty() && buildAddress(OpdsAddress("x", port, secure, "")) == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(PortWeight),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.opds_https_label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = secure,
                        onCheckedChange = {
                            secure = it
                            secureChosen = true
                            failure = null
                        },
                        enabled = !checking,
                    )
                }
                OutlinedTextField(
                    value = path,
                    onValueChange = {
                        path = it
                        failure = null
                    },
                    enabled = !checking,
                    label = { Text(stringResource(R.string.opds_path_label)) },
                    placeholder = { Text(DefaultOpdsPath) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        failure = null
                    },
                    enabled = !checking,
                    label = { Text(stringResource(R.string.opds_username_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        failure = null
                    },
                    enabled = !checking,
                    label = { Text(stringResource(R.string.opds_password_label)) },
                    supportingText = {
                        if (existing?.hasLogin == true) Text(stringResource(R.string.opds_password_keep))
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (checking) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        VayanaLoadingIndicator(modifier = Modifier.size(Sizes.icon))
                        Text(stringResource(R.string.opds_checking), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                failure?.let {
                    Text(
                        stringResource(it.messageRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (clearLogin) {
                    Text(
                        stringResource(R.string.opds_http_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !checking,
                onClick = {
                    when {
                        url.isEmpty() -> invalid = true
                        // Already told it doesn't answer: the second tap keeps it anyway (a server that is off for now).
                        failure != null -> invalid = !onSave(name, url, username, password)
                        else -> {
                            checking = true
                            scope.launch {
                                when (val result = onCheck(url, username, password)) {
                                    is OpdsCheck.Ok ->
                                        // The working address (a bare server gains its /opds) and the catalogue's own name.
                                        invalid = !onSave(name.ifBlank { result.title }, result.url, username, password)
                                    is OpdsCheck.Failed -> failure = result.failure
                                }
                                checking = false
                            }
                        }
                    }
                },
            ) {
                Text(stringResource(if (failure != null) R.string.opds_save_anyway else R.string.opds_save))
            }
        },
        dismissButton = {
            FilledTonalButton(enabled = !checking, onClick = onDismiss) { Text(stringResource(R.string.opds_cancel)) }
        },
    )
}

/**
 * Browse one catalogue: folders lead to other feeds, books open a sheet to download an EPUB or PDF through the normal
 * import. [onImported] returns to the library, which shows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsBrowseRoute(
    onBack: () -> Unit,
    onImported: () -> Unit,
    viewModel: OpdsBrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.imported.collect { onImported() } }
    BackHandler(enabled = state.canGoUp) { viewModel.up() }
    val listState = rememberLazyListState()
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LoadMoreThreshold
        }
    }
    val loadMore by rememberUpdatedState(viewModel::loadMore)
    LaunchedEffect(nearEnd, state.hasNext) { if (nearEnd && state.hasNext) loadMore() }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.up()) onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.opds_back))
                    }
                },
            )
        },
    ) { padding ->
        PagedLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Paddings.screenHorizontal,
                end = Paddings.screenHorizontal,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.xl + LocalFloatingNavigationInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (state.canSearch) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.opds_search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.opds_clear_search), modifier = Modifier.size(Sizes.iconSmall))
                                }
                            }
                        },
                        isError = state.searchFailed,
                        supportingText = { if (state.searchFailed) Text(stringResource(R.string.opds_search_failed)) },
                        shape = RoundedCornerShape(Radii.full),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focus.clearFocus()
                            viewModel.search(query)
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            when {
                state.loading -> item { Loading(R.string.opds_loading) }
                state.failure != null -> item {
                    Message(
                        text = stringResource(state.failure!!.messageRes()),
                        actionLabel = stringResource(R.string.opds_retry),
                        onAction = viewModel::retry,
                    )
                }
                state.entries.isEmpty() -> item { Message(stringResource(R.string.opds_empty_folder)) }
                else -> {
                    items(state.entries, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            cachedCover = viewModel::cachedCover,
                            loadCover = viewModel::cover,
                            onClick = { if (entry.isBook) viewModel.select(entry) else viewModel.openEntry(entry) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (state.loadingMore) item { Loading(R.string.opds_loading_more) }
                }
            }
        }
    }
    selection?.let { current ->
        BookSheet(
            selection = current,
            cachedCover = viewModel::cachedCover,
            loadCover = viewModel::cover,
            onDownload = viewModel::download,
            onCancel = viewModel::cancelDownload,
            onClose = viewModel::close,
        )
    }
}

@Composable
private fun EntryRow(
    entry: OpdsEntry,
    cachedCover: (String) -> ImageBitmap?,
    loadCover: suspend (String) -> ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(if (entry.isBook) Radii.large else Radii.largeIncreased)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
        modifier = modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(if (entry.isBook) PaddingValues(Spacing.sm) else PaddingValues(Paddings.card)),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.isBook) {
                Cover(entry.thumbnailUrl, entry.title, cachedCover, loadCover, Modifier.width(Sizes.coverWidthMin))
            } else {
                IconTile(Icons.Outlined.Folder)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (entry.isBook) {
                    Text(
                        entry.acquisitions.joinToString(" · ") { it.format.name },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    entry.summary?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Cover(
    url: String?,
    title: String,
    cachedCover: (String) -> ImageBitmap?,
    loadCover: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState(url?.let(cachedCover), url) { if (value == null && url != null) value = loadCover(url) }
    Surface(
        modifier = modifier.aspectRatio(Sizes.coverAspectRatio),
        shape = Radii.coverShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        val image = bitmap
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookSheet(
    selection: OpdsSelection,
    cachedCover: (String) -> ImageBitmap?,
    loadCover: suspend (String) -> ImageBitmap?,
    onDownload: (OpdsAcquisition) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val entry = selection.entry
    val download = selection.download
    // While a book downloads, only its Cancel button stops it: a stray swipe or tap outside doesn't.
    val downloading by rememberUpdatedState(download != null && !download.failed)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !downloading },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!downloading) onClose() },
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !downloading),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Paddings.screenHorizontal)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Cover(entry.coverUrl, entry.title, cachedCover, loadCover, Modifier.width(Sizes.coverWidthDetail))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(entry.title, style = MaterialTheme.typography.titleLarge)
                    entry.author?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    entry.language?.let {
                        Text(
                            stringResource(R.string.opds_language, it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            entry.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (download != null && !download.failed) {
                val progress = download.progress
                Text(
                    text = if (progress == null) {
                        stringResource(R.string.opds_downloading)
                    } else {
                        stringResource(R.string.opds_downloading_percent, (progress * 100).toInt())
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (progress == null) {
                    VayanaLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    VayanaLinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.opds_cancel)) }
            } else {
                if (download?.failed == true) {
                    Text(
                        stringResource(R.string.opds_download_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                entry.acquisitions.forEachIndexed { index, acquisition ->
                    val label = acquisition.sizeBytes
                        ?.let { stringResource(R.string.opds_download_format, acquisition.format.name, Formatter.formatShortFileSize(context, it)) }
                        ?: stringResource(R.string.opds_download_format_unknown, acquisition.format.name)
                    if (index == 0) {
                        FilledTonalButton(onClick = { onDownload(acquisition) }, shapes = morphingButtonShapes(), modifier = Modifier.fillMaxWidth()) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(onClick = { onDownload(acquisition) }, shapes = morphingButtonShapes(), modifier = Modifier.fillMaxWidth()) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading(textRes: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        VayanaLoadingIndicator(modifier = Modifier.size(Sizes.badge))
        Text(stringResource(textRes), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Message(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

private fun OpdsFailure.messageRes(): Int = when (this) {
    OpdsFailure.UNAUTHORIZED -> R.string.opds_failed_unauthorized
    OpdsFailure.NOT_A_CATALOGUE -> R.string.opds_failed_not_catalogue
    OpdsFailure.UNREACHABLE -> R.string.opds_failed_unreachable
}

private const val LoadMoreThreshold = 6
private const val HostWeight = 0.68f
private const val PortWeight = 0.32f
private const val MaxPortDigits = 5
