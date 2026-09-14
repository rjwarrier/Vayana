package com.vayana.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import com.vayana.core.designsystem.theme.asAppDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun Long.formatBackupDate(): String = if (this <= 0L) "" else asAppDateTime()

internal fun Long.formatByteSize(): String {
    if (this < 1024) return "$this B"
    val units = listOf("KB", "MB", "GB")
    var value = this / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return "%.1f %s".format(Locale.getDefault(), value, units[unitIndex])
}

@Composable
internal fun BackupRestoreCard(
    backupState: BackupUiState,
    onCreateBackup: (Uri) -> Unit,
    onPickRestoreFile: (Uri) -> Unit,
    onDismissBackupState: () -> Unit,
) {
    val backupFileName = remember {
        "vayana-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.zip"
    }
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(onCreateBackup)
    }
    val pickRestoreFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPickRestoreFile)
    }
    val working = backupState is BackupUiState.Working

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevations.none,
    ) {
        Column(
            modifier = Modifier
                .padding(Paddings.card)
                .vayanaAnimateContentSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SettingsIconBubble(icon = Icons.Outlined.Backup, selected = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_backup_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_backup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilledTonalButton(
                    onClick = { createBackupLauncher.launch(backupFileName) },
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
                BackupUiState.Working -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_backup_working), style = MaterialTheme.typography.bodySmall)
                }
                BackupUiState.BackupComplete -> BackupStatusRow(
                    message = stringResource(R.string.settings_backup_complete),
                    isError = false,
                    onDismiss = onDismissBackupState,
                )
                is BackupUiState.BackupFailed -> BackupStatusRow(
                    message = stringResource(R.string.settings_backup_failed, backupState.message),
                    isError = true,
                    onDismiss = onDismissBackupState,
                )
                is BackupUiState.RestoreFailed -> BackupStatusRow(
                    message = stringResource(R.string.settings_restore_failed, backupState.message),
                    isError = true,
                    onDismiss = onDismissBackupState,
                )
                is BackupUiState.RestoreIncompatible -> BackupStatusRow(
                    message = backupState.message,
                    isError = true,
                    onDismiss = onDismissBackupState,
                )
                BackupUiState.Idle -> Unit
            }
        }
    }
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
