package com.vayana.feature.library

import com.vayana.format.epub.GutenbergCleanup
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.*

class GutenbergCleanupTest {
    @Test fun removesMissingIllustrationCodesButKeepsImagesCaptionsAndStoryText() {
        val source = File.createTempFile("gutenberg", ".epub")
        val output = File.createTempFile("cleaned", ".epub")
        try {
            ZipOutputStream(source.outputStream()).use { zip ->
                val chapter = """<html xmlns="http://www.w3.org/1999/xhtml"><body>
                    <p>Before 0025m in ordinary text.</p>
                    <div class="fig" style="width:60%;"><span style="width:100%;" id="img_images_0025m.jpg">0025m</span></div>
                    <div class="fig"><span id="img_images_50027m.jpg">50025m</span></div>
                    <div class="fig"><span id="img_images_0023m.jpg">[Illustration: Edmond Dantès]</span></div>
                    <div class="fig"><img src="images/0025m.jpg" alt="0025m"/></div>
                    <div class="fig"><span id="img_images_0030m.jpg">0030m</span><p>A meaningful caption.</p></div>
                    <p><span id="img_images_0040m.jpg">0040m</span> outside a figure.</p>
                    <p>After.</p></body></html>"""
                mapOf("mimetype" to "application/epub+zip", "chapter.xhtml" to chapter).forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
            }
            assertEquals(3, GutenbergCleanup.clean(source, output))
            ZipFile(output).use { zip ->
                val chapter = zip.getInputStream(zip.getEntry("chapter.xhtml")).reader().readText()
                assertFalse(chapter.contains("img_images_0025m.jpg"))
                assertFalse(chapter.contains("50025m"))
                assertFalse(chapter.contains("0030m"))
                assertTrue(chapter.contains("Before 0025m in ordinary text."))
                assertTrue(chapter.contains("[Illustration: Edmond Dantès]"))
                assertTrue(chapter.contains("src=\"images/0025m.jpg\""))
                assertTrue(chapter.contains("A meaningful caption."))
                assertTrue(chapter.contains("0040m"))
                assertTrue(chapter.contains("After."))
            }
        } finally { source.delete(); output.delete() }
    }

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
