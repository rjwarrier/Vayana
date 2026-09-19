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
    suspend fun clearSelection()
    suspend fun search(query: String)
    suspend fun clearSearch()
    fun events(): Flow<EngineEvent>

    /** Sentences from the first one on the current page to the end of its chapter, for read-aloud. */
    suspend fun startSpeech(): SpeechChunk

    /** Moves to the next chapter and returns all of its sentences. */
    suspend fun nextSpeechChunk(): SpeechChunk

    /** Highlights [start, end) within sentence [id] and turns the page once that range is past the one on screen. */
    suspend fun markSpeech(id: String, start: Int, end: Int)

    suspend fun stopSpeech()

    /** Each all-letter word of at least [minLength] letters in the chapter on screen, as written, with how often it appears. */
    suspend fun chapterWordCounts(minLength: Int): Map<String, Int>

    /**
     * The smallest range covering [cfi] and every one of [others] that overlaps it (directly or through another
     * overlapping one), with its text. Null when none overlap or the ranges can't be resolved on screen.
     */
    suspend fun mergeRanges(cfi: String, others: List<String>): MergedRange?

    fun close()
}
