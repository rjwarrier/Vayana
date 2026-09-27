package com.vayana.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.vayana.core.designsystem.component.syncDeviceLabel
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.theme.asAppDateTime
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ImportProgressSheet(progress: ImportProgressState, onDismissRequest: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { if (!progress.isRunning) onDismissRequest() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Paddings.screenHorizontal)
                .padding(bottom = Spacing.lg),
        ) {
            Text(text = stringResource(R.string.library_import_progress_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = progress.summary.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            if (progress.isRunning) {
                VayanaLinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
            }
            if (progress.rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.library_import_no_files),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(top = Spacing.md)) {
                    items(progress.rows, key = { it.id }) { row ->
                        ImportProgressRow(row = row)
                        HorizontalDivider()
                    }
                }
            }
            if (!progress.isRunning) {
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                ) {
                    Text(stringResource(R.string.library_import_done))
                }
            }
        }
    }
}

@Composable
private fun ImportProgressRow(row: ImportProgressRow) {
    ListItem(
        headlineContent = {
            Text(
                text = row.fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(row.status.label()) },
        leadingContent = { ImportStatusIcon(row.status) },
    )
}

@Composable
private fun ImportStatusIcon(status: ImportRowStatus) {
    when (status) {
        ImportRowStatus.COPYING,
        ImportRowStatus.PARSING,
        -> VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.icon))
        ImportRowStatus.IMPORTED,
        ImportRowStatus.DUPLICATE,
        -> Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        ImportRowStatus.UNSUPPORTED,
        ImportRowStatus.FAILED,
        -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        ImportRowStatus.QUEUED -> Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, modifier = Modifier.size(Sizes.icon))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun GitHubSyncProgressSheet(progress: GitHubSyncProgressState, onDismissRequest: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rows = listOf(
        GitHubSyncProgressStep.PREPARING,
        GitHubSyncProgressStep.READING_CLOUD,
        GitHubSyncProgressStep.ADDING_CLOUD_BOOKS,
        GitHubSyncProgressStep.UPLOADING_BOOKS,
        GitHubSyncProgressStep.SAVING_SNAPSHOT,
    )

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { if (!progress.isRunning) onDismissRequest() },
        shape = Radii.sheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Paddings.screenHorizontal)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.extraLarge),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = Elevations.level1,
                border = BorderStroke(
                    Strokes.outline,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                modifier = Modifier.padding(Spacing.sm),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.library_sync_progress_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = progress.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    VayanaLinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.large),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(Strokes.outline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = stringResource(
                        R.string.library_sync_progress_summary,
                        progress.cloudBooksCreated,
                        progress.cloudBooksUpdated,
                        progress.uploadedBooks,
                        progress.failedBooks,
                        progress.progressUpdated,
                        progress.uploadedCovers,
                        progress.downloadedCovers,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(rows, key = { it.name }) { step ->
                    val status = progress.statusFor(step)
                    GitHubSyncProgressRow(
                        label = step.label(),
                        status = status,
                        detail = progress.detailFor(step, status),
                    )
                }
            }
            if (!progress.isRunning) {
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Radii.buttonShape,
                ) {
                    Text(stringResource(R.string.library_import_done))
                }
            }
        }
    }
}

