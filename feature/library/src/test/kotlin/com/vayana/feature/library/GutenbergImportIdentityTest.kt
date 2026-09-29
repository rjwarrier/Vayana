package com.vayana.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GutenbergImportIdentityTest {
    @Test
    fun readsIdentityFromBothOfficialDownloadNames() {
        assertEquals(1342L, gutenbergBookIdFromFileName("pg1342.epub"))
        assertEquals(84L, gutenbergBookIdFromFileName("PG84-IMAGES.EPUB"))
    }

    @Test
    fun leavesUnrelatedFilesWithoutProvenance() {
        assertNull(gutenbergBookIdFromFileName("my-book.epub"))
        assertNull(gutenbergBookIdFromFileName("pg0.epub"))
    }
}
