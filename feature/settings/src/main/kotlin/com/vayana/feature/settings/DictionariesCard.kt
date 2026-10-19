package com.vayana.feature.settings

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.designsystem.component.VayanaLoadingIndicator
import com.vayana.core.designsystem.component.asString
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import com.vayana.core.resources.UiText
import com.vayana.core.resources.uiText
import com.vayana.dictionary.api.DictionaryKind
import com.vayana.dictionary.api.DictionaryPackState
import com.vayana.dictionary.api.DictionaryRepository
import com.vayana.dictionary.api.DictionaryStatus
import com.vayana.dictionary.api.FreeDictOffer
import com.vayana.dictionary.api.languageDisplayName
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface CatalogState {
    data object Loading : CatalogState
    data class Loaded(val offers: List<FreeDictOffer>) : CatalogState
    data class Failed(val message: UiText) : CatalogState
}

@HiltViewModel
internal class DictionariesViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {
    val dictionaries: StateFlow<List<DictionaryStatus>> = dictionaryRepository.dictionaries

    private val _errors = MutableStateFlow<Map<String, UiText>>(emptyMap())
    val errors: StateFlow<Map<String, UiText>> = _errors

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalog: StateFlow<CatalogState> = _catalog

    fun installGcide() = run("gcide") { dictionaryRepository.installGcide() }
    fun installOlam() = run("olam") { dictionaryRepository.installOlam() }
    fun installWordNet(uri: String) = run("wordnet") { dictionaryRepository.installEnglish(uri) }
    fun installFreeDict(offer: FreeDictOffer) = run("freedict-${offer.name}") { dictionaryRepository.installFreeDict(offer) }
    fun importStarDict(uri: String, wordLanguage: String, meaningLanguage: String) =
        run(ImportErrorKey) { dictionaryRepository.importStarDict(uri, wordLanguage, meaningLanguage) }
    fun remove(id: String) = run(id) { dictionaryRepository.remove(id) }
    fun moveUp(id: String) = dictionaryRepository.moveUp(id)

    fun loadCatalog() {
        if (_catalog.value is CatalogState.Loaded) return
        _catalog.value = CatalogState.Loading
        applicationScope.launch {
            _catalog.value = runCatchingCancellable { dictionaryRepository.freeDictCatalog() }.fold(
                onSuccess = { CatalogState.Loaded(it) },
                onFailure = { CatalogState.Failed(it.uiText(R.string.dictionary_error_download_failed)) },
            )
        }
    }

    private fun run(key: String, action: suspend () -> Unit) {
        _errors.update { it - key }
        // A minute-long install keeps going when Settings is left; the card shows its progress on return.
        applicationScope.launch {
            runCatchingCancellable { action() }.onFailure { failure ->
                _errors.update { it + (key to failure.uiText(R.string.dictionary_error_finish_install)) }
            }
        }
    }

    companion object {
        const val ImportErrorKey = "import"
    }
}

/** The offline dictionaries word lookups use, in the order they're tried, and where to get more. */
@Composable
internal fun DictionariesCard(modifier: Modifier = Modifier, viewModel: DictionariesViewModel = hiltViewModel()) {
    val dictionaries by viewModel.dictionaries.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }
    val wordNetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.installWordNet(uri.toString())
    }
    val starDictPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri = uri.toString()
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(Radii.large), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(Paddings.card), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column {
                Text(
                    text = stringResource(R.string.settings_dictionaries_title),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = stringResource(R.string.settings_dictionaries_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            dictionaries.forEachIndexed { index, status ->
                DictionaryRow(
                    status = status,
                    error = errors[status.id],
                    canMoveUp = index > 0,
                    onMoveUp = { viewModel.moveUp(status.id) },
                    onInstall = {
                        when (status.kind) {
                            DictionaryKind.GCIDE -> viewModel.installGcide()
                            DictionaryKind.OLAM -> viewModel.installOlam()
                            DictionaryKind.WORDNET -> wordNetPicker.launch(ArchiveTypes)
                            else -> Unit
                        }
                    },
                    onGetWordNet = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WordNetDownloadUrl))) } },
                    onRemove = { viewModel.remove(status.id) },
                )
            }
            errors[DictionariesViewModel.ImportErrorKey]?.let {
                Text(text = it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilledTonalButton(onClick = {
                    viewModel.loadCatalog()
                    showCatalog = true
                }) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                    Text(stringResource(R.string.dictionary_action_get_more))
                }
                OutlinedButton(onClick = { starDictPicker.launch(ArchiveTypes) }) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                    Text(stringResource(R.string.dictionary_action_import))
                }
            }
        }
    }
    if (showCatalog) {
        FreeDictCatalogDialog(
            catalog = catalog,
            installed = dictionaries,
            errors = errors,
            onRetry = { viewModel.loadCatalog() },
            onInstall = viewModel::installFreeDict,
            onDismiss = { showCatalog = false },
        )
    }
    importUri?.let { uri ->
        ImportLanguagesDialog(
            onImport = { word, meaning ->
                viewModel.importStarDict(uri, word, meaning)
                importUri = null
            },
            onDismiss = { importUri = null },
        )
    }
}

