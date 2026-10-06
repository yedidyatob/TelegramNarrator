package com.example.telegramnarrator.ui.screens.settings

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.telegramnarrator.domain.edge.EdgeVoiceGender
import com.example.telegramnarrator.domain.openai.OpenAiTts
import com.example.telegramnarrator.domain.tts.EngineOption
import com.example.telegramnarrator.domain.tts.SpeechProvider
import com.example.telegramnarrator.domain.tts.VoiceOption
import com.example.telegramnarrator.ui.theme.TelegramNarratorTheme
import com.example.telegramnarrator.ui.viewmodel.EdgeVoiceUiState
import com.example.telegramnarrator.ui.viewmodel.OpenAiVoiceUiState
import com.example.telegramnarrator.ui.viewmodel.SystemVoiceUiState
import com.example.telegramnarrator.ui.viewmodel.TtsSettingsUiState
import com.example.telegramnarrator.ui.viewmodel.VoiceGroup

private val previewSystem = SystemVoiceUiState(
    loading = false,
    engines = listOf(EngineOption("com.google.android.tts", "Speech Services by Google")),
    selectedEngine = null,
    groups = listOf(
        VoiceGroup(
            language = "he",
            displayName = "Hebrew",
            voices = listOf(VoiceOption("he-il-x-heb-local", "he-IL", "he", 400, requiresNetwork = false, installed = true)),
            selectedVoice = "he-il-x-heb-local"
        ),
        VoiceGroup(
            language = "en",
            displayName = "English",
            voices = listOf(VoiceOption("en-us-x-iol-local", "en-US", "en", 400, requiresNetwork = false, installed = true)),
            selectedVoice = null
        )
    )
)

@Composable
private fun PreviewSheet(state: TtsSettingsUiState, darkTheme: Boolean = false) {
    TelegramNarratorTheme(darkTheme = darkTheme) {
        Surface {
            VoiceSettingsContent(state = state, isPlaying = false, actions = VoiceSettingsActions())
        }
    }
}

@Preview(name = "System", showBackground = true, heightDp = 900)
@Composable
private fun VoiceSettingsSystemPreview() {
    PreviewSheet(TtsSettingsUiState(provider = SpeechProvider.SYSTEM, system = previewSystem))
}

@Preview(name = "System (engine starting)", showBackground = true, heightDp = 700)
@Composable
private fun VoiceSettingsSystemLoadingPreview() {
    PreviewSheet(TtsSettingsUiState(provider = SpeechProvider.SYSTEM, system = SystemVoiceUiState(loading = true)))
}

@Preview(name = "Edge", showBackground = true, heightDp = 900)
@Composable
private fun VoiceSettingsEdgePreview() {
    PreviewSheet(
        TtsSettingsUiState(
            provider = SpeechProvider.EDGE,
            system = SystemVoiceUiState(loading = true), // never shown for Edge
            edge = EdgeVoiceUiState(gender = EdgeVoiceGender.FEMALE)
        )
    )
}

@Preview(name = "Edge + custom voice", showBackground = true, heightDp = 1000)
@Composable
private fun VoiceSettingsEdgeCustomPreview() {
    PreviewSheet(
        TtsSettingsUiState(
            provider = SpeechProvider.EDGE,
            edge = EdgeVoiceUiState(gender = EdgeVoiceGender.MALE, customVoice = "en-GB-SoniaNeural")
        )
    )
}

@Preview(name = "OpenAI", showBackground = true, heightDp = 1000)
@Composable
private fun VoiceSettingsOpenAiPreview() {
    PreviewSheet(
        TtsSettingsUiState(
            provider = SpeechProvider.OPENAI,
            openAi = OpenAiVoiceUiState(model = OpenAiTts.MODEL_TTS_1, voice = "nova", hasKey = true, keyHint = "sk-…abcd")
        )
    )
}

@Preview(
    name = "OpenAI dark (no key)",
    showBackground = true,
    heightDp = 1000,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun VoiceSettingsOpenAiDarkPreview() {
    PreviewSheet(
        TtsSettingsUiState(provider = SpeechProvider.OPENAI, openAi = OpenAiVoiceUiState(model = OpenAiTts.MODEL_TTS_1_HD)),
        darkTheme = true
    )
}
