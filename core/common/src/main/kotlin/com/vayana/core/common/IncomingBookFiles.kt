package com.vayana.core.common

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * Book files other apps hand to Vayana ("Open with", Share) waiting for the library to import them. The activity
 * offers them as they arrive; the library takes them as soon as it exists, so nothing is lost while onboarding or the
 * reader is on screen.
 */
@Singleton
class IncomingBookFiles @Inject constructor() {
    private val _pending = MutableStateFlow<List<Uri>>(emptyList())
    val pending: StateFlow<List<Uri>> = _pending.asStateFlow()

    fun offer(uris: List<Uri>) {
        if (uris.isNotEmpty()) _pending.update { current -> (current + uris).distinct() }
    }

    /** Hands over everything waiting, exactly once. */
    fun drain(): List<Uri> = _pending.getAndUpdate { emptyList() }
}

/** The book files an "Open with" or Share intent carries: a viewed file, one shared stream, or several. */
fun Intent.incomingBookUris(): List<Uri> {
    val uris = when (action) {
        Intent.ACTION_VIEW -> listOfNotNull(data)
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
    // A shared file may arrive only through the clip; file:// is unreadable on modern Android, so it is dropped.
    val fromClip = if (uris.isEmpty() && action != Intent.ACTION_VIEW) {
        clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
    } else {
        emptyList()
    }
    return (uris + fromClip).filter { it.scheme == "content" }.distinct()
}
