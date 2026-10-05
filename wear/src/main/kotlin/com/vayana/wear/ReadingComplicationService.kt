package com.vayana.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.data.*
import com.vayana.core.wear.*
import java.time.Instant

class ReadingComplicationService : ComplicationDataSourceService() {
    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        val store = WatchStore(this)
        val state = store.read().recover(store.bootCount(), SystemClock.elapsedRealtime())
        val active = state.displayActive(SystemClock.elapsedRealtime(), store.bootCount())
        val book = active?.book ?: state.books.firstOrNull()
        val seconds = active?.timer?.elapsedMillis(SystemClock.elapsedRealtime())?.div(1000) ?: 0
        val text = when {
            active?.timer?.phase == PhysicalTimerPhase.RUNNING -> TimeDifferenceComplicationText.Builder(
                TimeDifferenceStyle.STOPWATCH, CountUpTimeReference(Instant.now().minusSeconds(seconds))).build()
            active != null -> PlainComplicationText.Builder("${seconds / 60}m").build()
            book != null -> PlainComplicationText.Builder("p${book.page}").build()
            else -> PlainComplicationText.Builder("Read").build()
        }
        val description = PlainComplicationText.Builder(book?.title ?: "Vayana reading").build()
        val tap = PendingIntent.getActivity(this, 102, Intent(this, WatchActivity::class.java)
            .putExtra("bookId", book?.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val data = when (request.complicationType) {
            ComplicationType.RANGED_VALUE -> {
                val total = book?.total
                val max = (total?.toFloat() ?: (getSharedPreferences("watch-settings", 0).getInt("dailyGoal", 30) * 60f)).coerceAtLeast(1f)
                val value = if (total != null) (active?.page ?: book.page).toFloat() else WatchGoals.today(this, state).toFloat()
                RangedValueComplicationData.Builder(value.coerceIn(0f, max), 0f, max, description)
                    .setText(if (total != null) text else PlainComplicationText.Builder("${value.toLong() / 60}m").build())
                    .setTapAction(tap).build()
            }
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(PlainComplicationText.Builder(
                (book?.title ?: "Vayana") + " · page ${active?.page ?: book?.page ?: 0}" +
                    (active?.let { " · " + if (it.timer.phase == PhysicalTimerPhase.RUNNING) "Reading" else "Paused" } ?: "")).build(), description).setTapAction(tap).build()
            else -> ShortTextComplicationData.Builder(text, description).setTapAction(tap).build()
        }
        listener.onComplicationData(data)
    }
    override fun getPreviewData(type: ComplicationType): ComplicationData {
        val text = PlainComplicationText.Builder("p132").build()
        val description = PlainComplicationText.Builder("Vayana reading progress").build()
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(132f, 0f, 194f, description).setText(text).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(description, description).build()
            else -> ShortTextComplicationData.Builder(text, description).build()
        }
    }
    companion object {
        fun refresh(context: Context) = ComplicationDataSourceUpdateRequester.create(context,
            ComponentName(context, ReadingComplicationService::class.java)).requestUpdateAll()
    }
}
