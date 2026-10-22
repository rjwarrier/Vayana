package com.vayana.feature.reader

/** Text layers and search must reload when pages arrive or whole-book line joining finishes. */
internal class RecognitionRefresh {
    private var previousPages = -1
    private var hadJoins = false

    fun update(pages: Int, joinsReady: Boolean): Boolean {
        val refresh = (previousPages >= 0 && pages > previousPages) || (joinsReady && !hadJoins)
        previousPages = pages
        hadJoins = joinsReady
        return refresh
    }
}
