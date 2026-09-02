package com.vayana.app.navigation

import kotlinx.serialization.Serializable

/** Not a [TopLevelRoute] — full-screen, no bottom nav (PROMPT2appbuild.md §4.2: "chrome-less by default"). */
@Serializable
data class ReaderRoute(val bookId: Long)
