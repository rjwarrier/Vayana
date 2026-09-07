package com.vayana.core.diagnostics

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/** Installs a global uncaught-exception handler that journals the crash before the process dies. */
@Singleton
class CrashReporter @Inject constructor(
    private val logStore: DiagnosticsLogStore,
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
                    detail = throwable.stackTraceToString().take(MaxCrashDetailChars),
                )
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(CrashExitCode)
            }
        }
    }
}

private const val MaxCrashDetailChars = 8_000
private const val CrashExitCode = 10
