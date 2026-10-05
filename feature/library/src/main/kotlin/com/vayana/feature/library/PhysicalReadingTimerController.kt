package com.vayana.feature.library

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.vayana.core.database.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.vayana.core.wear.*
import kotlinx.coroutines.flow.first

/** A single durable timer; a stopped log stays recoverable until its database transaction succeeds. */
@Singleton
class PhysicalReadingTimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val books: BookRepository,
) {
    private val preferences = context.getSharedPreferences("physical_reading_timer", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    private var remoteSnapshot = preferences.getString("remoteTimer", null)?.let { runCatching { SharedTimerSnapshot.parse(it) }.getOrNull() }
    private val mutableRemoteStatus = MutableStateFlow(if (preferences.getString("pendingTimerCommand", null) != null) "Waiting for watch · action queued" else if (remoteSnapshot?.book != null) "Watch timer" else "")
    val companionStatus = mutableRemoteStatus.asStateFlow()
    val isRemote get() = remoteSnapshot?.book != null
    val hasPendingCommand get() = preferences.getString("pendingTimerCommand", null) != null
    private val mutableSession = MutableStateFlow(readSession() ?: remoteSession())
    val session = mutableSession.asStateFlow()

    private fun readSession(): PhysicalTimerSession? = runCatching {
        val json = JSONObject(preferences.getString("session", null) ?: return null)
        PhysicalTimerSession(json.getLong("bookId"), json.getString("bookTitle"), json.getString("syncId"),
            json.getLong("startedAt"), json.getInt("startPage"), json.getLong("accumulatedMillis"),
            json.getLong("runningSince"), json.getInt("bootCount"),
            PhysicalTimerPhase.valueOf(json.getString("phase")), json.optLong("endedAt"),
            json.optInt("pageCount").takeIf { it > 0 },
            activeIntervals = json.optString("intervals").takeUnless { json.isNull("intervals") || !json.has("intervals") },
            anchorWall = json.optLong("anchorWall", json.getLong("startedAt")),
            anchorMono = json.optLong("anchorMono", json.getLong("runningSince")), clockChanged = json.optBoolean("clockChanged"))
            .let { stored ->
                // Keep the old boot marker until recovery is durably committed by restore/resume.
                stored.recoverBoot(bootCount, SystemClock.elapsedRealtime(), System.currentTimeMillis()).copy(bootCount = stored.bootCount)
            }
    }.getOrNull()

    private suspend fun persist(next: PhysicalTimerSession?, command: SharedTimerCommand? = null, status: String = "applied", page: Int? = null) {
        if (next == mutableSession.value && command == null) return
        withContext(NonCancellable + Dispatchers.IO) {
            val value = next?.let { JSONObject().put("bookId", it.bookId).put("bookTitle", it.bookTitle)
                .put("syncId", it.syncId).put("startedAt", it.startedAt).put("startPage", it.startPage)
                .put("accumulatedMillis", it.accumulatedMillis).put("runningSince", it.runningSince)
                .put("bootCount", it.bootCount).put("phase", it.phase.name).put("endedAt", it.endedAt)
                .put("pageCount", it.pageCount).put("intervals", it.activeIntervals)
                .put("anchorWall", it.anchorWall).put("anchorMono", it.anchorMono).put("clockChanged", it.clockChanged).toString() }
            val editor = preferences.edit().putString("session", value)
            val changed = next != mutableSession.value || (command != null && status == "applied")
            if (changed) editor.putLong("sharedRevision", preferences.getLong("sharedRevision", 0) + 1)
            if (next?.syncId != mutableSession.value?.syncId) editor.putInt("sharedPage", next?.startPage ?: 0)
            page?.let { editor.putInt("sharedPage", it) }
            command?.let {
                val receipts = JSONObject(preferences.getString("timerReceipts", "{}")!!)
                receipts.put(it.id, it.receipt(status, preferences.getLong("sharedRevision", 0) + if (changed) 1 else 0))
                while (receipts.length() > 200) receipts.remove(receipts.keys().next())
                editor.putString("timerReceipts", receipts.toString())
            }
            check(editor.commit()) { "Could not save timer" }
            mutableSession.value = next
        }
    }

    suspend fun start(bookId: Long, title: String, startPage: Int, pageCount: Int?) = mutex.withLock {
        check(mutableSession.value == null) { "Another reading timer is active" }
        require(startPage >= 0 && (pageCount == null || (pageCount > 0 && startPage <= pageCount)))
        withContext(Dispatchers.IO) { books.markPhysicalBookReading(bookId) }
        persist(PhysicalTimerSession(bookId, title, "session-${UUID.randomUUID()}", System.currentTimeMillis(),
            startPage, runningSince = SystemClock.elapsedRealtime(), bootCount = bootCount, pageCount = pageCount, activeIntervals = ""))
    }

    /** Persists the safe paused state produced when monotonic time was reset by a reboot. */
    suspend fun restoreAfterBoot() = mutex.withLock {
        if (isRemote) return@withLock
        val timer = mutableSession.value ?: return@withLock
        if (timer.bootCount == bootCount) return@withLock
        persist(
            timer.recoverBoot(bootCount, SystemClock.elapsedRealtime(), System.currentTimeMillis()),
        )
    }

    suspend fun pause() = mutex.withLock {
        if (isRemote) { queueRemote("pause"); return@withLock }
        mutableSession.value?.let { persist(it.pause(SystemClock.elapsedRealtime())) }
    }
    suspend fun resume() = mutex.withLock {
        if (isRemote) { queueRemote("resume"); return@withLock }
        mutableSession.value?.takeIf { it.phase == PhysicalTimerPhase.PAUSED }?.let {
            withContext(Dispatchers.IO) { books.markPhysicalBookReading(it.bookId) }
            persist(it.resume(SystemClock.elapsedRealtime()).copy(bootCount = bootCount))
        }
    }
    suspend fun stop() = mutex.withLock {
        if (isRemote) { queueRemote("stop"); return@withLock }
        mutableSession.value?.let { persist(it.stop(SystemClock.elapsedRealtime(), System.currentTimeMillis())) }
    }
    suspend fun discard() = mutex.withLock {
        if (isRemote) queueRemote("discard") else persist(null)
    }

    suspend fun save(endPage: Int, pageCount: Int?) = mutex.withLock {
        if (isRemote) { queueRemote("finish", endPage, pageCount); return@withLock }
        val timer = checkNotNull(mutableSession.value)
        check(timer.phase == PhysicalTimerPhase.STOPPED)
        withContext(Dispatchers.IO) {
            books.recordPhysicalReadingSession(timer.bookId, timer.syncId, timer.startedAt, timer.endedAt,
                (timer.accumulatedMillis / 1000).coerceAtLeast(1), timer.startPage, endPage, pageCount, activeIntervals = timer.activeIntervals)
        }
        persist(null)
    }

    private fun remoteSession(): PhysicalTimerSession? = remoteSnapshot?.let {
        val now = SystemClock.elapsedRealtime()
        it.mirror(now, it.receiveAnchor(now, System.currentTimeMillis()), bootCount, preferences.getLong("remoteBookId", 0))?.timer
    }

    fun localSession(): PhysicalTimerSession? = mutableSession.value.takeUnless { isRemote }
    fun pendingCommand(): SharedTimerCommand? = preferences.getString("pendingTimerCommand", null)?.let(SharedTimerCommand::parse)

    private suspend fun queueRemote(action: String, page: Int? = null, total: Int? = null) {
        check(!hasPendingCommand) { "Waiting for the watch to acknowledge the previous action" }
        val remote = checkNotNull(remoteSnapshot)
        val command = SharedTimerCommand("cmd-${UUID.randomUUID()}", "watch", checkNotNull(remote.sessionId), remote.revision, action, page, total)
        withContext(NonCancellable + Dispatchers.IO) {
            check(preferences.edit().putString("pendingTimerCommand", command.json()).commit())
            mutableRemoteStatus.value = "Waiting for watch · $action queued"
        }
    }

    suspend fun receiveRemote(snapshot: SharedTimerSnapshot, localBookId: Long) = mutex.withLock {
        if (localSession() != null) {
            mutableRemoteStatus.value = if (snapshot.book != null) "A separate watch timer is active" else ""
            return@withLock
        }
        if (remoteSnapshot == snapshot ||
            (remoteSnapshot != null && (if (remoteSnapshot!!.epoch == snapshot.epoch) snapshot.revision < remoteSnapshot!!.revision
                else snapshot.publishedAt < remoteSnapshot!!.publishedAt))) return@withLock
        withContext(NonCancellable + Dispatchers.IO) {
            check(preferences.edit().putString("remoteTimer", snapshot.json()).putLong("remoteBookId", localBookId).commit())
            remoteSnapshot = snapshot
            mutableSession.value = remoteSession()
            if (!hasPendingCommand) mutableRemoteStatus.value = if (snapshot.book != null) "Watch timer" else ""
        }
    }

    suspend fun acknowledgeRemote(command: SharedTimerCommand, receipt: String) = mutex.withLock {
        if (pendingCommand() != command) return@withLock
        val status = command.receiptStatus(receipt) ?: return@withLock
        val revision = command.receiptRevision(receipt)
        if (revision != null && (remoteSnapshot?.revision ?: -1) < revision) return@withLock
        withContext(NonCancellable + Dispatchers.IO) {
            check(preferences.edit().remove("pendingTimerCommand").commit())
            mutableRemoteStatus.value = if (status == "applied") { if (isRemote) "Watch timer" else "" }
                else "Timer changed on watch · action was not applied"
        }
    }

    suspend fun ownSnapshot(): SharedTimerSnapshot = mutex.withLock {
        val timer = localSession()
        val row = timer?.let { t -> books.observeAll().first().firstOrNull { it.id == t.bookId } }
        val active = if (timer != null && row != null) WatchActive(WearBook(row.syncId, timer.bookTitle,
            timer.startPage, timer.pageCount, row.updatedAt), timer, preferences.getInt("sharedPage", timer.startPage)) else null
        val epoch = preferences.getString("timerEpoch", null) ?: UUID.randomUUID().toString().also {
            check(preferences.edit().putString("timerEpoch", it).commit())
        }
        SharedTimerSnapshot.create("phone", preferences.getLong("sharedRevision", 0), active,
            SystemClock.elapsedRealtime(), System.currentTimeMillis(), epoch)
    }

    fun commandReceipt(command: SharedTimerCommand): String? = JSONObject(preferences.getString("timerReceipts", "{}")!!)
        .optString(command.id).takeIf { command.receiptStatus(it) != null }

    suspend fun applyCompanionCommand(command: SharedTimerCommand): String = mutex.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            val receipts = JSONObject(preferences.getString("timerReceipts", "{}")!!)
            receipts.optString(command.id).takeIf { it.isNotBlank() }?.let { return@withContext command.receiptStatus(it) ?: "payload_conflict" }
            val timer = localSession()
            var status = command.validFor(timer?.syncId, preferences.getLong("sharedRevision", 0)) ?: "applied"
            var next = timer
            var page: Int? = null
            if (status == "applied") try {
                require(command.target == "phone")
                val current = checkNotNull(timer)
                when (command.action) {
                    "pause" -> next = current.pause(SystemClock.elapsedRealtime())
                    "resume" -> { check(current.phase == PhysicalTimerPhase.PAUSED); next = current.resume(SystemClock.elapsedRealtime()) }
                    "stop" -> next = current.stop(SystemClock.elapsedRealtime(), System.currentTimeMillis())
                    "discard" -> next = null
                    "page" -> { require(command.page!! >= 0 && (current.pageCount == null || command.page!! <= current.pageCount!!)); page = command.page }
                    "finish" -> {
                        val stopped = current.stop(SystemClock.elapsedRealtime(), System.currentTimeMillis())
                        val total = command.total ?: current.pageCount
                        require(command.page!! >= 0 && (total == null || (total > 0 && command.page!! <= total && current.startPage <= total)))
                        books.recordPhysicalReadingSession(current.bookId, current.syncId, current.startedAt, stopped.endedAt,
                            (stopped.accumulatedMillis / 1000).coerceAtLeast(1), current.startPage, command.page!!, total, activeIntervals = stopped.activeIntervals)
                        next = null
                    }
                }
            } catch (e: IllegalArgumentException) { status = "invalid"; next = timer }
              catch (e: IllegalStateException) { status = "invalid"; next = timer }
            // Local state, revision and command receipt commit together before transport acknowledgement.
            if (isRemote) {
                receipts.put(command.id, command.receipt("ended", preferences.getLong("sharedRevision", 0)))
                check(preferences.edit().putString("timerReceipts", receipts.toString()).commit())
                return@withContext "ended"
            }
            persist(next, command, status, page)
            status
        }
    }
}
