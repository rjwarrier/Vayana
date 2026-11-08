package com.vayana.feature.library

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.resources.R
import com.vayana.format.epub.EpubCleanupInventory
import com.vayana.format.epub.EpubCleanupOptions
import kotlinx.coroutines.CancellationException
import java.text.NumberFormat
import kotlin.math.abs

@Composable
internal fun BookCleanupResultDialog(result: BookCleanupResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val difference = result.originalSizeBytes - result.cleanedSizeBytes
    val sizeChange = Formatter.formatFileSize(context, abs(difference))
    val percentage = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(
        if (result.originalSizeBytes > 0) abs(difference).toDouble() / result.originalSizeBytes else 0.0,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_cleanup_complete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CleanupSizeRow(stringResource(R.string.library_cleanup_original_size), Formatter.formatFileSize(context, result.originalSizeBytes))
                CleanupSizeRow(stringResource(R.string.library_cleanup_new_size), Formatter.formatFileSize(context, result.cleanedSizeBytes))
                CleanupSizeRow(
                    stringResource(if (difference >= 0) R.string.library_cleanup_reduction else R.string.library_cleanup_increase),
                    stringResource(R.string.library_cleanup_size_change, sizeChange, percentage),
                )
                Text(stringResource(R.string.library_cleanup_result_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_import_done)) } },
    )
}

@Composable
private fun CleanupSizeRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun BookCleanupDialog(
    sourceKey: String,
    inspect: suspend () -> EpubCleanupInventory,
    onDismissRequest: () -> Unit,
    onConfirm: (EpubCleanupOptions) -> Unit,
) {
    var options by remember(sourceKey) { mutableStateOf(EpubCleanupOptions()) }
    var attempt by remember { mutableIntStateOf(0) }
    val inspection by produceState<Result<EpubCleanupInventory>?>(null, sourceKey, attempt) {
        value = null
        value = try { Result.success(inspect()) } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) { Result.failure(failure) }
    }
    val inventory = inspection?.getOrNull()
    val context = LocalContext.current
    fun resources(count: Int, bytes: Long): String = if (count > 0) context.getString(
        R.string.library_cleanup_resources, count, Formatter.formatShortFileSize(context, bytes),
    ) else context.getString(R.string.library_cleanup_inline)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.library_clean_up)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.library_cleanup_choose))
                when {
                    inspection == null -> {
                        VayanaLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.library_cleanup_inspecting))
                    }
                    inventory == null -> {
                        Text(stringResource(R.string.library_cleanup_inspection_failed))
                        TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.library_cleanup_retry)) }
                    }
                    else -> {
                        CleanupChoice(
                            stringResource(R.string.library_cleanup_gutenberg), stringResource(R.string.library_cleanup_gutenberg_body),
                            options.gutenberg, inventory.gutenberg,
                        ) { options = options.copy(gutenberg = it) }
                        CleanupChoice(
                            stringResource(R.string.library_cleanup_images),
                            stringResource(R.string.library_cleanup_images_body) + "\n" + resources(inventory.images, inventory.imageBytes),
                            options.images, inventory.images > 0 || inventory.inlineImages,
                        ) { options = options.copy(images = it) }
                        CleanupChoice(
                            stringResource(R.string.library_cleanup_fonts),
                            stringResource(R.string.library_cleanup_fonts_body) + "\n" + resources(inventory.fonts, inventory.fontBytes),
                            options.fonts, inventory.fonts > 0 || inventory.inlineFonts,
                        ) { options = options.copy(fonts = it) }
                        CleanupChoice(
                            stringResource(R.string.library_cleanup_text), stringResource(R.string.library_cleanup_text_body),
                            options.textArtifacts, inventory.textArtifacts,
                        ) { options = options.copy(textArtifacts = it) }
                        CleanupChoice(
                            stringResource(R.string.library_cleanup_media),
                            stringResource(R.string.library_cleanup_media_body) + "\n" + resources(inventory.media, inventory.mediaBytes),
                            options.media, inventory.media > 0 || inventory.inlineMedia,
                        ) { options = options.copy(media = it) }
                    }
                }
                Text(stringResource(R.string.library_clean_up_body), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(options) }, enabled = inventory != null && options.hasSelection) {
                Text(stringResource(R.string.library_clean_up))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.library_edit_metadata_cancel)) }
        },
    )
}

@Composable
private fun CleanupChoice(title: String, description: String, checked: Boolean, available: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled = available, role = Role.Checkbox, onValueChange = onChecked).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = available)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
            if (!available) Text(stringResource(R.string.library_cleanup_not_found), style = MaterialTheme.typography.labelSmall)
        }
    }
}
