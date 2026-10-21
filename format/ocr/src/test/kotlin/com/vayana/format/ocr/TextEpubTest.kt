package com.vayana.format.ocr

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.json.JSONArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextEpubTest {
    // a line of words at [left], [height] points tall, from [top]
    private fun line(left: Float, top: Float, vararg words: String, height: Float = 8f): List<RecognizedWord> {
        var x = left
        return words.map { word -> RecognizedWord(word, x, top, x + 20f, top + height, 90f).also { x += 25f } }
    }

    private val header = line(100f, 20f, "റാം", "c/o", "ആനന്ദി")
    private fun page(vararg body: List<RecognizedWord>, number: String? = null, joins: Set<Int> = emptySet()) =
        RecognizedPage(listOf(header) + body + listOfNotNull(number?.let { line(150f, 560f, it) }), joins.map { it + 1 }.toSet())

    private val pages = mapOf(
        // a chapter heading: the first line, with several lines' space below it
        0 to page(line(30f, 60f, "ഒന്ന്"), line(40f, 160f, "ആദ്യത്തെ", "വരി"), line(30f, 175f, "തുടർന്ന്", "കിലോമീറ്ററു"),
            line(30f, 190f, "കൾക്കു", "അപ്പുറം."), number = "9", joins = setOf(2)),
        1 to page(line(30f, 60f, "അതേ", "ഖണ്ഡിക."), line(40f, 75f, "പുതിയ", "ഖണ്ഡിക."), number = "10"),
        2 to page(line(30f, 60f, "മറ്റൊരു"), line(30f, 75f, "വരി."), line(30f, 90f, "വെളിക്കിറ")),
        // the word broken at the foot of page 2 runs on into page 3
        3 to page(line(30f, 60f, "ങ്ങാനിരുന്നവർ"), line(30f, 75f, "ഒരു"), line(30f, 90f, "വരി.")),
        4 to page(line(30f, 60f, "രണ്ട്"), line(40f, 160f, "രണ്ടാം", "അധ്യായം."), line(30f, 175f, "തുടരുന്നു.")),
    )

    @Test
    fun `pages become chapters of paragraphs without headers or page numbers`() {
        val book = reflowRecognizedPages(pages)
        assertEquals(listOf("ഒന്ന്", "രണ്ട്"), book.chapters.map { it.title })
        val first = book.chapters[0].blocks.map { block -> block.parts.filterIsInstance<TextPart.Text>().joinToString("|") { it.text } }
        // an indented line starts a paragraph, a word broken across lines is joined, and a paragraph runs on across
        // a page; the running header and the page numbers are gone
        assertEquals(
            listOf("ആദ്യത്തെ വരി തുടർന്ന് കിലോമീറ്ററുകൾക്കു അപ്പുറം.|അതേ ഖണ്ഡിക.", "പുതിയ ഖണ്ഡിക.|മറ്റൊരു വരി. വെളിക്കിറ|ങ്ങാനിരുന്നവർ ഒരു വരി."),
            first.filter { it.isNotEmpty() },
        )
        val allText = book.chapters.flatMap { it.blocks }.flatMap { it.parts }.filterIsInstance<TextPart.Text>().joinToString(" ") { it.text }
        assertTrue("റാം" !in allText && " 9" !in allText && "10" !in allText)
    }

    @Test
    fun `every page's start is marked once, in the chapter it begins`() {
        val book = reflowRecognizedPages(pages)
        val starts = book.chapters.map { chapter ->
            chapter.blocks.flatMap { it.parts }.filterIsInstance<TextPart.PageStart>().map { it.page }
        }
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(4)), starts)
        assertEquals(listOf(0, 4), book.chapters.map { it.firstPage })
    }

    @Test
    fun `the epub opens with an uncompressed mimetype and maps every page to its chapter file`() {
        val dir = Files.createTempDirectory("text-epub").toFile()
        val epub = dir.resolve("text.epub")
        val map = dir.resolve("pages.json")
        writeTextEpub(reflowRecognizedPages(pages), "Ram C/O Anandi", "Akhil P Dharmajan", "ml", epub, map, pageCount = 6)
        ZipFile(epub).use { zip ->
            val first = zip.entries().nextElement()
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
            val chapter = zip.getInputStream(zip.getEntry("OEBPS/c001.xhtml")).reader().readText()
            assertTrue("""<span id="page-1" class="vayana-page">⁠</span>""" in chapter)
            // no space where a page starts inside a word
            assertTrue("""വെളിക്കിറ<span id="page-3" class="vayana-page">⁠</span>ങ്ങാനിരുന്നവർ""" in chapter)
            assertTrue("<h2>ഒന്ന്</h2>" in chapter)
            assertTrue(zip.getEntry("OEBPS/c002.xhtml") != null && zip.getEntry("OEBPS/content.opf") != null)
        }
        // page 5 has no text: it opens where the text before it is
        assertEquals(listOf("OEBPS/c001.xhtml", "OEBPS/c001.xhtml", "OEBPS/c001.xhtml", "OEBPS/c001.xhtml", "OEBPS/c002.xhtml", "OEBPS/c002.xhtml"), JSONArray(map.readText()).let { array -> List(array.length()) { array.getString(it) } })
    }
}
