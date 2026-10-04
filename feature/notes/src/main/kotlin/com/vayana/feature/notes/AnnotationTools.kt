package com.vayana.feature.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vayana.core.database.model.*
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.repository.*
import com.vayana.core.datastore.settings.*
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.filesystem.ResolvedBooks
import com.vayana.core.resources.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class AnnotationToolsViewModel @Inject constructor(
    private val annotations: AnnotationRepository,
    resolved: ResolvedBooks,
    private val settings: SettingsRepository,
    val exporter: AutomaticNotebookExport,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    val physicalBooks = resolved.all.map { books -> books.filter { it.format == BookFormat.PHYSICAL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val folder = settings.observe(SettingsRegistry.NotebookExportFolder)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    suspend fun question(annotation: Annotation, value: String?) {
        val current = annotations.getById(annotation.id) ?: error("Annotation no longer exists")
        val normalized = value?.trim()?.take(2000)?.takeIf { it.isNotEmpty() }
        if (normalized == current.reviewQuestion) return
        annotations.update(current.copy(reviewQuestion = normalized))
    }
    suspend fun chooseFolder(uri: Uri?) {
        if (uri != null) context.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        settings.update(SettingsRegistry.NotebookExportFolder, uri?.toString().orEmpty())
        if (uri != null) exporter.export()
    }
    suspend fun recognize(uri: Uri): String = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(context, uri)
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image).addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result.text)
                }.addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
            }
        } finally { recognizer.close() }
    }
    suspend fun savePhoto(bookId: Long, text: String, page: Int?, chapterTitle: String?) {
        require(text.isNotBlank() && text.length <= 20_000 && (page == null || page > 0))
        require(physicalBooks.value.any { it.id == bookId })
        annotations.create(bookId, AnnotationType.HIGHLIGHT, "yellow", "physical-page:${page ?: 0}:${UUID.randomUUID()}",
            chapterTitle, null, text.trim(), null)
    }
}

@Composable
internal fun ReviewQuestionAction(annotation: Annotation, viewModel: AnnotationToolsViewModel = hiltViewModel()) {
    if (annotation.type != AnnotationType.HIGHLIGHT || annotation.selectedText.isBlank() || annotation.isCommunityQuote()) return
    var editing by rememberSaveable(annotation.id) { mutableStateOf(false) }
    IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.School,
        contentDescription = stringResource(if (annotation.reviewQuestion == null) R.string.flashcard_create else R.string.flashcard_edit)) }
    if (!editing) return
    var question by remember(annotation.id) { mutableStateOf(annotation.reviewQuestion.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save(value: String?) { busy = true; scope.launch {
        try { viewModel.question(annotation, value); editing = false }
        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true }
        finally { busy = false }
    } }
    AlertDialog(onDismissRequest = { if (!busy) editing = false }, title = { Text(stringResource(R.string.flashcard_create)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.flashcard_hint))
            OutlinedTextField(question, { question = it.take(2000) }, label = { Text(stringResource(R.string.flashcard_question)) }, enabled = !busy)
            Text(stringResource(R.string.flashcard_answer), style = MaterialTheme.typography.labelLarge)
            Text(annotation.selectedText)
            if (failed) Text(stringResource(R.string.feature_action_failed), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !busy && question.isNotBlank(), onClick = { save(question) }) { Text(stringResource(R.string.flashcard_save)) } },
        dismissButton = { Row {
            if (annotation.reviewQuestion != null) TextButton(enabled = !busy, onClick = { save(null) }) { Text(stringResource(R.string.flashcard_remove)) }
            TextButton(enabled = !busy, onClick = { editing = false }) { Text(stringResource(R.string.tools_close)) }
        } })
}

@Composable
internal fun NotebookExportDialog(onDismiss: () -> Unit, viewModel: AnnotationToolsViewModel = hiltViewModel()) {
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val status by viewModel.exporter.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun run(action: suspend () -> Unit) { busy = true; scope.launch {
        try { action(); failed = false } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true } finally { busy = false }
    } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) run { viewModel.chooseFolder(uri) }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.notebook_auto_export)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.notebook_export_hint))
            Text(stringResource(when {
                failed || status == NotebookExportStatus.FAILED -> R.string.notebook_export_failed
                folder.isBlank() -> R.string.notebook_export_off
                status == NotebookExportStatus.DONE -> R.string.notebook_export_done
                else -> R.string.notebook_export_ready
            }))
            TextButton(enabled = !busy, onClick = { picker.launch(folder.takeIf { it.isNotBlank() }?.let(Uri::parse)) }) { Text(stringResource(R.string.notebook_choose_folder)) }
            if (folder.isNotBlank()) {
                TextButton(enabled = !busy, onClick = { run { viewModel.exporter.export() } }) { Text(stringResource(R.string.notebook_export_now)) }
                TextButton(enabled = !busy, onClick = { run { viewModel.chooseFolder(null) } }) { Text(stringResource(R.string.notebook_disable)) }
            }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.tools_close)) } })
}

@Composable
internal fun PhotoHighlightDialog(onDismiss: () -> Unit, viewModel: AnnotationToolsViewModel = hiltViewModel()) {
    val books by viewModel.physicalBooks.collectAsStateWithLifecycle()
    var bookId by rememberSaveable { mutableStateOf<Long?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var recognizing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val selected = books.firstOrNull { it.id == bookId } ?: books.firstOrNull()
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) {
        busy = true; recognizing = true
        scope.launch {
            try { text = viewModel.recognize(uri).take(20_000); failed = text.isBlank() }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true }
            finally { busy = false; recognizing = false }
        }
    } }
    val pageNumber = page.toIntOrNull()?.takeIf { it > 0 }
    val chapterTitle = pageNumber?.let { stringResource(R.string.photo_page_title, it) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.photo_highlight)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.photo_hint))
            TextButton(enabled = !busy, onClick = { picker.launch("image/*") }) { Text(stringResource(R.string.photo_choose)) }
            if (recognizing) Text(stringResource(R.string.photo_recognizing))
            if (books.isEmpty()) Text(stringResource(R.string.photo_no_books))
            var expanded by remember { mutableStateOf(false) }
            Box { TextButton(enabled = !busy, onClick = { expanded = true }) { Text(selected?.title ?: stringResource(R.string.photo_book)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    books.forEach { book -> DropdownMenuItem(text = { Text(book.title) }, onClick = { bookId = book.id; expanded = false }) }
                }
            }
            OutlinedTextField(page, { page = it.filter(Char::isDigit).take(8) }, label = { Text(stringResource(R.string.photo_page)) }, singleLine = true, enabled = !busy)
            OutlinedTextField(text, { text = it.take(20_000) }, label = { Text(stringResource(R.string.photo_text)) }, enabled = !busy)
            if (failed) Text(stringResource(R.string.photo_failed), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !busy && selected != null && text.isNotBlank() && (page.isBlank() || pageNumber != null), onClick = {
        selected?.let { book -> busy = true; scope.launch {
            try { viewModel.savePhoto(book.id, text, pageNumber, chapterTitle); onDismiss() }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true }
            finally { busy = false }
        } }
    }) { Text(stringResource(R.string.photo_save)) } }, dismissButton = {
        TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.tools_close)) }
    })
}
