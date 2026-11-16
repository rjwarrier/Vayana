package com.vayana.feature.reader

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The reader's layout estimates live in memory and only apply to the position that produced them. */
@Singleton
class ChapterTimeEstimates @Inject constructor() {
    data class Estimate(val locator: String, val progression: Float, val minutes: Int)

    private val latest = MutableStateFlow<Map<Long, Estimate>>(emptyMap())
    val byBook = latest.asStateFlow()

    internal fun record(bookId: Long, locator: String, progression: Float, minutes: Int?) {
        latest.update { previous ->
            val recent = previous - bookId
            if (minutes == null || minutes < 0) recent
            else {
                val retained = if (recent.size >= 32) recent - recent.keys.first() else recent
                retained + (bookId to Estimate(locator, progression, minutes))
            }
        }
    }
}
