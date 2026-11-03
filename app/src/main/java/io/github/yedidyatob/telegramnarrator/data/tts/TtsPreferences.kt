package io.github.yedidyatob.telegramnarrator.data.tts

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTts
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTtsOptions
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsSettings
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsVoiceLogic
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
 */
@Singleton
class TtsPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private companion object {
            const val FILE = "tts_settings"
            const val KEY_ENGINE = "engine"
            const val KEY_RATE = "rate"
            const val KEY_MARK_AS_READ = "mark_as_read"
            const val KEY_PROVIDER = "provider"
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
            val voices =
                prefs.all
                    .filterKeys { it.startsWith(VOICE_PREFIX) }
                    .mapNotNull { (key, value) -> (value as? String)?.let { key.removePrefix(VOICE_PREFIX) to it } }
                    .toMap()
            return TtsSettings(
                enginePackage = prefs.getString(KEY_ENGINE, null),
                speechRate = TtsVoiceLogic.clampRate(prefs.getFloat(KEY_RATE, TtsVoiceLogic.DEFAULT_RATE)),
                voices = voices,
                markAsReadOverride = if (prefs.contains(KEY_MARK_AS_READ)) prefs.getBoolean(KEY_MARK_AS_READ, true) else null,
                provider = SpeechProvider.fromId(prefs.getString(KEY_PROVIDER, null)),
                gemini =
                    GeminiTtsOptions(
                        voice = GeminiTts.normalizeVoice(prefs.getString(KEY_GEMINI_VOICE, GeminiTts.DEFAULT_VOICE)),
                    ),
            )
        }

        private fun write(settings: TtsSettings) {
            prefs.edit {
                clear()
                settings.enginePackage?.let { putString(KEY_ENGINE, it) }
                putFloat(KEY_RATE, settings.speechRate)
                settings.markAsReadOverride?.let { putBoolean(KEY_MARK_AS_READ, it) }
                putString(KEY_PROVIDER, settings.provider.id)
                putString(KEY_GEMINI_VOICE, GeminiTts.normalizeVoice(settings.gemini.voice))
                settings.voices.forEach { (language, name) -> putString(VOICE_PREFIX + language, name) }
            }
        }
    }
