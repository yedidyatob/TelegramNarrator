package com.example.telegramnarrator.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.telegramnarrator.data.tts.TtsPreferences
import com.example.telegramnarrator.domain.audio.LanguageDetector
import com.example.telegramnarrator.domain.tts.EngineOption
import com.example.telegramnarrator.domain.tts.TtsVoiceLogic
import com.example.telegramnarrator.domain.tts.VoiceOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: TtsPreferences
) {

    private var tts: TextToSpeech? = null
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    private val lock = Any()
    private var onDoneCallback: ((Boolean) -> Unit)? = null
    private var currentUtteranceId: String? = null
    private var utteranceCounter = 0L

    // Language the engine is currently set to (null = unknown / last attempt failed)
    private var currentLocale: Locale? = null

    // Engine package the current TextToSpeech instance was requested with (null = system default engine)
    private var requestedEngine: String? = null
    // Bumped for every TextToSpeech instance so that late init callbacks of replaced instances are ignored
    private var initGeneration = 0
    // Installed voices of the current engine (loaded once the engine is ready)
    private var voices: List<Voice> = emptyList()

    init {
        createEngine(preferences.settings.value.enginePackage, fallbackToDefault = true)
    }

    private fun createEngine(enginePackage: String?, fallbackToDefault: Boolean) {
        val generation = ++initGeneration
        _isInitialized.value = false
        requestedEngine = enginePackage
        currentLocale = null
        voices = emptyList()
        val listener = TextToSpeech.OnInitListener { status ->
            onEngineReady(generation, status, enginePackage, fallbackToDefault)
        }
        tts = try {
            if (enginePackage != null) TextToSpeech(context, listener, enginePackage) else TextToSpeech(context, listener)
        } catch (e: Exception) {
            android.util.Log.e("TtsManager", "Could not create TextToSpeech for engine $enginePackage", e)
            null
        }
        if (tts == null) onEngineReady(generation, TextToSpeech.ERROR, enginePackage, fallbackToDefault)
    }

    private fun onEngineReady(generation: Int, status: Int, enginePackage: String?, fallbackToDefault: Boolean) {
        if (generation != initGeneration) return // replaced by a newer engine in the meantime
        if (status == TextToSpeech.SUCCESS) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    finishUtterance(utteranceId, completed = true)
                }

                override fun onError(utteranceId: String?) {
                    // Handle error, maybe skip to next
                    finishUtterance(utteranceId, completed = false)
                }
            })
            // Spoken content on the media stream, flagged as speech (matches the audio focus request)
            tts?.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            voices = loadVoices()
            tts?.setSpeechRate(preferences.settings.value.speechRate)
            // Hebrew is the default; the language is switched per utterance in speak()
            applyLanguage(LanguageDetector.DEFAULT)
            _isInitialized.value = true
        } else if (enginePackage != null && fallbackToDefault) {
            // The chosen engine is gone / broken: use the system default engine (the choice stays saved)
            android.util.Log.w("TtsManager", "TTS engine $enginePackage failed to start (status $status), using the default engine")
            tts?.shutdown()
            createEngine(null, fallbackToDefault = false)
        } else {
            android.util.Log.e("TtsManager", "TextToSpeech initialization failed with status: $status")
            _isInitialized.value = true // Unblock listeners so they don't deadlock
        }
    }

    private fun loadVoices(): List<Voice> = try {
        tts?.voices?.toList().orEmpty()
    } catch (e: Exception) {
        // Some engines throw / return null when they have no voice list
        android.util.Log.w("TtsManager", "Could not list TTS voices", e)
        emptyList()
    }

    /** Engines installed on the device (empty until the first engine is ready). */
    fun availableEngines(): List<EngineOption> = try {
        tts?.engines?.map { EngineOption(it.name, it.label ?: it.name) }.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /** The voices of the active engine, as platform-independent options. */
    fun availableVoices(): List<VoiceOption> = voices.map { voice ->
        VoiceOption(
            name = voice.name,
            localeTag = voice.locale.toLanguageTag(),
            language = TtsVoiceLogic.normalizeLanguage(voice.locale.language),
            quality = voice.quality,
            requiresNetwork = voice.isNetworkConnectionRequired,
            installed = !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        )
    }

    /**
     * Applies the saved engine / voices / speech rate (call after the user changed them). A different
     * engine restarts TextToSpeech (isInitialized goes false until it is ready); voice and rate changes
     * take effect from the next utterance.
     */
    fun refreshSettings() {
        val settings = preferences.settings.value
        if (settings.enginePackage != requestedEngine) {
            stop()
            tts?.shutdown()
            tts = null
            createEngine(settings.enginePackage, fallbackToDefault = true)
            return
        }
        tts?.setSpeechRate(settings.speechRate)
        currentLocale = null // re-apply the language (and so the chosen voice) on the next utterance
    }

    /**
     * Speaks [text] in the language it appears to be written in (see [LanguageDetector]),
     * then calls [onDone] (also right away if there is nothing to say). [onDone] gets true if the
     * text was spoken to the end and false if it couldn't be spoken (error / nothing was said).
     * It is not called at all if the utterance is cancelled with [stop] or replaced by a new speak().
     * [language] forces the language (used by the "Test voice" button); by default it is detected from the text.
     */
    fun speak(text: String, language: Locale? = null, onDone: (completed: Boolean) -> Unit) {
        if (!_isInitialized.value || tts == null || text.isBlank()) {
            onDone(false)
            return
        }

        val utteranceId = synchronized(lock) {
            onDoneCallback = onDone
            "utterance_${++utteranceCounter}".also { currentUtteranceId = it }
        }

        applyLanguage(language ?: LanguageDetector.detect(text))

        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            finishUtterance(utteranceId, completed = false)
        }
    }

    private fun finishUtterance(utteranceId: String?, completed: Boolean) {
        val callback = synchronized(lock) {
            // Ignore callbacks of utterances that were cancelled by stop() or replaced by a newer speak()
            if (utteranceId == null || utteranceId != currentUtteranceId) return
            val pending = onDoneCallback
            onDoneCallback = null
            currentUtteranceId = null
            pending
        }
        callback?.invoke(completed)
    }

    /**
     * Switches the engine to [target]. If the voice data for it isn't installed, falls back to
     * the Hebrew default, then to the device language, and finally keeps whatever is set.
     */
    private fun applyLanguage(target: Locale) {
        val engine = tts ?: return
        if (currentLocale == target) return

        val fallbacks = listOf(target, LanguageDetector.DEFAULT, Locale.getDefault()).distinct()
        for (candidate in fallbacks) {
            val result = try {
                engine.setLanguage(candidate)
            } catch (e: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                currentLocale = candidate
                applyChosenVoice(engine, candidate)
                return
            }
            android.util.Log.w("TtsManager", "TTS language not available: $candidate")
        }
        currentLocale = null
    }

    /**
     * Uses the voice the user picked for [language], if any. Languages without a pick keep the engine's
     * default voice (setLanguage() just selected it), so other languages are unaffected.
     */
    private fun applyChosenVoice(engine: TextToSpeech, language: Locale) {
        val settings = preferences.settings.value
        val chosen = TtsVoiceLogic.chosenVoiceFor(settings, language.language, availableVoices()) ?: return
        val voice = voices.firstOrNull { it.name == chosen.name } ?: return
        try {
            if (engine.setVoice(voice) != TextToSpeech.SUCCESS) {
                android.util.Log.w("TtsManager", "Could not set voice ${voice.name}, using the default voice")
            }
        } catch (e: Exception) {
            android.util.Log.w("TtsManager", "Could not set voice ${voice.name}", e)
        }
    }

    fun stop() {
        // Drop the callback first so a late onDone of the interrupted utterance can't fire it
        synchronized(lock) {
            onDoneCallback = null
            currentUtteranceId = null
        }
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
