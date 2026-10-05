package com.vayana.core.wear

import org.json.JSONObject

/** The device that starts a timer remains its sole owner and saves its one finished log. */
data class SharedTimerSnapshot(val owner: String, val revision: Long, val publishedAt: Long,
    val book: WearBook?, val sessionId: String?, val startedAt: Long = 0, val startPage: Int = 0,
    val page: Int = 0, val elapsed: Long = 0, val phase: PhysicalTimerPhase = PhysicalTimerPhase.PAUSED, val epoch: String = "legacy") {
    fun json() = JSONObject().put("owner", owner).put("epoch", epoch).put("revision", revision).put("publishedAt", publishedAt)
        .put("book", book?.json()).put("sessionId", sessionId).put("startedAt", startedAt)
        .put("startPage", startPage).put("page", page).put("elapsed", elapsed).put("phase", phase.name).toString()
    fun mirror(now: Long, anchor: Long, boot: Int, localBookId: Long = 0): WatchActive? = book?.let {
        WatchActive(it, PhysicalTimerSession(localBookId, it.title, checkNotNull(sessionId), startedAt, startPage,
            elapsed, anchor, boot, phase, pageCount = it.total), page)
    }
    fun receiveAnchor(monotonic: Long, wall: Long): Long = monotonic -
        if (phase == PhysicalTimerPhase.RUNNING) (wall - publishedAt).coerceAtLeast(0) else 0
    companion object {
        const val ROOT = WearProtocol.ROOT + "timer/"
        fun statePath(owner: String) = ROOT + "state/" + owner
        fun commandPath(command: SharedTimerCommand) = ROOT + "command/${command.target}/${command.id}"
        fun receiptPath(command: SharedTimerCommand) = ROOT + "receipt/${command.target}/${command.id}"
        fun create(owner: String, revision: Long, active: WatchActive?, monotonic: Long, wall: Long, epoch: String = "legacy") =
            SharedTimerSnapshot(owner, revision, wall, active?.book, active?.timer?.syncId,
                active?.timer?.startedAt ?: 0, active?.timer?.startPage ?: 0, active?.page ?: 0,
                active?.timer?.elapsedMillis(monotonic) ?: 0, active?.timer?.phase ?: PhysicalTimerPhase.PAUSED, epoch)
        fun parse(text: String): SharedTimerSnapshot {
            val j = JSONObject(text)
            return SharedTimerSnapshot(j.getString("owner"), j.getLong("revision"), j.getLong("publishedAt"),
                j.optJSONObject("book")?.let(WearBook::parse), j.optString("sessionId").takeUnless { j.isNull("sessionId") },
                j.optLong("startedAt"), j.optInt("startPage"), j.optInt("page"), j.optLong("elapsed"),
                PhysicalTimerPhase.valueOf(j.getString("phase")), j.optString("epoch", "legacy")).also {
                require(it.owner in setOf("phone", "watch") && it.revision >= 0 && it.publishedAt > 0 && it.elapsed >= 0)
                require((it.book == null) == (it.sessionId == null))
                if (it.book != null) require(it.startedAt > 0 && it.startPage >= 0 && it.page >= 0 &&
                    (it.book.total == null || (it.page <= it.book.total && it.startPage <= it.book.total)))
            }
        }
    }
}

