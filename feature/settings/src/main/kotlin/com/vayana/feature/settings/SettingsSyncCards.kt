package com.vayana.feature.settings

import com.vayana.core.designsystem.component.asString
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun GitHubSyncHealthCard(settings: SettingsSnapshot, connectionState: GitHubConnectionTestState, pendingCloudDeletions: Int) {
    val enabled = settings.githubSyncEnabled
    val repositoryReady = settings.githubOwner.isNotBlank() && settings.githubRepository.isNotBlank() && settings.githubBranch.isNotBlank()
    val tokenReady = settings.githubToken.isNotBlank()
    val passphraseReady = settings.githubSyncPassphrase.isNotBlank()
    val ready = enabled && repositoryReady && tokenReady && passphraseReady
    val statusText = when {
        !enabled -> stringResource(R.string.settings_github_health_off)
        ready -> stringResource(R.string.settings_github_health_ready)
        else -> stringResource(R.string.settings_github_health_needs_setup)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radii.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = Elevations.level1,
    ) {
        Column(
            modifier = Modifier.padding(Paddings.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SettingsIconBubble(icon = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, selected = ready)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_github_health_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SyncHealthRow(
                    label = stringResource(R.string.settings_github_health_repository),
                    value = if (repositoryReady) {
                        "${settings.githubOwner}/${settings.githubRepository} · ${settings.githubBranch}"
                    } else {
                        stringResource(R.string.settings_github_health_missing)
                    },
                    healthy = repositoryReady,
                )
                SyncHealthRow(
                    label = stringResource(R.string.settings_github_health_token),
                    value = if (tokenReady) stringResource(R.string.settings_github_health_present) else stringResource(R.string.settings_github_health_missing),
                    healthy = tokenReady,
                )
                SyncHealthRow(
                    label = stringResource(R.string.settings_github_health_passphrase),
                    value = if (passphraseReady) stringResource(R.string.settings_github_health_present) else stringResource(R.string.settings_github_health_missing),
                    healthy = passphraseReady,
                )
                SyncHealthRow(
                    label = stringResource(R.string.settings_github_health_last_check),
                    value = connectionState.healthLabel(),
                    healthy = connectionState is GitHubConnectionTestState.Connected || connectionState is GitHubConnectionTestState.ReadyForInitialSync,
                )
                if (pendingCloudDeletions > 0) {
                    SyncHealthRow(
                        label = stringResource(R.string.settings_github_health_pending_deletions),
                        value = pendingCloudDeletions.toString(),
                        healthy = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncHealthRow(label: String, value: String, healthy: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = if (healthy) Icons.Outlined.CheckCircle else Icons.Outlined.Close,
            contentDescription = null,
            modifier = Modifier.size(Sizes.iconSmall),
            tint = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GitHubConnectionTestState.healthLabel(): String = when (this) {
    GitHubConnectionTestState.Idle -> stringResource(R.string.settings_github_health_not_checked)
    GitHubConnectionTestState.Working -> stringResource(R.string.settings_github_connection_test_working)
    GitHubConnectionTestState.Connected -> stringResource(R.string.settings_github_connection_test_connected)
    GitHubConnectionTestState.ReadyForInitialSync -> stringResource(R.string.settings_github_connection_test_ready_initial)
    GitHubConnectionTestState.MissingConfig -> stringResource(R.string.settings_github_connection_test_missing_config)
    is GitHubConnectionTestState.Failed -> stringResource(R.string.settings_github_connection_test_failed, message)
}

@Composable
internal fun GitHubConnectionTestCard(
    state: GitHubConnectionTestState,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val working = state is GitHubConnectionTestState.Working
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
                SettingsIconBubble(icon = Icons.Outlined.Sync, selected = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_github_connection_test_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_github_connection_test_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(
                onClick = onTest,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (working) {
                    VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                } else {
                    Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                }
                Text(stringResource(R.string.settings_github_connection_test_action), modifier = Modifier.padding(start = Spacing.xs))
            }
            when (state) {
                GitHubConnectionTestState.Working -> Text(
                    text = stringResource(R.string.settings_github_connection_test_working),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GitHubConnectionTestState.Connected -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_connection_test_connected),
                    isError = false,
                    onDismiss = onDismiss,
                )
                GitHubConnectionTestState.ReadyForInitialSync -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_connection_test_ready_initial),
                    isError = false,
                    onDismiss = onDismiss,
                )
                GitHubConnectionTestState.MissingConfig -> SyncWarningCallout(
                    message = stringResource(R.string.settings_github_connection_test_missing_config),
                    onDismiss = onDismiss,
                )
                is GitHubConnectionTestState.Failed -> SyncWarningCallout(
                    message = stringResource(R.string.settings_github_connection_test_failed, state.message.asString()),
                    onDismiss = onDismiss,
                )
                GitHubConnectionTestState.Idle -> Unit
            }
        }
    }
}

@Composable
internal fun GitHubSyncSettingsTransferCard(
    state: GitHubSyncSettingsTransferState,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val exportFileName = remember {
        "vayana-github-sync-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.vayana-ghsync"
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(onExport)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    val working = state is GitHubSyncSettingsTransferState.Working

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
                SettingsIconBubble(icon = Icons.Outlined.Storage, selected = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_github_transfer_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_github_transfer_subtitle),
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
                    onClick = { exportLauncher.launch(exportFileName) },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_github_transfer_export), modifier = Modifier.padding(start = Spacing.xs))
                }
                FilledTonalButton(
                    onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_github_transfer_import), modifier = Modifier.padding(start = Spacing.xs))
                }
            }
            when (state) {
                GitHubSyncSettingsTransferState.Working -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_github_transfer_working), style = MaterialTheme.typography.bodySmall)
                }
                GitHubSyncSettingsTransferState.ExportComplete -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_transfer_export_complete),
                    isError = false,
                    onDismiss = onDismiss,
                )
                GitHubSyncSettingsTransferState.ImportComplete -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_transfer_import_complete),
                    isError = false,
                    onDismiss = onDismiss,
                )
                GitHubSyncSettingsTransferState.MissingPassphrase -> SyncWarningCallout(
                    message = stringResource(R.string.settings_github_transfer_missing_passphrase),
                    onDismiss = onDismiss,
                )
                is GitHubSyncSettingsTransferState.Failed -> SyncWarningCallout(
                    message = stringResource(R.string.settings_github_transfer_failed, state.message.asString()),
                    onDismiss = onDismiss,
                )
                GitHubSyncSettingsTransferState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun SyncWarningCallout(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.extraLarge),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = BorderStroke(Strokes.outline, MaterialTheme.colorScheme.error.copy(alpha = 0.34f)),
        tonalElevation = Elevations.shadowSmall,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(
                modifier = Modifier.size(SettingsCategoryBadgeSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                tonalElevation = Elevations.shadowSmall,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.iconSmall),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.settings_sync_warning_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.86f),
                )
            }
            FilledTonalButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(Radii.full),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(stringResource(R.string.settings_sync_warning_dismiss))
            }
        }
    }
}
