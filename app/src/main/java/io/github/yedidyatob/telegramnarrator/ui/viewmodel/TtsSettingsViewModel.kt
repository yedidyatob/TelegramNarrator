package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yedidyatob.telegramnarrator.BuildConfig
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.core.audio.TtsManager
import io.github.yedidyatob.telegramnarrator.data.edge.EdgeSpeechSynthesizer
import io.github.yedidyatob.telegramnarrator.data.openai.OpenAiKeyStore
import io.github.yedidyatob.telegramnarrator.data.openai.OpenAiSpeechSynthesizer
import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackManager
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeTts
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeVoiceGender
import io.github.yedidyatob.telegramnarrator.domain.openai.OpenAiTts
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechSynthesisOutcome
import io.github.yedidyatob.telegramnarrator.domain.tts.TestLanguage
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsSettings
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsVoiceLogic
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TtsSettingsViewModel @Inject constructor(
    private val ttsManager: TtsManager,
    private val preferences: TtsPreferences,
    private val openAiKeyStore: OpenAiKeyStore,
    private val edgeSpeech: EdgeSpeechSynthesizer,
    private val openAiSpeech: OpenAiSpeechSynthesizer,
    playbackManager: PlaybackManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(
        stateFrom(preferences.settings.value).copy(testLanguage = TestLanguage.defaultFor(deviceUiLanguage()))
    )
    val state: StateFlow<TtsSettingsUiState> = _state.asStateFlow()

    /** Voice changes and "Test voice" would interrupt the reading, so they are disabled while it plays. */
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying

    /** Whether the system TTS engine finished (re)starting; the sheet calls [reload] when this changes. */
    val ttsReady: StateFlow<Boolean> = ttsManager.isInitialized

    private var testPlayer: MediaPlayer? = null

    /** Re-reads settings (and, once the system engine is ready, its engines and voices); call when the sheet opens. */
    fun reload() {
        val current = _state.value
        _state.value = stateFrom(preferences.settings.value).copy(
            edge = EdgeVoiceUiState.from(preferences.settings.value.edge, testing = current.edge.testing),
            openAi = openAiState(testing = current.openAi.testing),
            testLanguage = current.testLanguage
        )
    }

    private fun deviceUiLanguage(): String? =
        ConfigurationCompat.getLocales(context.resources.configuration).get(0)?.language

    /** Language of the Test voice sample, for every engine. */
    fun setTestLanguage(language: TestLanguage) {
        _state.update { it.copy(testLanguage = language) }
    }

    private fun testSentence(language: TestLanguage): String = context.getString(language.sentenceRes)

    private fun stateFrom(settings: TtsSettings) = TtsSettingsUiState(
        provider = settings.provider,
        system = systemState(settings),
        edge = EdgeVoiceUiState.from(settings.edge),
        openAi = openAiState(testing = false),
        speechRate = settings.speechRate,
        markAsRead = settings.markAsReadEnabled(BuildConfig.DEBUG),
        markAsReadSwitchVisible = TtsSettings.markAsReadSwitchVisible(BuildConfig.DEBUG)
    )

    private fun systemState(settings: TtsSettings): SystemVoiceUiState {
        if (!ttsManager.isInitialized.value) return SystemVoiceUiState(loading = true)
        val deviceLanguages = ConfigurationCompat.getLocales(context.resources.configuration).let { list ->
            (0 until list.size()).mapNotNull { list.get(it)?.language }
        }
        val byLanguage = TtsVoiceLogic.voicesByLanguage(
            ttsManager.availableVoices(),
            TtsVoiceLogic.languagesToShow(deviceLanguages)
        )
        return SystemVoiceUiState(
            loading = false,
            engines = ttsManager.availableEngines(),
            selectedEngine = settings.enginePackage,
            groups = byLanguage.map { (language, voices) ->
                VoiceGroup(
                    language = language,
                    displayName = Locale.forLanguageTag(language).displayLanguage,
                    voices = voices,
                    // Only show a selection that is actually usable
                    selectedVoice = TtsVoiceLogic.chosenVoiceFor(settings, language, voices)?.name
                )
            }
        )
    }

    /** The key is read only to derive "saved" + the masked hint; it is never put into UI state. */
    private fun openAiState(testing: Boolean) =
        OpenAiVoiceUiState.from(preferences.settings.value.openAi, openAiKeyStore.getApiKey(), testing)

    // ---- Engine ---------------------------------------------------------------------------------

    /** System (default) / Edge / OpenAI. */
    fun setProvider(provider: SpeechProvider) {
        if (provider == _state.value.provider) return
        stopTest()
        preferences.update { it.copy(provider = provider) }
        _state.update { it.copy(provider = provider) }
    }

    // ---- System ---------------------------------------------------------------------------------

    /** [enginePackage] null = the system default engine. Restarts TextToSpeech (the System section shows loading). */
    fun selectEngine(enginePackage: String?) {
        preferences.update { TtsVoiceLogic.withEngine(it, enginePackage) }
        ttsManager.refreshSettings()
        reload()
    }

    /** [voiceName] null = back to the engine's default voice for [language]. */
    fun selectVoice(language: String, voiceName: String?) {
        preferences.update { TtsVoiceLogic.withVoice(it, language, voiceName) }
        ttsManager.refreshSettings()
        reload()
    }

    /**
     * Speaks the sample in the selected test language with the system voice chosen for that language and the
     * current speech rate. The button shows progress until the engine starts speaking.
     */
    fun testSystemVoice() {
        val system = _state.value.system
        if (!VoiceSettingsLogic.canTestSystem(isPlaying.value, ttsManager.isInitialized.value, system.testing)) return
        stopTestPlayer()
        val language = _state.value.testLanguage
        val done = { _state.update { it.copy(system = it.system.copy(testing = false)) } }
        _state.update { it.copy(system = it.system.copy(testing = true)) }
        ttsManager.speak(testSentence(language), Locale.forLanguageTag(language.code), onStart = { done() }) { done() }
    }

    // ---- Edge -----------------------------------------------------------------------------------

    fun setEdgeGender(gender: EdgeVoiceGender) {
        preferences.update { it.copy(edge = it.edge.copy(gender = gender)) }
        _state.update { it.copy(edge = it.edge.copy(gender = gender)) }
    }

    /**
     * Advanced: sets a custom Edge voice used for every message (overrides Male / Female). Blank clears it.
     * Returns false (and changes nothing) when [voice] is not a valid Edge short name.
     */
    fun setEdgeCustomVoice(voice: String?): Boolean {
        val trimmed = voice?.trim().orEmpty()
        if (trimmed.isNotEmpty() && !EdgeTts.isValidVoiceName(trimmed)) return false
        val custom = trimmed.ifEmpty { null }
        preferences.update { it.copy(edge = it.edge.copy(customVoice = custom)) }
        _state.update { it.copy(edge = it.edge.copy(customVoice = custom)) }
        return true
    }

    /**
     * Plays the sample in the selected test language with the voice Edge would use for a message in that
     * language: Avri / Hila for Hebrew, the same-gender multilingual voice otherwise, or the custom voice.
     */
    fun testEdgeVoice() {
        val edge = _state.value.edge
        if (!VoiceSettingsLogic.canTestEdge(isPlaying.value, edge.testing)) return
        val language = _state.value.testLanguage
        val voice = EdgeTts.voiceFor(preferences.settings.value.edge, language.code)
        val sentence = testSentence(language)
        _state.update { it.copy(edge = it.edge.copy(testing = true)) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                edgeSpeech.synthesizeForTest(sentence, voice)
            }
            _state.update { it.copy(edge = it.edge.copy(testing = false)) }
            handleTestOutcome(outcome, R.string.settings_edge_test_failed, edgeSpeech::discard)
        }
    }

    // ---- OpenAI ---------------------------------------------------------------------------------

    fun setOpenAiModel(model: String) {
        val normalized = OpenAiTts.normalizeModel(model)
        preferences.update { it.copy(openAi = it.openAi.copy(model = normalized)) }
        _state.update { it.copy(openAi = it.openAi.copy(model = normalized)) }
    }

    fun setOpenAiVoice(voice: String) {
        val normalized = OpenAiTts.normalizeVoice(voice)
        preferences.update { it.copy(openAi = it.openAi.copy(voice = normalized)) }
        _state.update { it.copy(openAi = it.openAi.copy(voice = normalized)) }
    }

    /** Saves the typed API key (encrypted). Never log [key]; it is not kept in the ViewModel. */
    fun saveOpenAiApiKey(key: String) {
        if (key.isBlank()) return
        openAiKeyStore.setApiKey(key)
        _state.update { it.copy(openAi = openAiState(testing = it.openAi.testing)) }
    }

    fun removeOpenAiApiKey() {
        openAiKeyStore.clear()
        _state.update { it.copy(openAi = openAiState(testing = it.openAi.testing)) }
    }

    /**
     * Plays the sample in the selected test language with the selected OpenAI model and voice (OpenAI voices
     * are multilingual). Needs a saved key; billed to it.
     */
    fun testOpenAiVoice() {
        val openAi = _state.value.openAi
        if (!VoiceSettingsLogic.canTestOpenAi(isPlaying.value, openAi.testing, openAi.hasKey)) return
        val sentence = testSentence(_state.value.testLanguage)
        _state.update { it.copy(openAi = it.openAi.copy(testing = true)) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                openAiSpeech.synthesizeForTest(sentence)
            }
            _state.update { it.copy(openAi = it.openAi.copy(testing = false)) }
            handleTestOutcome(outcome, R.string.settings_openai_test_failed, openAiSpeech::discard)
        }
    }

    // ---- Playback -------------------------------------------------------------------------------

    fun setSpeechRate(rate: Float) {
        preferences.update { TtsVoiceLogic.withRate(it, rate) }
        ttsManager.refreshSettings()
        _state.update { it.copy(speechRate = preferences.settings.value.speechRate) }
    }

    /** Debug builds only: release builds have no switch and always mark played messages as read. */
    fun setMarkAsRead(enabled: Boolean) {
        if (!TtsSettings.markAsReadSwitchVisible(BuildConfig.DEBUG)) return
        preferences.update { it.copy(markAsReadOverride = enabled) }
        _state.update { it.copy(markAsRead = preferences.settings.value.markAsReadEnabled(BuildConfig.DEBUG)) }
    }

    // ---- Test playback --------------------------------------------------------------------------

    private fun handleTestOutcome(outcome: SpeechSynthesisOutcome, @StringRes failedMessage: Int, discard: (File) -> Unit) {
        when (outcome) {
            is SpeechSynthesisOutcome.Ready -> playTestFile(outcome, failedMessage, discard)
            is SpeechSynthesisOutcome.Fallback -> toast(failedMessage)
            SpeechSynthesisOutcome.UseSystem -> Unit
        }
    }

    private fun playTestFile(outcome: SpeechSynthesisOutcome.Ready, @StringRes failedMessage: Int, discard: (File) -> Unit) {
        // A late result must not start over the reading that began meanwhile
        if (isPlaying.value) return
        stopTestPlayer()
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(outcome.file.absolutePath)
            player.setOnCompletionListener { stopTestPlayer() }
            player.prepare()
            testPlayer = player
            player.start()
            if (outcome.playbackSpeed != 1f) {
                runCatching { player.playbackParams = player.playbackParams.setSpeed(outcome.playbackSpeed) }
            }
        } catch (e: Exception) {
            player.release()
            if (testPlayer === player) testPlayer = null
            discard(outcome.file)
            toast(failedMessage)
        }
    }

    private fun toast(@StringRes message: Int) =
        Toast.makeText(context, context.getString(message), Toast.LENGTH_LONG).show()

    private fun stopTestPlayer() {
        val player = testPlayer ?: return
        testPlayer = null
        runCatching { player.stop() }
        player.release()
    }

    /** Stops a running test sample (not the reading itself). */
    fun stopTest() {
        stopTestPlayer()
        if (!isPlaying.value) ttsManager.stop()
        // stop() drops the pending onStart / onDone callbacks of a system test sample
        _state.update { it.copy(system = it.system.copy(testing = false)) }
    }

    override fun onCleared() {
        stopTestPlayer()
        super.onCleared()
    }
}
