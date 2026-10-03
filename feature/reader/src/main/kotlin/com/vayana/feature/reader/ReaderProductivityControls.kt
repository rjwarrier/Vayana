package com.vayana.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vayana.core.datastore.settings.ReadingPreset
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ReadingPresetsPanel(
    presets: List<ReadingPreset>,
    onSave: suspend (String) -> Unit,
    onApply: suspend (ReadingPreset) -> Unit,
    onDelete: suspend (String) -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun runAction(action: suspend () -> Unit) {
        busy = true
        failed = false
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    var namingPreset by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.tools_presets), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.tools_presets_scope), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (presets.isEmpty()) {
            Text(
                stringResource(R.string.tools_presets_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (failed) Text(stringResource(R.string.tools_failed), color = MaterialTheme.colorScheme.error)
        if (busy) VayanaCircularProgressIndicator()
        presets.forEach { preset ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !busy, onClick = { runAction { onApply(preset) } }, modifier = Modifier.weight(1f)) {
                    Text(preset.name)
                }
                IconButton(enabled = !busy, onClick = { runAction { onDelete(preset.id) } }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.tools_delete))
                }
            }
        }
        TextButton(enabled = !busy && presets.size < 50, onClick = { name = ""; failed = false; namingPreset = true }) {
            Text(stringResource(R.string.tools_save_preset))
        }
    }
    if (namingPreset) {
        AlertDialog(
            onDismissRequest = { if (!busy) namingPreset = false },
            title = { Text(stringResource(R.string.tools_save_preset)) },
            text = {
                Column {
                if (failed) Text(stringResource(R.string.tools_failed), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    enabled = !busy,
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text(stringResource(R.string.tools_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                    runAction { onSave(name.trim()); namingPreset = false }
                }) { Text(stringResource(R.string.tools_save)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { namingPreset = false }) { Text(stringResource(R.string.tools_cancel)) }
            },
        )
    }
}

@Composable
internal fun ReadingJournalDialog(
    initialText: String = "",
    onDismiss: () -> Unit,
    onSave: suspend (String) -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText.take(4000)) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.tools_journal)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(4000); failed = false },
                    label = { Text(stringResource(R.string.tools_journal_hint)) },
                    enabled = !saving,
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (failed) {
                    Text(
                        stringResource(R.string.tools_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && text.isNotBlank(), onClick = {
                saving = true
                failed = false
                scope.launch {
                    try {
                        onSave(text.trim())
                        onDismiss()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                    } finally {
                        saving = false
                    }
                }
            }) {
                if (saving) VayanaCircularProgressIndicator()
                else Text(stringResource(R.string.tools_save))
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.tools_cancel)) }
        },
    )
}
