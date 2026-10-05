package com.vayana.wear

import android.app.Activity

import android.app.AlertDialog

import android.content.SharedPreferences

import android.graphics.Color

import android.graphics.Typeface

import android.graphics.drawable.GradientDrawable

import android.graphics.drawable.RippleDrawable

import android.content.res.ColorStateList

import android.text.TextUtils

import android.os.Bundle

import android.os.Handler

import android.os.Looper

import android.os.SystemClock

import android.text.InputType

import android.view.Gravity

import android.view.MotionEvent

import android.widget.*

import com.vayana.core.wear.*

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A compact, scrollable watch UI; the timer is timestamp-based and needs no background ticking service. */

class WatchActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val accent = Color.rgb(207, 230, 161)

    private val surface = Color.rgb(28, 35, 30)

    private val ink = Color.rgb(242, 245, 237)

    private val muted = Color.rgb(170, 184, 171)

    private lateinit var store: WatchStore

    private lateinit var content: LinearLayout

    private val handler = Handler(Looper.getMainLooper())

    private var timerLabel: TextView? = null

    private var statusLabel: TextView? = null
    private var connectionLabel: TextView? = null
    private var connectionJob: Job? = null
    private var phoneReachable = false

    private var selected: WearBook? = null

    private var renderedBooks: List<WearBook> = emptyList()

    private var renderedEntries: List<WatchEntry> = emptyList()

    private val tick = object : Runnable {

        override fun run() {

            runCatching { store.read().active?.let { timerLabel?.text = elapsed(it.timer.elapsedMillis(SystemClock.elapsedRealtime())) } }

            handler.postDelayed(this, 1000)

        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        store = WatchStore(this)

        action { store.update { it }; WatchSync.start(this); render() }

    }

    override fun onResume() {

        super.onResume()

        getSharedPreferences("reading", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this)

        action { render(); WatchSync.enqueue(this) }

        connectionJob?.cancel()
        connectionJob = CoroutineScope(Dispatchers.Main).launch {
            companionConnection(this@WatchActivity, WearSyncRules.PHONE).collect { connected ->
                phoneReachable = connected
                updateConnectionIndicator()
                statusLabel?.text = syncStatus(store.read())
            }
        }
        handler.post(tick)

    }

    override fun onPause() {

        connectionJob?.cancel()
        connectionJob = null
        handler.removeCallbacks(tick)

        getSharedPreferences("reading", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)

        action { store.update { it.checkpoint(SystemClock.elapsedRealtime()) } }

        super.onPause()

    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {

        action {

            val state = store.read()

            statusLabel?.text = syncStatus(state)

            if (state.active == null && selected == null &&

                (state.books != renderedBooks || state.entries != renderedEntries)) render()

        }

    }

    private fun action(block: () -> Unit) {

        try { block() } catch (e: Exception) {

            Toast.makeText(this, e.message ?: "Could not save. Please try again.", Toast.LENGTH_LONG).show()

        }

    }

    private fun render() {

        val state = store.read()

        renderedBooks = state.books

        renderedEntries = state.entries

        timerLabel = null

        val scroll = ScrollView(this).apply { isFillViewport = true }

        content = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            gravity = Gravity.CENTER_HORIZONTAL

            setPadding(dp(24), dp(22), dp(24), dp(64))

            setBackgroundColor(Color.BLACK)

        }

        scroll.addView(content)

        scroll.setOnGenericMotionListener { _, event ->

            if (event.action == MotionEvent.ACTION_SCROLL) {

                scroll.scrollBy(0, (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(40)).toInt()); true

            } else false

        }

        setContentView(scroll)

        label("VAYANA", 12f).apply { setTextColor(accent); letterSpacing = 0.14f }

        connectionLabel = label("", 12f)
        updateConnectionIndicator()
        statusLabel = label(syncStatus(state), 12f).apply { setTextColor(muted) }

        val active = state.active

        if (active != null) {

            label(active.book.title, 16f)

            timerLabel = label(elapsed(active.timer.elapsedMillis(SystemClock.elapsedRealtime())), 30f).apply { setTextColor(accent); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

            label(if (active.timer.phase == PhysicalTimerPhase.RUNNING) "Reading" else "Paused", 14f)

            if (state.recoveredAfterReboot) label("Watch restarted. Timer paused at the last saved checkpoint.", 12f)

            button(if (active.timer.phase == PhysicalTimerPhase.RUNNING) "Pause" else "Resume", primary = true) {

                store.update { if (active.timer.phase == PhysicalTimerPhase.RUNNING) it.pause(now()) else it.resume(now()) }

                render()

            }

            button("Current page: ${active.page}") { editPage(active.page, active.book.total) { page ->

                store.update { it.page(page).checkpoint(now()) }; render()

            } }

            button("Stop & save", primary = true) {

                store.update { it.pause(now()) }

                editPage(active.page, active.book.total) { page ->

                    store.update { it.page(page).finish(now(), System.currentTimeMillis()) }

                    WatchSync.enqueue(this); render()

                }

                render()

            }

        } else {

            val book = selected

            if (book != null) {

                label(book.title, 18f)

                val page = state.entries.lastOrNull { it.session.book.id == book.id &&

                    (it.receipt == null || it.receipt == "phone_timer_active" ||

                        (it.receipt in setOf("saved", "duplicate", "merged", "page_applied") && it.session.book.version == book.version))

                }?.session?.endPage ?: book.page

                button("Start at page $page", primary = true) { editPage(page, book.total) { start ->

                    store.update { it.start(book, start, "wear-${UUID.randomUUID()}", System.currentTimeMillis(), now(), store.bootCount()) }

                    selected = null; render()

                } }

                button("Back to books") { selected = null; render() }

            } else {

                label("Reading now", 20f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

                if (state.books.isEmpty()) label("Mark a physical book as currently reading in Vayana on your phone, then tap Sync. Your books stay available offline.", 14f)

                state.books.forEach { book -> bookCard(book) }

                button("Sync with phone") { WatchSync.enqueue(this, requestBooks = true); Toast.makeText(this, "Sync requested", Toast.LENGTH_SHORT).show() }

                if (state.entries.isNotEmpty()) {

                    label("Recent sessions", 16f)

                    (state.entries.filter { it.receipt !in WearSyncRules.delivered || it.receipt == "page_kept" } +

                        state.entries.filter { it.receipt in WearSyncRules.delivered && it.receipt != "page_kept" }.takeLast(10))

                        .reversed().forEach { entry ->

                        label("${entry.session.book.title}\n${entry.session.startPage} → ${entry.session.endPage} · ${elapsed(entry.session.seconds * 1000)}\n${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(entry.session.startedAt))}\n${receiptLabel(entry.receipt)}", 12f)
                        if (entry.receipt in setOf("page_kept", "overlap", "timing_conflict", "overlap_other_book", "clock_conflict", "payload_conflict", "reset")) button("Review session") { resolve(entry) }

                    }

                }

            }

        }

    }

    private fun revise(entry: WatchEntry, resolution: String? = null, receipt: String? = null) {
        store.update { state -> state.copy(entries = state.entries.map {
            if (it.session.id == entry.session.id) it.copy(session = it.session.copy(resolution = resolution ?: it.session.resolution), receipt = receipt) else it
        }) }
        WatchSync.enqueue(this); render()
    }

    private fun resolve(entry: WatchEntry) {
        val options = when (entry.receipt) {
            "page_kept" -> arrayOf("Use watch page", "Keep phone page")
            "clock_conflict" -> arrayOf("Correct start time", "Count separately", "Ignore session")
            "reset", "payload_conflict" -> arrayOf("Ignore session")
            else -> arrayOf("Count separately", "Ignore session")
        }
        AlertDialog.Builder(this).setTitle("Resolve session").setItems(options) { _, index -> action {
            when (options[index]) {
                "Use watch page" -> revise(entry, "watch_page")
                "Keep phone page" -> revise(entry, receipt = "saved")
                "Ignore session" -> revise(entry, receipt = "ignored")
                "Correct start time" -> correctTime(entry)
                else -> AlertDialog.Builder(this).setTitle("Count full reading time?")
                    .setMessage("This adds the full session independently, including overlapping time. Use only for a separate reading occasion.")
                    .setPositiveButton("Count separately") { _, _ -> action { revise(entry, "separate") } }
                    .setNegativeButton("Cancel", null).show()
            }
        } }.setNegativeButton("Cancel", null).show()
    }

    private fun correctTime(entry: WatchEntry) {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).apply { isLenient = false }
        val input = EditText(this).apply { setText(format.format(java.util.Date(entry.session.startedAt))); setTextColor(ink) }
        val dialog = AlertDialog.Builder(this).setTitle("Start: yyyy-MM-dd HH:mm").setView(input)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { action {
            val value = input.text.toString()
            val position = java.text.ParsePosition(0)
            val time = format.parse(value, position)?.time
            if (time == null || position.index != value.length || time <= 0) { input.error = "Enter a valid local date and time"; return@action }
            val old = entry.session
            val delta = time - old.startedAt
            val session = old.copy(startedAt = time, endedAt = old.endedAt + delta,
                activeIntervals = old.activeIntervals?.let { encoded -> com.vayana.core.common.ReadingIntervals.encode(
                    com.vayana.core.common.ReadingIntervals.decode(encoded).map { it.copy(start = it.start + delta, end = it.end + delta) }) }, clockChanged = false)
            session.validate()
            store.update { state -> state.copy(entries = state.entries.map { if (it.session.id == old.id) it.copy(session = session, receipt = null) else it }) }
            WatchSync.enqueue(this); dialog.dismiss(); render()
        } } }
        dialog.show()
    }

    private fun editPage(initial: Int, total: Int?, save: (Int) -> Unit) {

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }

        val input = EditText(this).apply {

            inputType = InputType.TYPE_CLASS_NUMBER

            setTextColor(ink); backgroundTintList = ColorStateList.valueOf(accent)

            setText(String.format(java.util.Locale.ROOT, "%d", initial)); gravity = Gravity.CENTER; selectAll()

            contentDescription = "Current page"

        }

        layout.addView(input)

        val row = LinearLayout(this)

        listOf(-1, 1).forEach { delta -> row.addView(Button(this).apply {

            text = if (delta < 0) "−" else "+"

            contentDescription = if (delta < 0) "Previous page" else "Next page"

            setOnClickListener {

                val next = ((input.text.toString().toLongOrNull() ?: initial.toLong()) + delta)

                    .coerceIn(0, (total ?: Int.MAX_VALUE).toLong())

                input.setText(String.format(java.util.Locale.ROOT, "%d", next))

            }

        }, LinearLayout.LayoutParams(0, dp(48), 1f)) }

        layout.addView(row)

        val dialog = AlertDialog.Builder(this).setTitle(if (total == null) "Current page" else "Page / $total")

            .setView(layout).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()

        dialog.setOnShowListener {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent)

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(muted)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

            val page = input.text.toString().toIntOrNull()

            if (page == null || page < 0 || (total != null && page > total)) input.error = "Enter a valid page"

            else action { save(page); dialog.dismiss() }

        } }

        dialog.show()

    }

    private fun label(value: String, size: Float) = TextView(this).apply {

        text = value; textSize = size; gravity = Gravity.CENTER; setTextColor(ink); typeface = Typeface.create("sans-serif", Typeface.NORMAL)

        setPadding(0, dp(4), 0, dp(4)); content.addView(this)

    }

    private fun shape(color: Int) = GradientDrawable().apply {

        setColor(color); cornerRadius = dp(24).toFloat()

    }

    private fun styleButton(button: Button, primary: Boolean) = button.apply {

        backgroundTintList = null

        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape(if (primary) accent else surface), null)

        setTextColor(if (primary) Color.rgb(21, 35, 18) else ink)

        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        textSize = 14f; isAllCaps = false; minHeight = dp(48)

        setPadding(dp(12), dp(10), dp(12), dp(10))

    }

    private fun button(value: String, primary: Boolean = false, click: () -> Unit) {

        content.addView(Button(this).apply {

            text = value; styleButton(this, primary)

            setOnClickListener { action(click) }

        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(2) })

    }

    private fun bookCard(book: WearBook) {

        val card = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL

            minimumHeight = dp(72); setPadding(dp(16), dp(12), dp(16), dp(12))

            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape(surface), null)

            isFocusable = true

            setOnClickListener { selected = book; render() }

        }

        card.addView(TextView(this).apply {

            text = book.title; textSize = 15f; setTextColor(ink)

            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

            maxLines = 2; ellipsize = TextUtils.TruncateAt.END

        })

        card.addView(TextView(this).apply {

            text = if (book.total == null) "Page ${book.page}" else "Page ${book.page} of ${book.total}"

            textSize = 12f; setTextColor(muted); setPadding(0, dp(5), 0, 0)

        })

        book.total?.let { total -> card.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {

            max = total; progress = book.page

            progressTintList = ColorStateList.valueOf(accent)

            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(59, 71, 58))

        }, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(8) }) }

        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

    }

    private fun updateConnectionIndicator() {
        connectionLabel?.apply {
            text = if (phoneReachable) "● Phone connected" else "○ Phone offline"
            setTextColor(if (phoneReachable) accent else muted)
            contentDescription = if (phoneReachable) "Phone connected" else "Phone offline"
        }
    }

    private fun syncStatus(state: WatchState): String {

        val pending = state.entries.count { it.receipt !in WearSyncRules.delivered }

        val connection = if (phoneReachable) "Phone connected" else "Offline · saved on watch"

        return when {

            state.syncFailed -> "Saved on watch · sync will retry"

            pending > 0 -> "$pending sessions pending"

            state.books.isEmpty() -> "$connection · waiting for books"

            else -> if (phoneReachable) "Ready to sync" else "Saved on watch"

        }

    }

    private fun receiptLabel(receipt: String?) = when (receipt) {

        "saved", "duplicate" -> "Synced"

        "page_kept" -> "Time synced · phone page kept"

        "merged" -> "Synced · overlapping time counted once"
        "page_applied" -> "Synced · watch page applied"
        "ignored" -> "Ignored on watch"
        "timing_conflict" -> "Review · pause timing is unknown"
        "overlap_other_book" -> "Review · time overlaps another book"
        "clock_conflict" -> "Review · watch clock changed"
        "payload_conflict" -> "Review · session ID already has different data"
        "book_missing" -> "Pending · restore the physical book on phone"

        "reset" -> "Needs review · phone reading history was reset"

        "overlap" -> "Needs review · overlaps another reading session"

        "phone_timer_active" -> "Pending · stop the phone timer, then Sync"

        else -> "Saved on watch · pending sync"

    }

    private fun elapsed(ms: Long): String {

        val seconds = ms / 1000

        return "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)

    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

}
