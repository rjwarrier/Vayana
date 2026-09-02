package com.vayana.reader.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The reader engine contract (PROMPT2appbuild.md §3). Kept honest enough that a future native
 * Readium engine could implement it too — nothing here is foliate-js- or WebView-specific.
 * Uses plain [kotlin.Result], not `:core:common`'s `AppResult`, on purpose: this module is pure
 * Kotlin/JVM (§0.5, "domain has zero Android imports") and must not depend on an
 * `com.android.library` module like `:core:common` even transitively.
 */
interface BookEngine {
    /** [resumeLocator] (its `cfi`) is applied atomically with the first render — the underlying
     * renderer's one-shot init step can't be safely followed by a separate `goTo` call. */
    suspend fun open(source: BookSource, resumeLocator: Locator?): Result<OpenBook>
    val location: StateFlow<Locator?>
    suspend fun goTo(target: NavTarget)
    suspend fun applyStyle(style: BookStyle, theme: ReadTheme)
    suspend fun renderAnnotations(annotations: List<ReaderAnnotation>)
    fun events(): Flow<EngineEvent>
    fun close()
}
