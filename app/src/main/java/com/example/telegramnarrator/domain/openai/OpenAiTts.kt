package com.example.telegramnarrator.domain.openai

import java.security.MessageDigest

/**
 * Pure helpers for the optional Bring-Your-Own-Key OpenAI TTS engine.
 * System TTS stays the default until the user selects OpenAI as the engine and pastes a key.
 */
object OpenAiTts {
    const val MODEL_TTS_1 = "tts-1"
    const val MODEL_TTS_1_HD = "tts-1-hd"
    const val DEFAULT_MODEL = MODEL_TTS_1
    const val DEFAULT_VOICE = "alloy"

    /** Voices supported by tts-1 / tts-1-hd (see OpenAI text-to-speech guide). */
    val VOICES: List<String> = listOf(
        "alloy", "ash", "coral", "echo", "fable", "onyx", "nova", "sage", "shimmer"
    )

    val MODELS: List<String> = listOf(MODEL_TTS_1, MODEL_TTS_1_HD)

    /** OpenAI speech input limit; longer text falls back to system TTS. */
    const val MAX_INPUT_CHARS = 4096

    fun normalizeModel(model: String?): String =
        if (model != null && MODELS.contains(model)) model else DEFAULT_MODEL

    fun normalizeVoice(voice: String?): String =
        if (voice != null && VOICES.contains(voice)) voice else DEFAULT_VOICE

    /**
     * Stable cache / billing key: SHA-256 hex of model + voice + text.
     * Same cleaned message with the same voice/model reuses the cached audio file.
     */
    fun cacheKey(text: String, voice: String, model: String): String {
        val payload = "${normalizeModel(model)}\u0000${normalizeVoice(voice)}\u0000$text"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun isWithinApiLimit(text: String): Boolean = text.length <= MAX_INPUT_CHARS

    /** Keys shorter than this are shown fully masked so the hint can never reveal most of the key. */
    private const val MIN_KEY_LENGTH_FOR_HINT = 16

    /**
     * Masked preview of a saved key for the "Saved: sk-…abcd" indicator: first 3 + last 4 characters of a
     * real-length key, "••••" for short ones, null when there is no key. Never the key itself.
     */
    fun maskKey(key: String?): String? {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.length < MIN_KEY_LENGTH_FOR_HINT) return "••••"
        return trimmed.take(3) + "…" + trimmed.takeLast(4)
    }
}

/** Non-secret OpenAI TTS choices persisted with the rest of [com.example.telegramnarrator.domain.tts.TtsSettings]. */
data class OpenAiTtsOptions(
    // Whether OpenAI is used is TtsSettings.provider == SpeechProvider.OPENAI (one engine choice for all engines)
    val model: String = OpenAiTts.DEFAULT_MODEL,
    val voice: String = OpenAiTts.DEFAULT_VOICE
)
