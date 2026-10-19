package com.vayana.dictionary.stardict

import com.vayana.dictionary.api.PartOfSpeech
import com.vayana.dictionary.api.foldAccents
import com.vayana.dictionary.api.languageDisplayName
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

class StarDictPackTest {
    @Test
    fun ifoIndexAndHtmlArticleBecomeAnEntry() {
        val info = StarDictPack.parseIfo("StarDict's dict ifo file\nversion=3.0.0\nbookname=English-italiano FreeDict\nwordcount=2\nsametypesequence=h\n")
        assertEquals("English-italiano FreeDict", info.bookName)
        assertEquals("h", info.sameTypeSequence)

        val index = ByteArrayOutputStream().apply {
            listOf(Triple("abate", 0L, 10), Triple("cat", 10L, 20)).forEach { (word, offset, size) ->
                write(word.toByteArray()); write(0)
                write(byteArrayOf(0, 0, 0, offset.toByte())); write(byteArrayOf(0, 0, 0, size.toByte()))
            }
        }.toByteArray()
        val seen = mutableListOf<Triple<String, Long, Int>>()
        StarDictPack.forEachIndexEntry(index, 32) { word, offset, size -> seen += Triple(word, offset, size) }
        assertEquals(listOf(Triple("abate", 0L, 10), Triple("cat", 10L, 20)), seen)

        val article = """<div><div><font class="grammar" color="green">noun</font></div><ol><li>A small domesticated feline.<div>gatto</div></li></ol></div><div>/<font color="gray">kæt</font>/</div>"""
        val entry = StarDictPack.articleEntry("Cat", article.toByteArray(), "h")!!
        assertEquals("cat", entry.key)
        assertEquals(PartOfSpeech.NOUN, entry.partOfSpeech)
        assertEquals(listOf("A small domesticated feline.", "gatto"), entry.definitions)
        assertEquals(listOf("Tom & Jerry", "x"), StarDictPack.markupLines("Tom &amp; Jerry<br>x").filter { it.isNotBlank() })
    }

    @Test
    fun unpacksDictionaryFilesFromATarXz() {
        val tar = ByteArrayOutputStream().apply {
            fun member(name: String, body: ByteArray) {
                val header = ByteArray(512)
                name.toByteArray().copyInto(header)
                String.format(Locale.ROOT, "%011o", body.size).toByteArray().copyInto(header, 124)
                header[156] = '0'.code.toByte()
                write(header); write(body); write(ByteArray((512 - body.size % 512) % 512))
            }
            member("eng-ita/eng-ita.ifo", "StarDict's dict ifo file\nbookname=x\n".toByteArray())
            member("eng-ita/README", "readme".toByteArray())
            member("eng-ita/eng-ita.dict", "hello".toByteArray())
            write(ByteArray(1024))
        }.toByteArray()
        val directory = createTempDirectory("stardict").toFile()
        try {
            val archive = File(directory, "d.tar.xz")
            XZOutputStream(archive.outputStream(), LZMA2Options()).use { it.write(tar) }
            val out = File(directory, "out").apply { mkdirs() }
            StarDictPack.unpack(archive, out)
            assertEquals(setOf("eng-ita.ifo", "eng-ita.dict"), out.list()!!.toSet())
            assertEquals("hello", File(out, "eng-ita.dict").readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun catalogKeepsStarDictReleasesFromFreeDictOnly() {
        val offers = FreeDictCatalog.parse("""
            [{"name":"eng-ita","headwords":"53258","status":"big enough to be useful","releases":[
               {"platform":"slob","URL":"https://download.freedict.org/x.slob","size":"1"},
               {"platform":"stardict","URL":"https://download.freedict.org/dictionaries/eng-ita/1/freedict-eng-ita-1.stardict.tar.xz","size":"3177492","checksum":"AB"}]},
             {"name":"bad-pair-name","releases":[]},
             {"name":"deu-eng","releases":[{"platform":"stardict","URL":"https://elsewhere.example/x.tar.xz","size":"1"}]},
             {"software":"x"}]
        """.trimIndent())
        assertEquals(1, offers.size)
        val offer = offers.single()
        assertEquals("eng" to "ita", offer.wordLanguage to offer.meaningLanguage)
        assertEquals(53258, offer.headwords)
        assertEquals("ab", offer.sha512)
    }

    @Test
    fun foldingLeavesIndicVowelSignsAndNamesLanguages() {
        assertEquals("cafe", foldAccents("café"))
        assertEquals("മലയാളം", foldAccents("മലയാളം"))
        assertEquals("பொருள்", foldAccents("பொருள்"))
        assertTrue(languageDisplayName("mal", Locale.ENGLISH).startsWith("Malayalam"))
        assertEquals("German", languageDisplayName("deu", Locale.ENGLISH))
    }
}
