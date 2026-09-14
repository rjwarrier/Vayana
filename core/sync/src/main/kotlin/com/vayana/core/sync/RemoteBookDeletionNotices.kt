package com.vayana.core.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Titles of books a sync removed because they were deleted on another device, until the library screen reports them. */
@Singleton
class RemoteBookDeletionNotices @Inject constructor() {
    private val pending = MutableStateFlow<List<String>>(emptyList())

    val titles: StateFlow<List<String>> = pending.asStateFlow()

    fun post(titles: Collection<String>) {
        if (titles.isEmpty()) return
        pending.update { current -> current + titles }
    }

    /** Removes the titles a notice showed; titles posted while it was on screen stay for the next one. */
    fun consume(shown: List<String>) {
        pending.update { current ->
            if (current.take(shown.size) == shown) current.drop(shown.size) else current - shown.toSet()
        }
    }
}
