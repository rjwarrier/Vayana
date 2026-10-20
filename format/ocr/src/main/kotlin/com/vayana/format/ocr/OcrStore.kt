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
 * `{ "model": "mal-fast", "lines": [ [ ["word", left, top, right, bottom, confidence], ... ], ... ] }`, boxes in PDF
 * points from the page's top-left corner, words already normalised (see [normalizeRecognizedText]).
 */
fun RecognizedPage.toJson(model: String): JSONObject = JSONObject()
    .put("model", model)
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
    return RecognizedPage(List(lines.length()) { lineIndex ->
        val words = lines.getJSONArray(lineIndex)
        List(words.length()) { wordIndex ->
            val word = words.getJSONArray(wordIndex)
            RecognizedWord(
                text = word.getString(0),
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
