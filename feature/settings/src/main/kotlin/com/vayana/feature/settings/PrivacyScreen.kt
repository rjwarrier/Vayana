package com.vayana.feature.settings

import android.text.format.Formatter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.core.designsystem.component.VayanaLoadingIndicator
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import com.vayana.format.ocr.OcrModel
import java.util.Locale

@Composable
fun PrivacyRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: PrivacyViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    PrivacyScreen(state, onBack, viewModel::removeRecognizedText, modifier, viewModel::removeBookText, viewModel::removeModel)
}

private enum class Verdict { GOOD, WARN, UNKNOWN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyScreen(state: PrivacyState?, onBack: () -> Unit, onRemoveRecognizedText: () -> Unit, modifier: Modifier = Modifier,
    onRemoveBook: (String) -> Unit, onRemoveModel: (OcrModel) -> Unit) {
    var bookToRemove by remember { mutableStateOf<RecognizedBookStorage?>(null) }
    var modelToRemove by remember { mutableStateOf<OcrModel?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.notes_back_content_description))
                    }
                },
                // The app shell already pads for the status bar, as on Settings and Diagnostics.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { VayanaLoadingIndicator(Modifier.size(Sizes.fab)) }
            return@Scaffold
        }
        PagedLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Paddings.screenHorizontal,
                end = Paddings.screenHorizontal,
                top = innerPadding.calculateTopPadding() + Spacing.sm,
                bottom = innerPadding.calculateBottomPadding() + Spacing.xl + LocalFloatingNavigationInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { SettingsContentContainer { StatusCard(state) } }
            item { SettingsContentContainer { StoredCard(state, onRemoveRecognizedText) } }
            item { SettingsContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.ocr_storage_models), style = MaterialTheme.typography.titleMedium)
                    if (state.recognitionModels.isEmpty()) Text(stringResource(R.string.privacy_stored_ocr_none))
                    state.recognitionModels.forEach { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(storageModelName(entry.model))
                                Text(Formatter.formatShortFileSize(LocalContext.current, entry.bytes), style = MaterialTheme.typography.bodySmall)
                                if (entry.busy) Text(stringResource(R.string.ocr_storage_in_use), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(enabled = !state.storageBusy && !entry.busy, onClick = { modelToRemove = entry.model }) {
                                Text(stringResource(R.string.ocr_storage_remove))
                            }
                        }
                    }
                    state.storageError?.let { Text(it.resolve(LocalContext.current.resources), color = MaterialTheme.colorScheme.error) }
                    if (state.storageBusy) VayanaLoadingIndicator(Modifier.size(Sizes.icon))
                }
            } }
            if (state.recognizedBooks.isNotEmpty()) {
                item { SettingsContentContainer { Text(stringResource(R.string.ocr_storage_books), style = MaterialTheme.typography.titleMedium) } }
                items(state.recognizedBooks, key = { it.hash }) { book -> SettingsContentContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(book.title ?: stringResource(R.string.ocr_storage_unknown_book, book.hash.take(12)))
                            Text(Formatter.formatShortFileSize(LocalContext.current, book.bytes), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(enabled = !state.storageBusy, onClick = { bookToRemove = book }) { Text(stringResource(R.string.ocr_storage_remove)) }
                    }
                } }
            }
            item { SettingsContentContainer { LeavesCard() } }
            item { SettingsContentContainer { NeverCard() } }
        }
    }
    bookToRemove?.let { book ->
        val name = book.title ?: stringResource(R.string.ocr_storage_unknown_book, book.hash.take(12))
        StorageRemovalConfirmation(stringResource(R.string.ocr_storage_book_confirm, name), { bookToRemove = null }) {
            bookToRemove = null; onRemoveBook(book.hash)
        }
    }
    modelToRemove?.let { model ->
        StorageRemovalConfirmation(stringResource(R.string.ocr_storage_model_confirm, storageModelName(model)), { modelToRemove = null }) {
            modelToRemove = null; onRemoveModel(model)
        }
    }
}

@Composable private fun StorageRemovalConfirmation(text: String, dismiss: () -> Unit, remove: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, text = { Text(text) },
        confirmButton = { TextButton(onClick = remove) { Text(stringResource(R.string.ocr_storage_remove)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.privacy_stored_ocr_keep)) } })
}

@Composable private fun storageModelName(model: OcrModel): String = when (model) {
    OcrModel.MalayalamFast -> stringResource(R.string.ocr_model_malayalam)
    OcrModel.MalayalamScript -> stringResource(R.string.ocr_model_mixed)
    else -> Locale.forLanguageTag(model.textLanguage).getDisplayLanguage(Locale.getDefault())
}

