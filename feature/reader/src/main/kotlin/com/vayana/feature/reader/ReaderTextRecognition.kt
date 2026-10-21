package com.vayana.feature.reader

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.format.ocr.OcrModel
import com.vayana.reader.api.PageTextSource
import kotlin.math.ceil

/** Text recognition for a scanned PDF (docs/SCANNED_PDF_TEXT_PLAN.md), as the style panel and the page offer it. */
internal class TextRecognitionControls(
    val state: TextRecognitionUi?,
    val pageText: PageTextSource?,
    val onRecognize: () -> Unit,
    val onStop: () -> Unit,
    val onRemove: () -> Unit,
) {
    /** Only for a scanned book: the page on screen has no text of its own, or recognition has started on it. */
    val applies: Boolean
        get() = state != null && (pageText == PageTextSource.NONE || pageText == PageTextSource.RECOGNIZED ||
            state.running || state.recognizedPages > 0)
}

/** The PDF page section's text recognition rows: what it is, how far it has got, and what can be done. */
@Composable
internal fun TextRecognitionSection(controls: TextRecognitionControls) {
    val state = controls.state ?: return
    if (!controls.applies) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.reader_ocr_section_title), style = MaterialTheme.typography.labelLarge)
        Text(
            text = when {
                state.running -> stringResource(R.string.reader_ocr_running, state.recognizedPages, state.totalPages)
                state.recognizedPages > 0 -> stringResource(R.string.reader_ocr_done, state.recognizedPages, state.totalPages)
                else -> stringResource(R.string.reader_ocr_section_scanned)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            when {
                state.running -> OutlinedButton(onClick = controls.onStop) { Text(stringResource(R.string.reader_ocr_stop)) }
                state.recognizedPages < state.totalPages -> FilledTonalButton(onClick = controls.onRecognize) {
                    Text(stringResource(if (state.recognizedPages > 0) R.string.reader_ocr_continue else R.string.reader_ocr_start))
                }
            }
            if (state.recognizedPages > 0 && !state.running) {
                TextButton(onClick = controls.onRemove) { Text(stringResource(R.string.reader_ocr_remove)) }
            }
        }
    }
}

/** On a scanned page: a chip offering recognition, or showing its progress while it runs. */
@Composable
internal fun TextRecognitionChip(controls: TextRecognitionControls, modifier: Modifier = Modifier) {
    val state = controls.state ?: return
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val offer = controls.pageText == PageTextSource.NONE && state.recognizedPages == 0 && !state.running && !dismissed
    val progress = state.running && controls.pageText == PageTextSource.NONE
    if (!offer && !progress) return
    AssistChip(
        onClick = controls.onRecognize,
        label = {
            Text(
                if (progress) stringResource(R.string.reader_ocr_chip_running, state.recognizedPages, state.totalPages)
                else stringResource(R.string.reader_ocr_chip),
            )
        },
        trailingIcon = if (offer) ({
            IconButton(onClick = { dismissed = true }, modifier = Modifier) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.reader_ocr_chip_hide))
            }
        }) else null,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    )
}

/** Choosing a model and starting; while recognition runs, how far it has got and Stop. */
@Composable
internal fun TextRecognitionDialog(
    state: TextRecognitionUi,
    onStart: (OcrModel) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var model by rememberSaveable { mutableStateOf(OcrModel.MalayalamFast) }
    val remaining = (state.totalPages - state.recognizedPages).coerceAtLeast(0)
    // ~1 s a page on a recent phone (phase 1 measurements); rounded up to whole minutes.
    val minutes = ceil(remaining * SecondsPerPage / 60.0).toInt().coerceAtLeast(1)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_ocr_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (state.running) {
                    Text(stringResource(R.string.reader_ocr_running, state.recognizedPages, state.totalPages))
                    return@Column
                }
                Text(stringResource(R.string.reader_ocr_dialog_body, minutes))
                Column(Modifier.selectableGroup()) {
                    for ((option, title, subtitle) in listOf(
                        Triple(OcrModel.MalayalamFast, R.string.reader_ocr_model_malayalam, R.string.reader_ocr_model_malayalam_subtitle),
                        Triple(OcrModel.MalayalamScript, R.string.reader_ocr_model_mixed, R.string.reader_ocr_model_mixed_subtitle),
                    )) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = model == option, onClick = { model = option }, role = Role.RadioButton),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = model == option, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = stringResource(subtitle) + " " + if (option in state.installedModels) {
                                        stringResource(R.string.reader_ocr_installed)
                                    } else {
                                        stringResource(R.string.reader_ocr_download, Formatter.formatShortFileSize(context, option.sizeBytes))
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.running) {
                TextButton(onClick = { onStop(); onDismiss() }) { Text(stringResource(R.string.reader_ocr_stop)) }
            } else {
                TextButton(onClick = { onStart(model); onDismiss() }) { Text(stringResource(R.string.reader_ocr_dialog_start)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_ocr_dialog_close)) } },
    )
}

private const val SecondsPerPage = 1.0

/** Scan or Text, for a scanned book whose text has been recognised; nothing for any other book. */
@Composable
internal fun ReadingViewSwitch(state: ReadingViewUi?, onShowText: (Boolean) -> Unit) {
    if (state?.textAvailable != true) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(false to R.string.reader_view_scan, true to R.string.reader_view_text).forEachIndexed { index, (text, label) ->
                SegmentedButton(
                    selected = state.textActive == text,
                    onClick = { onShowText(text) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                ) { Text(stringResource(label)) }
            }
        }
        Text(
            text = stringResource(if (state.textActive) R.string.reader_view_text_note else R.string.reader_view_scan_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
