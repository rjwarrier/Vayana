package com.vayana.feature.library

import com.vayana.core.database.model.Book
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class LibraryStatsBook(val id: Long, val createdAt: Long, val author: String?, val read: Boolean)

internal data class LibraryStatsSnapshot(
    val books: List<LibraryStatsBook>,
    val authorCount: Int,
    val readCount: Int,
) {
    val bookCount: Int get() = books.size
    val readFraction: Float get() = if (bookCount == 0) 0f else readCount.toFloat() / bookCount
    val readPercent: Int get() = (readFraction * 100).roundToInt()
}

internal fun libraryStatsSnapshot(books: List<Book>): LibraryStatsSnapshot {
    val records = ArrayList<LibraryStatsBook>(books.size)
    val authors = HashSet<String>()
    var readCount = 0
    for (book in books) {
        val read = book.isFinished()
        records += LibraryStatsBook(book.id, book.createdAt, book.author, read)
        book.author?.trim()?.takeIf(String::isNotEmpty)?.let { authors += it.lowercase(Locale.ROOT) }
        if (read) readCount++
    }
    records.sortWith(compareBy(LibraryStatsBook::createdAt, LibraryStatsBook::id))
    return LibraryStatsSnapshot(records, authors.size, readCount)
}

internal enum class LibraryShareFormat(val designWidth: Int, val designHeight: Int, val exportWidth: Int, val exportHeight: Int) {
    STORY(432, 768, 1080, 1920),
    SQUARE(480, 480, 1080, 1080),
}

internal enum class LibraryShareTheme { NIGHT, PAPER, MARIGOLD, MATERIAL }

internal enum class LibrarySpineStyle { COLORFUL, OUTLINE }

internal data class LibrarySpine(
    val shelf: Int,
    val x: Float,
    val width: Float,
    val height: Float,
    val read: Boolean,
    val colorIndex: Int,
)

internal data class LibrarySpines(val spines: List<LibrarySpine>, val representsEveryBook: Boolean)

/** Uses one spine per book when it fits; otherwise makes the UI reference's deterministic proportional sample. */
internal fun librarySpines(snapshot: LibraryStatsSnapshot, format: LibraryShareFormat, colorCount: Int): LibrarySpines {
    require(colorCount > 0)
    val shelfCount = if (format == LibraryShareFormat.STORY) 3 else 1
    val shelfWidth = if (format == LibraryShareFormat.STORY) 360f else 408f
    val minHeight = if (format == LibraryShareFormat.STORY) 54f else 60f
    val heightRange = if (format == LibraryShareFormat.STORY) 26f else 30f

    fun placeRealBooks(): List<LibrarySpine>? {
        val result = mutableListOf<LibrarySpine>()
        val occupiedShelves = minOf(shelfCount, snapshot.bookCount)
        if (occupiedShelves == 0) return result
        for (shelf in 0 until occupiedShelves) {
            val row = snapshot.books.subList(
                shelf * snapshot.bookCount / occupiedShelves,
                (shelf + 1) * snapshot.bookCount / occupiedShelves,
            )
            val baseWidths = row.map { 8f + Math.floorMod(it.id, 10L).toFloat() }
            val gap = 3f
            val available = shelfWidth - gap * (row.size - 1)
            val scale = (available / baseWidths.sum()).coerceAtMost(2.4f)
            if (scale < 0.8f) return null
            val usedWidth = baseWidths.sum() * scale + gap * (row.size - 1)
            var x = (shelfWidth - usedWidth) / 2f
            row.forEachIndexed { index, book ->
                val width = baseWidths[index] * scale
                val height = minHeight + Math.floorMod(book.id * 37L, heightRange.toLong()).toFloat()
                result += LibrarySpine(shelf, x, width, height, book.read,
                    Math.floorMod(book.id, colorCount.toLong()).toInt())
                x += width + gap
            }
        }
        return result
    }

    fun placeSample(): List<LibrarySpine> {
        val result = mutableListOf<LibrarySpine>()
        var seed = 7L + snapshot.bookCount * 31L + snapshot.readCount
        fun rnd(): Double {
            seed = (seed * 1103515245L + 12345L) % 2147483648L
            return seed / 2147483648.0
        }
        for (shelf in 0 until shelfCount) {
            var x = 0f
            while (true) {
                val width = 8f + floor(rnd() * 10).toFloat()
                if (x + width > shelfWidth) break
                val height = minHeight + floor(rnd() * heightRange).toFloat()
                val read = rnd() < snapshot.readFraction
                val colorIndex = floor(rnd() * colorCount).toInt().coerceIn(0, colorCount - 1)
                result += LibrarySpine(shelf, x, width, height, read, colorIndex)
                x += width + 3f
            }
        }
        return result
    }
    val real = placeRealBooks()
    return if (real != null) LibrarySpines(real, true) else LibrarySpines(placeSample(), false)
}
