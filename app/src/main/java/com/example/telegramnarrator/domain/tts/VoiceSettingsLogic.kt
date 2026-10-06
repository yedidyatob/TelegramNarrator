package com.example.telegramnarrator.domain.tts

/** Sections of the Voice settings sheet, top to bottom. */
enum class VoiceSettingsSection {
    /** Engine picker (always shown). */
    ENGINE,

    /** System TTS: speech engine + one voice per language (only for [SpeechProvider.SYSTEM]). */
    SYSTEM_VOICES,

    /** Edge: Male / Female, Advanced custom voice (only for [SpeechProvider.EDGE]). */
    EDGE_VOICE,

    /** OpenAI: key, quality, voice (only for [SpeechProvider.OPENAI]). */
    OPENAI_VOICE,

    /** Speech rate + mark-as-read (always shown). */
    PLAYBACK
}

/** Pure rules behind the Voice settings sheet layout (unit tested, no Android dependencies). */
object VoiceSettingsLogic {

    /** Engine picker, then only the selected engine's own section, then Playback. */
    fun visibleSections(provider: SpeechProvider): List<VoiceSettingsSection> = listOf(
        VoiceSettingsSection.ENGINE,
        when (provider) {
            SpeechProvider.SYSTEM -> VoiceSettingsSection.SYSTEM_VOICES
            SpeechProvider.EDGE -> VoiceSettingsSection.EDGE_VOICE
            SpeechProvider.OPENAI -> VoiceSettingsSection.OPENAI_VOICE
        },
        VoiceSettingsSection.PLAYBACK
    )

    /**
     * Whether the System section shows its loading row instead of the pickers. Only that section waits for
     * the system TTS engine; the rest of the sheet works immediately.
     */
    fun systemSectionLoading(provider: SpeechProvider, systemTtsReady: Boolean): Boolean =
        provider == SpeechProvider.SYSTEM && !systemTtsReady

    /**
     * "Test voice" for System TTS needs an initialized engine and no sample still waiting to start; tests never
     * interrupt the reading.
     */
    fun canTestSystem(isPlaying: Boolean, systemTtsReady: Boolean, testing: Boolean = false): Boolean =
        !isPlaying && systemTtsReady && !testing

    /** "Test voice" for Edge: not while reading, not while a sample is already being fetched. */
    fun canTestEdge(isPlaying: Boolean, testing: Boolean): Boolean = !isPlaying && !testing

    /** "Test voice" for OpenAI additionally needs a saved key (the request is billed to it). */
    fun canTestOpenAi(isPlaying: Boolean, testing: Boolean, hasKey: Boolean): Boolean =
        !isPlaying && !testing && hasKey

    /** Male / Female is ignored (and the toggle disabled) while an Advanced custom Edge voice is set. */
    fun edgeGenderEnabled(isPlaying: Boolean, customVoice: String?): Boolean = !isPlaying && customVoice == null
}
