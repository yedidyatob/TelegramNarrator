package com.example.telegramnarrator.ui.viewmodel

import android.content.Context
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.ViewModel
import com.example.telegramnarrator.BuildConfig
import com.example.telegramnarrator.core.audio.TtsManager
import com.example.telegramnarrator.data.tts.TtsPreferences
import com.example.telegramnarrator.domain.audio.PlaybackManager
import com.example.telegramnarrator.domain.tts.EngineOption
import com.example.telegramnarrator.domain.tts.TtsSettings
import com.example.telegramnarrator.domain.tts.TtsVoiceLogic
import com.example.telegramnarrator.domain.tts.VoiceOption
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject

/** Voices of one language, as shown in the settings sheet. */
data class VoiceGroup(
    val language: String,
    /** Language name in the user's UI language, e.g. "Hebrew". */
    val displayName: String,
    val voices: List<VoiceOption>,
    /** Name of the voice chosen for this language, null = engine default. */
    val selectedVoice: String?
)

data class TtsSettingsUiState(
    val loading: Boolean = true,
    val engines: List<EngineOption> = emptyList(),
    /** Engine in use. */
    val activeEngine: String? = null,
    /** Engine the user picked; null = the system default engine. */
    val selectedEngine: String? = null,
    val speechRate: Float = TtsVoiceLogic.DEFAULT_RATE,
    val groups: List<VoiceGroup> = emptyList(),
    /** Mark played messages as read in Telegram. */
    val markAsRead: Boolean = true
)

@HiltViewModel
class TtsSettingsViewModel @Inject constructor(
    private val ttsManager: TtsManager,
    private val preferences: TtsPreferences,
    playbackManager: PlaybackManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(TtsSettingsUiState())
    val state: StateFlow<TtsSettingsUiState> = _state.asStateFlow()

    /** Voice changes and "Test voice" would interrupt the reading, so they are disabled while it plays. */
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying

    /** (Re)reads the engines and voices from the TTS engine; call when the sheet opens. */
    fun reload() {
        val settings = preferences.settings.value
        if (!ttsManager.isInitialized.value) {
            _state.value = TtsSettingsUiState(loading = true, speechRate = settings.speechRate, markAsRead = markAsReadNow())
            return
        }
        val deviceLanguages = ConfigurationCompat.getLocales(context.resources.configuration).let { list ->
            (0 until list.size()).mapNotNull { list.get(it)?.language }
        }
        val languages = TtsVoiceLogic.languagesToShow(deviceLanguages)
        val byLanguage = TtsVoiceLogic.voicesByLanguage(ttsManager.availableVoices(), languages)
        _state.value = TtsSettingsUiState(
            loading = false,
            engines = ttsManager.availableEngines(),
            activeEngine = ttsManager.activeEngine(),
            selectedEngine = settings.enginePackage,
            speechRate = settings.speechRate,
            markAsRead = markAsReadNow(),
            groups = byLanguage.map { (language, voices) ->
                VoiceGroup(
                    language = language,
                    displayName = Locale.forLanguageTag(language).getDisplayLanguage(),
                    voices = voices,
                    // Only show a selection that is actually usable
                    selectedVoice = TtsVoiceLogic.chosenVoiceFor(settings, language, voices)?.name
                )
            }
        )
    }

    /** Whether the engine finished (re)starting; the sheet calls [reload] when this becomes true. */
    val ttsReady: StateFlow<Boolean> = ttsManager.isInitialized

    private fun markAsReadNow() = preferences.settings.value.markAsReadEnabled(BuildConfig.DEBUG)

    fun setMarkAsRead(enabled: Boolean) {
        preferences.update { it.copy(markAsReadOverride = enabled) }
        _state.value = _state.value.copy(markAsRead = enabled)
    }

    fun setSpeechRate(rate: Float) {
        preferences.update { TtsVoiceLogic.withRate(it, rate) }
        ttsManager.refreshSettings()
        _state.value = _state.value.copy(speechRate = preferences.settings.value.speechRate)
    }

    /** [voiceName] null = back to the engine's default voice for [language]. */
    fun selectVoice(language: String, voiceName: String?) {
        preferences.update { TtsVoiceLogic.withVoice(it, language, voiceName) }
        ttsManager.refreshSettings()
        reload()
    }

    /** [enginePackage] null = the system default engine. Restarts TextToSpeech. */
    fun selectEngine(enginePackage: String?) {
        preferences.update { TtsVoiceLogic.withEngine(it, enginePackage) }
        ttsManager.refreshSettings()
        reload()
    }

    /** Speaks a sample sentence in [language] with the current voice and speech rate. */
    fun testVoice(language: String) {
        if (isPlaying.value || !ttsManager.isInitialized.value) return
        ttsManager.speak(TtsVoiceLogic.testSentence(language), Locale.forLanguageTag(language)) { }
    }

    /** Stops a running test sentence (not the reading itself). */
    fun stopTest() {
        if (!isPlaying.value) ttsManager.stop()
    }
}
