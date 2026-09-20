package com.vayana.core.designsystem

import android.view.KeyEvent
import com.vayana.core.designsystem.theme.PageKeyDirection
import com.vayana.core.designsystem.theme.pageKeyDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PageKeyTest {
    @Test
    fun `page up and navigate previous go back a page`() {
        assertEquals(PageKeyDirection.PREVIOUS, pageKeyDirection(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(PageKeyDirection.PREVIOUS, pageKeyDirection(KeyEvent.KEYCODE_NAVIGATE_PREVIOUS))
    }

    @Test
    fun `page down and navigate next go forward a page`() {
        assertEquals(PageKeyDirection.NEXT, pageKeyDirection(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(PageKeyDirection.NEXT, pageKeyDirection(KeyEvent.KEYCODE_NAVIGATE_NEXT))
    }

    @Test
    fun `other keys are not page keys`() {
        assertNull(pageKeyDirection(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(pageKeyDirection(KeyEvent.KEYCODE_A))
    }
}
