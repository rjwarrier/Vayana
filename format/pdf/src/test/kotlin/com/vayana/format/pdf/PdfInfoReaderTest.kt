package com.vayana.format.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PdfInfoReaderTest {
    private fun pdf(infoBody: String, trailerExtra: String = "") = """
        %PDF-1.7
        1 0 obj << /Type /Catalog /Pages 2 0 R /Outlines 5 0 R >> endobj
        6 0 obj << /Title (Chapter One) /Parent 5 0 R /Dest [3 0 R /Fit] >> endobj
        9 0 obj $infoBody endobj
        xref
        0 10
        trailer
        << /Size 10 /Root 1 0 R /Info 9 0 R $trailerExtra >>
        startxref
        1234
        %%EOF
    """.trimIndent()

    @Test
    fun `reads title and author from the trailer's info dictionary, not an outline entry`() {
        val info = PdfInfoReader.parse(pdf("<< /Title (The Pragmatic Reader) /Author (Ada Lovelace) /Producer (x) >>"))
        assertEquals("The Pragmatic Reader", info.title)
        assertEquals("Ada Lovelace", info.author)
    }

    @Test
    fun `decodes escapes, balanced parentheses and octal codes in literal strings`() {
        val info = PdfInfoReader.parse(pdf("""<< /Title (Notes \(draft\) on (nested) caf\351) >>"""))
        assertEquals("Notes (draft) on (nested) café", info.title)
    }

    @Test
    fun `decodes UTF-16 hex strings`() {
        // FEFF byte-order mark, then "Ω Book" in UTF-16BE.
        val info = PdfInfoReader.parse(pdf("<< /Title <FEFF03A900200042006F006F006B> >>"))
        assertEquals("Ω Book", info.title)
    }

    @Test
    fun `decodes UTF-16 literal strings`() {
        val title = "þÿ\u0000H\u0000i"
        val info = PdfInfoReader.parse(pdf("<< /Title ($title) >>"))
        assertEquals("Hi", info.title)
    }

    @Test
    fun `later incremental updates win`() {
        val text = pdf("<< /Title (Old) >>") + "\n" + """
            10 0 obj << /Title (New) >> endobj
            trailer
            << /Size 11 /Root 1 0 R /Info 10 0 R /Prev 1234 >>
            %%EOF
        """.trimIndent()
        assertEquals("New", PdfInfoReader.parse(text).title)
    }

    @Test
    fun `blank, missing and encrypted fields read as absent`() {
        assertNull(PdfInfoReader.parse(pdf("<< /Title () /Author <> >>")).title)
        assertNull(PdfInfoReader.parse(pdf("<< /Producer (x) >>")).title)
        assertNull(PdfInfoReader.parse("%PDF-1.4 no trailer here").title)
        assertNull(PdfInfoReader.parse(pdf("<< /Title (x\u0001y) >>", trailerExtra = "/Encrypt 12 0 R")).title)
    }

    @Test
    fun `word processor names and placeholders are not titles`() {
        assertEquals("Chapter 1", PdfInfoReader.cleanTitle("Microsoft Word - Chapter 1.docx"))
        assertEquals("Deck", PdfInfoReader.cleanTitle("Microsoft PowerPoint - Deck.pptx"))
        assertEquals("Report", PdfInfoReader.cleanTitle("Report.pdf"))
        assertNull(PdfInfoReader.cleanTitle("Untitled"))
        assertNull(PdfInfoReader.cleanTitle("Microsoft Word - Document.docx"))
        assertEquals("The Andromeda Strain", PdfInfoReader.cleanTitle("The Andromeda Strain"))
        assertNull(PdfInfoReader.cleanAuthor("Administrator"))
        assertEquals("Michael Crichton", PdfInfoReader.cleanAuthor("Michael Crichton"))
        val info = PdfInfoReader.parse(pdf("<< /Title (Microsoft Word - Notes.doc) /Author (Owner) >>"))
        assertEquals("Notes", info.title)
        assertNull(info.author)
    }

    @Test
    fun `a TitleCase key is not mistaken for Title`() {
        val info = PdfInfoReader.parse(pdf("<< /TitleSort (Sorted) /Title (Real) >>"))
        assertEquals("Real", info.title)
    }
}
