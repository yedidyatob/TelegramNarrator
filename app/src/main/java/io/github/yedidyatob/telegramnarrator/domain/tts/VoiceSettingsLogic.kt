package io.github.yedidyatob.telegramnarrator.domain.tts

/** Sections of the Voice settings sheet, top to bottom. */
enum class VoiceSettingsSection {
    /** Engine picker (always shown). */
    ENGINE,

    /** System TTS: speech engine + one voice per language (only for [SpeechProvider.SYSTEM]). */
    SYSTEM_VOICES,

    /** Gemini: key, voice (only for [SpeechProvider.GEMINI]). */
    GEMINI_VOICE,

    /** Speech rate (always shown) + the mark-as-read switch (debug builds only). */
    PLAYBACK
}

/** Pure rules behind the Voice settings sheet layout (unit tested, no Android dependencies). */
object VoiceSettingsLogic {

    /** Engine picker, then only the selected engine's own section, then Playback. */
    fun visibleSections(provider: SpeechProvider): List<VoiceSettingsSection> = listOf(
        VoiceSettingsSection.ENGINE,
        when (provider) {
            SpeechProvider.SYSTEM -> VoiceSettingsSection.SYSTEM_VOICES
            SpeechProvider.GEMINI -> VoiceSettingsSection.GEMINI_VOICE
        },
        VoiceSettingsSection.PLAYBACK
    )

    /**
     * "Test voice" for System TTS needs an initialized engine and no sample still waiting to start; tests never
     * interrupt the reading.
     */
    fun canTestSystem(isPlaying: Boolean, systemTtsReady: Boolean, testing: Boolean = false): Boolean =
        !isPlaying && systemTtsReady && !testing

    /** "Test voice" for Gemini additionally needs a saved key (the request is billed to it). */
    fun canTestGemini(isPlaying: Boolean, testing: Boolean, hasKey: Boolean): Boolean =
        !isPlaying && !testing && hasKey
}
