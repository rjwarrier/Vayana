package com.vayana.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.*
import com.vayana.core.datastore.settings.*
import com.vayana.core.designsystem.tokens.*
import com.vayana.core.designsystem.theme.VayanaLinearWavyProgressIndicator
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.theme.vayanaTween
import com.vayana.core.filesystem.ResolvedBooks
import com.vayana.core.resources.R
import com.vayana.core.wear.WearSyncRules
import com.vayana.core.wear.companionConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class TodayState(val minutes: Int = 0, val goal: Int = 0, val words: Int = 0,
    val highlights: Int = 0, val current: Book? = null, val plans: List<Pair<Book, FinishByPlan>> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadingDashboardViewModel @Inject constructor(
    resolved: ResolvedBooks,
    private val settings: SettingsRepository,
    tools: ReadingToolsRepository,
    sessions: ReadingSessionRepository,
    highlights: HighlightReviewRepository,
    words: VocabularyCardRepository,
    private val books: BookRepository,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    val enabled = settings.observe(SettingsRegistry.TodayCardEnabled)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val capacity = settings.observe(SettingsRegistry.ReadNextCapacity)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    val queue = resolved.all.map { orderedReadNext(it) }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(60_000) } }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)
    private val reviewCounts = clock.flatMapLatest { now ->
        combine(highlights.observeDueCount(now), words.observeDueCount(now)) { h, w -> h to w }
    }
    internal val today = clock.readingDays().flatMapLatest { day ->
        val library = combine(resolved.all, tools.plans,
            settings.snapshot.map { it.dailyReadingGoalMinutes }.distinctUntilChanged()) { all, plans, goal ->
            todayLibraryState(all, plans, goal, day.date)
        }.flowOn(dispatchers.default)
        combine(library, sessions.observeSecondsSince(day.start), reviewCounts) { state, seconds, (h, w) ->
            state.copy(minutes = (seconds / 60).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), words = w, highlights = h)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayState())
    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = false }
    private fun mutate(action: suspend () -> Unit) { viewModelScope.launch {
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _error.value = true }
    } }
    fun showToday(show: Boolean) = mutate { settings.update(SettingsRegistry.TodayCardEnabled, show) }
    fun capacity(value: Int) = mutate { settings.update(SettingsRegistry.ReadNextCapacity, value.coerceIn(2, 50)) }
    fun move(id: Long, delta: Int) = mutate {
        val current = orderedReadNext(books.observeAll().first()).map { it.id }.toMutableList()
        val from = current.indexOf(id)
        val to = from + delta
        if (from >= 0 && to in current.indices) { current.add(to, current.removeAt(from)); books.reorderReadNext(current) }
    }
    fun pin(book: Book) = mutate { books.pinReadNext(book.id, !book.readNextPinned) }
    fun remove(id: Long) = mutate { books.setReadNext(id, false) }
    suspend fun disposition(id: Long, status: String, reason: String?) { books.updateDisposition(id, status, reason) }
}

internal data class ReadingDay(val date: LocalDate, val start: Long)

/** Minute ticks refresh due reviews; session/library subscriptions only change at local midnight or a zone change. */
internal fun Flow<Long>.readingDays(zone: () -> ZoneId = { ZoneId.systemDefault() }): Flow<ReadingDay> = map { now ->
    val currentZone = zone()
    val date = Instant.ofEpochMilli(now).atZone(currentZone).toLocalDate()
    ReadingDay(date, date.atStartOfDay(currentZone).toInstant().toEpochMilli())
}.distinctUntilChanged()

internal fun todayLibraryState(all: List<Book>, plans: Map<String, LocalDate>, goal: Int, date: LocalDate): TodayState {
    var current: Book? = null
    val dailyPlans = ArrayList<Pair<Book, FinishByPlan>>(3)
    for (book in all) {
        if (book.readingDisposition != "ACTIVE" || book.finishedReadingAt != null || !(book.readingPercent < 1f)) continue
        if (book.hasLocalReadableSource() && book.hasStartedReading() &&
            (current == null || (book.lastReadAt ?: 0L) > (current.lastReadAt ?: 0L))) current = book
        if (dailyPlans.size < 3) plans[book.syncId]?.let { dailyPlans += book to finishByPlan(book, it, date) }
    }
    return TodayState(goal = goal, current = current, plans = dailyPlans)
}

