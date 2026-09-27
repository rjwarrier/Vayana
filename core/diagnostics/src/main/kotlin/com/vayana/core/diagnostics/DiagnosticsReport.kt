package com.vayana.core.diagnostics

import java.time.Instant

data class DiagnosticsEnvironment(
    val appVersionName: String,
    val appVersionCode: Long,
    val androidVersion: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
)

/** Builds a plain-text report that users can inspect before sharing with support. */
fun buildDiagnosticsReport(
    environment: DiagnosticsEnvironment,
    events: List<DiagnosticEvent>,
    generatedAt: Long = System.currentTimeMillis(),
): String = buildString {
    val crashCount = events.count { it.category == DiagnosticCategory.CRASH }
    val syncCount = events.count { it.category == DiagnosticCategory.SYNC }
    appendLine("Vayana diagnostics report")
    appendLine("Generated: ${generatedAt.toIsoTimestamp()}")
    appendLine("App: ${environment.appVersionName} (${environment.appVersionCode})")
    appendLine("Android: ${environment.androidVersion} (SDK ${environment.sdkInt})")
    appendLine("Device: ${environment.manufacturer} ${environment.model}")
    appendLine("Events: ${events.size} total; $crashCount crashes; $syncCount sync issues")
    appendLine()
    appendLine("Private book content, notes, and settings are not included. Common credentials and user file paths are redacted, but review this report before sharing.")

    if (events.isEmpty()) {
        appendLine()
        appendLine("No crashes or sync issues have been recorded.")
        return@buildString
    }

    events.sortedByDescending { it.timestamp }.forEachIndexed { index, event ->
        appendLine()
        appendLine("--- Event ${index + 1}: ${event.category.name} ---")
        appendLine("ID: ${event.id}")
        appendLine("Time: ${event.timestamp.toIsoTimestamp()}")
        appendLine("Source: ${event.source.redactForSupportReport()}")
        appendLine("Message: ${event.message.redactForSupportReport()}")
        event.detail?.let {
            appendLine("Technical details:")
            appendLine(it.redactForSupportReport())
        }
    }
}

internal fun String.redactForSupportReport(): String {
    var redacted = this
    redacted = AuthorizationPattern.replace(redacted) { "${it.groupValues[1]}<redacted>" }
    redacted = SecretParameterPattern.replace(redacted) { "${it.groupValues[1]}<redacted>" }
    redacted = GitHubTokenPattern.replace(redacted, "<redacted-token>")
    redacted = WindowsUserPathPattern.replace(redacted) { "C:\\Users\\<redacted>" }
    redacted = UnixUserPathPattern.replace(redacted) { "${it.groupValues[1]}<redacted>" }
    redacted = ExternalStoragePathPattern.replace(redacted, "<external-file>")
    redacted = ContentUriPattern.replace(redacted, "<content-uri>")
    redacted = EmailPattern.replace(redacted, "<email>")
    return redacted
}

private fun Long.toIsoTimestamp(): String = Instant.ofEpochMilli(this).toString()

private val AuthorizationPattern = Regex("(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+)?)[^\\s,;]+")
private val SecretParameterPattern = Regex("(?i)([?&](?:access_token|token|password|secret|api_key|key)=)[^&\\s]+")
private val GitHubTokenPattern = Regex("\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b")
private val WindowsUserPathPattern = Regex("(?i)C:\\\\Users\\\\[^\\\\/\\s]+")
private val UnixUserPathPattern = Regex("(?i)(/home/|/Users/)[^/\\s]+")
private val ExternalStoragePathPattern = Regex("/storage/emulated/\\d+/[^\\s\\])}>]+")
private val ContentUriPattern = Regex("content://[^\\s\\])}>]+")
private val EmailPattern = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
