package com.vayana.feature.library

import com.vayana.format.epub.GutenbergCleanup
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.*

class GutenbergCleanupTest {
    @Test fun removesOnlyRecognizedBoilerplateAndPreservesArchiveResources() {
        val source = File.createTempFile("gutenberg", ".epub")
        val output = File.createTempFile("cleaned", ".epub")
        try {
            ZipOutputStream(source.outputStream()).use { zip ->
                mapOf("mimetype" to "application/epub+zip", "chapter.xhtml" to
                    """<html xmlns="http://www.w3.org/1999/xhtml"><body><section id="pg-header"><p>License</p></section><p id="story">A story about Project Gutenberg.</p><section id="pg-footer"><div>End license</div></section></body></html>""",
                    "image.svg" to "unchanged").forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
            }
            assertEquals(2, GutenbergCleanup.clean(source, output))
            ZipFile(source).use { zip ->
                assertTrue(zip.getInputStream(zip.getEntry("chapter.xhtml")).reader().readText().contains("End license"))
            }
            ZipFile(output).use { zip ->
                val chapter = zip.getInputStream(zip.getEntry("chapter.xhtml")).reader().readText()
                assertFalse(chapter.contains("License"))
                assertFalse(chapter.contains("End license"))
                assertTrue(chapter.contains("A story about Project Gutenberg."))
                assertEquals("unchanged", zip.getInputStream(zip.getEntry("image.svg")).reader().readText())
                assertEquals(ZipEntry.STORED, zip.getEntry("mimetype").method)
            }
            assertEquals(0, GutenbergCleanup.clean(output, File.createTempFile("again", ".epub").also { it.deleteOnExit() }))
        } finally { source.delete(); output.delete() }
    }

    @Test fun malformedChapterLeavesOriginalIntactAndDeletesPartialOutput() {
        val source = File.createTempFile("gutenberg", ".epub")
        val output = File.createTempFile("cleaned", ".epub")
        try {
            ZipOutputStream(source.outputStream()).use { zip ->
                mapOf("mimetype" to "application/epub+zip", "chapter.xhtml" to
                    "<html><section id=\"pg-header\">broken").forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
            }
            val original = source.readBytes()
            assertFails { GutenbergCleanup.clean(source, output) }
            assertContentEquals(original, source.readBytes())
            assertFalse(output.exists())
        } finally { source.delete(); output.delete() }
    }
}
