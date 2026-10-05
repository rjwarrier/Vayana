package com.vayana.core.wear

object WearSyncRules {
    const val PHONE = "vayana_phone"
    const val WATCH = "vayana_watch"
    val delivered = setOf("saved", "page_kept", "duplicate", "merged", "page_applied", "ignored")
    val receipts = delivered + setOf("book_missing", "reset", "overlap", "phone_timer_active",
        "timing_conflict", "overlap_other_book", "clock_conflict", "payload_conflict")
    fun readReceipt(payload: String?, resolution: String, fingerprint: String? = null): String? {
        if (payload == null) return null
        if (!payload.startsWith("{")) return payload.takeIf { fingerprint == null && resolution == "auto" && it in receipts }
        return runCatching { org.json.JSONObject(payload).let {
            it.getString("status").takeIf { status -> status in receipts && it.optString("resolution", "auto") == resolution && (fingerprint == null || it.optString("fingerprint") == fingerprint) }
        } }.getOrNull()
    }
    fun phoneEvent(path: String?, changed: Boolean): Boolean =
        (path?.startsWith(WearProtocol.SESSION) == true) || (changed && (path == WearProtocol.REQUEST ||
            path == SharedTimerSnapshot.statePath("watch") || path?.startsWith(SharedTimerSnapshot.ROOT + "command/phone/") == true ||
            path?.startsWith(SharedTimerSnapshot.ROOT + "receipt/watch/") == true))
    fun watchEvent(path: String?, changed: Boolean): Boolean = changed &&
        (path == SharedTimerSnapshot.statePath("phone") || path?.startsWith(SharedTimerSnapshot.ROOT + "command/watch/") == true ||
            path?.startsWith(SharedTimerSnapshot.ROOT + "receipt/phone/") == true || path == WearProtocol.CATALOG || path == WearProtocol.DAILY || path?.startsWith(WearProtocol.ACK) == true || path?.startsWith(WearProtocol.COVER) == true)
    fun acknowledged(current: String?, incoming: String): String = when {
        incoming !in receipts -> current ?: ""
        current in delivered -> current!!
        else -> incoming
    }
    /** Cleanup uses committed local receipts even if the remote acknowledgement was already removed. */
    fun cleanupIds(state: WatchState): Set<String> = state.entries.filter { it.receipt in delivered }
        .map { it.session.id }.toSet()
}
