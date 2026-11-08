package com.vayana.feature.library

import com.vayana.format.epub.EpubCleanup
import com.vayana.format.epub.EpubCleanupOptions
import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.*

class EpubCleanupTest {
    private fun fixture(action: (File, File) -> Unit) {
        val source = File.createTempFile("cleanup-source", ".epub")
        val output = File.createTempFile("cleanup-result", ".epub")
        try {
            ZipOutputStream(source.outputStream()).use { zip ->
                val entries = linkedMapOf(
                    "mimetype" to "application/epub+zip",
                    "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                    "OPS/book.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata>
                        <meta name="cover" content="cover"/><meta property="media:duration" refines="#overlay">00:10</meta>
                        </metadata><manifest>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                        <item id="cover" href="Images/cover%20art.bin" media-type="image/jpeg"/>
                        <item id="font" href="Fonts/book.otf" media-type="font/otf"/>
                        <item id="style" href="style.css" media-type="text/css"/>
                        <item id="audio" href="voice.mp3" media-type="audio/mpeg"/>
                        <item id="overlay" href="read.smil" media-type="application/smil+xml"/>
                        </manifest><spine><itemref idref="chapter"/></spine><guide><reference type="cover" href="Images/cover%20art.bin"/></guide></package>""",
                    "OPS/chapter.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Story</title><link rel="stylesheet" href="style.css"/></head><body>
                        <section id="pg-header">Gutenberg header</section>
                        <p>A quiet inter­national story stays here.</p>
                        <p id="entities">A&nbsp;space, caf&eacute;, an &mdash; and &amp;.</p>
                        <script><![CDATA[var literal = "&nbsp;";]]></script>
                        <figure><img src="Images/cover%20art.bin" alt="Cover"/><figcaption>A caption stays.</figcaption></figure>
                        <audio src="voice.mp3"/><svg xmlns="http://www.w3.org/2000/svg"><rect width="10" height="10"/></svg>
                        <section id="pg-footer">Gutenberg footer</section></body></html>""",
                    "OPS/style.css" to """@font-face { font-family: Book; src: url('Fonts/book.otf'); } p { font-family: Book, serif; color: black; } figure { background-image: url('Images/cover%20art.bin'); }""",
                    "OPS/Images/cover art.bin" to "image bytes",
                    "OPS/Fonts/book.otf" to "font bytes",
                    "OPS/voice.mp3" to "audio bytes",
                    "OPS/read.smil" to """<smil xmlns="http://www.w3.org/ns/SMIL"><body><par><text src="chapter.xhtml"/><audio src="voice.mp3"/></par></body></smil>""",
                    "META-INF/encryption.xml" to """<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><EncryptedData xmlns="http://www.w3.org/2001/04/xmlenc#"><CipherData><CipherReference URI="OPS/Fonts/book.otf"/></CipherData></EncryptedData></encryption>""",
                )
                for ((name, text) in entries) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
            }
            action(source, output)
        } finally { source.delete(); output.delete() }
    }

    private fun ZipFile.text(name: String) = getInputStream(getEntry(name)).reader().readText()

    @Test fun inspectionUsesManifestTypesAndFindsIndependentChoices() = fixture { source, _ ->
        val inventory = EpubCleanup.inspect(source)
        assertEquals(1, inventory.images)
        assertEquals(1, inventory.fonts)
        assertEquals(2, inventory.media)
        assertTrue(inventory.imageBytes > 0)
        assertTrue(inventory.gutenberg)
        assertTrue(inventory.textArtifacts)
    }

    @Test fun removingImagesUpdatesReferencesAndKeepsTextFontsAndMedia() = fixture { source, output ->
        val original = source.readBytes()
        assertTrue(EpubCleanup.clean(source, output, EpubCleanupOptions(images = true)) > 0)
        assertContentEquals(original, source.readBytes())
        ZipFile(output).use { zip ->
            assertEquals("mimetype", zip.entries().nextElement().name)
            assertEquals(ZipEntry.STORED, zip.getEntry("mimetype").method)
            assertNull(zip.getEntry("OPS/Images/cover art.bin"))
            assertNotNull(zip.getEntry("OPS/Fonts/book.otf"))
            assertNotNull(zip.getEntry("OPS/voice.mp3"))
            assertFalse(zip.text("OPS/book.opf").contains("cover%20art"))
            assertFalse(zip.text("OPS/book.opf").contains("content=\"cover\""))
            val chapter = zip.text("OPS/chapter.xhtml")
            assertFalse(chapter.contains("<img"))
            assertFalse(chapter.contains("<svg"))
            assertTrue(chapter.contains("A caption stays."))
            assertTrue(chapter.contains("inter­national"))
            assertTrue(chapter.contains("pg-header"))
            assertTrue(chapter.contains("<audio"))
            assertFalse(zip.text("OPS/style.css").contains("cover%20art"))
            assertTrue(zip.text("OPS/style.css").contains("@font-face"))
        }
    }

