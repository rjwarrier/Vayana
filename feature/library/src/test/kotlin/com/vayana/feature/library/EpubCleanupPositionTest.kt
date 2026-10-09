package com.vayana.feature.library

import com.vayana.format.epub.EpubCleanupPosition
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class EpubCleanupPositionTest {
    private fun book(duplicatePassage: Boolean = false, action: (File) -> Unit) {
        val file = File.createTempFile("position", ".epub")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                val entries = mapOf(
                    "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"OPS/book.opf\"/></rootfiles></container>",
                    "OPS/book.opf" to "<package><manifest><item id=\"chapter\" href=\"chapter.xhtml\"/></manifest><spine><itemref idref=\"chapter\"/></spine></package>",
                    "OPS/chapter.xhtml" to "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Book</title></head><body><section id=\"pg-header\">License text to remove.</section><p>Along the inter\u00adnational river, a traveller found <em>a quiet place</em> to rest.</p></body></html>")
                entries.forEach { (path, content) ->
                    val chapter = if (duplicatePassage && path.endsWith("xhtml")) {
                        content.replace("</body>", "<p>Along the inter\u00adnational RIVER, a traveller found <em>a quiet place</em> to rest.</p></body>")
                    } else content
                    zip.putNextEntry(ZipEntry(path)); zip.write(chapter.toByteArray()); zip.closeEntry()
                }
            }
            action(file)
        } finally { file.delete() }
    }

    @Test fun capturesPassageAcrossInlineMarkupAndRepairsItsArtifacts() = book { source ->
        val locator = assertNotNull(EpubCleanupPosition.capture(source, "epubcfi(/6/2[chapter]!/4/4/1:10)"))
        assertTrue(locator.startsWith("text:search:OPS%2Fchapter.xhtml:"))
        val text = URLDecoder.decode(locator.substringAfterLast(':'), "UTF-8")
        assertEquals("international river, a traveller found a quiet place to rest.", text)
    }

    @Test fun supportsRangeStartAndRejectsRemovedOrInvalidPositions() = book { source ->
        assertNotNull(EpubCleanupPosition.capture(source, "epubcfi(/6/2!/4/4,/1:10,/1:20)"))
        for (position in listOf("epubcfi(/6/2!/4/2/1:0)", "epubcfi(/6/99!/4/4/1:0)", "broken")) {
            assertNull(EpubCleanupPosition.capture(source, position))
        }
        assertNull(EpubCleanupPosition.capture(source, null))
    }

    @Test fun ambiguousPassagesAreNotRestoredToTheWrongOccurrence() = book(duplicatePassage = true) { source ->
        assertNull(EpubCleanupPosition.capture(source, "epubcfi(/6/2!/4/4/1:10)"))
    }

    @Test fun handlesEscapedAssertionsAndKeepsCancellationVisible() = book { source ->
        assertNotNull(EpubCleanupPosition.capture(source, "epubcfi(/6/2[chapter^],part]!/4/4/1:10)"))
        assertFailsWith<java.util.concurrent.CancellationException> {
            EpubCleanupPosition.capture(source, "epubcfi(/6/2!/4/4/1:10)") { throw java.util.concurrent.CancellationException() }
        }
    }
}
