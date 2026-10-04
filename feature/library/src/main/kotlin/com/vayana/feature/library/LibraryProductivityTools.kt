package com.vayana.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import com.vayana.core.designsystem.theme.PagedLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.Shelf
import com.vayana.core.datastore.settings.SmartShelf
import com.vayana.core.datastore.settings.SmartShelfStatus
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SmartShelvesDialog(
    shelves: List<SmartShelfItem>, onSave: suspend (SmartShelf) -> Unit, onDelete: suspend (String) -> Unit,
    onBookClick: (Book) -> Unit, onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<SmartShelf?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (editing != null) {
        SmartShelfEditor(editing!!, onDismiss = { editing = null }, onSave = { onSave(it); editing = null })
        return
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.tools_smart_shelves)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.tools_smart_shelves_hint))
                if (error) Text(stringResource(R.string.tools_failed), color = MaterialTheme.colorScheme.error)
                LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    shelves.forEach { shelfItem ->
                      item(key = "shelf-${shelfItem.shelf.id}") {
                        val item = shelfItem
                        Column {
                            TextButton(onClick = { expanded = if (expanded == item.shelf.id) null else item.shelf.id }) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(item.shelf.name, style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.tools_matches, item.books.size))
                                }
                            }
                            Row {
                                TextButton(enabled = !busy, onClick = { editing = item.shelf }) { Text(stringResource(R.string.tools_edit)) }
                                TextButton(enabled = !busy, onClick = {
                                    scope.launch {
                                        busy = true; error = false
                                        try { onDelete(item.shelf.id) } catch (e: CancellationException) { throw e }
                                        catch (_: Exception) { error = true } finally { busy = false }
                                    }
                                }) { Text(stringResource(R.string.tools_delete)) }
                            }
                            HorizontalDivider()
                        }
                      }
                      if (expanded == shelfItem.shelf.id) {
                          if (shelfItem.books.isEmpty()) item(key = "empty-${shelfItem.shelf.id}") { Text(stringResource(R.string.tools_no_matches)) }
                          items(shelfItem.books, key = { "${shelfItem.shelf.id}-${it.id}" }) { book ->
                              TextButton(onClick = { onBookClick(book) }) { Text(book.title) }
                          }
                      }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && shelves.size < 50, onClick = { editing = SmartShelf(name = "") }) { Text(stringResource(R.string.tools_new_shelf)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.tools_close)) } },
    )
}

@Composable
private fun SmartShelfEditor(shelf: SmartShelf, onDismiss: () -> Unit, onSave: suspend (SmartShelf) -> Unit) {
    var name by remember(shelf.id) { mutableStateOf(shelf.name) }
    var query by remember(shelf.id) { mutableStateOf(shelf.query) }
    var author by remember(shelf.id) { mutableStateOf(shelf.author) }
    var tag by remember(shelf.id) { mutableStateOf(shelf.tag) }
    var days by remember(shelf.id) { mutableStateOf(shelf.dormantDays.takeIf { it > 0 }?.toString().orEmpty()) }
    var status by remember(shelf.id) { mutableStateOf(shelf.status) }
    var notes by remember(shelf.id) { mutableStateOf(shelf.withNotes) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.tools_smart_shelves)) },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.tools_name)) }, singleLine = true)
                OutlinedTextField(query, { query = it.take(500) }, label = { Text(stringResource(R.string.tools_query)) }, singleLine = true)
                OutlinedTextField(author, { author = it.take(160) }, label = { Text(stringResource(R.string.tools_author)) }, singleLine = true)
                OutlinedTextField(tag, { tag = it.take(160) }, label = { Text(stringResource(R.string.tools_tag)) }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SmartShelfStatus.entries.forEach { value -> FilterChip(selected = value == status,
                        onClick = { status = value }, label = { Text(stringResource(value.labelRes())) }) }
                }
                OutlinedTextField(days, { days = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.tools_dormant)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(notes, { notes = it }); Text(stringResource(R.string.tools_with_notes))
                }
                if (error) Text(stringResource(R.string.tools_failed), color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            TextButton(enabled = name.isNotBlank() && !busy, onClick = {
                scope.launch {
                    busy = true; error = false
                    try { onSave(shelf.copy(name = name, query = query, author = author, tag = tag, status = status, dormantDays = days.toIntOrNull() ?: 0, withNotes = notes)) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { error = true } finally { busy = false }
                }
            }) { Text(stringResource(R.string.tools_save)) }
        }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.tools_cancel)) } })
}

private fun SmartShelfStatus.labelRes() = when (this) {
    SmartShelfStatus.ALL -> R.string.tools_all
    SmartShelfStatus.UNREAD -> R.string.tools_unread
    SmartShelfStatus.READING -> R.string.tools_reading
    SmartShelfStatus.FINISHED -> R.string.tools_finished
    SmartShelfStatus.PAUSED -> R.string.disposition_paused
    SmartShelfStatus.DNF -> R.string.disposition_dnf
}

