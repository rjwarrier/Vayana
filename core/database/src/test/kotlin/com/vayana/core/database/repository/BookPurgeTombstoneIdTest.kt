package com.vayana.core.database.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BookPurgeTombstoneIdTest {
    @Test
    fun purgeIdRoundTrips() {
        assertEquals("purge:book-1", bookPurgeTombstoneId("book-1"))
        assertEquals("book-1", bookSyncIdOfPurge(bookPurgeTombstoneId("book-1")))
    }

    @Test
    fun otherIdsAreNotPurges() {
        assertNull(bookSyncIdOfPurge("book-1"))
        assertNull(bookSyncIdOfPurge("reset:book-1"))
        assertNull(bookSyncIdOfPurge("purge:"))
        assertEquals(TombstoneEntityType.BOOK_PURGE, TombstoneEntityType.fromValue("book_purge"))
    }
}