data class SharedTimerCommand(val id: String, val target: String, val sessionId: String,
    val revision: Long, val action: String, val page: Int? = null, val total: Int? = null) {
    fun json() = JSONObject().put("id", id).put("target", target).put("sessionId", sessionId)
        .put("revision", revision).put("action", action).put("page", page).put("total", total).toString()
    fun fingerprint() = WearCover.key(json())
    fun receipt(status: String, revision: Long? = null) = JSONObject().put("fingerprint", fingerprint())
        .put("status", status).put("revision", revision).toString()
    fun receiptRevision(payload: String?): Long? = payload?.let { runCatching {
        JSONObject(it).takeUnless { j -> j.isNull("revision") }?.getLong("revision")
    }.getOrNull() }
    fun receiptStatus(payload: String?): String? = payload?.let { runCatching {
        JSONObject(it).takeIf { j -> j.optString("fingerprint") == fingerprint() }?.getString("status")
    }.getOrNull() }
    fun validFor(id: String?, currentRevision: Long): String? = when {
        sessionId != id -> "ended"
        revision != currentRevision -> "stale"
        else -> null
    }
    companion object {
        fun parse(text: String): SharedTimerCommand {
            val j = JSONObject(text)
            return SharedTimerCommand(j.getString("id"), j.getString("target"), j.getString("sessionId"),
                j.getLong("revision"), j.getString("action"), j.optInt("page").takeUnless { j.isNull("page") },
                j.optInt("total").takeUnless { j.isNull("total") }).also {
                require(it.id.matches(Regex("cmd-[0-9a-f-]{36}")) && it.target in setOf("phone", "watch") && it.revision >= 0)
                require(it.action in setOf("pause", "resume", "stop", "page", "finish", "discard"))
                require(it.total == null || (it.total > 0 && (it.page == null || it.page <= it.total)))
                require(it.action !in setOf("page", "finish") || (it.page != null && it.page >= 0))
            }
        }
    }
}

fun WatchState.receiveRemote(snapshot: SharedTimerSnapshot, monotonic: Long, wall: Long, boot: Int): WatchState {
    if (remoteTimer == snapshot || (remoteTimer != null &&
        (if (remoteTimer.epoch == snapshot.epoch) snapshot.revision < remoteTimer.revision else snapshot.publishedAt < remoteTimer.publishedAt))) return this
    return copy(remoteTimer = snapshot, remoteAnchor = snapshot.receiveAnchor(monotonic, wall), remoteBoot = boot)
}

fun WatchState.displayActive(monotonic: Long, boot: Int): WatchActive? = active ?: remoteTimer?.let { remote ->
    // After reboot, recompute the display anchor; only the owner can change a remote timer's phase.
    remote.mirror(monotonic, if (remoteBoot == boot) remoteAnchor else remote.receiveAnchor(monotonic, System.currentTimeMillis()), boot)
}

fun WatchState.applySharedCommand(command: SharedTimerCommand, monotonic: Long, wall: Long): Pair<WatchState, String> {
    processedCommands[command.id]?.let { prior -> return this to
        (command.receiptStatus(prior) ?: "payload_conflict") }
    val rejection = command.validFor(active?.timer?.syncId, timerRevision)
    var status = rejection ?: "applied"
    val next = if (rejection != null) this else runCatching {
        require(command.target == "watch")
        when (command.action) {
            "pause" -> pause(monotonic)
            "resume" -> { check(active!!.timer.phase == PhysicalTimerPhase.PAUSED); resume(monotonic) }
            "stop" -> copy(active = active!!.copy(timer = active.timer.stop(monotonic, wall)))
            "page" -> page(command.page!!)
            "finish" -> {
                val withTotal = if (command.total != null) {
                    require(command.total >= active!!.timer.startPage && command.total >= active.page)
                    copy(active = active.copy(book = active.book.copy(total = command.total), timer = active.timer.copy(pageCount = command.total)))
                } else this
                withTotal.page(command.page!!).finish(monotonic, wall)
            }
            "discard" -> copy(active = null)
            else -> error("Unknown timer command")
        }
    }.getOrElse { status = "invalid"; this }
    val revision = timerRevision + if (WatchTimerSurface.from(active) != WatchTimerSurface.from(next.active) || active?.timer?.syncId != next.active?.timer?.syncId) 1 else 0
    return next.copy(processedCommands = (next.processedCommands + (command.id to command.receipt(status, revision))).entries
        .toList().takeLast(200).associate { it.toPair() }) to status
}
