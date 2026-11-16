package com.vayana.app.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.BookFileAvailability
import com.vayana.core.database.model.hasStartedReading
import com.vayana.core.database.model.isActiveForReading
import com.vayana.core.database.model.isFinished
import com.vayana.core.database.model.orderedReadNext
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.feature.reader.ChapterTimeEstimates
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class ReadingWidgetResult(
    val status: String,
    val json: String? = null,
    val day: ReadingWidgetDay? = null,
    val generatedAt: Instant? = null,
    val cover: ReadingWidgetCover? = null,
) {
    companion object {
        val Unavailable = ReadingWidgetResult("unavailable")
    }
}

internal data class ReadingWidgetDay(val date: LocalDate, val zone: ZoneId) {
    companion object {
        fun at(now: Instant): ReadingWidgetDay {
            val zone = ZoneId.systemDefault()
            return ReadingWidgetDay(now.atZone(zone).toLocalDate(), zone)
        }
    }
}

/** Publishes an immutable snapshot before notifying the client. Binder threads never wait for Room or DataStore. */
@Singleton
class ReadingWidgetProjection @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val settings: SettingsRepository,
    private val books: BookRepository,
    private val sessions: ReadingSessionRepository,
    private val chapterTimes: ChapterTimeEstimates,
    private val covers: ReadingWidgetCovers,
) {
    @Volatile private var latest = ReadingWidgetResult.Unavailable
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val clock: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(30_000)
        }
    }

    internal fun current(): ReadingWidgetResult {
        val result = latest
        val generated = result.generatedAt ?: return result
        val now = Instant.now()
        return if (result.day == ReadingWidgetDay.at(now) && now.toEpochMilli() - generated.toEpochMilli() in -60_000..7_200_000) {
            result
        } else ReadingWidgetResult.Unavailable
    }

    /** Recheck consent and the selected book against the repositories before serving a cached image. */
    internal suspend fun cover(key: String): ReadingWidgetCover = withTimeout(1_500) {
        if (!settings.snapshot.first().batteryWidgetSharingEnabled) throw FileNotFoundException("Sharing is disabled")
        val published = current().takeIf { it.status == "ok" }?.cover
            ?.takeIf { it.key == key } ?: throw FileNotFoundException("Cover is unavailable")
        val digital = books.observeContinueReading().first()
        val selected = readingWidgetCurrentBook(books.observeAll().first(), digital)
        val actual = covers.prepare(selected)
        if (actual?.key != published.key) {
            throw FileNotFoundException("Cover was removed or replaced")
        }
        if (!settings.snapshot.first().batteryWidgetSharingEnabled) throw FileNotFoundException("Sharing is disabled")
        actual
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch(dispatchers.io) {
            settings.snapshot.map { it.batteryWidgetSharingEnabled }.distinctUntilChanged()
                .flatMapLatest { enabled ->
                    if (enabled) snapshots() else {
                        covers.clear()
                        flowOf(Disabled)
                    }
                }
                .retryWhen { error, _ -> retry(error) }
                .collect(::publish)
        }
        scope.launch(dispatchers.io) {
            for (change in changes) {
                if (latest.status == "ok") delay(350)
                // Changes received during the delay are already represented by the published projection.
                changes.tryReceive()
                context.sendBroadcast(
                    Intent("com.vayana.app.action.READING_WIDGET_CHANGED")
                        .setClassName(BatteryWidgetPackage, "com.rj.batterywidget.VayanaChangedReceiver"),
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun snapshots(): Flow<ReadingWidgetResult> = flow {
        // Read the install time once. It is the earliest date for which the local store can establish a zero.
        @Suppress("DEPRECATION")
        val installed = Instant.ofEpochMilli(context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime)
        val refreshes = clock.map { now -> ReadingWidgetDay.at(now) to now.epochSecond / (30 * 60) }
            .distinctUntilChanged()
        val options = settings.snapshot.map { Triple(it.weekStart.day, it.yearlyBooksGoal, it.finishedFraction) }
            .distinctUntilChanged()
        val catalog = combine(books.observeAll(), books.observeContinueReading(), chapterTimes.byBook) { all, digital, estimates ->
            val current = readingWidgetCurrentBook(all, digital)
            val minutes = current?.let { book ->
                estimates[book.id]?.takeIf { it.locator == book.lastLocator && it.progression == book.readingPercent }?.minutes
            }
            Triple(all, current, minutes)
        }.flatMapLatest { (all, current, minutes) ->
            flow {
                while (true) {
                    emit(Catalog(all, current, minutes, covers.prepare(current)))
                    // Also detect an image replaced in place without a database update.
                    delay(30_000)
                }
            }
        }.distinctUntilChanged()
        refreshes.map { it.first }.distinctUntilChanged().flatMapLatest { day ->
            val start = day.date.minusDays(ReadingWidgetHistoryDays - 1).atStartOfDay(day.zone).toInstant()
            combine(
                sessions.observeOverlappingSince(start.toEpochMilli()), catalog, options, refreshes.map { it.second },
            ) { history, catalog, (weekStart, goal, finishedThreshold), _ ->
                val now = Instant.now()
                if (ReadingWidgetDay.at(now) != day) return@combine ReadingWidgetResult.Unavailable
                val stats = readingWidgetTime(history, day.date, day.zone, weekStart, installed.atZone(day.zone).toLocalDate())
                val json = readingWidgetJson(
                    now, day.zone, stats, catalog.all, catalog.current, catalog.minutes,
                    orderedReadNext(catalog.all).firstOrNull(), goal, finishedThreshold, catalog.cover,
                )
                ReadingWidgetResult("ok", json, day, now, catalog.cover)
            }
        }.collect { emit(it) }
    }.retryWhen { error, _ -> retry(error) }

    private suspend fun retry(error: Throwable): Boolean {
        if (error !is Exception) return false
        Log.w("ReadingWidget", "Could not prepare the reading snapshot", error)
        publish(ReadingWidgetResult.Unavailable)
        delay(5_000)
        return true
    }

    private fun publish(result: ReadingWidgetResult) {
        if (latest == result) return
        latest = result
        changes.trySend(Unit)
    }

    private companion object {
        val Disabled = ReadingWidgetResult("disabled")
    }

    private data class Catalog(val all: List<Book>, val current: Book?, val minutes: Int?, val cover: ReadingWidgetCover?)
}

/** The existing Continue Reading query selects digital books. Offline titles have no local reader file. */
internal fun readingWidgetCurrentBook(books: List<Book>, digital: Book?): Book? {
    val current = digital?.let { selected ->
        books.firstOrNull { it.id == selected.id && it.opensInReaderFromWidget() && it.isActiveForReading() }
    }
    return books.asSequence()
        .filter {
            it.format.isOffline && it.isActiveForReading() && it.hasStartedReading() && !it.isFinished() &&
                (it.lastReadAt ?: 0L) > (current?.lastReadAt ?: 0L)
        }
        .maxByOrNull { it.lastReadAt ?: 0L } ?: current
}

internal fun Book.opensInReaderFromWidget(): Boolean =
    !format.isOffline && fileAvailability == BookFileAvailability.LOCAL && filePath.isNotBlank()
