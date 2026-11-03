package io.github.yedidyatob.telegramnarrator.domain.tts

/**
 * Which engine turns message text into speech. [SYSTEM] (the on-device Android TTS) is the default and the
 * fallback whenever Gemini fails. GEMINI synthesizes audio that is cached on disk and played through the
 * MediaPlayer queue.
 */
enum class SpeechProvider(val id: String) {
    /** On-device Android text-to-speech (offline; the default and the fallback for Gemini). */
    SYSTEM("system"),

    /** Optional Bring-Your-Own-Key Gemini TTS. */
    GEMINI("gemini");

    companion object {
        /** Engine when none is stored, and of every unknown or removed id (e.g. the former "edge" engine). */
        val DEFAULT = SYSTEM

        fun fromIdOrNull(id: String?): SpeechProvider? = entries.firstOrNull { it.id == id }

        fun fromId(id: String?): SpeechProvider = fromIdOrNull(id) ?: DEFAULT

        /**
         * Reads the stored choice. Legacy builds stored "openai" or an `openai_enabled` flag; both are migrated
         * to [GEMINI] so existing BYOK users keep the paid-engine slot. Unknown ids (including the removed
         * "edge") fall back to [SYSTEM].
         */
        fun fromStored(id: String?, legacyOpenAiEnabled: Boolean): SpeechProvider {
            if (id == "openai") return GEMINI
            fromIdOrNull(id)?.let { return it }
            if (legacyOpenAiEnabled) return GEMINI
            return DEFAULT
        }
    }
}
