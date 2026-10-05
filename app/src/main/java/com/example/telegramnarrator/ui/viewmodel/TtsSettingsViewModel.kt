package com.example.telegramnarrator.ui.viewmodel

import android.content.Context
import androidx.core.os.ConfigurationCompat
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.telegramnarrator.BuildConfig
import com.example.telegramnarrator.R
import com.example.telegramnarrator.core.audio.TtsManager
import com.example.telegramnarrator.data.edge.EdgeSpeechSynthesizer
import com.example.telegramnarrator.data.openai.OpenAiKeyStore
import com.example.telegramnarrator.data.tts.TtsPreferences
import com.example.telegramnarrator.domain.edge.EdgeTts
import com.example.telegramnarrator.domain.openai.OpenAiTts
import com.example.telegramnarrator.domain.tts.SpeechProvider
import com.example.telegramnarrator.domain.tts.SpeechSynthesisOutcome
import com.example.telegramnarrator.domain.audio.PlaybackManager
import com.example.telegramnarrator.domain.tts.EngineOption
import com.example.telegramnarrator.domain.tts.TtsSettings
import com.example.telegramnarrator.domain.tts.TtsVoiceLogic
import com.example.telegramnarrator.domain.tts.VoiceOption
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val markAsRead: Boolean = true,
    /** Engine that speaks messages (system TTS is the default). */
    val provider: SpeechProvider = SpeechProvider.DEFAULT,
    /** Bring-Your-Own-Key OpenAI TTS (optional; system TTS remains default). */
    val openAiModel: String = OpenAiTts.DEFAULT_MODEL,
    val openAiVoice: String = OpenAiTts.DEFAULT_VOICE,
    /** Whether a key is stored (never expose the key itself in UI state). */
    val openAiHasKey: Boolean = false,
    /** Masked preview for the key field (empty when none). */
    val openAiKeyMasked: String = "",
    /** Experimental Edge TTS voice short name. */
    val edgeVoice: String = EdgeTts.DEFAULT_VOICE,
    /** True while the Edge "Test voice" sample is being fetched. */
    val edgeTesting: Boolean = false
) {
    val openAiEnabled: Boolean get() = provider == SpeechProvider.OPENAI
    val edgeEnabled: Boolean get() = provider == SpeechProvider.EDGE
}

@HiltViewModel
class TtsSettingsViewModel @Inject constructor(
    private val ttsManager: TtsManager,
    private val preferences: TtsPreferences,
    private val openAiKeyStore: OpenAiKeyStore,
    private val edgeSpeech: EdgeSpeechSynthesizer,
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
            _state.value = TtsSettingsUiState(
                loading = true,
                speechRate = settings.speechRate,
                markAsRead = markAsReadNow(),
                provider = settings.provider,
                edgeVoice = settings.edge.voice,
                openAiModel = settings.openAi.model,
                openAiVoice = settings.openAi.voice,
                openAiHasKey = openAiKeyStore.hasApiKey(),
                openAiKeyMasked = maskKey(openAiKeyStore.getApiKey())
            )
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
            provider = settings.provider,
            edgeVoice = settings.edge.voice,
            openAiModel = settings.openAi.model,
            openAiVoice = settings.openAi.voice,
            openAiHasKey = openAiKeyStore.hasApiKey(),
            openAiKeyMasked = maskKey(openAiKeyStore.getApiKey()),
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

    private fun maskKey(key: String?): String {
        if (key.isNullOrBlank()) return ""
        if (key.length <= 8) return "••••"
        return key.take(3) + "…" + key.takeLast(4)
    }

    /** System (default) / OpenAI (BYOK) / Edge (experimental). */
    fun setProvider(provider: SpeechProvider) {
        preferences.update { it.copy(provider = provider) }
        _state.value = _state.value.copy(provider = provider)
    }

    /** Selects an Edge voice; returns false (and changes nothing) when [voice] is not a valid Edge short name. */
    fun setEdgeVoice(voice: String): Boolean {
        if (!EdgeTts.isValidVoiceName(voice)) return false
        val normalized = EdgeTts.normalizeVoice(voice)
        preferences.update { it.copy(edge = it.edge.copy(voice = normalized)) }
        _state.value = _state.value.copy(edgeVoice = normalized)
        return true
    }

    private var testPlayer: MediaPlayer? = null

    /** Fetches (or reuses the cached) Edge sample for the selected voice and plays it. */
    fun testEdgeVoice() {
        if (isPlaying.value || _state.value.edgeTesting) return
        val voice = _state.value.edgeVoice
        val language = if (voice.startsWith("he-")) "he" else Locale.forLanguageTag(voice).language
        _state.value = _state.value.copy(edgeTesting = true)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                edgeSpeech.synthesizeForTest(TtsVoiceLogic.testSentence(language), voice)
            }
            _state.value = _state.value.copy(edgeTesting = false)
            when (outcome) {
                is SpeechSynthesisOutcome.Ready -> playTestFile(outcome)
                is SpeechSynthesisOutcome.Fallback ->
                    Toast.makeText(context, context.getString(R.string.settings_edge_test_failed), Toast.LENGTH_LONG).show()
                SpeechSynthesisOutcome.UseSystem -> Unit
            }
        }
    }

    private fun playTestFile(outcome: SpeechSynthesisOutcome.Ready) {
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
            edgeSpeech.discard(outcome.file)
            Toast.makeText(context, context.getString(R.string.settings_edge_test_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun stopTestPlayer() {
        val player = testPlayer ?: return
        testPlayer = null
        runCatching { player.stop() }
        player.release()
    }

    override fun onCleared() {
        stopTestPlayer()
        super.onCleared()
    }

    fun setOpenAiModel(model: String) {
        val normalized = OpenAiTts.normalizeModel(model)
        preferences.update { it.copy(openAi = it.openAi.copy(model = normalized)) }
        _state.value = _state.value.copy(openAiModel = normalized)
    }

    fun setOpenAiVoice(voice: String) {
        val normalized = OpenAiTts.normalizeVoice(voice)
        preferences.update { it.copy(openAi = it.openAi.copy(voice = normalized)) }
        _state.value = _state.value.copy(openAiVoice = normalized)
    }

    /** Saves the pasted API key (encrypted). Pass blank to clear. Never log [key]. */
    fun setOpenAiApiKey(key: String) {
        openAiKeyStore.setApiKey(key)
        _state.value = _state.value.copy(
            openAiHasKey = openAiKeyStore.hasApiKey(),
            openAiKeyMasked = maskKey(openAiKeyStore.getApiKey())
        )
    }

    fun clearOpenAiApiKey() {
        openAiKeyStore.clear()
        _state.value = _state.value.copy(openAiHasKey = false, openAiKeyMasked = "")
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
        stopTestPlayer()
        if (!isPlaying.value) ttsManager.stop()
    }
}
