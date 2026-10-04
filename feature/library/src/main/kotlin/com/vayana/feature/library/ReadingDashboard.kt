package com.vayana.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Book
import com.vayana.core.database.repository.*
import com.vayana.core.datastore.settings.*
import com.vayana.core.designsystem.tokens.*
import com.vayana.core.filesystem.ResolvedBooks
import com.vayana.core.resources.R
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
) : ViewModel() {
    val enabled = settings.observe(SettingsRegistry.TodayCardEnabled)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val capacity = settings.observe(SettingsRegistry.ReadNextCapacity)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    val queue = resolved.all.map { orderedReadNext(it) }
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
        }
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(Radii.large), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Paddings.card), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.today_collapse else R.string.today_expand)) }
            }
            Text(stringResource(R.string.today_goal, state.minutes, state.goal))
            Text(stringResource(R.string.today_reviews, state.words, state.highlights), style = MaterialTheme.typography.bodySmall)
            if (expanded) {
                state.plans.forEach { (book, plan) ->
                    val label = when (plan.unit) {
                        PlanUnit.MINUTES -> R.string.today_plan_minutes
                        PlanUnit.PAGES -> R.string.today_plan_pages
                        PlanUnit.PERCENT -> R.string.today_plan_percent
                        else -> R.string.today_plan_overdue
                    }
                    Text(stringResource(label, book.title, plan.amount), style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(onClick = onWords, enabled = state.words > 0) { Text(stringResource(R.string.today_review_words)) }
                    TextButton(onClick = onHighlights, enabled = state.highlights > 0) { Text(stringResource(R.string.today_review_highlights)) }
                }
                TextButton(onClick = { viewModel.showToday(false) }) { Text(stringResource(R.string.today_hide)) }
            }
            state.current?.let { book -> FilledTonalButton(onClick = { onContinue(book) }) { Text(stringResource(R.string.today_continue)) } }
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
internal fun BookDispositionControl(book: Book, viewModel: ReadingDashboardViewModel = hiltViewModel()) {
    var editing by rememberSaveable(book.id) { mutableStateOf(false) }
    TextButton(onClick = { editing = true }) {
        Text(stringResource(R.string.reading_disposition) + ": " + stringResource(dispositionLabel(book.readingDisposition)))
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
