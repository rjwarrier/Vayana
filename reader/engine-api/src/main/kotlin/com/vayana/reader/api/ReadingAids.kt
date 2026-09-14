package com.vayana.reader.api

/** A footnote's text, shown in place of jumping to it; [href] still leads to where the note lives. */
data class Footnote(val text: String, val href: String)

/** The book asked to open a footnote (a tapped note reference). */
data class FootnoteOpened(val footnote: Footnote) : EngineEvent

/** One sentence to read aloud; [id] is what [BookEngine.markSpeech] highlights. */
data class SpeechSentence(val id: String, val text: String)

/** Sentences to read aloud next; [endOfBook] means nothing comes after them. */
data class SpeechChunk(val sentences: List<SpeechSentence>, val endOfBook: Boolean)
