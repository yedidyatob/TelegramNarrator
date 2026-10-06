package io.github.yedidyatob.telegramnarrator.domain.tts

/**
 * Which engine turns message text into speech. [SYSTEM] (the on-device Android TTS) is the default;
 * the others synthesize an MP3 that is cached on disk and played through the MediaPlayer queue.
 */
enum class SpeechProvider(val id: String) {
    /** On-device Android text-to-speech (default, offline). */
    SYSTEM("system"),

    /** Optional Bring-Your-Own-Key OpenAI TTS. */
    OPENAI("openai"),

    /** Experimental: Microsoft Edge "Read aloud" neural voices (unofficial, free, needs network). */
    EDGE("edge");

    companion object {
        val DEFAULT = SYSTEM

        /** Unknown / missing ids fall back to [DEFAULT] so an old or corrupt preference never breaks playback. */
        fun fromId(id: String?): SpeechProvider = values().firstOrNull { it.id == id } ?: DEFAULT

        /**
         * Reads the stored choice. Builds from the OpenAI PR stored only an `openai_enabled` flag; when no
         * provider id was saved yet, that flag still selects OpenAI.
         */
        fun fromStored(id: String?, legacyOpenAiEnabled: Boolean): SpeechProvider = when {
            id != null -> fromId(id)
            legacyOpenAiEnabled -> OPENAI
            else -> DEFAULT
        }
    }
}
