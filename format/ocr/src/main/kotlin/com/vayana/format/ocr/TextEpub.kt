package com.vayana.format.ocr

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray

/**
 * A scanned book's recognised text as running prose, so it can be read as an EPUB with the reader's fonts, sizes,
 * spacing and themes (docs/SCANNED_PDF_TEXT_PLAN.md, "Text view"). Built from the pages :format:ocr saved:
 * - the running header (a page's first line, repeated on many pages or set apart by a little space) and a page
 *   number on its own last line are left out,
 * - a short first line with a wide space below it (a chapter number or title) starts a chapter,
 * - a line indented from the page's left margin starts a paragraph; others continue it, across pages too,
 * - words broken across lines are joined (see [lineJoins]).
 * Each printed page's place is kept as `<span id="page-N" class="vayana-page">`, so the reader can tell which page is
 * being read and open the text at a page. The span holds a word joiner (U+2060): invisible, no line break and no word
 * break (a page can start inside a word), but it gives the span a place on screen, which an empty one lacks and
 * going to it needs.
 */
data class TextBook(val chapters: List<TextChapter>)

/** A chapter: its [title] (null for the pages before the first heading) and paragraphs. */
data class TextChapter(val title: String?, val firstPage: Int, val blocks: List<TextBlock>)

/** A paragraph, made of runs of text with the start of a printed page between them. */
data class TextBlock(val parts: List<TextPart>)

sealed interface TextPart {
    data class Text(val text: String) : TextPart
    /** The start of a printed page; [insideWord] when a word broken at the foot of the page before runs on into it. */
    data class PageStart(val page: Int, val insideWord: Boolean = false) : TextPart
}

fun reflowRecognizedPages(pages: Map<Int, RecognizedPage>): TextBook {
    val wordHeights = pages.values.flatMap { page -> page.lines.flatten().map { it.bottom - it.top } }
    val bodyHeight = wordHeights.median() ?: return TextBook(emptyList())
    val runningHeaders = pages.values.mapNotNull { it.lines.firstOrNull()?.headerKey() }
        .groupingBy { it }.eachCount().filterValues { it >= maxOf(RunningHeaderMinPages, pages.size / 10) }.keys

    val chapters = mutableListOf<TextChapter>()
    var chapterTitle: String? = null
    var chapterFirstPage = pages.keys.minOrNull() ?: 0
    var blocks = mutableListOf<TextBlock>()
    var parts = mutableListOf<TextPart>()
    var text = StringBuilder()
    var joinNext = false
    var lastWord: String? = null
    val wholeWords by lazy { wholeWordCounts(pages) }

    fun endText() {
        if (text.isNotBlank()) parts += TextPart.Text(text.toString().trim())
        text = StringBuilder()
    }
    fun endParagraph() {
        endText()
        if (parts.isNotEmpty()) blocks += TextBlock(parts)
        parts = mutableListOf()
        joinNext = false
    }
    fun endChapter(page: Int) {
        endParagraph()
        if (blocks.isNotEmpty() || chapterTitle != null) chapters += TextChapter(chapterTitle, chapterFirstPage, blocks)
        blocks = mutableListOf()
        chapterFirstPage = page
    }

    for (pageIndex in pages.keys.sorted()) {
        val page = pages.getValue(pageIndex)
        val tops = page.lines.map { line -> line.minOf { it.top } }
        // the page's line spacing; the space below a line is measured in it
        // (a low percentile, so a page of a few lines with a heading's wide space still gives its lines' spacing)
        val pitch = tops.zipWithNext { above, below -> below - above }.filter { it > 0 }.percentile(0.3f) ?: (bodyHeight * 2)
        fun spaceBelow(index: Int) = if (index + 1 < tops.size) (tops[index + 1] - tops[index]) / pitch else 0f
        val header = page.lines.firstOrNull()?.let { first ->
            first.headerKey() in runningHeaders || (first.size <= HeadingMaxWords && spaceBelow(0) in HeaderSpace)
        } == true
        val lines = page.lines.withIndex().filterNot { (index, line) ->
            (index == 0 && header) || (index == page.lines.lastIndex && line.size == 1 && PageNumber.matches(line[0].text))
        }
        val margin = lines.map { it.value.first().left }.percentile(0.2f) ?: 0f
        // a word broken at the foot of the last page runs on into this one when the paragraph does
        val firstWord = lines.firstOrNull()?.value?.firstOrNull()
        val runsOn = text.isNotEmpty() && firstWord != null && firstWord.left - margin <= bodyHeight * IndentRatio &&
            lastWord?.let { continuesOnNextLine(it, firstWord.text, wholeWords) } == true
        endText()
        parts += TextPart.PageStart(pageIndex, insideWord = runsOn)
        for ((position, indexed) in lines.withIndex()) {
            val (index, line) = indexed
            val words = line.joinToString(" ") { it.text }
            if (position == 0 && line.size <= HeadingMaxWords && spaceBelow(index) >= HeadingSpace && words.isMostlyLetters()) {
                // the page's start, if nothing came after it yet, belongs at the top of the new chapter
                val pageStart = (parts.lastOrNull() as? TextPart.PageStart)?.takeIf { text.isEmpty() }
                if (pageStart != null) parts.removeAt(parts.lastIndex)
                endChapter(pageIndex)
                chapterTitle = words
                pageStart?.let { parts += it }
                continue
            }
            if (line.first().left - margin > bodyHeight * IndentRatio) endParagraph()
            if (text.isNotEmpty() && !joinNext) text.append(' ')
            text.append(words)
            joinNext = index in page.joinedLines
            lastWord = line.last().text
        }
    }
    endChapter(pages.keys.maxOrNull() ?: 0)
    return TextBook(chapters)
}

