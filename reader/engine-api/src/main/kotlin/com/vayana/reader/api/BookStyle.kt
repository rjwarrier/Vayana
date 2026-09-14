package com.vayana.reader.api

/**
 * Minimal slice of PROMPT2appbuild.md §4.3's full typography settings — just enough to drive
 * the style panel's first pass. Grows in place as more of §4.3 is implemented; never a breaking
 * rename, since [com.vayana.core.datastore]'s settings registry (§5) will own the persisted form.
 */
data class BookStyle(
    val fontSizePercent: Int = 100,
    val lineHeight: Float = 1.5f,
    val fontFamily: String? = null,
    val customFontFileName: String? = null,
    val sideMarginPercent: Int = 10,
    val bionicReading: Boolean = false,
    /** Slide pages when turning instead of switching instantly. */
    val pageTurnAnimation: Boolean = false,
)

data class ReadTheme(
    val backgroundColorArgb: Int,
    val textColorArgb: Int,
)
