package com.vayana.feature.settings

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.ReadOnlyComposable
import android.text.format.Formatter
import com.vayana.core.designsystem.component.asString
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import com.vayana.core.designsystem.theme.asAppDateTime
import com.vayana.core.designsystem.component.VayanaDropdownMenu
import com.vayana.core.designsystem.component.VayanaMenuGroup
import com.vayana.core.designsystem.component.VayanaMenuItem
import com.vayana.feature.settings.backup.AutomaticBackupFrequency
import com.vayana.feature.settings.backup.AutomaticBackupState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun Long.formatBackupDate(): String = if (this <= 0L) "" else asAppDateTime()

/** A file size in the system's own localized units. */
@Composable
@ReadOnlyComposable
internal fun Long.formatByteSize(): String = Formatter.formatShortFileSize(LocalContext.current, this)

@Composable
internal fun BackupRestoreCard(
    backupState: BackupUiState,
    automaticBackup: AutomaticBackupState,
    backupFolderFiles: BackupFolderFilesState,
    onCreateBackup: (Uri) -> Unit,
    onChooseAutomaticBackupFolder: (Uri) -> Unit,
    onSetAutomaticBackupKeepCount: (Int) -> Unit,
    onSetAutomaticBackupFrequency: (AutomaticBackupFrequency) -> Unit,
    onDisableAutomaticBackup: () -> Unit,
    onRefreshBackupFolderFiles: () -> Unit,
    onPickRestoreFile: (Uri) -> Unit,
    onDismissBackupState: () -> Unit,
) {
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(onCreateBackup)
    }
    val pickRestoreFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPickRestoreFile)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onChooseAutomaticBackupFolder)
    }
    val working = backupState == BackupUiState.Creating || backupState == BackupUiState.Restoring
    var frequencyMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        BackupPanel {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.settings_backup_manual_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_backup_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilledTonalButton(
                    onClick = {
                        createBackupLauncher.launch("vayana-backup-${SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())}.zip")
                    },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_backup_create), modifier = Modifier.padding(start = Spacing.xs))
                }
                FilledTonalButton(
                    onClick = {
                        pickRestoreFileLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_backup_restore), modifier = Modifier.padding(start = Spacing.xs))
                }
            }

            when (backupState) {
                BackupUiState.Creating, BackupUiState.Restoring -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                    Text(
                        stringResource(
                            if (backupState == BackupUiState.Creating) R.string.settings_backup_creating
                            else R.string.settings_restore_working,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                BackupUiState.BackupComplete -> BackupStatusRow(
                    message = stringResource(R.string.settings_backup_complete),
                    isError = false,
                    onDismiss = onDismissBackupState,
                )
                is BackupUiState.BackupFailed -> BackupStatusRow(
                    message = stringResource(R.string.settings_backup_failed, backupState.message.asString()),
                    isError = true,
                    onDismiss = onDismissBackupState,
                )
                is BackupUiState.RestoreFailed -> BackupStatusRow(
                    message = stringResource(R.string.settings_restore_failed, backupState.message.asString()),
                    isError = true,
                    onDismiss = onDismissBackupState,
                )
                is BackupUiState.RestoreIncompatible -> BackupStatusRow(
                    message = backupState.message.asString(),
                    isError = true,
                    onDismiss = onDismissBackupState,
                )
                BackupUiState.Idle -> Unit
            }

        }
        BackupPanel {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.settings_auto_backup_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_auto_backup_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(stringResource(R.string.settings_auto_backup_location), modifier = Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = { folderPicker.launch(automaticBackup.folderUri?.let(Uri::parse)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            automaticBackup.folderName ?: stringResource(R.string.settings_auto_backup_choose_folder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(stringResource(R.string.settings_auto_backup_frequency), modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.weight(1f)) {
                        FilledTonalButton(
                            onClick = { frequencyMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(automaticBackup.frequency.labelResource()), maxLines = 1)
                            Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = null)
                        }
                        VayanaDropdownMenu(
                            expanded = frequencyMenuExpanded,
                            onDismissRequest = { frequencyMenuExpanded = false },
                            groups = listOf(VayanaMenuGroup(AutomaticBackupFrequency.entries.map { frequency ->
                                VayanaMenuItem(
                                    label = stringResource(frequency.labelResource()),
                                    selected = automaticBackup.frequency == frequency,
                                    onClick = { onSetAutomaticBackupFrequency(frequency) },
                                )
                            })),
                        )
                    }
                }
                if (automaticBackup.folderUri != null) {
                    Text(
                        stringResource(R.string.settings_auto_backup_keep, automaticBackup.keepCount),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = automaticBackup.keepCount.toFloat(),
                        onValueChange = { onSetAutomaticBackupKeepCount(it.roundToInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                    TextButton(onClick = onDisableAutomaticBackup) {
                        Text(stringResource(R.string.settings_auto_backup_disable))
                    }
                }
                if (automaticBackup.folderUri != null) Text(
                    if (automaticBackup.lastSuccessAt > 0L) {
                        stringResource(
                            R.string.settings_auto_backup_last_success,
                            automaticBackup.lastSuccessAt.formatBackupDate(),
                        )
                    } else stringResource(R.string.settings_auto_backup_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                automaticBackup.lastError?.let { error ->
                    Text(
                        stringResource(R.string.settings_auto_backup_error, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

        }
        if (automaticBackup.folderUri != null) {
            BackupPanel {
                BackupFolderFiles(
                    state = backupFolderFiles,
                    onRefresh = onRefreshBackupFolderFiles,
                    onPreview = onPickRestoreFile,
                    enabled = !working,
                )
            }
        }
    }
}

@Composable
private fun BackupPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevations.none,
    ) {
        Column(
            modifier = Modifier.padding(Paddings.card).vayanaAnimateContentSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}

@Composable
private fun BackupFolderFiles(
    state: BackupFolderFilesState,
    onRefresh: () -> Unit,
    onPreview: (Uri) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.settings_backup_files_title), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.settings_backup_files_refresh))
            }
        }
        Text(
            stringResource(R.string.settings_backup_files_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.loading -> VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
            state.error != null -> Text(
                stringResource(R.string.settings_backup_files_error, state.error.asString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            state.files.isEmpty() -> Text(
                stringResource(R.string.settings_backup_files_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> state.files.forEach { file ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onPreview(file.uri) },
                    shape = Radii.cardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                if (file.modifiedAt > 0L) file.modifiedAt.formatBackupDate()
                                else stringResource(R.string.settings_backup_files_unknown_date),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            file.sizeBytes.takeIf { it > 0L }?.let { size ->
                                Text(
                                    size.formatByteSize(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun AutomaticBackupFrequency.labelResource(): Int = when (this) {
    AutomaticBackupFrequency.DAILY -> R.string.settings_auto_backup_daily
    AutomaticBackupFrequency.WEEKLY -> R.string.settings_auto_backup_weekly
    AutomaticBackupFrequency.EVERY_30_DAYS -> R.string.settings_auto_backup_every_30_days
}

@Composable
internal fun BackupStatusRow(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.input_clear_content_description),
                modifier = Modifier.size(Sizes.iconSmall),
            )
        }
    }
}