/**
 * Writes [book] as an EPUB 3 to [epub] and, beside it, [pageMap]: a JSON array giving for every printed page the
 * chapter its start is in, as the reader names it (`OEBPS/c003.xhtml`), so the reader can open the text at a page.
 */
fun writeTextEpub(book: TextBook, title: String, author: String?, language: String, epub: File, pageMap: File, pageCount: Int) {
    epub.parentFile?.mkdirs()
    val chapterFiles = book.chapters.indices.map { "c%03d.xhtml".format(it + 1) }
    val partial = File(epub.parentFile, "${epub.name}.part")
    ZipOutputStream(partial.outputStream().buffered()).use { zip ->
        // the mimetype entry comes first and uncompressed, as EPUB requires
        val mimetype = "application/epub+zip".toByteArray()
        zip.putNextEntry(ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = mimetype.size.toLong()
            crc = CRC32().apply { update(mimetype) }.value
        })
        zip.write(mimetype)
        zip.closeEntry()
        fun add(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
        add("META-INF/container.xml", ContainerXml)
        add("OEBPS/style.css", StyleCss)
        add("OEBPS/content.opf", contentOpf(title, author, language, chapterFiles))
        add("OEBPS/nav.xhtml", navXhtml(title, language, book.chapters.zip(chapterFiles)))
        book.chapters.forEachIndexed { index, chapter -> add("OEBPS/${chapterFiles[index]}", chapterXhtml(chapter, language)) }
    }
    if (!partial.renameTo(epub)) {
        epub.delete()
        if (!partial.renameTo(epub)) partial.delete()
    }
    val pageFiles = arrayOfNulls<String>(pageCount)
    book.chapters.forEachIndexed { index, chapter ->
        chapter.blocks.flatMap { it.parts }.filterIsInstance<TextPart.PageStart>()
            .forEach { if (it.page in pageFiles.indices && pageFiles[it.page] == null) pageFiles[it.page] = "OEBPS/${chapterFiles[index]}" }
    }
    // a page with no text of its own (a blank page) opens where the text before it ends
    var last = chapterFiles.firstOrNull()?.let { "OEBPS/$it" }
    for (index in pageFiles.indices) {
        if (pageFiles[index] == null) pageFiles[index] = last else last = pageFiles[index]
    }
    pageMap.writeText(JSONArray(pageFiles.toList()).toString())
}

