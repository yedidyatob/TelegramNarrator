package io.github.yedidyatob.telegramnarrator.ui.screens.settings

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTts
import io.github.yedidyatob.telegramnarrator.domain.tts.EngineOption
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceOption
import io.github.yedidyatob.telegramnarrator.ui.theme.TelegramNarratorTheme
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.GeminiVoiceUiState
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.SystemVoiceUiState
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.TtsSettingsUiState
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.VoiceGroup

private val previewSystem =
    SystemVoiceUiState(
        loading = false,
        engines = listOf(EngineOption("com.google.android.tts", "Speech Services by Google")),
        selectedEngine = null,
        groups =
            listOf(
                VoiceGroup(
                    language = "he",
                    displayName = "Hebrew",
                    voices = listOf(VoiceOption("he-il-x-heb-local", "he-IL", "he", 400, requiresNetwork = false, installed = true)),
                    selectedVoice = "he-il-x-heb-local",
                ),
                VoiceGroup(
                    language = "en",
                    displayName = "English",
                    voices = listOf(VoiceOption("en-us-x-iol-local", "en-US", "en", 400, requiresNetwork = false, installed = true)),
                    selectedVoice = null,
                ),
            ),
    )

@Composable
private fun PreviewSheet(
    state: TtsSettingsUiState,
    darkTheme: Boolean = false,
) {
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

@Preview(name = "System (debug build: mark-as-read switch)", showBackground = true, heightDp = 900)
@Composable
private fun VoiceSettingsDebugMarkAsReadPreview() {
    PreviewSheet(
        TtsSettingsUiState(
            provider = SpeechProvider.SYSTEM,
            system = previewSystem,
            markAsRead = false,
            markAsReadSwitchVisible = true,
        ),
    )
}

@Preview(name = "System (engine starting)", showBackground = true, heightDp = 700)
@Composable
private fun VoiceSettingsSystemLoadingPreview() {
    PreviewSheet(TtsSettingsUiState(provider = SpeechProvider.SYSTEM, system = SystemVoiceUiState(loading = true)))
}

@Preview(name = "Gemini", showBackground = true, heightDp = 1000)
@Composable
private fun VoiceSettingsGeminiPreview() {
    PreviewSheet(
        TtsSettingsUiState(
            provider = SpeechProvider.GEMINI,
            gemini = GeminiVoiceUiState(voice = GeminiTts.DEFAULT_VOICE, hasKey = true, keyHint = "AIza…abcd"),
        ),
    )
}

@Preview(
    name = "Gemini dark (no key)",
    showBackground = true,
    heightDp = 1000,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun VoiceSettingsGeminiDarkPreview() {
    PreviewSheet(
        TtsSettingsUiState(provider = SpeechProvider.GEMINI, gemini = GeminiVoiceUiState(voice = GeminiTts.DEFAULT_VOICE)),
        darkTheme = true,
    )
}
