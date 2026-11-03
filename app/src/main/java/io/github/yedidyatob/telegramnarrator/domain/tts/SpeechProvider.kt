package io.github.yedidyatob.telegramnarrator.domain.tts

/**
 * Which engine turns message text into speech. [SYSTEM] (the on-device Android TTS) is the default and the
 * fallback whenever Gemini fails. GEMINI synthesizes audio that is cached on disk and played through the
 * MediaPlayer queue.
 */
enum class SpeechProvider(
    val id: String,
) {
    /** On-device Android text-to-speech (offline; the default and the fallback for Gemini). */
    SYSTEM("system"),

    /** Optional Bring-Your-Own-Key Gemini TTS. */
    GEMINI("gemini"),
    ;

    companion object {
        /** Engine when none is stored, and of every unknown id. */
        val DEFAULT = SYSTEM

        fun fromId(id: String?): SpeechProvider = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
