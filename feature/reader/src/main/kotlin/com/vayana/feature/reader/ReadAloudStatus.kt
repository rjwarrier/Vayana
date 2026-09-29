package com.vayana.feature.reader

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** The book being read aloud and whether it's speaking right now, or paused. */
data class ReadAloudStatus(val bookId: Long, val playing: Boolean)

/**
 * Read-aloud as the rest of the app sees it, for the home-screen widget's play/pause button: the reader publishes
 * what it's doing, and the widget can ask the reader showing a book to start reading it aloud.
 */
@Singleton
class ReadAloudStatusHolder @Inject constructor() {
    private val _status = MutableStateFlow<ReadAloudStatus?>(null)
    val status: StateFlow<ReadAloudStatus?> = _status.asStateFlow()

    private val _startRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    internal val startRequests: SharedFlow<Long> = _startRequests.asSharedFlow()

    internal fun publish(status: ReadAloudStatus?) {
        _status.value = status
    }

    /** Clears the status, but only if it's still [bookId]'s: another reader may have taken over. */
    internal fun clear(bookId: Long) {
        _status.compareAndSet(_status.value?.takeIf { it.bookId == bookId } ?: return, null)
    }

    /** Asks the reader already showing [bookId] to start reading it aloud. */
    fun requestStart(bookId: Long) {
        _startRequests.tryEmit(bookId)
    }

    companion object {
        /**
         * Pauses or resumes read-aloud that's already running, from outside the app (the widget), the way the
         * notification's button does.
         */
        fun playPauseIntent(context: Context, play: Boolean): PendingIntent =
            PendingIntent.getForegroundService(
                context,
                if (play) PlayRequestCode else PauseRequestCode,
                ReadAloudForegroundService.commandIntent(context, if (play) ReadAloudCommand.PLAY else ReadAloudCommand.PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private const val PlayRequestCode = 4_230
        private const val PauseRequestCode = 4_231
    }
}
