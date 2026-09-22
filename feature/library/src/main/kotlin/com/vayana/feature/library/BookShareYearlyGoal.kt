package com.vayana.feature.library

import com.vayana.core.database.model.Book
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class YearlyBookShareProgress(val readCount: Int, val target: Int)

/** Match Statistics' finish-date count, then include the book being shared exactly once. */
internal fun yearlyBookShareProgress(
    books: List<Book>,
    sharedBook: Book,
    target: Int,
    year: Int = LocalDate.now().year,
    zone: ZoneId = ZoneId.systemDefault(),
): YearlyBookShareProgress? {
    if (target <= 0) return null
    val finishedThisYear = books.filter { book ->
        book.finishedReadingAt?.let { Instant.ofEpochMilli(it).atZone(zone).year == year } == true
    }
    val sharedAlreadyCounted = finishedThisYear.any { it.id == sharedBook.id }
    return YearlyBookShareProgress(
        readCount = finishedThisYear.size + if (sharedAlreadyCounted) 0 else 1,
        target = target,
    )
}
