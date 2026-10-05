package com.vayana.core.database.repository

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompanionProgressTest {
    @Test fun `changed phone page and newer reading are preserved`() {
        assertFalse(companionCanUpdateProgress(200, 100, 50, 100, 90, 10, 200, 200))
        assertFalse(companionCanUpdateProgress(200, 100, 150, 100, 10, 10, 200, 200))
        assertFalse(companionCanUpdateProgress(100, 100, 50, 100, 10, 10, 100, 200))
    }
    @Test fun `unchanged book and sequential offline sessions advance progress`() {
        assertTrue(companionCanUpdateProgress(100, 100, 50, 100, 10, 10, 200, 200))
        assertTrue(companionCanUpdateProgress(1000, 100, 200, 300, 20, 20, 200, 200))
        assertTrue(companionCanUpdateProgress(100, 100, null, 100, 0, 0, null, null))
    }
}
