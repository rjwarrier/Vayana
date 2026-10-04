package com.vayana.app.widget

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A book to open in the reader - and, from the widget's play button, to read aloud. */
data class OpenBookRequest(val bookId: Long = 0, val readAloud: Boolean = false, val bookSyncId: String? = null, val annotationSyncId: String? = null, val locator: String? = null, val offline: Boolean = false)

/** A book to open, asked for from outside the app (the widget, a shortcut), waiting for the app's navigation. */
@Singleton
class OpenBookRequests @Inject constructor() {
    private val _pending = MutableStateFlow<OpenBookRequest?>(null)
    val pending: StateFlow<OpenBookRequest?> = _pending.asStateFlow()

    fun offer(request: OpenBookRequest) {
        _pending.value = request
    }

    /** Clears [request] once navigation has opened it, unless a newer request replaced it meanwhile. */
    fun consume(request: OpenBookRequest) {
        _pending.compareAndSet(request, null)
    }
}
