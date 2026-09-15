package com.vayana.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class HighlightTagsTest {
    @Test
    fun parsesDistinctLowerCaseTags() {
        assertEquals(listOf("idea", "to-do", "quote"), HighlightTags.parse("#Idea for later #to-do, #quote #idea"))
    }

    @Test
    fun ignoresHashesInsideWordsAndNumbersOnlyStarts() {
        assertEquals(emptyList(), HighlightTags.parse("C# and issue#12 and ## and # alone"))
        assertEquals(emptyList(), HighlightTags.parse(null))
    }

    @Test
    fun addAppendsOnlyMissingTags() {
        assertEquals("#idea", HighlightTags.add("", "idea"))
        assertEquals("nice #idea", HighlightTags.add("nice", "idea"))
        assertEquals("nice #Idea", HighlightTags.add("nice #Idea", "idea"))
    }
}
