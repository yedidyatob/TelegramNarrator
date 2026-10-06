package io.github.yedidyatob.telegramnarrator.domain.tts

/**
 * Which engine turns message text into speech. [EDGE] is the default for new installs; [SYSTEM] (the on-device
 * Android TTS) stays the engine of installs that existed before that change and never stored a choice, and is the
 * fallback whenever an online engine fails. EDGE and OPENAI synthesize an MP3 that is cached on disk and played
 * through the MediaPlayer queue.
 */
enum class SpeechProvider(val id: String) {
    /** On-device Android text-to-speech (offline; the fallback for the online engines). */
    SYSTEM("system"),

    /** Optional Bring-Your-Own-Key OpenAI TTS. */
    OPENAI("openai"),

    /** Microsoft Edge "Read aloud" neural voices (unofficial, free, needs network). Default for new installs. */
    EDGE("edge");

    companion object {
        /** Engine of a new install (preselected in the onboarding's voice step). */
        val DEFAULT = EDGE

        /**
         * Engine of an install that was updated from a version where no stored choice meant [SYSTEM]: keeps what
         * those users were hearing, and doesn't start sending their messages to Microsoft without them choosing it.
         */
        val UPGRADE_DEFAULT = SYSTEM

        /**
         * Unknown ids (an old or corrupt preference) fall back to the offline [SYSTEM] engine, so they never break
         * playback nor send text to an online service the user didn't pick.
         */
        fun fromId(id: String?): SpeechProvider = values().firstOrNull { it.id == id } ?: SYSTEM

        /** The engine when none is stored: [DEFAULT] on a fresh install, [UPGRADE_DEFAULT] after an update. */
        fun defaultFor(freshInstall: Boolean): SpeechProvider = if (freshInstall) DEFAULT else UPGRADE_DEFAULT

        /**
         * Reads the stored choice. Builds from the OpenAI PR stored only an `openai_enabled` flag; when no
         * provider id was saved yet, that flag still selects OpenAI. Otherwise see [defaultFor].
         */
        fun fromStored(id: String?, legacyOpenAiEnabled: Boolean, freshInstall: Boolean): SpeechProvider = when {
            id != null -> fromId(id)
            legacyOpenAiEnabled -> OPENAI
            else -> defaultFor(freshInstall)
        }
    }
}
