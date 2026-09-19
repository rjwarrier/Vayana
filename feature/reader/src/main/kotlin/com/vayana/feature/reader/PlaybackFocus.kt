package com.vayana.feature.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** What happened to the audio focus read-aloud holds, or to the output it plays through. */
internal enum class PlaybackFocusEvent {
    /** Another app took focus for a moment (a call, a navigation prompt); reading should resume when it returns. */
    LOST_TEMPORARILY,

    /** Another app took focus for good (music started); reading stays paused until the reader presses play. */
    LOST,

    /** A temporary loss ended. */
    REGAINED,

    /** Headphones were unplugged or the Bluetooth device disconnected; speech would jump to the speaker. */
    BECOMING_NOISY,
}

/** Audio focus for read-aloud, so it is polite: it pauses for calls and other audio instead of talking over them. */
internal interface PlaybackFocus {
    /** Asks for focus; false if it was refused (for example during a call). Events arrive on the main thread. */
    fun request(onEvent: (PlaybackFocusEvent) -> Unit): Boolean

    fun abandon()

    /** For tests and devices without audio focus: always granted, never lost. */
    object Unmanaged : PlaybackFocus {
        override fun request(onEvent: (PlaybackFocusEvent) -> Unit): Boolean = true

        override fun abandon() = Unit
    }
}

internal class AndroidPlaybackFocus(context: Context) : PlaybackFocus {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var request: AudioFocusRequest? = null
    private var noisyReceiver: BroadcastReceiver? = null

    /** False after another app took focus, until it is granted back. */
    private var holdingFocus = false

    override fun request(onEvent: (PlaybackFocusEvent) -> Unit): Boolean {
        // Resuming after a pause keeps the same request; after a loss the reader pressing play asks again.
        val existing = request
        if (existing != null && holdingFocus) return true
        val focusRequest = existing ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(SpeechAudioAttributes)
            .setOnAudioFocusChangeListener(
                { change ->
                    val event = change.toFocusEvent()
                    holdingFocus = event == PlaybackFocusEvent.REGAINED
                    event?.let(onEvent)
                },
                mainHandler,
            )
            .build()
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return false
        holdingFocus = true
        if (existing == null) {
            request = focusRequest
            registerNoisyReceiver(onEvent)
        }
        return true
    }

    override fun abandon() {
        noisyReceiver?.let { receiver -> runCatching { appContext.unregisterReceiver(receiver) } }
        noisyReceiver = null
        request?.let(audioManager::abandonAudioFocusRequest)
        request = null
        holdingFocus = false
    }

    private fun registerNoisyReceiver(onEvent: (PlaybackFocusEvent) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onEvent(PlaybackFocusEvent.BECOMING_NOISY)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyReceiver = receiver
    }
}

/** Speech that plays as media, so it follows the media volume and is treated as spoken word by the system. */
internal val SpeechAudioAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

internal fun Int.toFocusEvent(): PlaybackFocusEvent? = when (this) {
    AudioManager.AUDIOFOCUS_LOSS -> PlaybackFocusEvent.LOST
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
    -> PlaybackFocusEvent.LOST_TEMPORARILY
    AudioManager.AUDIOFOCUS_GAIN -> PlaybackFocusEvent.REGAINED
    else -> null
}
