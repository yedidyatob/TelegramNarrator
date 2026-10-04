package com.example.telegramnarrator.data.tts

import android.content.Context
import com.example.telegramnarrator.domain.tts.TtsSettings
import com.example.telegramnarrator.domain.tts.TtsVoiceLogic
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's [TtsSettings] in SharedPreferences. (SharedPreferences rather than DataStore:
 * TtsManager needs the values synchronously while it initializes the engine.)
 */
@Singleton
class TtsPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private companion object {
        const val FILE = "tts_settings"
        const val KEY_ENGINE = "engine"
        const val KEY_RATE = "rate"
        const val VOICE_PREFIX = "voice."
    }

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<TtsSettings> = _settings.asStateFlow()

    /** Applies [transform] to the current settings, saves and publishes the result. */
    @Synchronized
    fun update(transform: (TtsSettings) -> TtsSettings) {
        val updated = transform(_settings.value)
        write(updated)
        _settings.value = updated
    }

    private fun read(): TtsSettings {
        val voices = prefs.all
            .filterKeys { it.startsWith(VOICE_PREFIX) }
            .mapNotNull { (key, value) -> (value as? String)?.let { key.removePrefix(VOICE_PREFIX) to it } }
            .toMap()
        return TtsSettings(
            enginePackage = prefs.getString(KEY_ENGINE, null),
            speechRate = TtsVoiceLogic.clampRate(prefs.getFloat(KEY_RATE, TtsVoiceLogic.DEFAULT_RATE)),
            voices = voices
        )
    }

    private fun write(settings: TtsSettings) {
        val editor = prefs.edit().clear()
        settings.enginePackage?.let { editor.putString(KEY_ENGINE, it) }
        editor.putFloat(KEY_RATE, settings.speechRate)
        settings.voices.forEach { (language, name) -> editor.putString(VOICE_PREFIX + language, name) }
        editor.apply()
    }
}