@Composable
private fun DictionaryRow(
    status: DictionaryStatus,
    error: UiText?,
    canMoveUp: Boolean,
    onMoveUp: () -> Unit,
    onInstall: () -> Unit,
    onGetWordNet: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val name = status.displayName()
    Surface(shape = RoundedCornerShape(Radii.medium), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = status.about(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canMoveUp) {
                    IconButton(onClick = onMoveUp) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.dictionary_action_move_up, name))
                    }
                }
                if (status.state == DictionaryPackState.Installed) {
                    FilledTonalIconButton(onClick = onRemove) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.learn_words_remove, name))
                    }
                }
            }
            val state = status.state
            Text(
                text = when (state) {
                    DictionaryPackState.Installed -> stringResource(R.string.dictionary_state_installed, Formatter.formatShortFileSize(context, status.sizeBytes))
                    DictionaryPackState.Installing -> stringResource(R.string.dictionary_state_installing, ((status.progress ?: 0f) * 100).toInt())
                    DictionaryPackState.Failed -> stringResource(R.string.dictionary_state_failed)
                    DictionaryPackState.NotInstalled -> stringResource(R.string.dictionary_state_not_installed)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (state == DictionaryPackState.Installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state == DictionaryPackState.Installing) {
                val progress = status.progress
                if (progress != null) VayanaLinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) else VayanaLoadingIndicator()
            }
            error?.let { Text(text = it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (state != DictionaryPackState.Installing && state != DictionaryPackState.Installed) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    when (status.kind) {
                        DictionaryKind.WORDNET -> {
                            OutlinedButton(onClick = onGetWordNet) { Text(stringResource(R.string.reader_dictionary_download)) }
                            FilledTonalButton(onClick = onInstall) { Text(stringResource(R.string.reader_dictionary_install_zip)) }
                        }
                        DictionaryKind.GCIDE, DictionaryKind.OLAM -> FilledTonalButton(onClick = onInstall) {
                            val bytes = if (status.kind == DictionaryKind.GCIDE) GcideDownloadBytes else OlamDownloadBytes
                            Text(stringResource(R.string.dictionary_action_download, Formatter.formatShortFileSize(context, bytes)))
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

/** FreeDict's dictionaries, the reader's own languages first, searchable by language name. */
@Composable
private fun FreeDictCatalogDialog(
    catalog: CatalogState,
    installed: List<DictionaryStatus>,
    errors: Map<String, UiText>,
    onRetry: () -> Unit,
    onInstall: (FreeDictOffer) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dictionary_catalog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.dictionary_catalog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.dictionary_catalog_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                when (catalog) {
                    CatalogState.Loading -> VayanaLoadingIndicator(modifier = Modifier.padding(Spacing.lg))
                    is CatalogState.Failed -> Column {
                        Text(catalog.message.asString(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.dictionary_catalog_retry)) }
                    }
                    is CatalogState.Loaded -> {
                        val mine = remember { setOf(Locale.getDefault().isO3Language, "eng") }
                        val offers = remember(catalog, query) {
                            catalog.offers
                                .map { offer -> offer to "${languageDisplayName(offer.wordLanguage)} ${languageDisplayName(offer.meaningLanguage)}" }
                                .filter { (_, names) -> query.isBlank() || names.contains(query.trim(), ignoreCase = true) }
                                .sortedWith(compareBy({ (offer, _) -> -(listOf(offer.wordLanguage, offer.meaningLanguage).count { it in mine }) }, { it.second }))
                                .map { it.first }
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = Sizes.contentMaxWidth), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            items(offers, key = { it.name }) { offer ->
                                val status = installed.firstOrNull { it.id == "freedict-${offer.name}" }
                                CatalogRow(offer, status, errors["freedict-${offer.name}"], onInstall = { onInstall(offer) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

@Composable
private fun CatalogRow(offer: FreeDictOffer, status: DictionaryStatus?, error: UiText?, onInstall: () -> Unit) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(Radii.medium), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dictionary_language_pair, languageDisplayName(offer.wordLanguage), languageDisplayName(offer.meaningLanguage)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.dictionary_catalog_details, offer.headwords, Formatter.formatShortFileSize(context, offer.sizeBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (status?.state) {
                    DictionaryPackState.Installed -> Icon(
                        Icons.Outlined.Check,
                        contentDescription = stringResource(R.string.dictionary_catalog_installed),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    DictionaryPackState.Installing -> Text(
                        stringResource(R.string.dictionary_state_installing, ((status.progress ?: 0f) * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    else -> FilledTonalIconButton(onClick = onInstall) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = stringResource(R.string.dictionary_action_download, Formatter.formatShortFileSize(context, offer.sizeBytes)),
                        )
                    }
                }
            }
            error?.let { Text(text = it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** A StarDict file doesn't say its languages: the reader picks the words' language and the meanings'. */
@Composable
private fun ImportLanguagesDialog(onImport: (String, String) -> Unit, onDismiss: () -> Unit) {
    var wordLanguage by rememberSaveable { mutableStateOf("eng") }
    var meaningLanguage by rememberSaveable { mutableStateOf(Locale.getDefault().isO3Language.ifBlank { "eng" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dictionary_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LanguagePicker(stringResource(R.string.dictionary_import_word_language), wordLanguage) { wordLanguage = it }
                LanguagePicker(stringResource(R.string.dictionary_import_meaning_language), meaningLanguage) { meaningLanguage = it }
            }
        },
        confirmButton = { TextButton(onClick = { onImport(wordLanguage, meaningLanguage) }) { Text(stringResource(R.string.dictionary_import_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(label: String, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(selected) { (ImportLanguages + selected).distinct().sortedBy { languageDisplayName(it) } }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = languageDisplayName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.exposedDropdownSize()) {
            options.forEach { code ->
                DropdownMenuItem(text = { Text(languageDisplayName(code)) }, onClick = {
                    onSelect(code)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun DictionaryStatus.displayName(): String = when (kind) {
    DictionaryKind.WORDNET -> stringResource(R.string.dictionary_wordnet_name)
    DictionaryKind.GCIDE -> stringResource(R.string.dictionary_gcide_name)
    DictionaryKind.OLAM -> stringResource(R.string.dictionary_olam_name)
    DictionaryKind.FREEDICT -> stringResource(R.string.dictionary_language_pair, languageDisplayName(wordLanguage), languageDisplayName(meaningLanguage))
    DictionaryKind.STARDICT -> name ?: stringResource(R.string.dictionary_imported_name)
}

@Composable
private fun DictionaryStatus.about(): String = when (kind) {
    DictionaryKind.WORDNET -> stringResource(R.string.dictionary_wordnet_about)
    DictionaryKind.GCIDE -> stringResource(R.string.dictionary_gcide_about)
    DictionaryKind.OLAM -> stringResource(R.string.dictionary_olam_about)
    DictionaryKind.FREEDICT -> stringResource(R.string.dictionary_freedict_about)
    DictionaryKind.STARDICT -> stringResource(R.string.dictionary_language_pair, languageDisplayName(wordLanguage), languageDisplayName(meaningLanguage))
}

private val ArchiveTypes = arrayOf("application/zip", "application/x-xz", "application/gzip", "application/x-gzip",
    "application/x-tar", "application/octet-stream")
private val ImportLanguages = listOf("eng", "spa", "por", "rus", "deu", "fra", "ita", "mal", "tam", "hin", "ara", "zho", "jpn",
    "lat", "ell", "nld", "pol", "tur", "ben", "tel", "kan", "mar", "urd", "kor", "swe", "fin", "ces", "ukr")
private const val WordNetDownloadUrl = "https://en-word.net/static/english-wordnet-2025.zip"

/** As published by GNU (gcide 0.54) and olam.in (enml.tar.gz). */
private const val GcideDownloadBytes = 18_888_841L
private const val OlamDownloadBytes = 2_192_120L
