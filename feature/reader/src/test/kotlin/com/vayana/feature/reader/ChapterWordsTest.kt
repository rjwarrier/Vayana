package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterWordsTest {
    @Test
    fun keepsLongRareWordsAndDropsNamesKnownShortAndCommonOnes() {
        val counts = mapOf(
            "perspicacious" to 1,
            "Elizabeth" to 3,
            "wonderful" to 5,
            "cat" to 1,
            "Serendipity" to 1,
            "serendipity" to 1,
            "melancholy" to 2,
            "obfuscate" to 1,
            "twenty-one" to 1,
        )

        val result = unusualWordCandidates(counts, knownWords = setOf("obfuscate"))

        assertEquals(listOf("perspicacious", "serendipity", "melancholy"), result)
    }

    @Test
    fun respectsTheLimit() {
        val counts = (1..10).associate { "abcdefgh${'a' + it}" to 1 }

        assertEquals(3, unusualWordCandidates(counts, knownWords = emptySet(), limit = 3).size)
    }
}
