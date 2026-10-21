package com.vayana.format.ocr

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Recognised pages, one JSON file per page in a folder per book: `<root>/<book key>/<page index>.json`. The book key
 * is the SHA-256 of the book's file (`books.fileHash`), so replacing the file starts over and the same file keeps its
 * text however it is moved. A page that has a file is done; recognition skips it.
 */
class OcrStore(private val root: File) {

    fun save(book: String, pageIndex: Int, page: RecognizedPage, model: String) {
        val file = pageFile(book, pageIndex) ?: return
        file.parentFile?.mkdirs()
        val partial = File(file.parentFile, "${file.name}.part")
        partial.writeText(page.toJson(model).toString())
        if (!partial.renameTo(file)) partial.delete()
    }

    fun read(book: String, pageIndex: Int): RecognizedPage? =
        pageFile(book, pageIndex)?.takeIf { it.isFile }?.let { runCatching { recognizedPageFromJson(JSONObject(it.readText())) }.getOrNull() }

    /** The model a saved page was recognised with. */
    fun model(book: String, pageIndex: Int): String? =
        pageFile(book, pageIndex)?.takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText()).optString("model") }.getOrNull() }

    /** Works out which line ends fall inside a word across the whole book and saves it with each page that changed. */
    fun saveLineJoins(book: String) {
        val pages = savedPages(book).mapNotNull { index -> read(book, index)?.let { index to it } }.toMap()
        for ((index, joins) in lineJoins(pages)) {
            val page = pages.getValue(index)
            if (page.joinedLines != joins) save(book, index, page.copy(joinedLines = joins), model(book, index).orEmpty())
        }
        bookDir(book)?.let { File(it, LineJoinsMarker).writeText("") }
    }

    /** Whether [saveLineJoins] has run since the book was last fully recognised. */
    fun hasLineJoins(book: String): Boolean = bookDir(book)?.let { File(it, LineJoinsMarker).isFile } == true

    /** The book's recognised text as an EPUB (see [writeTextEpub]), or null if it hasn't been built. */
    fun textEpub(book: String): File? = bookDir(book)?.let { File(it, TextEpubName) }?.takeIf { it.isFile }

    /** For every printed page, the chapter file of [textEpub] its start is in. */
    fun textPageMap(book: String): List<String?> = runCatching {
        val json = JSONArray(File(bookDir(book), TextPageMapName).readText())
        List(json.length()) { json.optString(it).takeIf { file -> file.isNotEmpty() } }
    }.getOrDefault(emptyList())

    /** Builds the book's text EPUB from every page saved, replacing an older one. */
    fun buildTextEpub(book: String, title: String, author: String?, language: String) {
        val dir = bookDir(book) ?: return
        val pages = savedPages(book).mapNotNull { index -> read(book, index)?.let { index to it } }.toMap()
        if (pages.isEmpty()) return
        writeTextEpub(reflowRecognizedPages(pages), title, author, language, File(dir, TextEpubName), File(dir, TextPageMapName), (pages.keys.max()) + 1)
        // text built by an earlier version of the layout
        dir.listFiles().orEmpty()
            .filter { it.name.startsWith(TextFilePrefix) && it.name != TextEpubName && it.name != TextPageMapName }
            .forEach { it.delete() }
    }

    /** The page indexes saved so far. */
    fun savedPages(book: String): Set<Int> = bookDir(book)?.list().orEmpty()
        .mapNotNull { it.removeSuffix(".json").takeIf { name -> name != it }?.toIntOrNull() }
        .toSet()

    fun delete(book: String) {
        bookDir(book)?.deleteRecursively()
    }

    /** The file a page is saved in, or null for a key or index that could leave the store. */
    fun pageFile(book: String, pageIndex: Int): File? =
        if (pageIndex >= 0) bookDir(book)?.let { File(it, "$pageIndex.json") } else null

    private fun bookDir(book: String): File? = if (BookKeyPattern.matches(book)) File(root, book) else null
}

/**
 * `{ "model": "mal-fast", "lines": [ [ ["word", left, top, right, bottom, confidence], ... ], ... ], "joins": [2, 5] }`,
 * boxes in PDF points from the page's top-left corner, words already normalised (see [normalizeRecognizedText]),
 * `joins` the lines whose last word continues on the next line (see [lineJoins]).
 */
fun RecognizedPage.toJson(model: String): JSONObject = JSONObject()
    .put("model", model)
    .put("joins", JSONArray(joinedLines.sorted()))
    .put("lines", JSONArray(lines.map { line ->
        JSONArray(line.map { word ->
            JSONArray()
                .put(word.text)
                .put(word.left.rounded())
                .put(word.top.rounded())
                .put(word.right.rounded())
                .put(word.bottom.rounded())
                .put(word.confidence.toDouble().let { Math.round(it).toInt() })
        })
    }))

fun recognizedPageFromJson(json: JSONObject): RecognizedPage {
    val lines = json.getJSONArray("lines")
    val joins = json.optJSONArray("joins")
    return RecognizedPage(joinedLines = List(joins?.length() ?: 0) { joins!!.getInt(it) }.toSet(), lines = List(lines.length()) { lineIndex ->
        val words = lines.getJSONArray(lineIndex)
        List(words.length()) { wordIndex ->
            val word = words.getJSONArray(wordIndex)
            RecognizedWord(
                // pages saved before a normalisation rule was added get it here
                text = normalizeRecognizedText(word.getString(0)),
                left = word.getDouble(1).toFloat(),
                top = word.getDouble(2).toFloat(),
                right = word.getDouble(3).toFloat(),
                bottom = word.getDouble(4).toFloat(),
                confidence = word.getDouble(5).toFloat(),
            )
        }
    })
}

/**
 * The pages still to recognise, starting with the one being read and working outwards (after it first, then before
 * it), so the pages in front of the reader are ready first.
 */
fun pagesToRecognize(pages: List<Int>, saved: Set<Int>, startPage: Int): List<Int> =
    pages.filter { it !in saved }.distinct()
        .sortedWith(compareBy({ if (it >= startPage) it - startPage else startPage - it }, { if (it >= startPage) 0 else 1 }))

// Two decimals of a point is far finer than a printed letter.
private fun Float.rounded(): Double = Math.round(this * 100) / 100.0

private val BookKeyPattern = Regex("[0-9a-f]{64}")
private const val LineJoinsMarker = "lines-joined"
// Versioned, so a change in how the text is laid out builds it again.
private const val TextFilePrefix = "text-v"
private const val TextEpubName = "text-v4.epub"
private const val TextPageMapName = "text-v4-pages.json"
