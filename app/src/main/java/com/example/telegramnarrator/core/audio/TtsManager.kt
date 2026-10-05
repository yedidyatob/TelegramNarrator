package com.example.telegramnarrator.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

    private var onDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault() // Uses system language for narration
                // tts?.language = Locale.ENGLISH // Fallback
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        onDoneCallback?.invoke()
                    }

                    override fun onError(utteranceId: String?) {
                        // Handle error, maybe skip to next
                        onDoneCallback?.invoke()
                    }
                })
                _isInitialized.value = true
            } else {
                android.util.Log.e("TtsManager", "TextToSpeech initialization failed with status: $status")
                _isInitialized.value = true // Unblock listeners so they don't deadlock
            }
        }
    }

    fun speak(text: String, onDone: () -> Unit) {
        if (!_isInitialized.value || tts == null) {
            onDone()
            return
        }
        
        onDoneCallback = onDone
        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "id")
        
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "id")
        if (result == TextToSpeech.ERROR) {
            onDone()
        }
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
