package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionCardLayoutTest {
    private fun top(anchorTop: Float, anchorBottom: Float, cardHeight: Int = 200) = selectionCardTop(
        anchorTop = anchorTop,
        anchorBottom = anchorBottom,
        cardHeight = cardHeight,
        minY = 50,
        maxY = 1900,
        gapAbove = 12,
        gapBelow = 32,
    )

    @Test
    fun cardSitsJustAboveTheSelectionWhenThereIsRoom() {
        assertEquals(500 - 12 - 200, top(anchorTop = 500f, anchorBottom = 560f))
    }

    @Test
    fun cardMovesBelowTheSelectionWhenTheTopHasNoRoom() {
        assertEquals(160 + 32, top(anchorTop = 100f, anchorBottom = 160f))
    }

    @Test
    fun cardStopsAtTheTopEdgeWhenAboveJustFits() {
        // Exactly enough room above: the card's top lands on the top limit.
        assertEquals(50, top(anchorTop = 50f + 12f + 200f, anchorBottom = 400f))
    }

    @Test
    fun aSelectionFillingTheScreenPutsTheCardAtTheRoomierEdge() {
        // 250px free above, 200px below: the card takes the top edge, overlapping the selection.
        assertEquals(50, top(anchorTop = 300f, anchorBottom = 1700f, cardHeight = 400))
        // More room below instead.
        assertEquals(1900 - 400, top(anchorTop = 200f, anchorBottom = 1500f, cardHeight = 400))
    }

    @Test
    fun cardTallerThanTheAreaStaysAtTheTopLimit() {
        assertEquals(50, top(anchorTop = 900f, anchorBottom = 950f, cardHeight = 3000))
    }
}
