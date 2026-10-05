package com.vayana.wear

import android.app.Activity
import android.content.Intent
import android.view.HapticFeedbackConstants
import kotlin.math.roundToInt

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
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

class WatchActivity : androidx.activity.ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

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

    private var ambient = false
    private var lowBitAmbient = false
    private var selected: WearBook? = null
    private var selectedStartPage: Int? = null

    private var renderedBooks: List<WearBook> = emptyList()

    private var renderedEntries: List<WatchEntry> = emptyList()
    private var renderedTimer: WatchTimerSurface? = null
    private var renderedRemote: SharedTimerSnapshot? = null
    private var renderedCommands: List<SharedTimerCommand> = emptyList()

    private val tick = object : Runnable {

        override fun run() {

            runCatching {
                val active = store.read().displayActive(now(), store.bootCount())
                if (active?.timer?.phase == PhysicalTimerPhase.RUNNING) {
                    timerLabel?.text = elapsed(active.timer.elapsedMillis(SystemClock.elapsedRealtime()))
                    handler.postDelayed(this, 1000)
                }
            }

        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        store = WatchStore(this)
        lifecycle.addObserver(androidx.wear.ambient.AmbientLifecycleObserver(this,
            object : androidx.wear.ambient.AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: androidx.wear.ambient.AmbientLifecycleObserver.AmbientDetails) {
                    ambient = true; lowBitAmbient = ambientDetails.deviceHasLowBitAmbient; render()
                }
                override fun onUpdateAmbient() { render() }
                override fun onExitAmbient() { ambient = false; render() }
            }))

        action { store.update { it }; selectIntentBook(intent); WatchSync.start(this); render() }

    }

    private fun renderAmbient(state: WatchState) {
        handler.removeCallbacks(tick)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(32), dp(24), dp(32), dp(24)); setBackgroundColor(Color.BLACK)
        }
        fun line(value: String, size: Float) = layout.addView(TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            paint.isAntiAlias = !lowBitAmbient
            setPadding(0, dp(3), 0, dp(3)); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        line(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date()), 13f)
        state.displayActive(now(), store.bootCount())?.let {
            line(it.book.title, 13f)
            // Seconds are intentionally omitted because ambient updates are infrequent.
            val minutes = it.timer.elapsedMillis(now()) / 60_000
            line("${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}", 30f)
            line(if (it.timer.phase == PhysicalTimerPhase.RUNNING) "Reading · page ${it.page}" else "Paused · page ${it.page}", 12f)
        } ?: line("Today: ${WatchGoals.today(this, state) / 60} min", 18f)
        setContentView(layout)
    }

    private fun showGoals() {
        val settings = getSharedPreferences("watch-settings", MODE_PRIVATE)
        AlertDialog.Builder(this).setTitle("Reading goals").setItems(arrayOf("Daily goal", "Session reminder")) { _, choice ->
            val values = if (choice == 0) intArrayOf(15, 30, 45, 60) else intArrayOf(0, 15, 25, 30, 45, 60)
            AlertDialog.Builder(this).setTitle(if (choice == 0) "Daily reading goal" else "Remind me after")
                .setItems(values.map { if (it == 0) "Off" else "$it minutes" }.toTypedArray()) { _, index ->
                    settings.edit().putInt(if (choice == 0) "dailyGoal" else "sessionTarget", values[index]).apply()
                    if (choice == 1 && values[index] > 0) requestTimerNotifications()
                    WatchGoals.schedule(this, store.read()); render()
                }.setNegativeButton("Cancel", null).show()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun timerAction(name: String, page: Int? = null) {
        val state = store.read()
        if (state.active != null) store.update {
            when (name) {
                "pause" -> it.pause(now())
                "resume" -> it.resume(now())
                "page" -> it.page(page!!).checkpoint(now())
                "finish" -> it.page(page!!).finish(now(), System.currentTimeMillis())
                else -> it
            }
        } else {
            val remote = checkNotNull(state.remoteTimer)
            check(state.timerCommands.isEmpty()) { "Waiting for phone to acknowledge the previous action" }
            val command = SharedTimerCommand("cmd-${UUID.randomUUID()}", "phone", checkNotNull(remote.sessionId), remote.revision, name, page)
            store.update { it.copy(timerCommands = listOf(command), timerMessage = null) }
        }
        WatchSync.enqueue(this)
    }

    private fun selectIntentBook(source: Intent) {
        if (store.read().displayActive(now(), store.bootCount()) != null) return
        source.getStringExtra("bookId")?.let { id ->
            selected = store.read().books.firstOrNull { it.id == id }
            selectedStartPage = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        action { selectIntentBook(intent); render() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) action { WatchTimerSurfaces.refresh(this, store.read()) }
    }

    private fun requestTimerNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    private fun feedback(view: android.view.View, confirm: Boolean = false) {
        if (getSharedPreferences("watch-settings", MODE_PRIVATE).getBoolean("haptics", true)) {
            view.performHapticFeedback(if (confirm) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    override fun onResume() {

        super.onResume()

        getSharedPreferences("reading", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this)

        action { render(); WatchTimerSurfaces.refresh(this, store.read()); WatchSync.enqueue(this) }

        connectionJob?.cancel()
        connectionJob = CoroutineScope(Dispatchers.Main).launch {
            companionConnection(this@WatchActivity, WearSyncRules.PHONE).collect { connected ->
                phoneReachable = connected
                updateConnectionIndicator()
                updateSyncStatus(store.read())
            }
        }

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

            updateSyncStatus(state)

            if (WatchTimerSurface.from(state.displayActive(now(), store.bootCount())) != renderedTimer ||
                state.remoteTimer != renderedRemote || state.timerCommands != renderedCommands ||
                (state.active == null && selected == null && (key == "coversRevision" || key == "dailyProgress" ||
                    state.books != renderedBooks || state.entries != renderedEntries))) render()

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
        renderedTimer = WatchTimerSurface.from(state.displayActive(now(), store.bootCount()))
        renderedRemote = state.remoteTimer
        renderedCommands = state.timerCommands

        timerLabel = null
        if (ambient) { renderAmbient(state); return }

        val scroll = ScrollView(this).apply { isFillViewport = true }

        content = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            gravity = Gravity.CENTER_HORIZONTAL

            if (state.displayActive(now(), store.bootCount()) != null || selected != null) setPadding(dp(32), dp(20), dp(32), dp(28))
            else setPadding(dp(28), dp(18), dp(28), dp(32))

            setBackgroundColor(Color.BLACK)

        }

        scroll.addView(content)

        scroll.setOnGenericMotionListener { _, event ->

            if (event.action == MotionEvent.ACTION_SCROLL) {

                scroll.scrollBy(0, (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(40)).toInt()); true

            } else false

        }

        setContentView(scroll)

        val active = state.displayActive(now(), store.bootCount())
        connectionLabel = null
        statusLabel = null
        if (active != null) {
            timerTitle(active.book.title)
            timerLabel = timerFace(elapsed(active.timer.elapsedMillis(now())))
            val running = active.timer.phase == PhysicalTimerPhase.RUNNING
            val stopped = active.timer.phase == PhysicalTimerPhase.STOPPED
            label((if (stopped) "Ready to save" else if (running) "Reading" else "Paused") +
                (if (state.active == null) " · phone" else ""), 13f).apply { setTextColor(muted) }
            if (state.timerCommands.isNotEmpty()) label("Waiting for phone · action queued", 12f).setTextColor(muted)
            state.timerMessage?.let { label(it, 12f).setTextColor(muted) }
            if (state.active != null && state.remoteTimer?.book != null) label("A separate phone timer is active", 12f).setTextColor(muted)
            val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
            controls.addView(Button(this).apply {
                text = if (stopped) "Save" else if (running) "Pause" else "Resume"; styleButton(this, primary = true)
                isEnabled = state.timerCommands.isEmpty()
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setOnClickListener { action {
                    if (stopped) editPage(active.page, active.book.total) { page -> timerAction("finish", page); render() }
                    else { timerAction(if (running) "pause" else "resume"); feedback(this); render() }
                } }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
            controls.addView(Button(this).apply {
                text = "Stop"; styleButton(this, primary = false)
                isEnabled = !stopped && state.timerCommands.isEmpty()
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setOnClickListener { action {
                    feedback(this)
                    if (state.active == null) { timerAction("stop"); render() }
                    else {
                        timerAction("pause"); render()
                        editPage(active.page, active.book.total) { page -> timerAction("finish", page); render() }
                    }
                } }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(4) })
            content.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            button("Page ${active.page}", width = 150) { editPage(active.page, active.book.total) { page ->
                timerAction("page", page); render()
            } }
            if (state.recoveredAfterReboot) label("Watch restarted. Timer paused at the last saved checkpoint.", 12f)
            connectionLabel = label("", 12f)
            updateConnectionIndicator()
            statusLabel = label("", 12f).apply { setTextColor(muted) }
            updateSyncStatus(state)
        } else {

            val book = selected

            if (book != null) {

                timerTitle(book.title)
                timerFace("0:00:00")

                val page = selectedStartPage ?: state.entries.lastOrNull { it.session.book.id == book.id &&

                    (it.receipt == null || it.receipt == "phone_timer_active" ||

                        (it.receipt in setOf("saved", "duplicate", "merged", "page_applied") && it.session.book.version == book.version))

                }?.session?.endPage ?: book.page

                label("Page $page", 13f).apply { setTextColor(muted) }
                button("Start timer", primary = true, width = 150) {
                    store.update { it.start(book, page, "wear-${UUID.randomUUID()}", System.currentTimeMillis(), now(), store.bootCount()) }
                    selected = null; selectedStartPage = null; requestTimerNotifications(); WatchSync.enqueue(this); render()
                }
                button("Change start page") { editPage(page, book.total) { start ->
                    selectedStartPage = start; render()
                } }

                button("Back to books") { selected = null; selectedStartPage = null; render() }

            } else {

                label("Reading now", 18f).apply {
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setPadding(0, 0, 0, dp(2))
                }
                connectionLabel = label("", 11f).apply { setPadding(0, 0, 0, dp(2)) }
                updateConnectionIndicator()
                statusLabel = label("", 12f).apply { setTextColor(muted) }
                updateSyncStatus(state)

                if (state.books.isEmpty()) label("Mark a physical book as currently reading in Vayana on your phone, then tap Sync. Your books stay available offline.", 14f)

                state.books.forEach { book -> bookCard(book) }

                val settings = getSharedPreferences("watch-settings", MODE_PRIVATE)
                label("Today logged: ${WatchGoals.today(this, state) / 60} / ${settings.getInt("dailyGoal", 30)} min", 12f).setTextColor(muted)
                button("Reading goals", width = 150) { showGoals() }
                button("Sync with phone", width = 150) { WatchSync.enqueue(this, requestBooks = true); Toast.makeText(this, "Sync requested", Toast.LENGTH_SHORT).show() }

                content.addView(Switch(this).apply {
                    text = "Vibration"; textSize = 13f; setTextColor(muted); minHeight = dp(48)
                    isChecked = getSharedPreferences("watch-settings", MODE_PRIVATE).getBoolean("haptics", true)
                    thumbTintList = ColorStateList.valueOf(accent)
                    setOnCheckedChangeListener { _, checked ->
                        getSharedPreferences("watch-settings", MODE_PRIVATE).edit().putBoolean("haptics", checked).apply()
                        if (checked) feedback(this)
                    }
                }, LinearLayout.LayoutParams(dp(150), dp(48)))

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

        handler.removeCallbacks(tick)
        if (state.displayActive(now(), store.bootCount())?.timer?.phase == PhysicalTimerPhase.RUNNING) handler.post(tick)
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
        val dialog = Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.BLACK) }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32), dp(20), dp(32), dp(28))
        }
        scroll.addView(layout)
        layout.addView(TextView(this).apply {
            text = if (total == null) "CURRENT PAGE" else "PAGE / $total"
            textSize = 13f; setTextColor(muted); gravity = Gravity.CENTER
            letterSpacing = 0.06f; setPadding(0, 0, 0, dp(4))
        }, LinearLayout.LayoutParams(-1, -2))
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true; textSize = 32f; setTextColor(accent)
            background = shape(surface); gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            filters = arrayOf(android.text.InputFilter.LengthFilter(10))
            setText(String.format(java.util.Locale.ROOT, "%d", initial))
            contentDescription = "Current page"; setSelectAllOnFocus(true)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
                    clearFocus(); true
                } else false
            }
        }
        layout.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        val steps = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val stepButtons = listOf(-1, 1).map { delta -> Button(this).apply {
            text = if (delta < 0) "−" else "+"
            styleButton(this, primary = false); textSize = 24f; setTextColor(ink)
            contentDescription = if (delta < 0) "Previous page" else "Next page"
            setOnClickListener {
                val next = ((input.text.toString().toLongOrNull() ?: initial.toLong()) + delta)
                    .coerceIn(0, (total ?: Int.MAX_VALUE).toLong())
                input.setText(String.format(java.util.Locale.ROOT, "%d", next))
            }
        }.also { button -> steps.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            if (delta < 0) rightMargin = dp(4) else leftMargin = dp(4)
        }) } }
        fun updateSteps() {
            val page = input.text.toString().toLongOrNull()
            stepButtons[0].isEnabled = page != null && page > 0
            stepButtons[1].isEnabled = page != null && page < (total ?: Int.MAX_VALUE)
            stepButtons.forEach { it.alpha = if (it.isEnabled) 1f else 0.4f }
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateSteps()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        updateSteps()
        layout.addView(steps, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(Button(this).apply {
            text = "Cancel"; styleButton(this, primary = false)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
        actions.addView(Button(this).apply {
            text = "Save"; styleButton(this, primary = true)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setOnClickListener {
                val page = input.text.toString().toIntOrNull()
                if (page == null || page < 0 || (total != null && page > total)) input.error = "Enter a valid page"
                else action { save(page); feedback(input, confirm = true); dialog.dismiss() }
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(4) })
        // Narrow footer stays within the curved bottom edge of a round display.
        layout.addView(actions, LinearLayout.LayoutParams(dp(150), -2).apply { topMargin = dp(8) })
        var rotaryRemainder = 0f
        fun rotatePage(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_SCROLL) return false
            rotaryRemainder += event.getAxisValue(MotionEvent.AXIS_SCROLL)
            val steps = rotaryRemainder.roundToInt()
            if (steps != 0) {
                rotaryRemainder -= steps
                val current = input.text.toString().toLongOrNull() ?: initial.toLong()
                val next = (current + steps).coerceIn(0, (total ?: Int.MAX_VALUE).toLong())
                if (next != current) { input.setText(next.toString()); feedback(input) }
            }
            return true
        }
        input.setOnGenericMotionListener { _, event -> rotatePage(event) }
        scroll.setOnGenericMotionListener { _, event ->
            if (input.hasFocus()) rotatePage(event)
            else if (event.action == MotionEvent.ACTION_SCROLL) {
                scroll.scrollBy(0, (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(40)).toInt()); true
            } else false
        }
        layout.isFocusableInTouchMode = true
        dialog.setContentView(scroll)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        layout.requestFocus()
    }

    private fun timerTitle(title: String) = label(title, 13f).apply {
        setTextColor(muted); letterSpacing = 0.02f
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        contentDescription = title
        setPadding(0, 0, 0, dp(4))
    }

    private fun timerFace(value: String) = TextView(this).apply {
        text = value; textSize = 32f; setTextColor(accent)
        gravity = Gravity.CENTER; background = shape(surface)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setPadding(dp(8), 0, dp(8), 0)
        content.addView(this, LinearLayout.LayoutParams(-1, dp(48)))
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

    private fun button(value: String, primary: Boolean = false, width: Int = -1, click: () -> Unit) {

        content.addView(Button(this).apply {

            text = value; styleButton(this, primary)

            setOnClickListener { feedback(this); action(click) }

        }, LinearLayout.LayoutParams(if (width > 0) dp(width) else -1, dp(48)).apply { topMargin = dp(8); bottomMargin = dp(2) })

    }

    private fun bookCard(book: WearBook) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(76); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape(surface), null)
            isFocusable = true
            setOnClickListener { selected = book; selectedStartPage = null; render() }
            contentDescription = "${book.title}, page ${book.page}" + (book.total?.let { " of $it" } ?: "")
        }
        val cover = WatchCoverCache(this).bitmap(book.id)
        val coverView = if (cover != null) ImageView(this).apply {
            setImageBitmap(cover); scaleType = ImageView.ScaleType.CENTER_CROP
        } else TextView(this).apply {
            text = book.title.trim().take(1).uppercase(java.util.Locale.ROOT)
            gravity = Gravity.CENTER; textSize = 22f; setTextColor(accent)
        }
        coverView.apply {
            background = GradientDrawable().apply { setColor(Color.rgb(48, 60, 48)); cornerRadius = dp(5).toFloat() }
            clipToOutline = true; importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        card.addView(coverView, LinearLayout.LayoutParams(dp(34), dp(51)).apply { rightMargin = dp(10) })
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        details.addView(TextView(this).apply {
            text = book.title; textSize = 14f; setTextColor(ink)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        })
        details.addView(TextView(this).apply {
            text = if (book.total == null) "Page ${book.page}" else "${book.page} / ${book.total} pages"
            textSize = 12f; setTextColor(muted); setPadding(0, dp(4), 0, 0)
        })
        book.total?.let { total -> details.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = total; progress = book.page
            progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(59, 71, 58))
        }, LinearLayout.LayoutParams(-1, dp(3)).apply { topMargin = dp(6) }) }
        card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun updateConnectionIndicator() {
        connectionLabel?.apply {
            text = if (phoneReachable) "● Phone connected" else "○ Phone offline"
            setTextColor(if (phoneReachable) accent else muted)
            contentDescription = if (phoneReachable) "Phone connected" else "Phone offline"
        }
    }

    private fun updateSyncStatus(state: WatchState) {
        statusLabel?.apply {
            // Readiness is already conveyed by the connection indicator; reserve space for actionable status.
            visibility = if (state.syncFailed || state.books.isEmpty() ||
                state.entries.any { it.receipt !in WearSyncRules.delivered || it.receipt == "page_kept" })
                android.view.View.VISIBLE else android.view.View.GONE
            text = syncStatus(state)
        }
    }

    private fun syncStatus(state: WatchState): String {

        val pending = state.entries.count { it.receipt !in WearSyncRules.delivered }

        val connection = if (phoneReachable) "Phone connected" else "Offline · saved on watch"

        return when {

            state.syncFailed -> "Saved on watch · sync will retry"

            pending > 0 -> "$pending sessions pending"

            state.entries.any { it.receipt == "page_kept" } -> "Review synced page below"

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

        return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)

    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

}
