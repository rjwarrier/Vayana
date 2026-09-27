package com.vayana.core.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * A small on-device journal of app crashes and sync issues, so a developer can pull a device's
 * recent history when a user reports a problem. Deliberately file-based rather than a Room table:
 * the crash handler must be able to write a record synchronously, on whatever thread crashed,
 * without depending on subsystems (like the app database) that might themselves be implicated in
 * the crash. Each record is one JSON line (NDJSON) so a torn write only ever corrupts its own line.
 */
@Singleton
class DiagnosticsLogStore internal constructor(private val logFile: File) {
    @Inject constructor(@ApplicationContext context: Context) :
        this(File(context.filesDir, "diagnostics/events.ndjson"))

    private val lock = Any()
    private val exitCheckpointFile = File(logFile.absoluteFile.parentFile, ExitCheckpointFileName)

    fun record(
        category: DiagnosticCategory,
        source: String,
        message: String,
        detail: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        val event = DiagnosticEvent(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp,
            category = category,
            source = source.take(MaxSourceChars),
            message = message.take(MaxMessageChars),
            detail = detail?.truncateDiagnosticDetail(),
        )
        synchronized(lock) {
            runCatching {
                logFile.parentFile?.mkdirs()
                FileOutputStream(logFile, true).use { stream ->
                    stream.write((event.toJsonLine() + "\n").toByteArray(Charsets.UTF_8))
                    runCatching { stream.fd.sync() }
                }
            }
            trimIfNeededLocked()
        }
    }

    fun readAll(): List<DiagnosticEvent> = synchronized(lock) {
        if (!logFile.isFile) return@synchronized emptyList()
        runCatching {
            logFile.readLines(Charsets.UTF_8)
                .mapNotNull { line -> line.toDiagnosticEventOrNull() }
                .sortedByDescending { it.timestamp }
        }.getOrDefault(emptyList())
    }

    fun clear() {
        synchronized(lock) {
            runCatching { logFile.writeText("") }
        }
    }

    internal fun lastProcessedExitTimestamp(): Long = synchronized(lock) {
        runCatching { exitCheckpointFile.readText().trim().toLong() }.getOrDefault(0L)
    }

    internal fun markExitTimestampProcessed(timestamp: Long) {
        synchronized(lock) {
            runCatching {
                exitCheckpointFile.parentFile?.mkdirs()
                exitCheckpointFile.writeText(timestamp.toString())
            }
        }
    }

    private fun trimIfNeededLocked() {
        if (!logFile.isFile || logFile.length() <= MaxLogFileBytes) return
        runCatching {
            val trimmed = logFile.readLines(Charsets.UTF_8).takeLast(MaxRetainedEvents)
            logFile.writeText(if (trimmed.isEmpty()) "" else trimmed.joinToString("\n", postfix = "\n"))
        }
    }
}

private fun String.truncateDiagnosticDetail(): String {
    if (length <= MaxDetailChars) return this
    return take(MaxDetailChars - TruncatedSuffix.length) + TruncatedSuffix
}

private fun DiagnosticEvent.toJsonLine(): String =
    JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("category", category.name)
        put("source", source)
        put("message", message)
        detail?.let { put("detail", it) }
    }.toString()

private fun String.toDiagnosticEventOrNull(): DiagnosticEvent? {
    if (isBlank()) return null
    return runCatching {
        val obj = JSONObject(this)
        val category = DiagnosticCategory.valueOf(obj.getString("category"))
        val id = obj.getString("id")
        val timestamp = obj.getLong("timestamp")
        DiagnosticEvent(
            id = id,
            timestamp = timestamp,
            category = category,
            source = obj.optString("source"),
            message = obj.optString("message"),
            detail = obj.optString("detail").takeIf { it.isNotBlank() },
        )
    }.getOrNull()
}

private const val MaxSourceChars = 200
private const val MaxMessageChars = 2_000
private const val MaxDetailChars = 32_000
private const val MaxLogFileBytes = 2 * 1024 * 1024
private const val MaxRetainedEvents = 500
private const val ExitCheckpointFileName = "system-exit.checkpoint"
private const val TruncatedSuffix = "\n… [truncated]"
