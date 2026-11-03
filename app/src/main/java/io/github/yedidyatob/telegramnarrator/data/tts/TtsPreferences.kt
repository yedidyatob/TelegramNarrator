package io.github.yedidyatob.telegramnarrator.data.tts

import android.content.Context
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTts
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTtsOptions
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsSettings
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsVoiceLogic
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's [TtsSettings] in SharedPreferences. (SharedPreferences rather than DataStore:
 * TtsManager needs the values synchronously while it initializes the engine.)
 *
 * No stored engine, or an unknown one: [SpeechProvider.DEFAULT] (System).
 *
 * Migration: a stored provider id of "openai" and the legacy `openai_enabled` flag are both migrated to
 * "gemini" so existing BYOK users keep the paid-engine slot. The legacy `openai_model` key is dropped on next
 * write (it has no Gemini equivalent). The `openai_voice` key is similarly dropped.
 */
@Singleton
class TtsPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private companion object {
        const val FILE = "tts_settings"
        const val KEY_ENGINE = "engine"
        const val KEY_RATE = "rate"
        const val KEY_MARK_AS_READ = "mark_as_read"
        const val KEY_PROVIDER = "provider"
        /** Legacy (OpenAI PR builds): read once to migrate to [KEY_PROVIDER], no longer written. */
        const val KEY_OPENAI_ENABLED = "openai_enabled"
        const val KEY_GEMINI_VOICE = "gemini_voice"
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
            voices = voices,
            markAsReadOverride = if (prefs.contains(KEY_MARK_AS_READ)) prefs.getBoolean(KEY_MARK_AS_READ, true) else null,
            provider = SpeechProvider.fromStored(
                prefs.getString(KEY_PROVIDER, null),
                legacyOpenAiEnabled = prefs.getBoolean(KEY_OPENAI_ENABLED, false)
            ),
            gemini = GeminiTtsOptions(
                voice = GeminiTts.normalizeVoice(prefs.getString(KEY_GEMINI_VOICE, GeminiTts.DEFAULT_VOICE))
            )
        )
    }

    private fun write(settings: TtsSettings) {
        val editor = prefs.edit().clear()
        settings.enginePackage?.let { editor.putString(KEY_ENGINE, it) }
        editor.putFloat(KEY_RATE, settings.speechRate)
        settings.markAsReadOverride?.let { editor.putBoolean(KEY_MARK_AS_READ, it) }
        editor.putString(KEY_PROVIDER, settings.provider.id)
        editor.putString(KEY_GEMINI_VOICE, GeminiTts.normalizeVoice(settings.gemini.voice))
        settings.voices.forEach { (language, name) -> editor.putString(VOICE_PREFIX + language, name) }
        editor.apply()
    }
}
