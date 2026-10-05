package com.vayana.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFormat
import com.vayana.core.designsystem.theme.vayanaPressScale
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.theme.vayanaTween
import com.vayana.core.designsystem.theme.vayanaContentTransform
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun physicalReadingHomeBooks(books: List<Book>, activeBookId: Long?): List<Book> {
    val order = compareByDescending<Book> { it.id == activeBookId }
        .thenByDescending { it.lastReadAt ?: it.startedReadingAt ?: 0L }.thenBy { it.id }
    val recent = ArrayList<Book>(4)
    for (book in books) {
        if (book.readingDisposition != "ACTIVE" || book.format != BookFormat.PHYSICAL || (book.id != activeBookId && book.readingState() != BookReadingState.READING)) continue
        val index = recent.indexOfFirst { order.compare(book, it) < 0 }.let { if (it < 0) recent.size else it }
        if (index >= 3) continue
        recent.add(index, book)
        if (recent.size > 3) recent.removeAt(3)
    }
    return recent
}

@Composable
internal fun PhysicalReadingHomeShelf(
    books: List<Book>,
    timer: PhysicalTimerSession?,
    controller: PhysicalReadingTimerController,
    onSessionRecorded: (PhysicalSessionRecorded) -> Unit,
    onBookClick: (Book) -> Unit,
    onViewAll: () -> Unit,
) {
    var stopBookId by rememberSaveable { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(timer?.syncId, timer?.phase) {
        if (timer?.phase == PhysicalTimerPhase.STOPPED) stopBookId = timer.bookId
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.physical_home_reading), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onViewAll) { Text(stringResource(R.string.library_read_next_view_all)) }
        }
        if (failed) {
            Text(
                stringResource(R.string.physical_timer_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        books.forEach { book ->
            val active = timer?.takeIf { it.bookId == book.id }
            val currentPage = book.currentPage()
            val totalPages = book.pageCount?.takeIf { it > 0 }
            val progress = if (currentPage != null && totalPages != null) {
                currentPage.toFloat().div(totalPages).coerceIn(0f, 1f)
            } else null
            val accentColor by animateColorAsState(targetValue = when (active?.phase) {
                PhysicalTimerPhase.RUNNING -> MaterialTheme.colorScheme.primary
                PhysicalTimerPhase.PAUSED -> MaterialTheme.colorScheme.secondary
                PhysicalTimerPhase.STOPPED -> MaterialTheme.colorScheme.tertiary
                null -> MaterialTheme.colorScheme.primary
            }, animationSpec = vayanaTween(), label = "PhysicalTimerAccent")
            val interaction = remember(book.id) { MutableInteractionSource() }
            Surface(onClick = { onBookClick(book) }, interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().vayanaPressScale(interaction).vayanaAnimateContentSize(),
                shape = RoundedCornerShape(Radii.extraLargeIncreased),
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically) {
                    BookCover(book, Modifier.width(Sizes.coverWidthMin * 0.6f).bookSharedElement(book.id, BookOpenTransitionSource.COVER))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(book.homeLibraryDisplayTitle, style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        currentPage?.let { page ->
                            Text(
                                if (totalPages != null) stringResource(R.string.physical_home_page, page, totalPages)
                                else stringResource(R.string.physical_home_current_page, page),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        progress?.let {
                            VayanaLinearProgressIndicator(
                                progress = { it },
                                modifier = Modifier.fillMaxWidth(),
                                color = accentColor,
                                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                            )
                        }
                        if (active != null) {
                            Text(stringResource(when (active.phase) {
                                PhysicalTimerPhase.RUNNING -> R.string.physical_timer_running
                                PhysicalTimerPhase.PAUSED -> R.string.physical_timer_paused
                                PhysicalTimerPhase.STOPPED -> R.string.physical_timer_stopped
                            }),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                            )
                            PhysicalTimerClock(
                                timer = active,
                                style = MaterialTheme.typography.headlineSmall,
                                color = accentColor,
                            )
                        } else {
                            book.lastReadAt?.let {
                                Text(stringResource(R.string.library_last_read_on, it.formatDate()),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    PhysicalHomeTimerControls(
                        timer = active,
                        enabled = !busy && (timer == null || active != null),
                        onPlay = {
                            when (active?.phase) {
                                PhysicalTimerPhase.PAUSED -> perform { controller.resume() }
                                PhysicalTimerPhase.STOPPED -> stopBookId = book.id
                                PhysicalTimerPhase.RUNNING -> Unit
                                null -> perform {
                                    controller.start(
                                        bookId = book.id,
                                        title = book.homeLibraryDisplayTitle,
                                        startPage = book.currentPage() ?: 0,
                                        pageCount = book.pageCount,
                                    )
                                }
                            }
                        },
                        onPause = { perform { controller.pause() } },
                        onStop = {
                            if (active?.phase == PhysicalTimerPhase.STOPPED) {
                                stopBookId = book.id
                            } else {
                                perform {
                                    controller.stop()
                                    stopBookId = book.id
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    val stoppedTimer = timer?.takeIf { it.phase == PhysicalTimerPhase.STOPPED && it.bookId == stopBookId }
    val stopBook = books.firstOrNull { it.id == stoppedTimer?.bookId }
    if (stoppedTimer != null && stopBook != null) {
        PhysicalTimerPageDialog(
            book = stopBook.copy(pageCount = stoppedTimer.pageCount ?: stopBook.pageCount),
            page = stoppedTimer.startPage,
            starting = false,
            busy = busy,
            failed = failed,
            onDismiss = { stopBookId = null; failed = false },
            onDiscard = {
                perform {
                    controller.discard()
                    stopBookId = null
                }
            },
            onSave = { page, total ->
                val recorded = physicalSessionRecorded(stoppedTimer, page)
                perform {
                    controller.save(page, total)
                    stopBookId = null
                    onSessionRecorded(recorded)
                }
            },
        )
    }
}

@Composable
private fun PhysicalHomeTimerControls(
    timer: PhysicalTimerSession?,
    enabled: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    val stopColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
    val transition = vayanaContentTransform<PhysicalTimerPhase?>()
    AnimatedContent(
        targetState = timer?.phase,
        modifier = Modifier.vayanaAnimateContentSize(),
        transitionSpec = { transition().using(null) },
        label = "PhysicalHomeTimerControls",
    ) { phase ->
      // Outgoing controls remain visible during exit, but must not perform stale actions.
      val controlsEnabled = enabled && phase == timer?.phase
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        when (phase) {
            null -> FilledTonalIconButton(onClick = onPlay, enabled = controlsEnabled) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.physical_timer_start))
            }
            PhysicalTimerPhase.RUNNING -> {
                FilledTonalIconButton(onClick = onPause, enabled = controlsEnabled) {
                    Icon(Icons.Outlined.Pause, contentDescription = stringResource(R.string.physical_timer_pause))
                }
                FilledTonalIconButton(onClick = onStop, enabled = controlsEnabled, colors = stopColors) {
                    Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.physical_timer_stop))
                }
            }
            PhysicalTimerPhase.PAUSED -> {
                FilledTonalIconButton(onClick = onPlay, enabled = controlsEnabled) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.physical_timer_resume))
                }
                FilledTonalIconButton(onClick = onStop, enabled = controlsEnabled, colors = stopColors) {
                    Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.physical_timer_stop))
                }
            }
            PhysicalTimerPhase.STOPPED -> FilledTonalIconButton(
                onClick = onStop,
                enabled = controlsEnabled,
                colors = stopColors,
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.physical_timer_finish_log))
            }
        }
      }
    }
}
