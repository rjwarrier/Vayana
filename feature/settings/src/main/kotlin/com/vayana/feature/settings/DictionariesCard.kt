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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.designsystem.component.VayanaLoadingIndicator
import com.vayana.core.designsystem.component.asString
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import com.vayana.core.resources.UiText
import com.vayana.core.resources.uiText
import com.vayana.dictionary.api.DictionaryPack
import com.vayana.dictionary.api.DictionaryPackState
import com.vayana.dictionary.api.DictionaryPackStatus
import com.vayana.dictionary.api.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.vayana.core.common.ApplicationScope

@HiltViewModel
internal class DictionariesViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {
    val packs: StateFlow<List<DictionaryPackStatus>> = dictionaryRepository.packs

    private val _errors = MutableStateFlow<Map<DictionaryPack, UiText>>(emptyMap())
    val errors: StateFlow<Map<DictionaryPack, UiText>> = _errors

    fun installGcide() = run(DictionaryPack.GCIDE) { dictionaryRepository.installGcide() }

    fun installWordNet(uri: String) = run(DictionaryPack.WORDNET) { dictionaryRepository.installEnglish(uri) }

    fun remove(pack: DictionaryPack) = run(pack) { dictionaryRepository.remove(pack) }

    fun moveUp(pack: DictionaryPack) = dictionaryRepository.moveUp(pack)

    private fun run(pack: DictionaryPack, action: suspend () -> Unit) {
        _errors.update { it - pack }
        // A minute-long install keeps going when Settings is left; the card shows its progress on return.
        applicationScope.launch {
            runCatchingCancellable { action() }.onFailure { failure ->
                _errors.update { it + (pack to failure.uiText(R.string.dictionary_error_finish_install)) }
            }
        }
    }
}

/** The offline dictionaries word lookups use, in the order they're tried: install, remove, or try one first. */
@Composable
internal fun DictionariesCard(modifier: Modifier = Modifier, viewModel: DictionariesViewModel = hiltViewModel()) {
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.installWordNet(uri.toString())
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
            packs.forEachIndexed { index, status ->
                DictionaryPackRow(
                    status = status,
                    error = errors[status.pack],
                    canMoveUp = index > 0,
                    onMoveUp = { viewModel.moveUp(status.pack) },
                    onInstall = {
                        when (status.pack) {
                            DictionaryPack.GCIDE -> viewModel.installGcide()
                            DictionaryPack.WORDNET -> picker.launch(arrayOf("application/zip", "application/octet-stream"))
                        }
                    },
                    onGetWordNet = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WordNetDownloadUrl))) }
                    },
                    onRemove = { viewModel.remove(status.pack) },
                )
            }
        }
    }
}

@Composable
private fun DictionaryPackRow(
    status: DictionaryPackStatus,
    error: UiText?,
    canMoveUp: Boolean,
    onMoveUp: () -> Unit,
    onInstall: () -> Unit,
    onGetWordNet: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val name = stringResource(status.pack.nameRes())
    Surface(shape = RoundedCornerShape(Radii.medium), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(status.pack.aboutRes()),
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
                if (progress != null) {
                    VayanaLinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    VayanaLoadingIndicator()
                }
            }
            error?.let { Text(text = it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (state != DictionaryPackState.Installing && state != DictionaryPackState.Installed) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                when {
                    status.pack == DictionaryPack.GCIDE -> FilledTonalButton(onClick = onInstall) {
                        Text(stringResource(R.string.dictionary_action_download, Formatter.formatShortFileSize(context, GcideDownloadBytes)))
                    }
                    else -> {
                        OutlinedButton(onClick = onGetWordNet) { Text(stringResource(R.string.reader_dictionary_download)) }
                        FilledTonalButton(onClick = onInstall) { Text(stringResource(R.string.reader_dictionary_install_zip)) }
                    }
                }
            }
        }
    }
}

private fun DictionaryPack.nameRes(): Int = when (this) {
    DictionaryPack.WORDNET -> R.string.dictionary_wordnet_name
    DictionaryPack.GCIDE -> R.string.dictionary_gcide_name
}

private fun DictionaryPack.aboutRes(): Int = when (this) {
    DictionaryPack.WORDNET -> R.string.dictionary_wordnet_about
    DictionaryPack.GCIDE -> R.string.dictionary_gcide_about
}

private const val WordNetDownloadUrl = "https://en-word.net/static/english-wordnet-2025.zip"

/** GNU's gcide-latest.zip, as published for 0.54 (2024-12-31). */
private const val GcideDownloadBytes = 18_888_841L
