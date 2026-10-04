package com.example.telegramnarrator.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.telegramnarrator.domain.audio.LanguageDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
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

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Hebrew is the default; the language is switched per utterance in speak()
                applyLanguage(LanguageDetector.DEFAULT)

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
                _isInitialized.value = true
            } else {
                android.util.Log.e("TtsManager", "TextToSpeech initialization failed with status: $status")
                _isInitialized.value = true // Unblock listeners so they don't deadlock
            }
        }
    }

    /**
     * Speaks [text] in the language it appears to be written in (see [LanguageDetector]),
     * then calls [onDone] (also right away if there is nothing to say). [onDone] gets true if the
     * text was spoken to the end and false if it couldn't be spoken (error / nothing was said).
     * It is not called at all if the utterance is cancelled with [stop] or replaced by a new speak().
     */
    fun speak(text: String, onDone: (completed: Boolean) -> Unit) {
        if (!_isInitialized.value || tts == null || text.isBlank()) {
            onDone(false)
            return
        }

        val utteranceId = synchronized(lock) {
            onDoneCallback = onDone
            "utterance_${++utteranceCounter}".also { currentUtteranceId = it }
        }

        applyLanguage(LanguageDetector.detect(text))

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
                return
            }
            android.util.Log.w("TtsManager", "TTS language not available: $candidate")
        }
        currentLocale = null
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
