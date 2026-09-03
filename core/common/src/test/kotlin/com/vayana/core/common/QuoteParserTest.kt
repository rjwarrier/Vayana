package com.vayana.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuoteParserTest {
    @Test
    fun parsesGoodreadsSampleQuotes() {
        val sample = """
“Because as any writer will tell you, an IDEA for a book is like falling in love, it’s all wild emotion and headlong rush, but the ACTUAL ACT of writing a book is like building a relationship: it is joyous, slow, fragile, frustrating, exhilarating, painstaking, exhausting, worth it.”
― Ben H. Winters, The Last Policeman
tags: aspiring-authors, author-advice, on-writing, writing-advice20 likes
Like
“There is little novelty in the detective who cannot solve himself.”
― Ben H. Winters, The Last Policeman
13 likes
Like
“He books it into that little playground there. I mean the guy is zooming like the Road Runner, skidding through the gravel and the slush and everything. I’m yelling, “Police, police! Stop, motherfucker!”

‘You do not yell, “Stop, motherfucker.”’

‘I do. Because you know, Palace, this is it. This is the last chance I get to run after a perp yelling, “Stop, motherfucker.”
― Ben H. Winters, The Last Policeman
tags: end-of-the-world, policemen13 likes
Like
“The end of the world changes everything, from a law-enforcement perspective.”
― Ben H. Winters, The Last Policeman
8 likes
Like
“Nothing arguing against the case for suicide”
― Ben H. Winters, The Last Policeman
0 likes
Like
        """.trimIndent()

        val parsed = QuoteParser.parse(sample)
        assertEquals(5, parsed.size)

        // First quote
        assertTrue(parsed[0].quoteText.startsWith("Because as any writer will tell you"))
        assertEquals("Ben H. Winters", parsed[0].author)
        assertEquals("The Last Policeman", parsed[0].sourceTitle)
        assertEquals(20, parsed[0].likesCount)
        assertEquals(21, parsed[0].highlightsCount)
        assertEquals(listOf("aspiring-authors", "author-advice", "on-writing", "writing-advice"), parsed[0].tags)

        // Second quote
        assertEquals("There is little novelty in the detective who cannot solve himself.", parsed[1].quoteText)
        assertEquals(13, parsed[1].likesCount)
        assertEquals(14, parsed[1].highlightsCount)

        // Fifth quote with 0 likes
        assertEquals("Nothing arguing against the case for suicide", parsed[4].quoteText)
        assertEquals(0, parsed[4].likesCount)
        assertEquals(1, parsed[4].highlightsCount)
    }
}
