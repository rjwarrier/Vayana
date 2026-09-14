package com.vayana.feature.reader

import com.vayana.reader.api.Locator
import kotlin.math.abs

/** Says when enough pages have turned to sync; [thresholdPages] is read on every page, 0 or less turns syncing off. */
internal class PageTurnAutoSyncGate(
    private val thresholdPages: () -> Int,
) {
    private var lastPage: Int? = null
    private var pagesSinceSync = 0

    fun onLocator(locator: Locator): Boolean {
        val page = locator.currentPage?.takeIf { it > 0 } ?: return false
        val previousPage = lastPage
        lastPage = page
        if (previousPage == null || page == previousPage) return false

        val threshold = thresholdPages()
        if (threshold <= 0) {
            pagesSinceSync = 0
            return false
        }
        pagesSinceSync += abs(page - previousPage)
        if (pagesSinceSync < threshold) return false

        pagesSinceSync %= threshold
        return true
    }
}
