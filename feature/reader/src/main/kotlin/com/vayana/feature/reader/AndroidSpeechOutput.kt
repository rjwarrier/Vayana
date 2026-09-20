package com.vayana.feature.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SpeechVoiceOption(
    val name: String,
    val localeTag: String,
    val localeLabel: String,
    val requiresNetwork: Boolean,
    val isSystemDefault: Boolean,
    /** Male or female where the voice is on a published list; null when unknown. */
    val gender: VoiceGender? = null,
    /** False when the engine lists the voice but its data is not downloaded on this phone. */
    val installed: Boolean = true,
)

internal data class SpeechLanguageOption(val tag: String, val label: String)

internal fun speechLanguageOptions(voices: List<SpeechVoiceOption>): List<SpeechLanguageOption> =
    voices
        .distinctBy(SpeechVoiceOption::localeTag)
        .map { SpeechLanguageOption(tag = it.localeTag, label = it.localeLabel) }
        .sortedBy(SpeechLanguageOption::label)

internal fun resolveSpeechLanguageTag(
    voices: List<SpeechVoiceOption>,
    selectedVoiceName: String,
    previousTag: String,
): String = voices.firstOrNull { it.name == selectedVoiceName }?.localeTag
    ?: previousTag.takeIf { tag -> voices.any { it.localeTag == tag } }
    ?: voices.firstOrNull(SpeechVoiceOption::isSystemDefault)?.localeTag
    ?: speechLanguageOptions(voices).firstOrNull()?.tag.orEmpty()

/** Speaks text for read-aloud; [Listener] callbacks may arrive on any thread. */
internal interface SpeechOutput {
    var listener: Listener?

    val voices: StateFlow<List<SpeechVoiceOption>>

    /** Starts the speech engine if needed, then reports whether a voice can speak. */
    fun prepare(onReady: (Boolean) -> Unit)

    fun speak(utteranceId: String, text: String, flush: Boolean)

    fun setRate(rate: Float)

    fun setPitch(pitch: Float)

    fun setVoice(name: String?)

    fun stop()

    fun shutdown()

    interface Listener {
        fun onStart(utteranceId: String)

        fun onRangeStart(utteranceId: String, start: Int, end: Int)

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
    private var pitch = 1f
    private var selectedVoiceName: String? = null
    private var systemDefaultVoice: Voice? = null
    private var engineVoices: Set<Voice> = emptySet()
    private var preparing = false
    private val readinessCallbacks = mutableListOf<(Boolean) -> Unit>()
    private val _voices = MutableStateFlow<List<SpeechVoiceOption>>(emptyList())
    override val voices: StateFlow<List<SpeechVoiceOption>> = _voices.asStateFlow()

    override var listener: SpeechOutput.Listener? = null

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            listener?.onStart(utteranceId)
        }

        override fun onDone(utteranceId: String) {
            listener?.onDone(utteranceId)
        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            listener?.onRangeStart(utteranceId, start, end)
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
        readinessCallbacks += onReady
        if (preparing) return
        preparing = true
        tts?.shutdown()
        var engine: TextToSpeech? = null
        engine = TextToSpeech(appContext) { status ->
            // Posted so the callback never runs before `engine` is assigned, even if the engine initialises at once.
            mainHandler.post {
                val created = engine
                if (tts !== created) return@post
                ready = status == TextToSpeech.SUCCESS && created != null &&
                    runCatching { created.defaultVoice != null }.getOrDefault(false)
                if (ready && created != null) {
                    created.setOnUtteranceProgressListener(progressListener)
                    created.setSpeechRate(rate)
                    created.setPitch(pitch)
                    created.setAudioAttributes(SpeechAudioAttributes)
                    systemDefaultVoice = created.defaultVoice
                    engineVoices = created.installedVoices()
                    val defaultVoiceName = systemDefaultVoice?.name
                    val displayLocale = Locale.getDefault()
                    _voices.value = engineVoices
                        .map { voice ->
                            voice to SpeechVoiceOption(
                                name = voice.name,
                                localeTag = voice.locale.toLanguageTag(),
                                localeLabel = voice.locale.getDisplayName(displayLocale),
                                requiresNetwork = voice.isNetworkConnectionRequired,
                                isSystemDefault = voice.name == defaultVoiceName,
                                gender = googleVoiceGender(voice.name),
                                installed = voice.isInstalled(),
                            )
                        }
                        .sortedWith(
                            compareBy<Pair<Voice, SpeechVoiceOption>> { it.second.localeLabel }
                                .thenBy { !it.second.installed }
                                .thenBy { it.second.requiresNetwork }
                                .thenByDescending { it.first.quality }
                                .thenBy { it.first.name },
                        )
                        .map { it.second }
                    applySelectedVoice(created)
                }
                preparing = false
                val callbacks = readinessCallbacks.toList()
                readinessCallbacks.clear()
                callbacks.forEach { it(ready) }
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

    override fun setPitch(pitch: Float) {
        this.pitch = pitch
        tts?.setPitch(pitch)
    }

    override fun setVoice(name: String?) {
        selectedVoiceName = name?.takeIf { it.isNotBlank() }
        tts?.takeIf { ready }?.let(::applySelectedVoice)
    }

    private fun applySelectedVoice(engine: TextToSpeech) {
        val selected = selectedVoiceName?.let { name -> engineVoices.firstOrNull { it.name == name } }
        (selected ?: systemDefaultVoice)?.let(engine::setVoice)
    }

    private fun Voice.isInstalled(): Boolean =
        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty()

    /** Some engines throw or return null from `getVoices()`; treat that as "no voices" rather than crashing. */
    private fun TextToSpeech.installedVoices(): Set<Voice> = runCatching { voices }.getOrNull().orEmpty()

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
        preparing = false
        readinessCallbacks.clear()
        systemDefaultVoice = null
        engineVoices = emptySet()
        _voices.value = emptyList()
    }
}
