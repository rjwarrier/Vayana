package com.vayana.app.navigation

import kotlinx.serialization.Serializable

/** Not a [TopLevelRoute] — full-screen, no bottom nav (docs/PRODUCT_SPEC.md §4.2: "chrome-less by default"). */
@Serializable
data class ReaderRoute(
    val bookId: Long,
    val targetLocator: String? = null,
    /** Open at the start of the book rather than the saved position - reading a finished book again. */
    val fromStart: Boolean = false,
)
