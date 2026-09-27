package com.vayana.core.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.system.exitProcess

/** Installs a global uncaught-exception handler that journals the crash before the process dies. */
@Singleton
class CrashReporter @Inject constructor(
    private val logStore: DiagnosticsLogStore,
    @param:ApplicationContext private val context: Context,
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
                    detail = throwable.stackTraceToString().truncateDiagnosticDetail(),
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
        Thread(
            { runCatching { capturePreviousSystemExits() } },
            ExitCaptureThreadName,
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun capturePreviousSystemExits() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        val exits = activityManager
            .getHistoricalProcessExitReasons(context.packageName, 0, MaxSystemExitHistory)
            .filter { it.processName == context.packageName }
            .sortedBy { it.timestamp }
        if (exits.isEmpty()) return

        val checkpoint = logStore.lastProcessedExitTimestamp()
        val existingCrashes = logStore.readAll().filter { it.category == DiagnosticCategory.CRASH }
        exits.filter { it.timestamp > checkpoint }.forEach { exit ->
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
        logStore.markExitTimestampProcessed(exits.maxOf { it.timestamp })
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
}.truncateDiagnosticDetail()

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
private const val ExitCaptureThreadName = "vayana-exit-capture"
private const val SystemExitSource = "Android system"

private val CapturedExitReasons = setOf(
    ApplicationExitInfo.REASON_ANR,
    ApplicationExitInfo.REASON_CRASH,
    ApplicationExitInfo.REASON_CRASH_NATIVE,
)
