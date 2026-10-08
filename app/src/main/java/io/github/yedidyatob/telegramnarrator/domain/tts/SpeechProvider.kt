package io.github.yedidyatob.telegramnarrator.domain.tts

/**
 * Which engine turns message text into speech. [EDGE] is the default for new installs; [SYSTEM] (the on-device
 * Android TTS) stays the engine of installs that existed before that change and never stored a choice, and is the
 * fallback whenever an online engine fails. EDGE and GEMINI synthesize audio that is cached on disk and played
 * through the MediaPlayer queue.
 */
enum class SpeechProvider(val id: String) {
    /** On-device Android text-to-speech (offline; the fallback for the online engines). */
    SYSTEM("system"),

    /** Optional Bring-Your-Own-Key Gemini TTS. */
    GEMINI("gemini"),

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
        fun fromIdOrNull(id: String?): SpeechProvider? = entries.firstOrNull { it.id == id }

        fun fromId(id: String?): SpeechProvider = fromIdOrNull(id) ?: SYSTEM

        /** The engine when none is stored: [DEFAULT] on a fresh install, [UPGRADE_DEFAULT] after an update. */
        fun defaultFor(freshInstall: Boolean): SpeechProvider = if (freshInstall) DEFAULT else UPGRADE_DEFAULT

        /**
         * Reads the stored choice. Legacy builds stored "openai" or an `openai_enabled` flag; both are migrated
         * to [GEMINI] so existing BYOK users keep the paid-engine slot. Unknown ids fall back to [SYSTEM].
         */
        fun fromStored(id: String?, legacyOpenAiEnabled: Boolean, freshInstall: Boolean): SpeechProvider {
            if (id == "openai") return GEMINI
            val known = fromIdOrNull(id)
            if (known != null) return known
            if (legacyOpenAiEnabled) return GEMINI
            return defaultFor(freshInstall)
        }
    }
}
