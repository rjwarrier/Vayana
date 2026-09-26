package com.vayana.reader.api

/**
 * Minimal slice of docs/PRODUCT_SPEC.md §4.3's full typography settings — just enough to drive
 * the style panel's first pass. Grows in place as more of §4.3 is implemented; never a breaking
 * rename, since [com.vayana.core.datastore]'s settings registry (§5) will own the persisted form.
 */
data class BookStyle(
    val fontSizePercent: Int = 100,
    val lineHeight: Float = 1.5f,
    val fontFamily: String? = null,
    val customFontFileName: String? = null,
    val sideMarginPercent: Int = 10,
    /** Ignore font families and paragraph sizes supplied by the book so reader typography controls work. */
    val overridePublisherTypography: Boolean = false,
    val bionicReading: Boolean = false,
    /** Slide pages when turning instead of switching instantly. */
    val pageTurnAnimation: Boolean = false,
    /** Thickens every letter a little; thin strokes stay crisp on E-Ink and other low-contrast panels. */
    val boldText: Boolean = false,
    val textAlign: BookTextAlign = BookTextAlign.BOOK,
    val hyphenation: BookHyphenation = BookHyphenation.BOOK,
)

/** Paragraph alignment; [BOOK] leaves it to the book's own styles. */
enum class BookTextAlign { BOOK, JUSTIFIED, LEFT }

/** Whether words may break with a hyphen at a line end; [BOOK] leaves it to the book's own styles. */
enum class BookHyphenation { BOOK, ON, OFF }

data class ReadTheme(
    val backgroundColorArgb: Int,
    val textColorArgb: Int,
)
