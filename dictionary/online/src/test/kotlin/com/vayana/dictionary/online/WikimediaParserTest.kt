package com.vayana.dictionary.online

import com.vayana.dictionary.api.PartOfSpeech
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WikimediaParserTest {
    @Test
    fun `reads English Wiktionary senses as plain text`() {
        val json = """
            {"en":[{"partOfSpeech":"Noun","language":"English","definitions":[
              {"definition":"<span class=\"usage-label-sense\"></span> A <a href=\"/wiki/whisper\">whispering</a> or rustling sound; a murmur.",
               "parsedExamples":[{"example":"the <b>susurrus</b> of the leaves"}]},
              {"definition":""}]}],
             "la":[{"partOfSpeech":"Noun","language":"Latin","definitions":[{"definition":"whisper"}]}]}
        """.trimIndent()

        val entry = WikimediaParser.wiktionary("susurrus", json, "https://en.wiktionary.org/wiki/susurrus")!!

        assertEquals("susurrus", entry.headword)
        assertEquals(PartOfSpeech.NOUN, entry.senses.single().partOfSpeech)
        assertEquals("A whispering or rustling sound; a murmur.", entry.senses.single().definition)
        assertEquals(listOf("the susurrus of the leaves"), entry.senses.single().examples)
        assertEquals("https://en.wiktionary.org/wiki/susurrus", entry.sourceUrl)
    }

    @Test
    fun `marks other languages senses when English has none`() {
        val json = """{"la":[{"partOfSpeech":"Noun","language":"Latin","definitions":[{"definition":"<a>whisper</a>"}]}]}"""

        val entry = WikimediaParser.wiktionary("susurrus", json, "url")!!

        assertEquals("(Latin) whisper", entry.senses.single().definition)
    }

    @Test
    fun `puts word senses ahead of symbols`() {
        val json = """
            {"en":[{"partOfSpeech":"Symbol","language":"English","definitions":[{"definition":"a code"}]},
                   {"partOfSpeech":"Verb","language":"English","definitions":[{"definition":"simple past of run"}]}]}
        """.trimIndent()

        val senses = WikimediaParser.wiktionary("ran", json, "url")!!.senses

        assertEquals(listOf(PartOfSpeech.VERB, PartOfSpeech.UNKNOWN), senses.map { it.partOfSpeech })
    }

    @Test
    fun `no definitions means no entry`() {
        assertNull(WikimediaParser.wiktionary("x", """{"en":[{"partOfSpeech":"Noun","definitions":[]}]}""", "url"))
    }

    @Test
    fun `reads a Wikipedia summary as description then extract`() {
        val json = """
            {"type":"standard","title":"Byzantium","description":"ancient Greek city",
             "extract":"Byzantium was an ancient Greek city.",
             "content_urls":{"mobile":{"page":"https://en.m.wikipedia.org/wiki/Byzantium"}}}
        """.trimIndent()

        val entry = WikimediaParser.wikipedia(json)!!

        assertEquals("Byzantium", entry.headword)
        assertEquals(listOf("Ancient Greek city", "Byzantium was an ancient Greek city."), entry.senses.map { it.definition })
        assertEquals("https://en.m.wikipedia.org/wiki/Byzantium", entry.sourceUrl)
    }

    @Test
    fun `a Wikipedia page without an extract is no entry`() {
        assertNull(WikimediaParser.wikipedia("""{"type":"no-extract","title":"X","extract":""}"""))
    }

    @Test
    fun `reads opensearch titles`() {
        val json = """["hagia sophia",["Hagia Sophia","Hagia Sophia, Trabzon"],["",""],["u1","u2"]]"""

        assertEquals(listOf("Hagia Sophia", "Hagia Sophia, Trabzon"), WikimediaParser.openSearchTitles(json))
        assertEquals(emptyList<String>(), WikimediaParser.openSearchTitles("""["qqzz",[],[],[]]"""))
    }

    @Test
    fun `decodes entities and collapses whitespace`() {
        assertEquals("fish & chips — “hot”", "fish &amp; chips\n &#8212; &#x201C;hot&#x201d;".htmlToText())
    }
}
