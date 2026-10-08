package io.github.yedidyatob.telegramnarrator.domain.gemini

import java.security.MessageDigest

/**
 * Pure helpers for the optional Bring-Your-Own-Key Gemini TTS engine.
 * Edge TTS stays the default until the user selects Gemini as the engine and pastes a key.
 */
object GeminiTts {
    /** Where users create a key ("Get an API key" link in Voice settings). */
    const val API_KEYS_URL = "https://aistudio.google.com/apikey"
    
    const val MODEL_ID = "gemini-2.5-flash-preview-tts"
    const val DEFAULT_VOICE = "Kore"

    /** Voices supported by Gemini 2.0 Flash TTS. */
    val VOICES: List<String> = listOf(
        "Kore", "Charon", "Puck", "Aoede", "Fenrir", "Leda", "Orus", "Zephyr"
    )

    /** 
     * Gemini input limit. The API limit is 8,192 tokens; we use 4,000 characters 
     * to stay well under that limit even with long tokens.
     */
    const val MAX_INPUT_CHARS = 4000

    /** Fixed narration instruction sent with every request. */
    const val NARRATION_INSTRUCTION = 
        "Read this aloud as clear narration. Follow the language of the text, including Hebrew and mixed Hebrew/English. Do not translate."

    fun normalizeVoice(voice: String?): String =
        if (voice != null && VOICES.contains(voice)) voice else DEFAULT_VOICE

    /**
     * Stable cache key: SHA-256 hex of model + voice + text.
     * Same message with the same voice reuses the cached audio file.
     */
    fun cacheKey(text: String, voice: String?): String {
        val payload = "$MODEL_ID\u0000${normalizeVoice(voice)}\u0000$text"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun isWithinApiLimit(text: String): Boolean = text.length <= MAX_INPUT_CHARS

    /** Keys shorter than this are shown fully masked so the hint can never reveal most of the key. */
    private const val MIN_KEY_LENGTH_FOR_HINT = 16

    /**
     * Masked preview of a saved key for the "Saved: AI…abcd" indicator: first 3 + last 4 characters of a
     * real-length key, "••••" for short ones, null when there is no key. Never the key itself.
     */
    fun maskKey(key: String?): String? {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.length < MIN_KEY_LENGTH_FOR_HINT) return "••••"
        return trimmed.take(3) + "…" + trimmed.takeLast(4)
    }

    private val ERROR_FIELD = Regex(""""(code|message)"\s*:\s*"([^"]{1,128})"""")

    /**
     * Loggable summary of a Gemini error response: only machine-readable error codes/messages
     * (truncated to avoid logging user data).
     */
    fun safeErrorSummary(body: String?): String =
        ERROR_FIELD.findAll(body.orEmpty())
            .map { it.groupValues[2] }
            .distinct()
            .joinToString("/")
}

/** Non-secret Gemini TTS choices persisted with the rest of [io.github.yedidyatob.telegramnarrator.domain.tts.TtsSettings]. */
data class GeminiTtsOptions(
    val voice: String = GeminiTts.DEFAULT_VOICE
)
