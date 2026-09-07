package com.vayana.core.diagnostics

enum class DiagnosticCategory { CRASH, SYNC }

data class DiagnosticEvent(
    val id: String,
    val timestamp: Long,
    val category: DiagnosticCategory,
    /** Short tag for where this came from, e.g. a class name or thread name. */
    val source: String,
    val message: String,
    /** Stack trace or other extended context, if any. */
    val detail: String? = null,
)
