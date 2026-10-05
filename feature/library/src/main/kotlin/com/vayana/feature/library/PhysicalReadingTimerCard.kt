package com.vayana.feature.library

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.PhysicalReadingSessionSummary
import com.vayana.core.designsystem.component.ConnectedButton
import com.vayana.core.designsystem.component.VayanaConnectedButtonGroup
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.theme.vayanaContentTransform
import com.vayana.core.designsystem.theme.vayanaFadeIn
import com.vayana.core.designsystem.theme.vayanaFadeOut
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PhysicalReadingTimerCard(book: Book, viewModel: LibraryViewModel) {
    val controller = viewModel.physicalReadingTimer
    val active by controller.session.collectAsStateWithLifecycle()
    val companionStatus by controller.companionStatus.collectAsStateWithLifecycle()
    val pageLogs by remember(book.id) { viewModel.observeRecentPhysicalSessions(book.id, 3) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val paceSessions by remember(book.id) { viewModel.observeRecentPhysicalSessions(book.id, 5, forwardOnly = true) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val summary by remember(book.id) { viewModel.observePhysicalSessionSummary(book.id) }
        .collectAsStateWithLifecycle(initialValue = PhysicalReadingSessionSummary())
    val timer = active?.takeIf { it.bookId == book.id }
    val lastPage = pageLogs.firstOrNull()?.endPage
    val currentPage = book.currentPage() ?: lastPage
    val pace = remember(paceSessions, book.pageCount, currentPage) { physicalReadingPace(paceSessions, book.pageCount, currentPage) }
    val scope = rememberCoroutineScope()
    var showStart by rememberSaveable(book.id) { mutableStateOf(false) }
    var showStop by rememberSaveable(book.id) { mutableStateOf(false) }
    var showManual by rememberSaveable(book.id) { mutableStateOf(false) }
    var showHistory by rememberSaveable(book.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(timer?.syncId, timer?.phase) {
        if (timer?.phase == PhysicalTimerPhase.STOPPED) showStop = true
    }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    BookDetailSection(icon = Icons.Outlined.Timer, title = stringResource(R.string.physical_timer_title)) {
        PhysicalTimerClock(timer, Modifier.align(Alignment.CenterHorizontally))
        val statusTransition = vayanaContentTransform<PhysicalTimerPhase?>()
        AnimatedContent(
            targetState = timer?.phase,
            transitionSpec = { statusTransition().using(null) },
            label = "PhysicalTimerStatus",
        ) { phase ->
          Text(stringResource(when (phase) {
            PhysicalTimerPhase.RUNNING -> R.string.physical_timer_running
            PhysicalTimerPhase.PAUSED -> R.string.physical_timer_paused
            PhysicalTimerPhase.STOPPED -> R.string.physical_timer_stopped
            null -> R.string.physical_timer_ready
          }), style = MaterialTheme.typography.bodyMedium)
        }
        if (companionStatus.isNotEmpty()) Text(companionStatus, style = MaterialTheme.typography.bodySmall)
        if (active != null && timer == null) {
            Text(stringResource(R.string.physical_timer_other_book, active!!.bookTitle))
            TextButton(onClick = { perform { controller.discard() } }, enabled = !busy) {
                Text(stringResource(R.string.physical_timer_discard_active))
            }
        } else if (busy || controller.hasPendingCommand) {
            VayanaCircularProgressIndicator()
        } else {
            val buttons = when (timer?.phase) {
                null -> listOf(ConnectedButton(stringResource(R.string.physical_timer_start), Icons.Outlined.PlayArrow, { showStart = true }))
                PhysicalTimerPhase.RUNNING -> listOf(
                    ConnectedButton(stringResource(R.string.physical_timer_pause), Icons.Outlined.Pause, { perform { controller.pause() } }),
                    ConnectedButton(stringResource(R.string.physical_timer_stop), Icons.Outlined.Stop, { perform { controller.stop() } }),
                )
                PhysicalTimerPhase.PAUSED -> listOf(
                    ConnectedButton(stringResource(R.string.physical_timer_resume), Icons.Outlined.PlayArrow, { perform { controller.resume() } }),
                    ConnectedButton(stringResource(R.string.physical_timer_stop), Icons.Outlined.Stop, { perform { controller.stop() } }),
                )
                PhysicalTimerPhase.STOPPED -> listOf(ConnectedButton(stringResource(R.string.physical_timer_finish_log), Icons.Outlined.Stop, { showStop = true }))
            }
            VayanaConnectedButtonGroup(buttons, iconAboveLabel = true)
        }
        AnimatedVisibility(visible = failed, enter = vayanaFadeIn(), exit = vayanaFadeOut()) {
            Text(stringResource(R.string.physical_timer_failed), color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedButton(
                onClick = { failed = false; showManual = true },
                enabled = !busy && timer == null,
                modifier = Modifier.weight(1f),
                shape = Radii.buttonShape,
            ) {
                Text(stringResource(R.string.physical_manual_add))
            }
            if (summary.sessionCount > 0) {
                FilledTonalButton(
                    onClick = { showHistory = true },
                    modifier = Modifier.weight(1f),
                    shape = Radii.buttonShape,
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null)
                    Text(
                        stringResource(R.string.physical_timer_log_button, summary.sessionCount),
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
        }
    }
    if (showHistory) {
        val sessions by remember(book.id) { viewModel.observeReadingSessionsForBook(book.id) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val history = remember(sessions) { sessions.filter { it.startPage != null && it.endPage != null } }
        PhysicalSessionHistoryDialog(
            book = book,
            sessions = history,
            summary = summary,
            pace = pace,
            onUpdatePages = { session, startPage, endPage ->
                viewModel.updatePhysicalReadingSessionPages(
                    syncId = session.syncId,
                    startPage = startPage,
                    endPage = endPage,
                    pageCount = book.pageCount,
                )
            },
            onDeleteSession = { session -> viewModel.deletePhysicalReadingSession(book.id, session.syncId) },
            onDismiss = { showHistory = false },
        )
    }
    if (showStart && active == null) {
        PhysicalTimerPageDialog(book, currentPage ?: 0, starting = true, busy = busy, failed = failed,
            onDismiss = { showStart = false }, onDiscard = null,
            onSave = { page, total -> perform { controller.start(book.id, book.homeLibraryDisplayTitle, page, total); showStart = false } })
    }
    if (showStop && timer?.phase == PhysicalTimerPhase.STOPPED) {
        PhysicalTimerPageDialog(book.copy(pageCount = timer.pageCount ?: book.pageCount), timer.startPage, starting = false, busy = busy, failed = failed,
            onDismiss = { showStop = false }, onDiscard = { perform { controller.discard(); showStop = false } },
            onSave = { page, total -> perform { controller.save(page, total); showStop = false } })
    }
    if (showManual) {
        ManualPhysicalSessionDialog(book, currentPage ?: 0, busy, failed,
            onDismiss = { showManual = false },
            onSave = { syncId, log, updateProgress -> perform {
                viewModel.saveManualPhysicalReadingSession(book.id, syncId, log, updateProgress)
                showManual = false
            } })
    }
}

/** Only this display observes the tick; actions, dialogs and session history do not recompose each second. */
@Composable
internal fun PhysicalTimerClock(timer: PhysicalTimerSession?, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayMedium,
    color: Color = MaterialTheme.colorScheme.primary) {
    var now by remember(timer?.syncId) { mutableStateOf(SystemClock.elapsedRealtime()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(timer, lifecycle) {
        if (timer?.phase != PhysicalTimerPhase.RUNNING) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(1000 - timer.elapsedMillis(now) % 1000)
            }
        }
    }
    Text(
        text = formatTimerClock((timer?.elapsedMillis(now) ?: 0) / 1000),
        style = style,
        color = color,
        modifier = modifier,
    )
}

internal fun formatTimerClock(seconds: Long): String = String.format(Locale.getDefault(), "%02d:%02d:%02d",
    seconds.coerceAtLeast(0) / 3600, seconds.coerceAtLeast(0) / 60 % 60, seconds.coerceAtLeast(0) % 60)

@Composable
internal fun PhysicalTimerPageDialog(book: Book, page: Int, starting: Boolean, busy: Boolean, failed: Boolean,
    onDismiss: () -> Unit, onDiscard: (() -> Unit)?, onSave: (Int, Int?) -> Unit) {
    var pageText by rememberSaveable(book.id, starting) { mutableStateOf(page.toString()) }
    var totalText by rememberSaveable(book.id, starting) { mutableStateOf(book.pageCount?.toString().orEmpty()) }
    val current = pageText.toIntOrNull()
    val total = if (totalText.isBlank()) book.pageCount else totalText.toIntOrNull()
    val startPage = if (starting) current else page
    val valid = validPhysicalTimerPages(startPage, current, totalText, book.pageCount)
    val startPageTooHigh = !starting && total != null && page > total
    ExpressiveDialogSurface(onDismissRequest = { if (!busy) onDismiss() }, scrollable = true) {
        ExpressiveDialogHeader(Icons.Outlined.Timer,
            stringResource(if (starting) R.string.physical_timer_start else R.string.physical_timer_log_title),
            supportingText = stringResource(if (starting) R.string.physical_timer_start_hint else R.string.physical_timer_log_hint))
        OfflinePageFields(totalPages = totalText, currentPage = pageText,
            onTotalPagesChange = { totalText = it }, onCurrentPageChange = { pageText = it },
            currentPageTooHigh = current != null && total != null && current > total,
            enabled = !busy, showTotalPages = starting)
        if (startPageTooHigh) Text(stringResource(R.string.physical_timer_start_page_too_high, page),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (failed) Text(stringResource(R.string.physical_timer_failed), color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
            if (onDiscard != null) TextButton(onClick = onDiscard, enabled = !busy) { Text(stringResource(R.string.physical_timer_discard)) }
            FilledTonalButton(onClick = onDismiss, enabled = !busy, shape = Radii.buttonShape) {
                Text(stringResource(R.string.library_edit_metadata_cancel))
            }
            Button(onClick = { onSave(current!!, total) }, enabled = valid && !busy, shape = Radii.buttonShape) {
                Text(stringResource(if (starting) R.string.physical_timer_start else R.string.library_reading_date_save))
            }
        }
    }
}
