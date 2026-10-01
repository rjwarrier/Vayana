package com.vayana.feature.reader

import com.vayana.core.database.model.Annotation
import com.vayana.core.database.model.AnnotationType
import com.vayana.reader.api.Locator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderTapBehaviorTest {
    @Test
    fun `tapping the active highlight colour deletes the highlight`() {
        assertEquals(HighlightSwatchAction.Delete, highlightSwatchAction("green", "GREEN"))
    }

    @Test
    fun `tapping another highlight colour recolours the highlight`() {
        val action = assertIs<HighlightSwatchAction.Recolor>(highlightSwatchAction("green", "pink"))
        assertEquals("pink", action.colorKey)
    }

    @Test
    fun `marking the exact same range edits its highlight instead of stacking a duplicate`() {
        val highlight = annotation(id = 7, type = AnnotationType.HIGHLIGHT, locator = "epubcfi(/6/4!/4/2:0,/4/2:12)")

        assertEquals(highlight, listOf(highlight).exactEditableMark(highlight.locator, AnnotationType.HIGHLIGHT))
        assertNull(listOf(highlight).exactEditableMark(highlight.locator, AnnotationType.UNDERLINE))
        assertNull(listOf(highlight).exactEditableMark(highlight.locator, AnnotationType.NOTE))
    }

    @Test
    fun `community marks are never reused as personal highlights`() {
        val community = annotation(
            id = 8,
            type = AnnotationType.UNDERLINE,
            locator = "goodreads-quote:8",
            colorKey = "popular",
        )

        assertNull(listOf(community).exactEditableMark(community.locator, AnnotationType.UNDERLINE))
    }

    @Test
    fun `reader tap pauses read aloud while it is playing`() {
        assertTrue(shouldPauseReadAloudOnReaderTap(readAloudPlaying = true))
    }

    @Test
    fun `reader tap keeps its normal action while read aloud is not playing`() {
        assertFalse(shouldPauseReadAloudOnReaderTap(readAloudPlaying = false))
    }

    @Test
    fun `footer settings button stays available only during unobstructed reading`() {
        assertTrue(shouldShowReaderSettingsFooterButton(
            readerLoaded = true,
            chromeVisible = false,
            selectionActive = false,
            highlightCardActive = false,
            dictionaryActionsActive = false,
        ))
        assertFalse(shouldShowReaderSettingsFooterButton(true, true, false, false, false))
        assertFalse(shouldShowReaderSettingsFooterButton(true, false, true, false, false))
        assertFalse(shouldShowReaderSettingsFooterButton(true, false, false, true, false))
        assertFalse(shouldShowReaderSettingsFooterButton(true, false, false, false, true))
        assertFalse(shouldShowReaderSettingsFooterButton(false, false, false, false, false))
    }

    @Test
    fun `same-page relocation after a highlight tap keeps its edit card open`() {
        val card = HighlightCardState(
            annotationId = 42,
            top = 0.6f,
            bottom = 0.7f,
            page = 25,
            sectionCfi = "epubcfi(/6/22",
        )

        assertTrue(card.isAnchoredTo(locator(page = 25, cfi = "epubcfi(/6/22!/4/2:10)")))
        assertFalse(card.isAnchoredTo(locator(page = 26, cfi = "epubcfi(/6/22!/4/2:40)")))
    }

    @Test
    fun `section identity keeps highlight card stable when page numbers are unavailable`() {
        val card = HighlightCardState(
            annotationId = 42,
            top = null,
            bottom = null,
            page = null,
            sectionCfi = "epubcfi(/6/22",
        )

        assertTrue(card.isAnchoredTo(locator(page = null, cfi = "epubcfi(/6/22!/4/6:2)")))
        assertFalse(card.isAnchoredTo(locator(page = null, cfi = "epubcfi(/6/24!/4/2:0)")))
    }

    private fun locator(page: Int?, cfi: String) = Locator(
        cfi = cfi,
        href = null,
        progression = 0f,
        chapterTitle = null,
        currentPage = page,
    )

    private fun annotation(
        id: Long,
        type: AnnotationType,
        locator: String,
        colorKey: String = "yellow",
    ) = Annotation(
        id = id,
        bookId = 1,
        type = type,
        colorKey = colorKey,
        locator = locator,
        chapterTitle = null,
        chapterHref = null,
        selectedText = "selected text",
        readerNote = null,
        createdAt = 1,
        updatedAt = 1,
    )
}
