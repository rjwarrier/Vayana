package com.vayana.dictionary.stardict

import com.vayana.dictionary.api.PartOfSpeech
import com.vayana.dictionary.api.lookupForms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GcideParserTest {
    private val sample = """
        <p><ent>Abate</ent><br/
        <hw>A*bate"</hw> <pr>(<adot/*b<amac/t")</pr>, <pos>v. t.</pos>
        <ety>[OF. <ets>abatre</ets> to beat down.]</ety> <sn>1.</sn> <def>To beat down; to
        overthrow.</def> <mark>[Obs.]</mark><br/
        [<source>1913 Webster</source>]</p>

        <p><q>The King of Scots . . . sore <qex>abated</qex> the walls.</q><br/
        [<source>1913 Webster</source>]</p>

        <p><sn>2.</sn> <def>To bring down; to lessen; <as>as, to <ex>abate</ex> a demand.</as></def><br/
        [<source>1913 Webster</source>]</p>

        <p><ent>Abb<eacute/</ent><br/
        <hw>Ab`b<eacute/"</hw>, <pos>n.</pos> <def>The title of a French <ldquo/cleric<rdquo/.</def></p>
    """.trimIndent()

    @Test
    fun entriesCollectTheirSensesAcrossParagraphsAndDropCitations() {
        val entries = GcideParser.parse(sample)
        assertEquals(listOf("abate", "abbe"), entries.map { it.key })
        val abate = entries.first()
        assertEquals(PartOfSpeech.VERB, abate.partOfSpeech)
        assertEquals(listOf("To beat down; to overthrow.", "To bring down; to lessen; as, to abate a demand."), abate.definitions)
        val abbe = entries.last()
        assertEquals("Abbé", abbe.headword)
        assertEquals(PartOfSpeech.NOUN, abbe.partOfSpeech)
        assertEquals("The title of a French “cleric”.", abbe.definitions.single())
    }

    @Test
    fun lookupFormsCoverPossessivesContractionsArchaicEndingsAndAccents() {
        assertEquals("abbe", lookupForms("abbé’s").last { it == "abbe" })
        assertTrue("people" in lookupForms("People’s"))
        assertTrue("can" in lookupForms("can't"))
        assertTrue("you" in lookupForms("you’ll"))
        assertTrue("can" in lookupForms("canst"))
        assertTrue("lend" in lookupForms("lendeth"))
        assertTrue("archaeology" in lookupForms("archæology"))
        assertEquals("art", lookupForms("art").first()) // the literal word is always tried first
    }
}
