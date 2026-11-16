package com.vayana.app.widget

import com.vayana.core.database.model.Book
import com.vayana.core.database.model.ReadingSession
import com.vayana.core.database.model.hasStartedReading
import com.vayana.core.database.model.isActiveForReading
import com.vayana.core.database.model.isFinished
import com.vayana.core.database.model.shownReadingPercent
import com.vayana.core.wear.WearDailyProgress
import com.vayana.feature.statistics.startOfWeek
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

internal const val ReadingWidgetHistoryDays = 371L
private const val ChartDays = 8

internal data class ReadingWidgetTime(
    val dailySeconds: List<Int?>,
    val weekSeconds: Long?,
    val streakDays: Int?,
)

internal fun readingWidgetTime(
    sessions: List<ReadingSession>,
    today: LocalDate,
    zone: ZoneId,
    firstDay: DayOfWeek,
    installedOn: LocalDate,
): ReadingWidgetTime {
    val start = today.minusDays(ReadingWidgetHistoryDays - 1)
    val boundaries = List(ReadingWidgetHistoryDays.toInt() + 1) { offset ->
        start.plusDays(offset.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val seconds = LongArray(boundaries.lastIndex)
    for (session in sessions) {
        if (session.durationSeconds < MinCountedSessionSeconds ||
            session.endedAt <= boundaries.first() || session.startedAt >= boundaries.last()
        ) continue

        val first = boundaries.binarySearch(session.startedAt).let { if (it < 0) -it - 2 else it }
        for (index in first.coerceAtLeast(0) until seconds.size) {
            if (boundaries[index] >= session.endedAt) break
            seconds[index] += WearDailyProgress.clippedSeconds(
                session.startedAt, session.endedAt, session.durationSeconds, session.activeIntervals,
                boundaries[index], boundaries[index + 1],
            ).coerceAtLeast(0)
        }
    }
    // The local store covers dates since installation. Imported sessions can establish coverage for an older day.
    val days = seconds.mapIndexed { index, total ->
        total.takeIf { it > 0 || !start.plusDays(index.toLong()).isBefore(installedOn) }
            ?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
    }
    val weekStart = today.startOfWeek(firstDay)
    val week = days.drop((weekStart.toEpochDay() - start.toEpochDay()).toInt())
    val streakWindow = days.asReversed().let { latestFirst ->
        if (latestFirst.first() == 0) latestFirst.drop(1) else latestFirst
    }
    val streak = streakWindow.first()?.let { streakWindow.takeWhile { it != null && it > 0 }.size }

    return ReadingWidgetTime(
        dailySeconds = days.takeLast(ChartDays),
        weekSeconds = week.takeIf { values -> values.none { it == null } }?.filterNotNull()?.sumOf(Int::toLong)
            ?.coerceAtMost(Int.MAX_VALUE.toLong()),
        streakDays = streak,
    )
}

internal fun readingWidgetJson(
    generatedAt: Instant,
    zone: ZoneId,
    time: ReadingWidgetTime,
    books: List<Book>,
    current: Book?,
    chapterMinutes: Int?,
    next: Book?,
    goal: Int,
    finishedThreshold: Float,
): String {
    val today = generatedAt.atZone(zone).toLocalDate()
    val finishedThisYear = books.count { book ->
        book.finishedReadingAt?.let { Instant.ofEpochMilli(it).atZone(zone).year == today.year } == true && book.isFinished()
    }
    val library = JSONObject()
        .put("total", books.size)
        .put("reading", books.count {
            it.isActiveForReading() && it.hasStartedReading() && !it.isFinished() &&
                (it.readingDisposition == "READING" || it.readingPercent < finishedThreshold)
        })
        .put("finished", books.count {
            it.isFinished() || (it.readingDisposition != "READING" && it.shownReadingPercent >= finishedThreshold)
        })
        .put("unread", books.count { it.isActiveForReading() && !it.hasStartedReading() })
        .put("offline", books.count { it.format.isOffline })
    val currentBook = current?.title?.widgetTitle()?.let { title ->
        JSONObject().put("title", title)
            .put("percent", current.readingPercent.takeIf { it.isFinite() }?.let { (it.coerceIn(0f, 1f) * 100).roundToInt() }
                ?: JSONObject.NULL)
            .put("chapterMinutes", chapterMinutes ?: JSONObject.NULL)
    }
    return JSONObject()
        .put("version", 1)
        .put("generatedAt", generatedAt.toEpochMilli())
        .put("date", today.toString())
        .put("zone", zone.id)
        .put("dailySeconds", JSONArray(time.dailySeconds.map { it ?: JSONObject.NULL }))
        .put("weekSeconds", time.weekSeconds ?: JSONObject.NULL)
        .put("streakDays", time.streakDays ?: JSONObject.NULL)
        .put("year", JSONObject().put("finished", finishedThisYear).put("goal", goal.takeIf { it > 0 } ?: JSONObject.NULL))
        .apply { if (current == null || currentBook != null) put("currentBook", currentBook ?: JSONObject.NULL) }
        .put("nextTitle", next?.title?.widgetTitle() ?: JSONObject.NULL)
        .put("library", library)
        .toString().also { check(it.length <= 8_192) }
}

private fun String.widgetTitle(): String? = trim().take(160).dropLastWhile { it.isHighSurrogate() }.takeIf { it.isNotBlank() }
