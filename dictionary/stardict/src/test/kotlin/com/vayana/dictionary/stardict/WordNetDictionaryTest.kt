package com.vayana.dictionary.stardict

import com.vayana.dictionary.api.PartOfSpeech
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordNetDictionaryTest {
    @Test
    fun `looks up a WordNet index offset and parses definition and example`() {
        val directory = createTempDirectory("wordnet-test").toFile()
        try {
            File(directory, "index.noun").writeText("policeman n 1 0 1 0 00000000\n")
            File(directory, "data.noun").writeText(
                "00000000 00 n 01 policeman 0 000 | a member of a police force; \"the policeman arrived\"\n",
            )

            val entry = WordNetDictionary(directory).lookup("Policeman")

            assertEquals("policeman", entry?.headword)
            assertEquals(PartOfSpeech.NOUN, entry?.senses?.single()?.partOfSpeech)
            assertEquals("a member of a police force", entry?.senses?.single()?.definition)
            assertEquals(listOf("the policeman arrived"), entry?.senses?.single()?.examples)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `uses exception forms for inflected words`() {
        val directory = createTempDirectory("wordnet-test").toFile()
        try {
            File(directory, "index.noun").writeText("policeman n 1 0 1 0 00000000\n")
            File(directory, "data.noun").writeText("00000000 00 n 01 policeman 0 000 | a member of a police force\n")
            File(directory, "noun.exc").writeText("policemen policeman\n")

            assertEquals("policeman", WordNetDictionary(directory).lookup("policemen")?.headword)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `binary index lookup finds first middle and last entries`() {
        val directory = createTempDirectory("wordnet-test").toFile()
        try {
            val dataLines = listOf(
                "00 n 01 alpha 0 000 | first letter",
                "00 n 01 middle 0 000 | equally distant from the ends",
                "00 n 01 zulu 0 000 | a member of the Zulu people",
            )
            val data = StringBuilder()
            val offsets = dataLines.map { remainder ->
                val offset = data.length
                data.append(offset.toString().padStart(8, '0')).append(' ').append(remainder).append('\n')
                offset
            }
            File(directory, "index.noun").writeText(
                "  WordNet header\n" + listOf("alpha", "middle", "zulu").zip(offsets).joinToString("\n", postfix = "\n") {
                    (word, offset) -> "$word n 1 0 1 0 ${offset.toString().padStart(8, '0')}"
                },
            )
            File(directory, "data.noun").writeText(data.toString())

            val dictionary = WordNetDictionary(directory)

            assertEquals("first letter", dictionary.lookup("alpha")?.senses?.single()?.definition)
            assertEquals("equally distant from the ends", dictionary.lookup("middle")?.senses?.single()?.definition)
            assertEquals("a member of the Zulu people", dictionary.lookup("zulu")?.senses?.single()?.definition)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `falls back to regular morphology when an exception is absent`() {
        val directory = createTempDirectory("wordnet-test").toFile()
        try {
            File(directory, "index.verb").writeText("stop v 1 0 1 0 00000000\n")
            File(directory, "data.verb").writeText("00000000 00 v 01 stop 0 000 | come to a halt\n")

            assertEquals("stop", WordNetDictionary(directory).lookup("stopping")?.headword)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `handles y inflections without exception entries`() {
        val directory = createTempDirectory("wordnet-test").toFile()
        try {
            File(directory, "index.verb").writeText("study v 1 0 1 0 00000000\n")
            File(directory, "data.verb").writeText("00000000 00 v 01 study 0 000 | learn about a subject\n")

            assertEquals("study", WordNetDictionary(directory).lookup("studied")?.headword)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `decodes WordNet glosses as UTF-8`() {
        val directory = createTempDirectory("wordnet-test").toFile()
        try {
            File(directory, "index.noun").writeText("cafe n 1 0 1 0 00000000\n")
            File(directory, "data.noun").writeText("00000000 00 n 01 cafe 0 000 | a small café\n", Charsets.UTF_8)

            assertEquals("a small café", WordNetDictionary(directory).lookup("cafe")?.senses?.single()?.definition)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `rejects phrases and punctuation-only selections`() {
        assertNull(WordNetDictionary.normalizeLookupWord("two words"))
        assertNull(WordNetDictionary.normalizeLookupWord("…"))
        assertEquals("can't", WordNetDictionary.normalizeLookupWord("“Can’t”"))
    }
}
