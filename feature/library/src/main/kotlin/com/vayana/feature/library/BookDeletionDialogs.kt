package com.vayana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.vayana.core.database.model.Book
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

/** First step of deleting a book: move it to Recently deleted (restorable), or delete it permanently everywhere. */
@Composable
internal fun DeleteBookChoiceDialog(
    bookTitle: String,
    onDismissRequest: () -> Unit,
    onMoveToRecentlyDeleted: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    ExpressiveDialogSurface(onDismissRequest = onDismissRequest) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.Delete,
            title = stringResource(R.string.library_delete_choice_title, bookTitle),
            supportingText = stringResource(R.string.library_delete_choice_body),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        DeleteChoiceCard(
            icon = Icons.Outlined.DeleteOutline,
            title = stringResource(R.string.library_delete_move_to_recent),
            detail = stringResource(R.string.library_delete_move_to_recent_detail),
            destructive = false,
            onClick = onMoveToRecentlyDeleted,
        )
        DeleteChoiceCard(
            icon = Icons.Outlined.DeleteForever,
            title = stringResource(R.string.library_delete_everywhere),
            detail = stringResource(R.string.library_delete_everywhere_detail),
            destructive = true,
            onClick = onDeletePermanently,
        )
        TextButton(onClick = onDismissRequest, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.settings_reset_all_cancel))
        }
    }
}

/**
 * Confirms a permanent, everywhere deletion and spells out what goes. [highlightCount] is left out when unknown.
 * The delete button stays disabled until the user acknowledges it can't be undone.
 */
@Composable
internal fun PermanentDeleteConfirmDialog(
    book: Book,
    highlightCount: Int?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    var acknowledged by rememberSaveable(book.id) { mutableStateOf(false) }
    val context = LocalContext.current
    ExpressiveDialogSurface(onDismissRequest = onDismissRequest, scrollable = true) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.DeleteForever,
            title = stringResource(R.string.library_delete_everywhere_title),
            supportingText = stringResource(R.string.library_delete_everywhere_intro, book.title),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DeletionItem(stringResource(R.string.library_delete_everywhere_files))
            if (book.hasCloudCopy()) DeletionItem(stringResource(R.string.library_delete_everywhere_cloud))
            highlightCount?.takeIf { it > 0 }?.let { count ->
                DeletionItem(stringResource(R.string.library_delete_everywhere_notes, count))
            }
            if (book.totalReadingSeconds > 0L) {
                DeletionItem(
                    stringResource(R.string.library_delete_everywhere_reading, formatReadingDuration(book.totalReadingSeconds, context)),
                )
            }
            DeletionItem(stringResource(R.string.library_delete_everywhere_shelves))
        }
        Text(
            text = stringResource(R.string.library_delete_everywhere_kept),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (book.hasCloudCopy()) {
            Text(
                text = stringResource(R.string.library_delete_everywhere_history),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = acknowledged, role = Role.Checkbox, onValueChange = { acknowledged = it }),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = acknowledged, onCheckedChange = null)
            Text(
                text = stringResource(R.string.library_delete_everywhere_acknowledge),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onDismissRequest, shape = Radii.buttonShape) {
                Text(stringResource(R.string.settings_reset_all_cancel))
            }
            Button(
                onClick = onConfirm,
                enabled = acknowledged,
                shape = Radii.buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.library_delete_everywhere_confirm))
            }
        }
    }
}

/** Shows a pending [PermanentDeletionNotice] once, then consumes it. */
@Composable
internal fun PermanentDeletionNoticeEffect(
    notice: PermanentDeletionNotice?,
    snackbarHostState: SnackbarHostState,
    onShown: (PermanentDeletionNotice) -> Unit,
) {
    val message = notice?.let {
        stringResource(
            if (it.cloudCopyPending) R.string.library_deleted_everywhere_cloud_pending else R.string.library_deleted_everywhere,
            it.title,
        )
    }
    LaunchedEffect(notice) {
        if (notice == null || message == null) return@LaunchedEffect
        onShown(notice)
        snackbarHostState.showSnackbar(message)
    }
}

@Composable
private fun DeleteChoiceCard(
    icon: ImageVector,
    title: String,
    detail: String,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = Radii.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(Paddings.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (destructive) accent else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeletionItem(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = "•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Book.hasCloudCopy(): Boolean = !fileAssetId.isNullOrBlank() || !coverAssetId.isNullOrBlank()
