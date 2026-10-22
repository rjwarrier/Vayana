package com.vayana.feature.library

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.resources.R
import com.vayana.format.ocr.BookRecognition
import com.vayana.format.ocr.OcrModel
import com.vayana.format.ocr.OcrStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class PreparationRow(val book: Book, val job: WorkInfo, val state: PreparationState)

@HiltViewModel
internal class OfflinePreparationViewModel @Inject constructor(
    private val preparation: OfflinePreparation,
    books: BookRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    val rows = combine(preparation.jobs, BookRecognition.jobs(context), books.observeAll(), OcrStore.changes) { jobs, recognition, books, _ ->
        val byId = books.associateBy { it.id }
        val byHash = books.associateBy { it.fileHash }
        val store = BookRecognition.store(context)
        val requested = jobs.mapNotNull { job ->
            val id = job.tags.firstOrNull { it.startsWith(OfflinePreparation.BookTagPrefix) }
                ?.removePrefix(OfflinePreparation.BookTagPrefix)?.toLongOrNull()
            val book = byId[id] ?: return@mapNotNull null
            if ("${OfflinePreparation.SyncTagPrefix}${book.syncId}" !in job.tags) return@mapNotNull null
            val hash = job.outputData.getString(OfflinePreparation.KeyOcrHash)
            val legacy = if (hash != null && recognition.none { BookRecognition.bookTag(hash) in it.tags })
                BookRecognition.jobInfo(context, hash).first() else null
            var state = preparationState(job, recognition, legacy)
            if (hash != null) {
                val finalized = store.searchRevision(hash) != null
                if (state == PreparationState.READY && !finalized) state = PreparationState.CANCELLED
                else if (state == PreparationState.WAITING && legacy == null && recognition.none { BookRecognition.bookTag(hash) in it.tags })
                    state = if (finalized) PreparationState.READY else PreparationState.FAILED
            }
            PreparationRow(book, job, state)
        }
        val represented = requested.mapTo(HashSet()) { it.book.fileHash }
        val activeRecognition = recognition.filterNot { it.state.isFinished }.mapNotNull { job ->
            val hash = job.tags.firstOrNull { it.startsWith("ocr-book:") }?.removePrefix("ocr-book:")
            val book = byHash[hash]?.takeIf { it.fileHash !in represented } ?: return@mapNotNull null
            PreparationRow(book, job, if (job.state == WorkInfo.State.RUNNING) PreparationState.RECOGNIZING else PreparationState.WAITING)
        }
        (requested + activeRecognition).sortedWith(compareBy<PreparationRow> { it.state == PreparationState.READY }.thenBy { it.book.title.lowercase() })
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow(false)

    fun enqueue(books: List<Book>, model: OcrModel?, wifiOnly: Boolean) = action { preparation.enqueue(books, model, wifiOnly) }
    fun cancel(row: PreparationRow) = action { preparation.cancel(row.book,
        row.job.outputData.getString(OfflinePreparation.KeyOcrHash)
            ?: row.job.tags.firstOrNull { it.startsWith("prepare-ocr:") }?.removePrefix("prepare-ocr:") ?: row.book.fileHash) }
    fun retry(row: PreparationRow) {
        val model = row.job.tags.firstOrNull { it.startsWith(OfflinePreparation.ModelTagPrefix) }
            ?.removePrefix(OfflinePreparation.ModelTagPrefix)?.let(OcrModel::byId)
        enqueue(listOf(row.book), model, row.job.constraints.requiredNetworkType == androidx.work.NetworkType.UNMETERED)
    }
    private fun action(block: suspend () -> Unit) { viewModelScope.launch {
        busy.value = true; error.value = false
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error.value = true } finally { busy.value = false }
    } }
}