@Composable
internal fun BulkBooksDialog(books: List<Book>, shelves: List<Shelf>, onAction: suspend (Set<Long>, BulkLibraryAction) -> BulkLibraryResult, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var tags by remember { mutableStateOf("") }
    var chooseShelf by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BulkLibraryResult?>(null) }
    val scope = rememberCoroutineScope()
    val visibleIds = remember(books) { books.mapTo(HashSet()) { it.id } }
    val activeSelected = selected.intersect(visibleIds)
    fun run(action: BulkLibraryAction) {
        val ids = activeSelected.toSet()
        scope.launch {
            busy = true; result = null; error = false
            try { result = onAction(ids, action) } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = true } finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.tools_select_books)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.tools_selected, activeSelected.size))
                Row {
                    TextButton(enabled = !busy, onClick = { selected = visibleIds }) { Text(stringResource(R.string.tools_select_all)) }
                    TextButton(enabled = !busy, onClick = { selected = emptySet() }) { Text(stringResource(R.string.tools_clear_selection)) }
                }
                LazyColumn(Modifier.heightIn(max = 250.dp)) {
                    items(books, key = { it.id }) { book ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { selected = if (book.id in selected) selected - book.id else selected + book.id }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(book.id in activeSelected, onCheckedChange = null)
                            Text(book.title, modifier = Modifier.weight(1f), maxLines = 2)
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                result?.let { Text(stringResource(R.string.tools_bulk_result, it.completed, it.failed, it.skipped)) }
                if (error) Text(stringResource(R.string.tools_failed), color = MaterialTheme.colorScheme.error)
                TextButton(enabled = activeSelected.isNotEmpty() && !busy && shelves.isNotEmpty(), onClick = { chooseShelf = true }) { Text(stringResource(R.string.tools_add_to_shelf)) }
                if (shelves.isEmpty()) Text(stringResource(R.string.tools_create_shelf_first), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(tags, { tags = it.take(1024) }, enabled = !busy, label = { Text(stringResource(R.string.tools_add_tags)) }, supportingText = { Text(stringResource(R.string.tools_tags_hint)) })
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(enabled = activeSelected.isNotEmpty() && tags.isNotBlank() && !busy, onClick = { run(BulkLibraryAction.AddTags(tags)) }) { Text(stringResource(R.string.tools_add_tags)) }
                    TextButton(enabled = activeSelected.isNotEmpty() && !busy, onClick = { run(BulkLibraryAction.Download) }) { Text(stringResource(R.string.tools_download_selected)) }
                }
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.tools_close)) } })
    if (chooseShelf) AlertDialog(onDismissRequest = { chooseShelf = false }, title = { Text(stringResource(R.string.tools_add_to_shelf)) },
        text = { LazyColumn(Modifier.heightIn(max = 350.dp)) { items(shelves, key = { it.id }) { shelf ->
            TextButton(onClick = { chooseShelf = false; run(BulkLibraryAction.AddToShelf(shelf.id)) }) { Text(shelf.name) }
        } } }, confirmButton = { TextButton(onClick = { chooseShelf = false }) { Text(stringResource(R.string.tools_cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinishByCard(book: Book, target: LocalDate?, onChange: suspend (LocalDate?) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save(date: LocalDate?) { scope.launch {
        busy = true; error = false
        try { onChange(date); picker = false } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true } finally { busy = false }
    } }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.tools_finish_by), style = MaterialTheme.typography.titleMedium)
            target?.let { date ->
                Text(stringResource(R.string.tools_plan_date, date.toString()))
                val plan = finishByPlan(book, date)
                Text(when (plan.unit) {
                    PlanUnit.PAGES -> stringResource(R.string.tools_plan_pages, plan.amount, plan.days)
                    PlanUnit.MINUTES -> stringResource(R.string.tools_plan_minutes, plan.amount, plan.days)
                    PlanUnit.PERCENT -> stringResource(R.string.tools_plan_percent, plan.amount, plan.days)
                    PlanUnit.FINISHED -> stringResource(R.string.tools_plan_finished)
                    PlanUnit.OVERDUE -> stringResource(R.string.tools_plan_overdue)
                })
            }
            if (error) Text(stringResource(R.string.tools_failed), color = MaterialTheme.colorScheme.error)
            Row {
                TextButton(enabled = !busy, onClick = { picker = true }) { Text(stringResource(if (target == null) R.string.tools_set_target else R.string.tools_change_target)) }
                if (target != null) TextButton(enabled = !busy, onClick = { save(null) }) { Text(stringResource(R.string.tools_clear_target)) }
            }
        }
    }
    if (picker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = (target ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { if (!busy) picker = false }, confirmButton = {
            TextButton(enabled = !busy && dateState.selectedDateMillis != null, onClick = {
                dateState.selectedDateMillis?.let { save(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.tools_save)) }
        }, dismissButton = { TextButton(enabled = !busy, onClick = { picker = false }) { Text(stringResource(R.string.tools_cancel)) } }) {
            DatePicker(dateState, title = { Text(stringResource(R.string.tools_finish_by), Modifier.padding(Spacing.md)) })
            if (error) Text(stringResource(R.string.tools_failed), Modifier.padding(Spacing.md), color = MaterialTheme.colorScheme.error)
        }
    }
}
