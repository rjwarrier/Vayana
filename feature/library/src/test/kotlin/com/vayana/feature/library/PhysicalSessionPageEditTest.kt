package com.vayana.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhysicalSessionPageEditTest {
    @Test
    fun resolvesValidPageRangeWithoutChangingItsDirection() {
        assertEquals(
            PhysicalSessionPageEdit(startPage = 82, endPage = 90),
            resolvePhysicalSessionPageEdit("82", "90", 194),
        )
        assertEquals(
            PhysicalSessionPageEdit(startPage = 90, endPage = 82),
            resolvePhysicalSessionPageEdit("90", "82", 194),
        )
    }

    @Test
    fun rejectsBlankNegativeOverflowingAndOutOfBookPages() {
        assertNull(resolvePhysicalSessionPageEdit("", "90", 194))
        assertNull(resolvePhysicalSessionPageEdit("82", "-1", 194))
        assertNull(resolvePhysicalSessionPageEdit("82", "2147483648", 194))
        assertNull(resolvePhysicalSessionPageEdit("82", "195", 194))
        assertNull(resolvePhysicalSessionPageEdit("82", "90", 0))
    }

    @Test
    fun acceptsNonNegativePagesWhenTotalIsUnknown() {
        assertEquals(
            PhysicalSessionPageEdit(startPage = 0, endPage = 500),
            resolvePhysicalSessionPageEdit("0", "500", null),
        )
    }
}
