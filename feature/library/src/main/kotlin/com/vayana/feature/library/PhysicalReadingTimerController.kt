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

/** A single durable timer; a stopped log stays recoverable until its database transaction succeeds. */
@Singleton
class PhysicalReadingTimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val books: BookRepository,
) {
    private val preferences = context.getSharedPreferences("physical_reading_timer", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    private val mutableSession = MutableStateFlow(readSession())
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

    private suspend fun persist(next: PhysicalTimerSession?) {
        if (next == mutableSession.value) return
        withContext(NonCancellable + Dispatchers.IO) {
            val value = next?.let { JSONObject().put("bookId", it.bookId).put("bookTitle", it.bookTitle)
                .put("syncId", it.syncId).put("startedAt", it.startedAt).put("startPage", it.startPage)
                .put("accumulatedMillis", it.accumulatedMillis).put("runningSince", it.runningSince)
                .put("bootCount", it.bootCount).put("phase", it.phase.name).put("endedAt", it.endedAt)
                .put("pageCount", it.pageCount).put("intervals", it.activeIntervals)
                .put("anchorWall", it.anchorWall).put("anchorMono", it.anchorMono).put("clockChanged", it.clockChanged).toString() }
            check(preferences.edit().putString("session", value).commit()) { "Could not save timer" }
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
        val timer = mutableSession.value ?: return@withLock
        if (timer.bootCount == bootCount) return@withLock
        persist(
            timer.recoverBoot(bootCount, SystemClock.elapsedRealtime(), System.currentTimeMillis()),
        )
    }

    suspend fun pause() = mutex.withLock {
        mutableSession.value?.let { persist(it.pause(SystemClock.elapsedRealtime())) }
    }
    suspend fun resume() = mutex.withLock {
        mutableSession.value?.takeIf { it.phase == PhysicalTimerPhase.PAUSED }?.let {
            withContext(Dispatchers.IO) { books.markPhysicalBookReading(it.bookId) }
            persist(it.resume(SystemClock.elapsedRealtime()).copy(bootCount = bootCount))
        }
    }
    suspend fun stop() = mutex.withLock {
        mutableSession.value?.let { persist(it.stop(SystemClock.elapsedRealtime(), System.currentTimeMillis())) }
    }
    suspend fun discard() = mutex.withLock { persist(null) }

    suspend fun save(endPage: Int, pageCount: Int?) = mutex.withLock {
        val timer = checkNotNull(mutableSession.value)
        check(timer.phase == PhysicalTimerPhase.STOPPED)
        withContext(Dispatchers.IO) {
            books.recordPhysicalReadingSession(timer.bookId, timer.syncId, timer.startedAt, timer.endedAt,
                (timer.accumulatedMillis / 1000).coerceAtLeast(1), timer.startPage, endPage, pageCount, activeIntervals = timer.activeIntervals)
        }
        persist(null)
    }
}
