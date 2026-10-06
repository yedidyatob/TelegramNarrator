package io.github.yedidyatob.telegramnarrator.data.tts

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeTts
import io.github.yedidyatob.telegramnarrator.domain.openai.OpenAiTts
import io.github.yedidyatob.telegramnarrator.domain.openai.OpenAiTtsOptions
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
 * No stored engine: a fresh install starts on [SpeechProvider.DEFAULT] (Edge); an install updated from a version
 * where "nothing stored" meant the system voice keeps [SpeechProvider.UPGRADE_DEFAULT] (System). The resolved
 * engine is saved right away, so a later update never changes it.
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
        const val KEY_OPENAI_MODEL = "openai_model"
        const val KEY_OPENAI_VOICE = "openai_voice"
        const val KEY_EDGE_GENDER = "edge_gender"
        const val KEY_EDGE_CUSTOM_VOICE = "edge_custom_voice"
        /** Legacy (before the voice-settings redesign): one Edge voice name; migrated on read, no longer written. */
        const val KEY_EDGE_VOICE_LEGACY = "edge_voice"
        const val VOICE_PREFIX = "voice."
    }

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    private val _settings = MutableStateFlow(read().also(::persistResolvedProvider))
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
                legacyOpenAiEnabled = prefs.getBoolean(KEY_OPENAI_ENABLED, false),
                freshInstall = !prefs.contains(KEY_PROVIDER) && isFreshInstall()
            ),
            openAi = OpenAiTtsOptions(
                model = OpenAiTts.normalizeModel(prefs.getString(KEY_OPENAI_MODEL, OpenAiTts.DEFAULT_MODEL)),
                voice = OpenAiTts.normalizeVoice(prefs.getString(KEY_OPENAI_VOICE, OpenAiTts.DEFAULT_VOICE))
            ),
            edge = EdgeTts.optionsFromStored(
                gender = prefs.getString(KEY_EDGE_GENDER, null),
                customVoice = prefs.getString(KEY_EDGE_CUSTOM_VOICE, null),
                legacyVoice = prefs.getString(KEY_EDGE_VOICE_LEGACY, null)
            )
        )
    }

    /** Saves the engine chosen by [SpeechProvider.fromStored] when none was stored (see the class doc). */
    private fun persistResolvedProvider(settings: TtsSettings) {
        if (!prefs.contains(KEY_PROVIDER)) prefs.edit().putString(KEY_PROVIDER, settings.provider.id).apply()
    }

    /**
     * True when this version was installed fresh rather than updated over an older one: the package manager then
     * reports the same first-install and last-update time. Independent of what else already ran on first launch.
     */
    private fun isFreshInstall(): Boolean = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }
        info.firstInstallTime == info.lastUpdateTime
    } catch (e: PackageManager.NameNotFoundException) {
        // Can't tell: keep the offline system voice rather than sending text to an online service unasked
        Log.w("TtsPreferences", "Package info unavailable", e)
        false
    }

    private fun write(settings: TtsSettings) {
        val editor = prefs.edit().clear()
        settings.enginePackage?.let { editor.putString(KEY_ENGINE, it) }
        editor.putFloat(KEY_RATE, settings.speechRate)
        settings.markAsReadOverride?.let { editor.putBoolean(KEY_MARK_AS_READ, it) }
        editor.putString(KEY_PROVIDER, settings.provider.id)
        editor.putString(KEY_OPENAI_MODEL, OpenAiTts.normalizeModel(settings.openAi.model))
        editor.putString(KEY_OPENAI_VOICE, OpenAiTts.normalizeVoice(settings.openAi.voice))
        editor.putString(KEY_EDGE_GENDER, settings.edge.gender.id)
        settings.edge.customVoice?.let { editor.putString(KEY_EDGE_CUSTOM_VOICE, it) }
        settings.voices.forEach { (language, name) -> editor.putString(VOICE_PREFIX + language, name) }
        editor.apply()
    }
}
