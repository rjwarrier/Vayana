package com.vayana.app.widget

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A book to open in the reader, asked for from outside the app (the widget), waiting for the app's navigation. */
@Singleton
class OpenBookRequests @Inject constructor() {
    private val _pending = MutableStateFlow<Long?>(null)
    val pending: StateFlow<Long?> = _pending.asStateFlow()

    fun offer(bookId: Long) {
        _pending.value = bookId
    }

    /** Clears [bookId] once navigation has opened it, unless a newer request replaced it meanwhile. */
    fun consume(bookId: Long) {
        _pending.compareAndSet(bookId, null)
    }
}
