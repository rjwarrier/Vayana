package com.vayana.feature.notes

import androidx.compose.foundation.layout.*
import com.vayana.core.designsystem.theme.PagedLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vayana.core.resources.R
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NotesNotebookExportDialog(books: List<BookNotesItem>, onExport: suspend (List<BookNotesItem>) -> Unit, onDismiss: () -> Unit) {
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selectedBooks = books.filter { it.book.id in selectedIds }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.tools_export_notebooks)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tools_export_notebooks_hint))
                Row {
                    TextButton(enabled = !busy, onClick = { selectedIds = books.map { it.book.id }.toSet() }) { Text(stringResource(R.string.tools_select_all)) }
                    TextButton(enabled = !busy, onClick = { selectedIds = emptySet() }) { Text(stringResource(R.string.tools_clear_selection)) }
                }
                Text(stringResource(R.string.tools_selected, selectedBooks.size))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(books, key = { it.book.id }) { item ->
                        val checked = item.book.id in selectedIds
                        Row(
                            Modifier.fillMaxWidth().toggleable(checked, enabled = !busy, role = Role.Checkbox, onValueChange = { selected ->
                                selectedIds = if (selected) selectedIds + item.book.id else selectedIds - item.book.id
                            }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked, onCheckedChange = null, enabled = !busy)
                            Column(Modifier.weight(1f)) {
                                Text(item.book.title, style = MaterialTheme.typography.bodyLarge)
                                item.book.author?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                if (busy) VayanaCircularProgressIndicator()
                if (failed) Text(stringResource(R.string.tools_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && selectedBooks.isNotEmpty(), onClick = {
                busy = true
                failed = false
                scope.launch {
                    try {
                        onExport(selectedBooks)
                        onDismiss()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.tools_export)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.tools_cancel)) } },
    )
}
