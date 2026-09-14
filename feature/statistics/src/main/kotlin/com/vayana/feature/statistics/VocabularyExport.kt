package com.vayana.feature.statistics

import androidx.annotation.StringRes
import com.vayana.core.database.model.VocabularyCard
import com.vayana.core.resources.R

enum class VocabularyExportFormat(@param:StringRes val labelRes: Int, val fileName: String, val mimeType: String) {
    ANKI_CSV(R.string.vocabulary_export_anki, "vayana-vocabulary.csv", "text/csv"),
    MARKDOWN(R.string.vocabulary_export_markdown, "vayana-vocabulary.md", "text/markdown"),
}

/**
 * Saved words as a CSV Anki imports directly: the `#` header lines tell Anki the separator and the column names
 * (word on the front, definition, example sentence and book as fields). Every field is quoted.
 */
internal fun vocabularyAnkiCsv(cards: List<VocabularyCard>): String = buildString {
    appendLine("#separator:Comma")
    appendLine("#html:false")
    appendLine("#columns:Word,Definition,Sentence,Book")
    cards.sortedBy { it.word.lowercase() }.forEach { card ->
        appendLine(
            listOf(card.word, card.definition, card.sentence.orEmpty(), card.bookTitle.orEmpty())
                .joinToString(",") { it.asCsvField() },
        )
    }
}

/** Saved words grouped by the book they came from, then [otherWordsHeading] for words without one. */
internal fun vocabularyMarkdown(cards: List<VocabularyCard>, title: String, otherWordsHeading: String): String = buildString {
    appendLine("# $title")
    val byBook = cards.groupBy { it.bookTitle?.takeIf(String::isNotBlank) }
    val books = byBook.keys.filterNotNull().sortedBy { it.lowercase() }
    val sections = books.map { it to byBook.getValue(it) } + listOfNotNull(byBook[null]?.let { otherWordsHeading to it })
    sections.forEach { (heading, words) ->
        appendLine()
        appendLine("## $heading")
        appendLine()
        words.sortedBy { it.word.lowercase() }.forEach { card ->
            appendLine("- **${card.word}** — ${card.definition.singleLine()}")
            card.sentence?.takeIf { it.isNotBlank() }?.let { appendLine("  > ${it.singleLine()}") }
        }
    }
}

private fun String.singleLine(): String = replace(Regex("\\s*\\R\\s*"), " ").trim()

private fun String.asCsvField(): String = "\"${singleLine().replace("\"", "\"\"")}\""
