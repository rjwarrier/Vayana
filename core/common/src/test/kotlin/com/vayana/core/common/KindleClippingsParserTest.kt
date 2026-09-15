package com.vayana.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KindleClippingsParserTest {
    private val sample = "﻿The Pragmatic Programmer (Hunt, Andrew)\r\n" +
        "- Your Highlight on page 12 | Location 180-182 | Added on Monday, 1 January 2024 10:00:00\r\n" +
        "\r\n" +
        "Care about your craft.\r\n" +
        "==========\r\n" +
        "The Pragmatic Programmer (Hunt, Andrew)\r\n" +
        "- Your Note on page 12 | Location 182 | Added on Monday, 1 January 2024 10:01:00\r\n" +
        "\r\n" +
        "#craft remember this\r\n" +
        "==========\r\n" +
        "The Pragmatic Programmer (Hunt, Andrew)\r\n" +
        "- Your Bookmark on page 20 | Location 300 | Added on Monday, 1 January 2024 10:02:00\r\n" +
        "\r\n" +
        "\r\n" +
        "==========\r\n" +
        "Dune\r\n" +
        "- Your Highlight at location 1234-40 | Added on Tuesday, 2 January 2024 09:00:00\r\n" +
        "\r\n" +
        "Fear is the mind-killer.\r\n" +
        "==========\r\n" +
        "Dune\r\n" +
        "- Your Highlight at location 1234-50 | Added on Tuesday, 2 January 2024 09:01:00\r\n" +
        "\r\n" +
        "I must not fear. Fear is the mind-killer.\r\n" +
        "==========\r\n" +
        "Dune\r\n" +
        "- Your Note at location 2000 | Added on Tuesday, 2 January 2024 09:05:00\r\n" +
        "\r\n" +
        "A loose thought\r\n" +
        "==========\r\n"

    @Test
    fun groupsHighlightsByBookAndAttachesNotes() {
        val books = KindleClippingsParser.parse(sample)

        assertEquals(listOf("The Pragmatic Programmer", "Dune"), books.map { it.title })
        val pragmatic = books[0]
        assertEquals("Hunt, Andrew", pragmatic.author)
        assertEquals(listOf(KindleHighlight("Care about your craft.", "#craft remember this", 180)), pragmatic.highlights)
        assertEquals(emptyList(), pragmatic.looseNotes)
    }

    @Test
    fun dropsHighlightsExtendedLaterAndKeepsLooseNotes() {
        val dune = KindleClippingsParser.parse(sample)[1]

        assertNull(dune.author)
        assertEquals(listOf("I must not fear. Fear is the mind-killer."), dune.highlights.map { it.text })
        assertEquals(listOf("A loose thought"), dune.looseNotes)
    }

    @Test
    fun emptyOrForeignTextYieldsNothing() {
        assertEquals(emptyList(), KindleClippingsParser.parse(""))
        assertEquals(emptyList(), KindleClippingsParser.parse("just some text\nwithout clippings"))
    }
}