internal fun orderedReadNext(books: List<Book>): List<Book> = books.filter { it.readNextAddedAt != null }
    .sortedWith(compareByDescending<Book> { it.readNextPinned }.thenBy { it.readNextAddedAt }.thenBy { it.id })

@Composable
internal fun TodayCard(onContinue: (Book) -> Unit, onWords: () -> Unit, onHighlights: () -> Unit,
    viewModel: ReadingDashboardViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    if (!enabled) return
    val state by viewModel.today.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val watchConnection = remember(context) { companionConnection(context, WearSyncRules.WATCH) }
    val watchConnected by watchConnection.collectAsStateWithLifecycle(initialValue = false)
    var optionsExpanded by remember { mutableStateOf(false) }
    val hasGoal = state.goal > 0
    val goalReached = hasGoal && state.minutes >= state.goal
    val goalProgress = if (hasGoal) {
        (state.minutes.toFloat() / state.goal).coerceIn(0f, 1f)
    } else 0f
    val goalBadgeColor by animateColorAsState(
        targetValue = if (goalReached) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = vayanaTween(), label = "TodayGoalBadge",
    )
    val goalBadgeContentColor by animateColorAsState(
        targetValue = if (goalReached) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = vayanaTween(), label = "TodayGoalBadgeContent",
    )
    // Keep the large dashboard surface calm even after the goal is reached. Achievement
    // color belongs on the status and progress accents rather than flooding the whole card.
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(Radii.extraLarge),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth().vayanaAnimateContentSize(),
    ) {
        Column(
            Modifier.padding(horizontal = Paddings.card, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (hasGoal) stringResource(R.string.today_title_with_goal, state.goal)
                        else stringResource(R.string.today_title),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(R.string.today_minutes_read, state.minutes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (goalReached) MaterialTheme.colorScheme.primary else contentColor,
                    )
                }
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min).heightIn(min = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (watchConnected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Watch,
                                    contentDescription = stringResource(R.string.today_watch_connected),
                                    modifier = Modifier.size(Sizes.iconSmall),
                                )
                            }
                        }
                    }
                    if (hasGoal) {
                        Surface(
                            shape = RoundedCornerShape(Radii.full),
                            modifier = Modifier.vayanaAnimateContentSize(),
                            color = goalBadgeColor,
                            contentColor = goalBadgeContentColor,
                        ) {
                            Row(
                                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (goalReached) Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                Text(
                                    if (goalReached) stringResource(R.string.today_goal_reached)
                                    else stringResource(R.string.today_goal_remaining, (state.goal - state.minutes).coerceAtLeast(0)),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                Box {
                    IconButton(onClick = { optionsExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.today_options))
                    }
                    DropdownMenu(expanded = optionsExpanded, onDismissRequest = { optionsExpanded = false }) {
                        if (state.highlights > 0) {
                            DropdownMenuItem(
                                text = { Text(pluralStringResource(R.plurals.today_highlights_due, state.highlights, state.highlights)) },
                                leadingIcon = { Icon(Icons.Outlined.FormatQuote, contentDescription = null) },
                                onClick = {
                                    optionsExpanded = false
                                    onHighlights()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.today_hide)) },
                            onClick = {
                                optionsExpanded = false
                                viewModel.showToday(false)
                            },
                        )
                    }
                }
            }
            if (hasGoal) {
                VayanaLinearWavyProgressIndicator(
                    progress = { goalProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.current?.let { book ->
                Button(
                    onClick = { onContinue(book) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = Radii.buttonShape,
                ) {
                    Icon(Icons.Outlined.AutoStories, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        stringResource(R.string.today_continue_book, book.homeLibraryDisplayTitle),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.words > 0) {
                AssistChip(
                    onClick = onWords,
                    label = { Text(pluralStringResource(R.plurals.today_words_due, state.words, state.words)) },
                    leadingIcon = { Icon(Icons.Outlined.Spellcheck, contentDescription = null) },
                )
            } else if (state.current == null && state.highlights == 0) {
                Text(stringResource(R.string.today_caught_up), style = MaterialTheme.typography.bodyMedium)
            }
            state.plans.forEach { (book, plan) ->
                val label = when (plan.unit) {
                    PlanUnit.MINUTES -> stringResource(R.string.today_plan_minutes_compact, plan.amount, book.homeLibraryDisplayTitle)
                    PlanUnit.PAGES -> stringResource(R.string.today_plan_pages_compact, plan.amount, book.homeLibraryDisplayTitle)
                    PlanUnit.PERCENT -> stringResource(R.string.today_plan_percent_compact, plan.amount, book.homeLibraryDisplayTitle)
                    else -> stringResource(R.string.today_plan_overdue_compact, book.homeLibraryDisplayTitle)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Flag, contentDescription = null, tint = contentColor.copy(alpha = 0.72f))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReadNextManagerDialog(onDismiss: () -> Unit, onBook: (Book) -> Unit,
    viewModel: ReadingDashboardViewModel = hiltViewModel()) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val capacity by viewModel.capacity.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.queue_manage)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.queue_capacity_hint, queue.size, capacity))
            Row {
                TextButton(onClick = { viewModel.capacity(capacity - 1) }, enabled = capacity > 2) { Text("−") }
                Text(capacity.toString())
                TextButton(onClick = { viewModel.capacity(capacity + 1) }, enabled = capacity < 50) { Text("+") }
            }
            if (queue.isEmpty()) Text(stringResource(R.string.queue_empty))
            queue.forEachIndexed { index, book ->
                TextButton(onClick = { onDismiss(); onBook(book) }) { Text(book.title) }
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { viewModel.move(book.id, -1) }, enabled = index > 0 && queue[index - 1].readNextPinned == book.readNextPinned) { Text(stringResource(R.string.queue_move_up)) }
                    TextButton(onClick = { viewModel.move(book.id, 1) }, enabled = index < queue.lastIndex && queue[index + 1].readNextPinned == book.readNextPinned) { Text(stringResource(R.string.queue_move_down)) }
                }
                Row {
                    TextButton(onClick = { viewModel.pin(book) }) { Text(stringResource(if (book.readNextPinned) R.string.queue_unpin else R.string.queue_pin)) }
                    TextButton(onClick = { viewModel.remove(book.id) }) { Text(stringResource(R.string.queue_remove)) }
                }
                HorizontalDivider()
            }
            if (error) Text(stringResource(R.string.feature_action_failed), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tools_close)) } })
}