private fun chapterXhtml(chapter: TextChapter, language: String): String = buildString {
    append("""<?xml version="1.0" encoding="utf-8"?>""").append('\n')
    append("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="$language" xml:lang="$language">""")
    append("<head><title>").append(escape(chapter.title ?: "")).append("""</title><link rel="stylesheet" href="style.css"/></head>""")
    append("""<body data-first-page="${chapter.firstPage}">""")
    chapter.title?.let { append("<h2>").append(escape(it)).append("</h2>") }
    for (block in chapter.blocks) {
        val hasText = block.parts.any { it is TextPart.Text }
        append(if (hasText) "<p>" else "<div>")
        block.parts.forEachIndexed { index, part ->
            when (part) {
                is TextPart.Text -> {
                    append(escape(part.text))
                    if ((block.parts.getOrNull(index + 1) as? TextPart.PageStart)?.insideWord != true) append(' ')
                }
                is TextPart.PageStart -> append("""<span id="page-${part.page}" class="vayana-page">⁠</span>""")
            }
        }
        append(if (hasText) "</p>" else "</div>")
    }
    append("</body></html>")
}

private fun navXhtml(title: String, language: String, chapters: List<Pair<TextChapter, String>>): String = buildString {
    append("""<?xml version="1.0" encoding="utf-8"?>""").append('\n')
    append("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="$language" xml:lang="$language">""")
    append("<head><title>").append(escape(title)).append("</title></head><body>")
    append("""<nav epub:type="toc" id="toc"><ol>""")
    for ((chapter, file) in chapters) {
        val label = chapter.title ?: title
        append("""<li><a href="$file">""").append(escape(label)).append("</a></li>")
    }
    append("</ol></nav></body></html>")
}

private fun contentOpf(title: String, author: String?, language: String, files: List<String>): String = buildString {
    append("""<?xml version="1.0" encoding="utf-8"?>""").append('\n')
    append("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">""")
    append("""<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""")
    append("""<dc:identifier id="id">vayana-recognized-text</dc:identifier>""")
    append("<dc:title>").append(escape(title)).append("</dc:title>")
    author?.takeIf { it.isNotBlank() }?.let { append("<dc:creator>").append(escape(it)).append("</dc:creator>") }
    append("<dc:language>").append(escape(language)).append("</dc:language>")
    append("""<meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>""")
    append("</metadata><manifest>")
    append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
    append("""<item id="css" href="style.css" media-type="text/css"/>""")
    files.forEachIndexed { index, file -> append("""<item id="c$index" href="$file" media-type="application/xhtml+xml"/>""") }
    append("</manifest><spine>")
    files.indices.forEach { append("""<itemref idref="c$it"/>""") }
    append("</spine></package>")
}

private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun List<RecognizedWord>.headerKey(): String? =
    joinToString("") { it.text }.filter { it.isLetter() }.takeIf { it.length >= 3 }

// A heading is words, not the noise recognition makes of a picture.
private fun String.isMostlyLetters(): Boolean {
    val marks = filterNot { it.isWhitespace() }
    val letters = marks.count { it.isLetter() || Character.getType(it) in LetterMarks }
    return marks.isNotEmpty() && letters >= marks.length * 0.8
}

private val LetterMarks = setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt())

private fun List<Float>.median(): Float? = if (isEmpty()) null else sorted()[size / 2]

private fun List<Float>.percentile(fraction: Float): Float? = if (isEmpty()) null else sorted()[((size - 1) * fraction).toInt()]

private const val RunningHeaderMinPages = 5
// A chapter number or title: a page's first line of a few words with several lines' space below it (6-7 in the book
// this was tuned on). The running header has a line or two of space below it (about 2.4).
private const val HeadingMaxWords = 4
private const val HeadingSpace = 4f
private val HeaderSpace = 1.8f..3.5f
// an indent of half the text's height or more starts a paragraph
private const val IndentRatio = 0.5f
private val PageNumber = Regex("[0-9൦-൯]{1,4}")

private const val ContainerXml = """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""

private const val StyleCss = """p { text-indent: 1.5em; margin: 0; text-align: start; }
h2 { text-align: center; margin: 2em 0 1em; }
.vayana-page { }"""