@Composable
private fun StatusCard(state: PrivacyState) {
    PrivacyCard(Icons.Outlined.Shield, stringResource(R.string.privacy_status_section), hero = true) {
        StatusRow(
            stringResource(R.string.privacy_device_encryption),
            when (state.deviceEncryption) {
                DeviceEncryption.ENCRYPTED -> Verdict.GOOD to R.string.privacy_device_encrypted
                DeviceEncryption.ENCRYPTED_WITHOUT_LOCK -> Verdict.WARN to R.string.privacy_device_encrypted_without_lock
                DeviceEncryption.NOT_ENCRYPTED -> Verdict.WARN to R.string.privacy_device_not_encrypted
                DeviceEncryption.UNKNOWN -> Verdict.UNKNOWN to R.string.privacy_device_unknown
            },
        )
        StatusRow(
            stringResource(R.string.privacy_screen_lock),
            if (state.screenLock) Verdict.GOOD to R.string.privacy_screen_lock_on else Verdict.WARN to R.string.privacy_screen_lock_off,
        )
        StatusRow(
            stringResource(R.string.privacy_book_files),
            if (state.bookFilesPrivate) Verdict.GOOD to R.string.privacy_book_files_private else Verdict.WARN to R.string.privacy_book_files_shared,
        )
        val repository = state.syncRepository
        if (repository == null) {
            StatusRow(stringResource(R.string.privacy_sync), Verdict.GOOD, stringResource(R.string.privacy_sync_off))
        } else {
            StatusRow(stringResource(R.string.privacy_sync), Verdict.GOOD, stringResource(R.string.privacy_sync_on, repository))
            StatusRow(
                stringResource(R.string.privacy_repository),
                when (state.repositoryPrivate) {
                    true -> Verdict.GOOD to R.string.privacy_repository_private
                    false -> Verdict.WARN to R.string.privacy_repository_public
                    null -> Verdict.UNKNOWN to R.string.privacy_repository_unknown
                },
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, verdict: Pair<Verdict, Int>) = StatusRow(label, verdict.first, stringResource(verdict.second))

@Composable
private fun StatusRow(label: String, verdict: Verdict, detail: String) {
    val (icon, tint) = when (verdict) {
        Verdict.GOOD -> Icons.Outlined.CheckCircle to MaterialTheme.colorScheme.primary
        Verdict.WARN -> Icons.Outlined.WarningAmber to MaterialTheme.colorScheme.error
        Verdict.UNKNOWN -> Icons.Outlined.HelpOutline to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.icon))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StoredCard(state: PrivacyState, onRemoveRecognizedText: () -> Unit) {
    PrivacyCard(Icons.Outlined.PhoneAndroid, stringResource(R.string.privacy_stored_section)) {
        Fact(R.string.privacy_stored_library, R.string.privacy_stored_library_detail)
        Fact(R.string.privacy_stored_books, R.string.privacy_stored_books_detail)
        RecognizedTextFact(state, onRemoveRecognizedText)
        Fact(R.string.privacy_stored_secrets, R.string.privacy_stored_secrets_detail)
        Fact(R.string.privacy_stored_logs, R.string.privacy_stored_logs_detail)
        Fact(R.string.privacy_stored_backups, R.string.privacy_stored_backups_detail)
    }
}

@Composable
private fun LeavesCard() {
    PrivacyCard(Icons.Outlined.CloudUpload, stringResource(R.string.privacy_leaves_section)) {
        Fact(R.string.privacy_leaves_github, R.string.privacy_leaves_github_detail)
        Fact(R.string.privacy_leaves_goodreads, R.string.privacy_leaves_goodreads_detail)
        Fact(R.string.privacy_leaves_wikimedia, R.string.privacy_leaves_wikimedia_detail)
        Fact(R.string.privacy_leaves_catalogs, R.string.privacy_leaves_catalogs_detail)
        Fact(R.string.privacy_leaves_ocr, R.string.privacy_leaves_ocr_detail)
        Fact(R.string.privacy_leaves_apps, R.string.privacy_leaves_apps_detail)
    }
}

@Composable
private fun NeverCard() {
    PrivacyCard(Icons.Outlined.CheckCircle, stringResource(R.string.privacy_never_section)) {
        Text(stringResource(R.string.privacy_never_detail), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Text recognised from scanned books: how much there is, and removing it all (it can be recognised again). */
@Composable
private fun RecognizedTextFact(state: PrivacyState, onRemove: () -> Unit) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.privacy_stored_ocr), style = MaterialTheme.typography.titleSmall)
        Text(
            text = if (state.recognizedTextBooks == 0) stringResource(R.string.privacy_stored_ocr_none) else stringResource(
                R.string.privacy_stored_ocr_detail,
                state.recognizedTextBooks,
                Formatter.formatShortFileSize(context, state.recognizedTextBytes),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.recognizedTextBooks > 0) {
            TextButton(enabled = !state.storageBusy, onClick = { confirming = true }) { Text(stringResource(R.string.privacy_stored_ocr_remove)) }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            text = { Text(stringResource(R.string.privacy_stored_ocr_remove_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onRemove() }) { Text(stringResource(R.string.privacy_stored_ocr_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.privacy_stored_ocr_keep)) }
            },
        )
    }
}

@Composable
private fun Fact(title: Int, detail: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrivacyCard(icon: ImageVector, title: String, hero: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.extraLargeIncreased),
        color = if (hero) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { Icon(icon, contentDescription = null, modifier = Modifier.padding(Spacing.sm).size(Sizes.icon)) }
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
            content()
        }
    }
}
