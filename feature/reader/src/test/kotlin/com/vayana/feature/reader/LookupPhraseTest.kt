package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LookupPhraseTest {
    @Test
    fun keepsShortNamesAndIdiomsWithSingleSpaces() {
        assertEquals("Hagia Sophia", "  “Hagia\nSophia,”  ".toLookupPhrase())
        assertEquals("kick the bucket", "kick  the bucket.".toLookupPhrase())
        assertEquals("St. Petersburg", "St. Petersburg".toLookupPhrase())
        assertEquals("Achilles’ heel", "Achilles’ heel".toLookupPhrase())
    }

    @Test
    fun rejectsSingleWordsPassagesAndBrokenText() {
        assertNull("Byzantium".toLookupPhrase())
        assertNull("one two three four five six seven".toLookupPhrase())
        assertNull("He left! She stayed".toLookupPhrase())
        assertNull("a (bracketed) aside".toLookupPhrase())
        assertNull("he said “go” then".toLookupPhrase())
        assertNull("1914 1918".toLookupPhrase())
        assertNull("x".repeat(50).let { "$it $it" }.toLookupPhrase())
    }
}
