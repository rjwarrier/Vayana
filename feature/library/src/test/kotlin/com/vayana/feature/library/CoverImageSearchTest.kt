package com.vayana.feature.library

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverImageSearchTest {

    @Test
    fun searchUsesBookTitleAndCoverImagePhrase() {
        assertEquals(
            "https://www.google.com/search?tbm=isch&safe=active&q=Countdown+City+cover+image",
            coverImageSearchUrl("Countdown City"),
        )
    }

    @Test
    fun supportedDataImageIsDecoded() {
        val image = decodeDataImage("data:image/png;base64,AQID")

        assertEquals("png", image?.extension)
        assertContentEquals(byteArrayOf(1, 2, 3), image?.bytes)
    }

    @Test
    fun unsupportedDataImageTypeIsRejected() {
        assertNull(decodeDataImage("data:image/svg+xml;base64,AQID"))
    }
}
