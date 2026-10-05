package com.vayana.core.wear

/** Stable monotonic base lets the system animate the clock without an app background tick. */
data class WatchTimerSurface(val title: String, val page: Int, val running: Boolean, val time: Long) {
    companion object {
        fun from(active: WatchActive?): WatchTimerSurface? = active?.let {
            val running = it.timer.phase == PhysicalTimerPhase.RUNNING
            WatchTimerSurface(it.book.title, it.page, running,
                if (running) it.timer.runningSince - it.timer.accumulatedMillis else it.timer.accumulatedMillis)
        }
    }
}
