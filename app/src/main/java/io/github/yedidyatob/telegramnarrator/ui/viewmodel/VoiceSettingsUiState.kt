package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeTtsOptions
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeVoiceGender
import io.github.yedidyatob.telegramnarrator.domain.openai.OpenAiTts
import io.github.yedidyatob.telegramnarrator.domain.openai.OpenAiTtsOptions
import io.github.yedidyatob.telegramnarrator.domain.tts.EngineOption
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsVoiceLogic
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceOption
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsLogic
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsSection

/** Voices of one language, as shown in the System section. */
data class VoiceGroup(
    /** Normalized language code ("he", "en", ...). */
    val language: String,
    /** Language name in the user's UI language, e.g. "Hebrew". */
    val displayName: String,
    val voices: List<VoiceOption>,
    /** Name of the voice chosen for this language, null = engine default. */
    val selectedVoice: String?
)

/** System TTS section. [loading] is true while the system engine (re)starts; only this section waits for it. */
data class SystemVoiceUiState(
    val loading: Boolean = true,
    val engines: List<EngineOption> = emptyList(),
    /** Engine the user picked; null = the system default engine. */
    val selectedEngine: String? = null,
    val groups: List<VoiceGroup> = emptyList()
)

/** Edge section. A non-null [customVoice] (Advanced) overrides [gender]. */
data class EdgeVoiceUiState(
    val gender: EdgeVoiceGender = EdgeVoiceGender.DEFAULT,
    val customVoice: String? = null,
    /** True while the Test voice sample is being fetched. */
    val testing: Boolean = false
) {
    companion object {
        fun from(options: EdgeTtsOptions, testing: Boolean = false) =
            EdgeVoiceUiState(gender = options.gender, customVoice = options.customVoice, testing = testing)
    }
}

/**
 * OpenAI section. Holds only whether a key is saved and its masked hint: the raw API key never enters UI
 * state (it stays in OpenAiKeyStore; the text the user is typing lives only in the key field).
 */
data class OpenAiVoiceUiState(
    val model: String = OpenAiTts.DEFAULT_MODEL,
    val voice: String = OpenAiTts.DEFAULT_VOICE,
    val hasKey: Boolean = false,
    /** "sk-…abcd" style hint of the saved key, null when no key is saved. */
    val keyHint: String? = null,
    /** True while the Test voice sample is being synthesized. */
    val testing: Boolean = false
) {
    companion object {
        /** [apiKey] is only used to derive [hasKey] / [keyHint]; it is not stored. */
        fun from(options: OpenAiTtsOptions, apiKey: String?, testing: Boolean = false): OpenAiVoiceUiState {
            val hint = OpenAiTts.maskKey(apiKey)
            return OpenAiVoiceUiState(
                model = OpenAiTts.normalizeModel(options.model),
                voice = OpenAiTts.normalizeVoice(options.voice),
                hasKey = hint != null,
                keyHint = hint,
                testing = testing
            )
        }
    }
}

data class TtsSettingsUiState(
    /** Engine that speaks messages (system TTS is the default). */
    val provider: SpeechProvider = SpeechProvider.DEFAULT,
    val system: SystemVoiceUiState = SystemVoiceUiState(),
    val edge: EdgeVoiceUiState = EdgeVoiceUiState(),
    val openAi: OpenAiVoiceUiState = OpenAiVoiceUiState(),
    val speechRate: Float = TtsVoiceLogic.DEFAULT_RATE,
    /** Mark played messages as read in Telegram. */
    val markAsRead: Boolean = true
) {
    val sections: List<VoiceSettingsSection> get() = VoiceSettingsLogic.visibleSections(provider)
}
