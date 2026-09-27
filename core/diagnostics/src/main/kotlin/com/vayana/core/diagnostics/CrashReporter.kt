package com.vayana.core.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Installs a global uncaught-exception handler that journals the crash before the process dies. */
@Singleton
class CrashReporter @Inject constructor(
    private val logStore: DiagnosticsLogStore,
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) {
    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                logStore.record(
                    category = DiagnosticCategory.CRASH,
                    source = thread.name,
                    message = throwable.message ?: throwable.javaClass.simpleName,
                    detail = throwable.stackTraceToString(),
                )
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(CrashExitCode)
            }
        }
        capturePreviousSystemExitAsync()
    }

    private fun capturePreviousSystemExitAsync() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        appScope.launch(dispatchers.io) { runCatching { capturePreviousSystemExits() } }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun capturePreviousSystemExits() {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        val exits = activityManager
            .getHistoricalProcessExitReasons(context.packageName, 0, MaxSystemExitHistory)
            .filter { it.processName == context.packageName }
            .sortedBy { it.timestamp }
        val checkpoint = logStore.lastProcessedExitTimestamp()
        val newExits = exits.filter { it.timestamp > checkpoint }
        if (newExits.isEmpty()) return

        val existingCrashes = logStore.readAll().filter { it.category == DiagnosticCategory.CRASH }
        newExits.forEach { exit ->
            if (exit.reason in CapturedExitReasons && existingCrashes.none { event ->
                    abs(event.timestamp - exit.timestamp) <= DuplicateExitWindowMillis
                }
            ) {
                logStore.record(
                    category = DiagnosticCategory.CRASH,
                    source = SystemExitSource,
                    message = exit.reason.toExitMessage(),
                    detail = exit.toTechnicalDetail(),
                    timestamp = exit.timestamp,
                )
            }
        }
        logStore.markExitTimestampProcessed(newExits.last().timestamp)
    }
}

private fun Int.toExitMessage(): String = when (this) {
    ApplicationExitInfo.REASON_ANR -> "Vayana stopped responding"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "Vayana encountered a native crash"
    else -> "Vayana crashed"
}

private fun ApplicationExitInfo.toTechnicalDetail(): String = buildString {
    appendLine("Process: $processName")
    appendLine("Reason: $reason")
    appendLine("Status: $status")
    description?.takeIf { it.isNotBlank() }?.let { appendLine("Description: $it") }
    readTrace()?.takeIf { it.isNotBlank() }?.let {
        appendLine("System trace:")
        append(it)
    }
}

private fun ApplicationExitInfo.readTrace(): String? = runCatching {
    traceInputStream?.use { stream ->
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            val buffer = CharArray(TraceReadBufferChars)
            buildString {
                while (length < MaxSystemTraceChars) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, MaxSystemTraceChars - length))
                    if (count <= 0) break
                    append(buffer, 0, count)
                }
            }
        }
    }
}.getOrNull()

private const val CrashExitCode = 10
private const val MaxSystemExitHistory = 10
private const val MaxSystemTraceChars = 24_000
private const val TraceReadBufferChars = 2_048
private const val DuplicateExitWindowMillis = 15_000L
private const val SystemExitSource = "Android system"

private val CapturedExitReasons = setOf(
    ApplicationExitInfo.REASON_ANR,
    ApplicationExitInfo.REASON_CRASH,
    ApplicationExitInfo.REASON_CRASH_NATIVE,
)
