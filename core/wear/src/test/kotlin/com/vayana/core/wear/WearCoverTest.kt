package com.vayana.core.wear

import kotlin.test.*
import org.junit.Test

class WearCoverTest {
    @Test fun thumbnailAndRemovalRoundTripWithoutChangingBookPayload() {
        val book = WearBook("book-123", "A book", 10, 200, 1000)
        val original = book.json().toString()
        val cover = WearCover(book.id, "thumbnail-base64")
        assertEquals(cover, WearCover.parse(cover.json()))
        assertEquals(WearCover(book.id, null), WearCover.parse(WearCover(book.id, null).json()))
        assertEquals(original, book.json().toString())
        assertFalse(book.json().has("image"))
    }

    @Test fun stableSafePathsSupportArbitraryBookIdentities() {
        val id = "../books/മലയാളം"
        val key = WearCover.key(id)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
        assertEquals(WearProtocol.COVER + key, WearCover.path(id))
        assertNotEquals(WearCover.path(id), WearCover.path("other"))
    }

    @Test fun invalidOrOversizedImagesAreRejected() {
        assertFailsWith<IllegalArgumentException> { WearCover.parse(WearCover("", null).json()) }
        assertFailsWith<IllegalArgumentException> {
            WearCover.parse(WearCover("book", "a".repeat(WearCover.MAX_ENCODED_SIZE + 1)).json())
        }
    }

    @Test fun coverEventsRefreshWatchWithoutWakingPhoneInALoop() {
        val path = WearCover.path("book")
        assertTrue(WearSyncRules.watchEvent(path, true))
        assertFalse(WearSyncRules.watchEvent(path, false))
        assertFalse(WearSyncRules.phoneEvent(path, true))
    }
}
