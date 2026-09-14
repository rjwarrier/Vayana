package com.vayana.feature.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/** Speaks text for read-aloud; [Listener] callbacks may arrive on any thread. */
internal interface SpeechOutput {
    var listener: Listener?

    /** Starts the speech engine if needed, then reports whether a voice can speak. */
    fun prepare(onReady: (Boolean) -> Unit)

    fun speak(utteranceId: String, text: String, flush: Boolean)

    fun setRate(rate: Float)

    fun stop()

    fun shutdown()

    interface Listener {
        fun onStart(utteranceId: String)

        fun onDone(utteranceId: String)
    }
}

/** [SpeechOutput] over Android's text-to-speech, with whatever engine and voice the device has set up (offline). */
internal class AndroidSpeechOutput(context: Context) : SpeechOutput {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var rate = 1f

    override var listener: SpeechOutput.Listener? = null

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            listener?.onStart(utteranceId)
        }

        override fun onDone(utteranceId: String) {
            listener?.onDone(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            listener?.onDone(utteranceId)
        }
    }

    override fun prepare(onReady: (Boolean) -> Unit) {
        if (tts != null && ready) {
            onReady(true)
            return
        }
        tts?.shutdown()
        var engine: TextToSpeech? = null
        engine = TextToSpeech(appContext) { status ->
            // Posted so the callback never runs before `engine` is assigned, even if the engine initialises at once.
            mainHandler.post {
                val created = engine
                ready = status == TextToSpeech.SUCCESS && created != null &&
                    runCatching { created.defaultVoice != null }.getOrDefault(false)
                if (ready && created != null) {
                    created.setOnUtteranceProgressListener(progressListener)
                    created.setSpeechRate(rate)
                }
                onReady(ready)
            }
        }
        tts = engine
    }

    override fun speak(utteranceId: String, text: String, flush: Boolean) {
        if (!ready) return
        tts?.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    override fun setRate(rate: Float) {
        this.rate = rate
        tts?.setSpeechRate(rate)
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
