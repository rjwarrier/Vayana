package com.vayana.format.epub

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Must run on Android: ICU rejects some patterns that the desktop JVM accepts. */
@RunWith(AndroidJUnit4::class)
class EpubCleanupAndroidTest {
    @Test fun inspectionAndFontCleanupWorkWithAndroidRegexAndXml() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val source = File.createTempFile("cleanup-source", ".epub", cache)
        val output = File.createTempFile("cleanup-output", ".epub", cache)
        try {
            val entries = mapOf(
                "mimetype" to "application/epub+zip",
                "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf"><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/><item id="font" href="book.otf" media-type="font/otf"/></manifest><spine><itemref idref="chapter"/></spine></package>""",
                "chapter.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Test</title></head><body><p>A&nbsp;quiet caf&eacute;.</p><script><![CDATA[var literal = "&nbsp;";]]></script></body></html>""",
                "style.css" to """@font-face { font-family: Book; src: url('book.otf'); } p { color: black; }""",
                "book.otf" to "font bytes",
            )
            ZipOutputStream(source.outputStream()).use { zip ->
                for ((path, text) in entries) {
                    zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
                }
            }
            val original = source.readBytes()
            val inventory = EpubCleanup.inspect(source)
            assertEquals(1, inventory.fonts)
            assertTrue(inventory.inlineFonts)
            assertTrue(EpubCleanup.clean(source, output, EpubCleanupOptions(fonts = true)) > 0)
            assertArrayEquals(original, source.readBytes())
            ZipFile(output).use { zip ->
                assertNull(zip.getEntry("book.otf"))
                val css = zip.getInputStream(zip.getEntry("style.css")).reader().readText()
                assertFalse(css.contains("@font-face"))
                assertTrue(css.contains("color: black"))
                val chapter = zip.getInputStream(zip.getEntry("chapter.xhtml")).readBytes()
                assertTrue(EpubXml.parse(chapter).documentElement.textContent.contains("A quiet café."))
                assertTrue(chapter.toString(Charsets.UTF_8).contains("&nbsp;"))
            }
        } finally {
            source.delete()
            output.delete()
        }
    }
}
