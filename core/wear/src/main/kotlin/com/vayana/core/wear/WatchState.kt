package com.vayana.core.wear

import org.json.JSONArray
import org.json.JSONObject

data class WatchActive(val book: WearBook, val timer: PhysicalTimerSession, val page: Int)
data class WatchEntry(val session: WearSession, val receipt: String? = null)

/** One atomic snapshot holds both the active timer and outbox: stopping can never lose a session. */
data class WatchState(
    val books: List<WearBook> = emptyList(),
    val active: WatchActive? = null,
    val entries: List<WatchEntry> = emptyList(),
    val recoveredAfterReboot: Boolean = false,
    val phoneConnected: Boolean = false,
    val syncFailed: Boolean = false,
) {
    fun start(book: WearBook, page: Int, id: String, wall: Long, monotonic: Long, boot: Int): WatchState {
        check(active == null)
        require(page >= 0 && (book.total == null || page <= book.total))
        return copy(active = WatchActive(book, PhysicalTimerSession(0, book.title, id, wall, page,
            runningSince = monotonic, bootCount = boot, pageCount = book.total, activeIntervals = ""), page), recoveredAfterReboot = false)
    }
    fun recover(boot: Int, now: Long): WatchState {
        val a = active ?: return this
        if (a.timer.bootCount == boot) return this
        return copy(active = a.copy(timer = a.timer.recoverBoot(boot, now, System.currentTimeMillis())), recoveredAfterReboot = true)
    }
    fun checkpoint(now: Long): WatchState {
        val a = active ?: return this
        return copy(active = a.copy(timer = a.timer.checkpoint(now)))
    }
    fun page(value: Int): WatchState {
        val a = checkNotNull(active)
        require(value >= 0 && (a.book.total == null || value <= a.book.total))
        return copy(active = a.copy(page = value))
    }
    fun pause(now: Long) = copy(active = active?.let { it.copy(timer = it.timer.pause(now)) })
    fun resume(now: Long) = copy(active = active?.let { it.copy(timer = it.timer.resume(now)) }, recoveredAfterReboot = false)
    fun finish(now: Long, wall: Long): WatchState {
        val a = checkNotNull(active)
        val t = a.timer.stop(now, wall)
        val session = WearSession(t.syncId, a.book, t.startedAt, t.endedAt,
            (t.accumulatedMillis / 1000).coerceAtLeast(1), t.startPage, a.page, t.activeIntervals, t.clockChanged)
        session.validate()
        return copy(active = null, entries = entries + WatchEntry(session))
    }
    fun acknowledge(id: String, receipt: String): WatchState {
        val next = entries.map { if (it.session.id == id) it.copy(receipt = WearSyncRules.acknowledged(it.receipt, receipt)) else it }
        // Keep receipts durably, including across a crash between acknowledgement and transport cleanup.
        return copy(entries = next)
    }
    fun json(): String = JSONObject().put("books", JSONArray().also { a -> books.forEach { a.put(it.json()) } })
        .put("recovered", recoveredAfterReboot)
        .put("phoneConnected", phoneConnected).put("syncFailed", syncFailed)
        .put("entries", JSONArray().also { a -> entries.forEach { a.put(JSONObject()
            .put("session", it.session.json()).put("receipt", it.receipt)) } })
        .put("active", active?.let { a -> JSONObject().put("book", a.book.json()).put("page", a.page)
            .put("id", a.timer.syncId).put("started", a.timer.startedAt).put("startPage", a.timer.startPage)
            .put("elapsed", a.timer.accumulatedMillis).put("anchor", a.timer.runningSince)
            .put("boot", a.timer.bootCount).put("phase", a.timer.phase.name)
            .put("intervals", a.timer.activeIntervals).put("anchorWall", a.timer.anchorWall)
            .put("anchorMono", a.timer.anchorMono).put("clockChanged", a.timer.clockChanged) }).toString()

    companion object {
        fun parse(text: String): WatchState {
            val j = JSONObject(text)
            val books = j.getJSONArray("books")
            val entries = j.getJSONArray("entries")
            val active = j.optJSONObject("active")?.let { a ->
                val book = WearBook.parse(a.getJSONObject("book"))
                WatchActive(book, PhysicalTimerSession(0, book.title, a.getString("id"), a.getLong("started"),
                    a.getInt("startPage"), a.getLong("elapsed"), a.getLong("anchor"), a.getInt("boot"),
                    PhysicalTimerPhase.valueOf(a.getString("phase")), pageCount = book.total, activeIntervals = a.optString("intervals").takeUnless { a.isNull("intervals") || !a.has("intervals") },
                    anchorWall = a.optLong("anchorWall", a.getLong("started")),
                    anchorMono = a.optLong("anchorMono", a.getLong("anchor")), clockChanged = a.optBoolean("clockChanged")), a.getInt("page"))
            }
            return WatchState((0 until books.length()).map { WearBook.parse(books.getJSONObject(it)) }, active,
                (0 until entries.length()).map { i -> entries.getJSONObject(i).let {
                    WatchEntry(WearSession.parse(it.getJSONObject("session")),
                        it.optString("receipt").takeIf { value -> value in WearSyncRules.receipts })
                } }, j.optBoolean("recovered"), j.optBoolean("phoneConnected"), j.optBoolean("syncFailed"))
        }
    }
}
