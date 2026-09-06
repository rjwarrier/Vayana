package com.vayana.feature.reader

import com.vayana.reader.api.Locator
import kotlin.math.abs

internal class PageTurnAutoSyncGate(
    private val thresholdPages: Int,
) {
    private var lastPage: Int? = null
    private var pagesSinceSync = 0

    init {
        require(thresholdPages > 0) { "Auto sync threshold must be positive" }
    }

    fun onLocator(locator: Locator): Boolean {
        val page = locator.currentPage?.takeIf { it > 0 } ?: return false
        val previousPage = lastPage
        lastPage = page
        if (previousPage == null || page == previousPage) return false

        pagesSinceSync += abs(page - previousPage)
        if (pagesSinceSync < thresholdPages) return false

        pagesSinceSync %= thresholdPages
        return true
    }
}