@Composable
internal fun BookDispositionControl(
    book: Book,
    modifier: Modifier = Modifier,
    asChip: Boolean = false,
    viewModel: ReadingDashboardViewModel = hiltViewModel(),
) {
    var editing by rememberSaveable(book.id) { mutableStateOf(false) }
    if (asChip) {
        AssistChip(
            onClick = { editing = true },
            label = {
                Text(stringResource(R.string.reading_disposition) + " · " + stringResource(dispositionLabel(book.readingDisposition)))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            modifier = modifier,
        )
    } else {
        TextButton(onClick = { editing = true }, modifier = modifier) {
            Text(stringResource(R.string.reading_disposition) + ": " + stringResource(dispositionLabel(book.readingDisposition)))
        }
    }
    if (!editing) return
    var status by remember(book.id) { mutableStateOf(book.readingDisposition) }
    var reason by remember(book.id) { mutableStateOf(book.dispositionReason.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) editing = false }, title = { Text(stringResource(R.string.reading_disposition)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.disposition_hint))
            listOf("ACTIVE", "PAUSED", "DNF").forEach { value -> FilterChip(selected = value == status,
                onClick = { status = value }, label = { Text(stringResource(dispositionLabel(value))) }, enabled = !busy) }
            OutlinedTextField(value = reason, onValueChange = { reason = it.take(2000) }, label = { Text(stringResource(R.string.disposition_reason)) }, enabled = !busy)
            if (failed) Text(stringResource(R.string.feature_action_failed), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; scope.launch {
        try { viewModel.disposition(book.id, status, reason); editing = false }
        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true }
        finally { busy = false }
    } }) { Text(stringResource(R.string.disposition_save)) } }, dismissButton = {
        TextButton(enabled = !busy, onClick = { editing = false }) { Text(stringResource(R.string.tools_close)) }
    })
}

private fun dispositionLabel(value: String): Int = when (value) {
    "PAUSED" -> R.string.disposition_paused
    "DNF" -> R.string.disposition_dnf
    else -> R.string.disposition_active
}