    @Test fun removingFontsRepairsObfuscationMetadataAndPreservesOtherStyles() = fixture { source, output ->
        EpubCleanup.clean(source, output, EpubCleanupOptions(fonts = true))
        ZipFile(output).use { zip ->
            assertNull(zip.getEntry("OPS/Fonts/book.otf"))
            assertFalse(zip.text("OPS/book.opf").contains("Fonts/book.otf"))
            assertFalse(zip.text("META-INF/encryption.xml").contains("EncryptedData"))
            val css = zip.text("OPS/style.css")
            assertFalse(css.contains("@font-face"))
            assertTrue(css.contains("color: black"))
            assertTrue(css.contains("cover%20art"))
            assertNotNull(zip.getEntry("OPS/Images/cover art.bin"))
            assertTrue(zip.text("OPS/chapter.xhtml").contains("inter­national"))
        }
    }

    @Test fun gutenbergAndTextCleanupCanBeChosenSeparately() = fixture { source, output ->
        EpubCleanup.clean(source, output, EpubCleanupOptions(gutenberg = true))
        ZipFile(output).use { zip ->
            val chapter = zip.text("OPS/chapter.xhtml")
            assertFalse(chapter.contains("pg-header"))
            assertFalse(chapter.contains("pg-footer"))
            assertTrue(chapter.contains("inter­national"))
            assertTrue(chapter.contains("<img"))
        }
        EpubCleanup.clean(source, output, EpubCleanupOptions(textArtifacts = true))
        ZipFile(output).use { zip ->
            val chapter = zip.text("OPS/chapter.xhtml")
            assertTrue(chapter.contains("international"))
            assertTrue(chapter.contains("pg-header"))
            assertTrue(chapter.contains("pg-footer"))
            assertTrue(chapter.contains("<img"))
        }
    }

    @Test fun removingMediaRemovesOverlayReferencesAndLeavesReadableSpine() = fixture { source, output ->
        EpubCleanup.clean(source, output, EpubCleanupOptions(media = true))
        ZipFile(output).use { zip ->
            assertNull(zip.getEntry("OPS/voice.mp3"))
            assertNull(zip.getEntry("OPS/read.smil"))
            val opf = zip.text("OPS/book.opf")
            assertFalse(opf.contains("media-overlay"))
            assertFalse(opf.contains("media:duration"))
            assertFalse(opf.contains("read.smil"))
            assertFalse(zip.text("OPS/chapter.xhtml").contains("<audio"))
            assertTrue(opf.contains("idref=\"chapter\""))
            // Every XML document in the cleaned copy remains parseable.
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            for (entry in zip.entries().asSequence().filter { it.name.endsWith(".xml") || it.name.endsWith(".opf") || it.name.endsWith(".xhtml") }) {
                zip.getInputStream(entry).use { factory.newDocumentBuilder().parse(it) }
            }
        }
    }

    @Test fun allChoicesAreIdempotentAndReportProgress() = fixture { source, output ->
        val options = EpubCleanupOptions(true, true, true, true, true)
        val progress = mutableListOf<Pair<Int, Int>>()
        assertTrue(EpubCleanup.clean(source, output, options, { done, total -> progress += done to total }) > 0)
        assertEquals(0, progress.first().first)
        assertEquals(progress.last().second, progress.last().first)
        val again = File.createTempFile("cleanup-again", ".epub")
        try { assertEquals(0, EpubCleanup.clean(output, again, options)) } finally { again.delete() }
    }

    @Test fun namedXhtmlEntitiesSurviveRewritingAndCdataStaysLiteral() = fixture { source, output ->
        EpubCleanup.clean(source, output, EpubCleanupOptions(images = true))
        ZipFile(output).use { zip ->
            val chapter = zip.text("OPS/chapter.xhtml")
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val document = factory.newDocumentBuilder().parse(chapter.byteInputStream())
            val paragraphs = document.getElementsByTagNameNS("*", "p")
            val text = (0 until paragraphs.length).map { paragraphs.item(it).textContent }
            assertTrue("A space, café, an — and &." in text)
            assertEquals("var literal = \"&nbsp;\";", document.getElementsByTagNameNS("*", "script").item(0).textContent)
        }
    }

    @Test fun cancellationDeletesPartialDestinationAndKeepsOriginal() = fixture { source, output ->
        val original = source.readBytes()
        assertFailsWith<CancellationException> {
            EpubCleanup.clean(source, output, EpubCleanupOptions(images = true), checkCancelled = { throw CancellationException() })
        }
        assertFalse(output.exists())
        assertContentEquals(original, source.readBytes())
    }
}