@Composable
internal fun OfflinePreparationDialog(books: List<Book>, selected: Set<Long>, onDismiss: () -> Unit, onOpen: (Book) -> Unit) {
    val viewModel: OfflinePreparationViewModel = hiltViewModel()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var recognize by remember { mutableStateOf(false) }
    var wifi by remember { mutableStateOf(true) }
    var model by remember { mutableStateOf(OcrModel.EnglishFast) }
    var expanded by remember { mutableStateOf(false) }
    val selectedBooks = books.filter { it.id in selected && !it.format.isOffline }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.offline_prepare_title)) }, text = {
        PagedLazyColumn(modifier = Modifier.heightIn(max = Sizes.offlinePreparationDialogMaxHeight), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item {
                Text(stringResource(R.string.offline_prepare_detail))
                Text(stringResource(R.string.tools_selected, selectedBooks.size))
                Row(Modifier.fillMaxWidth().toggleable(wifi, role = Role.Checkbox, onValueChange = { wifi = it }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(wifi, null); Text(stringResource(R.string.offline_prepare_wifi))
                }
                Row(Modifier.fillMaxWidth().toggleable(recognize, role = Role.Checkbox, onValueChange = { recognize = it }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(recognize, null); Text(stringResource(R.string.offline_prepare_recognize))
                }
                if (recognize) Box {
                    TextButton(onClick = { expanded = true }) { Text(preparationModelName(model)) }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        OcrModel.entries.forEach { option -> DropdownMenuItem(text = { Text(preparationModelName(option)) },
                            onClick = { model = option; expanded = false }) }
                    }
                }
                Button(enabled = !busy && selectedBooks.isNotEmpty(), onClick = { viewModel.enqueue(selectedBooks, model.takeIf { recognize }, wifi) }) {
                    Text(stringResource(R.string.offline_prepare_start))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error) Text(stringResource(R.string.tools_failed), color = MaterialTheme.colorScheme.error)
                if (rows.isEmpty()) Text(stringResource(R.string.offline_prepare_empty))
            }
            items(rows, key = { it.job.id }) { row ->
                Column {
                    TextButton(onClick = { onOpen(row.book) }) { Text(row.book.title, maxLines = 2) }
                    Text(stringResource(row.state.label()))
                    if (row.state == PreparationState.FAILED) Text(stringResource(when (row.job.outputData.getString(OfflinePreparation.KeyError)) {
                        "syncOff" -> R.string.sync_detail_sync_off
                        "config" -> R.string.sync_detail_settings_incomplete
                        "missingAsset" -> R.string.sync_detail_cloud_book_missing
                        "missingFile" -> R.string.reader_error_file_unavailable
                        else -> R.string.library_book_cloud_download_failed
                    }), style = MaterialTheme.typography.bodySmall)
                    if (row.state in setOf(PreparationState.FAILED, PreparationState.CANCELLED)) {
                        TextButton(enabled = !busy, onClick = { viewModel.retry(row) }) { Text(stringResource(R.string.gutenberg_retry)) }
                    } else if (row.state != PreparationState.READY) {
                        TextButton(enabled = !busy, onClick = { viewModel.cancel(row) }) { Text(stringResource(R.string.tools_cancel)) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tools_close)) } })
}

@Composable private fun preparationModelName(model: OcrModel): String = when (model) {
    OcrModel.MalayalamFast -> stringResource(R.string.ocr_model_malayalam)
    OcrModel.MalayalamScript -> stringResource(R.string.ocr_model_mixed)
    else -> Locale.forLanguageTag(model.textLanguage).getDisplayLanguage(Locale.getDefault())
}

private fun PreparationState.label(): Int = when (this) {
    PreparationState.WAITING -> R.string.offline_prepare_waiting
    PreparationState.DOWNLOADING -> R.string.sync_detail_downloading_book
    PreparationState.RECOGNIZING -> R.string.offline_prepare_recognizing
    PreparationState.READY -> R.string.offline_prepare_ready
    PreparationState.FAILED -> R.string.tools_failed
    PreparationState.CANCELLED -> R.string.offline_prepare_cancelled
}