@Composable
private fun GitHubSyncProgressRow(label: String, status: GitHubSyncStepStatus, detail: String? = null) {
    val colors = status.containerAndContentColor()
    val accentColor = status.accentColor()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = colors.first,
        contentColor = colors.second,
        border = BorderStroke(
            Strokes.outline,
            when (status) {
                GitHubSyncStepStatus.RUNNING -> accentColor.copy(alpha = 0.65f)
                GitHubSyncStepStatus.FAILED -> accentColor.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            GitHubSyncStepIcon(status = status, color = accentColor, modifier = Modifier.size(Sizes.icon))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.second,
                )
                Text(
                    text = detail ?: status.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.second.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GitHubSyncStepIcon(status: GitHubSyncStepStatus, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    when (status) {
        GitHubSyncStepStatus.RUNNING -> VayanaCircularProgressIndicator(modifier = modifier, color = color)
        GitHubSyncStepStatus.DONE -> Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = modifier, tint = color)
        GitHubSyncStepStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = modifier, tint = color)
        GitHubSyncStepStatus.WAITING -> Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, modifier = modifier, tint = color)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CloudBookDownloadProgressSheet(
    progress: CloudBookDownloadProgressState,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { if (!progress.isRunning) onDismissRequest() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Paddings.screenHorizontal)
                .padding(bottom = Spacing.lg),
        ) {
            Text(text = stringResource(R.string.library_download_progress_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = progress.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(
                text = progress.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            VayanaLinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                strokeCap = StrokeCap.Round,
            )
            Text(
                text = stringResource(
                    R.string.library_download_progress_percent,
                    (progress.fraction * 100f).roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            if (!progress.isRunning) {
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                ) {
                    Text(stringResource(R.string.library_import_done))
                }
            }
        }
    }
}

@Composable
internal fun ImportSummary.label(): String = stringResource(
    R.string.library_import_summary,
    imported,
    duplicates,
    unsupported,
    failed,
)

@Composable
internal fun ImportRowStatus.label(): String = when (this) {
    ImportRowStatus.QUEUED -> stringResource(R.string.library_import_status_queued)
    ImportRowStatus.COPYING -> stringResource(R.string.library_import_status_copying)
    ImportRowStatus.PARSING -> stringResource(R.string.library_import_status_parsing)
    ImportRowStatus.IMPORTED -> stringResource(R.string.library_import_status_imported)
    ImportRowStatus.DUPLICATE -> stringResource(R.string.library_import_status_duplicate)
    ImportRowStatus.UNSUPPORTED -> stringResource(R.string.library_import_status_unsupported)
    ImportRowStatus.FAILED -> stringResource(R.string.library_import_status_failed)
}

internal enum class GitHubSyncStepStatus {
    WAITING,
    RUNNING,
    DONE,
    FAILED,
}

@Composable
internal fun GitHubSyncProgressStep.label(): String = when (this) {
    GitHubSyncProgressStep.PREPARING -> stringResource(R.string.library_sync_progress_prepare)
    GitHubSyncProgressStep.READING_CLOUD -> stringResource(R.string.library_sync_progress_read_cloud)
    GitHubSyncProgressStep.ADDING_CLOUD_BOOKS -> stringResource(R.string.library_sync_progress_add_cloud)
    GitHubSyncProgressStep.UPLOADING_BOOKS -> stringResource(R.string.library_sync_progress_upload)
    GitHubSyncProgressStep.SAVING_SNAPSHOT -> stringResource(R.string.library_sync_progress_save)
    GitHubSyncProgressStep.COMPLETE -> stringResource(R.string.library_sync_progress_complete)
    GitHubSyncProgressStep.FAILED -> stringResource(R.string.library_sync_progress_failed)
}

@Composable
internal fun GitHubSyncStepStatus.label(): String = when (this) {
    GitHubSyncStepStatus.WAITING -> stringResource(R.string.library_sync_progress_waiting)
    GitHubSyncStepStatus.RUNNING -> stringResource(R.string.library_sync_progress_running)
    GitHubSyncStepStatus.DONE -> stringResource(R.string.library_sync_progress_done)
    GitHubSyncStepStatus.FAILED -> stringResource(R.string.library_sync_progress_failed)
}

@Composable
private fun GitHubSyncStepStatus.containerAndContentColor() = when (this) {
    GitHubSyncStepStatus.RUNNING -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
    GitHubSyncStepStatus.DONE -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurface
    GitHubSyncStepStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    GitHubSyncStepStatus.WAITING -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun GitHubSyncStepStatus.accentColor() = when (this) {
    GitHubSyncStepStatus.RUNNING -> MaterialTheme.colorScheme.primary
    GitHubSyncStepStatus.DONE -> MaterialTheme.colorScheme.secondary
    GitHubSyncStepStatus.FAILED -> MaterialTheme.colorScheme.error
    GitHubSyncStepStatus.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun GitHubSyncProgressState.statusFor(step: GitHubSyncProgressStep): GitHubSyncStepStatus {
    if (this.step == GitHubSyncProgressStep.FAILED && step.ordinal == completedSteps.coerceAtMost(GitHubSyncProgressStep.SAVING_SNAPSHOT.ordinal)) {
        return GitHubSyncStepStatus.FAILED
    }
    return when {
        step.ordinal < completedSteps -> GitHubSyncStepStatus.DONE
        this.step == step && isRunning -> GitHubSyncStepStatus.RUNNING
        this.step == GitHubSyncProgressStep.COMPLETE -> GitHubSyncStepStatus.DONE
        else -> GitHubSyncStepStatus.WAITING
    }
}

private fun GitHubSyncProgressState.detailFor(step: GitHubSyncProgressStep, status: GitHubSyncStepStatus): String? =
    detail.takeIf {
        step == GitHubSyncProgressStep.SAVING_SNAPSHOT &&
            this.step == step &&
            status == GitHubSyncStepStatus.RUNNING
    }

@Composable
internal fun ReadingProgressSyncDialog(
    prompt: BookProgressChange,
    onKeepSyncedProgress: () -> Unit,
    onRevertSyncedProgress: () -> Unit,
) {
    val previousPercent = (prompt.previousPercent * 100).roundToInt()
    val newPercent = (prompt.newPercent * 100).roundToInt()
    val localTimestamp = (prompt.previousLastReadAt ?: prompt.previousUpdatedAt).asAppDateTime()
    val syncedTimestamp = (prompt.syncedAt ?: prompt.newLastReadAt ?: prompt.newUpdatedAt).asAppDateTime()
    val syncedDevice = syncDeviceLabel(prompt.syncedDeviceLabel)
    ExpressiveDialogSurface(onDismissRequest = onRevertSyncedProgress) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.Sync,
            title = stringResource(R.string.library_book_progress_sync_prompt_title),
            supportingText = stringResource(R.string.library_book_progress_sync_prompt_body, previousPercent, newPercent),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val localCard = @Composable { cardModifier: Modifier ->
                ProgressChoiceCard(
                    label = stringResource(R.string.library_book_progress_sync_prompt_local),
                    percent = previousPercent,
                    timestamp = localTimestamp,
                    modifier = cardModifier,
                )
            }
            val syncedCard = @Composable { cardModifier: Modifier ->
                ProgressChoiceCard(
                    label = stringResource(R.string.library_book_progress_sync_prompt_synced),
                    percent = newPercent,
                    timestamp = syncedTimestamp,
                    device = syncedDevice,
                    modifier = cardModifier,
                )
            }
            if (maxWidth < Sizes.compactChoiceBreakpoint) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    localCard(Modifier.fillMaxWidth())
                    syncedCard(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    localCard(Modifier.weight(1f).fillMaxHeight())
                    syncedCard(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilledTonalButton(onClick = onRevertSyncedProgress, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_book_progress_sync_prompt_revert, previousPercent))
            }
            Button(onClick = onKeepSyncedProgress, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_book_progress_sync_prompt_keep))
            }
        }
    }
}

@Composable
private fun ProgressChoiceCard(
    label: String,
    percent: Int,
    timestamp: String,
    modifier: Modifier = Modifier,
    device: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = Radii.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(Strokes.outline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = Elevations.level1,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            device?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
