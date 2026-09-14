package com.vayana.feature.statistics

import com.vayana.core.database.model.VocabularyCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VocabularyExportTest {
    @Test
    fun ankiCsvHasHeadersAndQuotesEveryField() {
        val csv = vocabularyAnkiCsv(listOf(card("zealous", "full of \"zeal\"", "A zealous\nfan", "Stories")))

        assertTrue(csv.startsWith("#separator:Comma\n#html:false\n#columns:Word,Definition,Sentence,Book\n"))
        assertTrue(csv.contains("\"zealous\",\"full of \"\"zeal\"\"\",\"A zealous fan\",\"Stories\""))
    }

    @Test
    fun markdownGroupsWordsByBookWithOtherWordsLast() {
        val markdown = vocabularyMarkdown(
            listOf(
                card("wistful", "sadly longing", null, null),
                card("austere", "severe", "An austere room.", "Bleak House"),
                card("ardent", "passionate", null, "Anna Karenina"),
            ),
            title = "Vocabulary",
            otherWordsHeading = "Other words",
        )

        assertEquals(
            """
            # Vocabulary

            ## Anna Karenina

            - **ardent** — passionate

            ## Bleak House

            - **austere** — severe
              > An austere room.

            ## Other words

            - **wistful** — sadly longing

            """.trimIndent(),
            markdown,
        )
    }

    private fun card(word: String, definition: String, sentence: String?, book: String?) = VocabularyCard(
        id = 0,
        word = word,
        definition = definition,
        sentence = sentence,
        bookId = null,
        bookTitle = book,
        createdAt = 1L,
        lastReviewedAt = null,
        known = false,
    )
}
