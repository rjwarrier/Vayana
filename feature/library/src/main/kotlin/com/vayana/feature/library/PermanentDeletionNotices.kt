package com.vayana.feature.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A book deleted permanently; reported once, by whichever library screen is showing. */
data class PermanentDeletionNotice(val title: String, val cloudCopyPending: Boolean)

/**
 * Carries a permanent-deletion result across screens: book detail closes as soon as the user confirms, and its view
 * model goes with it, so the library screen underneath reports the outcome.
 */
@Singleton
class PermanentDeletionNotices @Inject constructor() {
    private val latest = MutableStateFlow<PermanentDeletionNotice?>(null)

    val notice: StateFlow<PermanentDeletionNotice?> = latest.asStateFlow()

    fun post(notice: PermanentDeletionNotice) {
        latest.value = notice
    }

    fun consume(notice: PermanentDeletionNotice) {
        latest.compareAndSet(notice, null)
    }
}
