package com.vayana.core.designsystem.theme

import android.view.KeyEvent

enum class PageKeyDirection { PREVIOUS, NEXT }

/**
 * The dedicated page-turn buttons of e-readers (Boox, Kobo-style, Kindle-style hardware) arrive as page or navigation keys.
 * Unlike the volume keys they have no other job, so they always turn pages.
 */
fun pageKeyDirection(keyCode: Int): PageKeyDirection? = when (keyCode) {
    KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> PageKeyDirection.PREVIOUS
    KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_NAVIGATE_NEXT -> PageKeyDirection.NEXT
    else -> null
}
